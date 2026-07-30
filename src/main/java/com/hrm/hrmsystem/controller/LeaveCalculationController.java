package com.hrm.hrmsystem.controller;

import com.hrm.hrmsystem.dto.LeaveBalanceDTO;
import com.hrm.hrmsystem.engine.AttendanceEngine;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.User;
import com.hrm.hrmsystem.repository.UserRepository;
import com.hrm.hrmsystem.service.UnifiedCalculationService;
import com.hrm.hrmsystem.service.UnifiedCalculationService.LeaveStatistics;
import com.hrm.hrmsystem.service.LeaveService;
import com.hrm.hrmsystem.service.PayrollService;
import com.hrm.hrmsystem.service.PayslipService;
import com.hrm.hrmsystem.dto.LeaveDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * REST Controller for centralized leave calculations
 * Single endpoint for all leave statistics - used by all pages
 */
@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
@Slf4j
public class LeaveCalculationController {

    private final UnifiedCalculationService unifiedCalculationService;
    private final AttendanceEngine attendanceEngine;
    private final UserRepository userRepository;
    private final LeaveService leaveService;
    private final PayrollService payrollService;
    private final PayslipService payslipService;

    /**
     * Get complete leave statistics for an employee
     * Used by: leave.html, payslips.html, my-leaves.html, payroll.js
     * 
     * @param employeeId Employee ID
     * @param month Month (1-12), defaults to current month
     * @param year Year, defaults to current year
     * @return LeaveStatistics with all calculated data
     */
    @GetMapping("/calculation/employee/{employeeId}")
    public ResponseEntity<?> getEmployeeLeaveStatistics(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        
        try {
            // Default to current month/year if not provided
            LocalDate now = LocalDate.now();
            int targetMonth = month != null ? month : now.getMonthValue();
            int targetYear = year != null ? year : now.getYear();
            
            // Validate month and year
            if (targetMonth < 1 || targetMonth > 12) {
                return ResponseEntity.badRequest().body("Invalid month: must be between 1 and 12");
            }
            if (targetYear < 2000 || targetYear > 2100) {
                return ResponseEntity.badRequest().body("Invalid year: must be between 2000 and 2100");
            }
            
            log.info("Fetching leave statistics for employee {}: month={}, year={}", employeeId, targetMonth, targetYear);
            
            LeaveStatistics stats = unifiedCalculationService.calculateLeaveStatistics(employeeId, targetMonth, targetYear);
            return ResponseEntity.ok(stats);
        } catch (RuntimeException e) {
            log.error("Error fetching leave statistics for employee {}: {}", employeeId, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching leave statistics for employee {}: {}", employeeId, e.getMessage());
            return ResponseEntity.internalServerError().body("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Get current month leave statistics for an employee
     * Shortcut endpoint for current month
     */
    @GetMapping("/calculation/employee/{employeeId}/current")
    public ResponseEntity<?> getCurrentMonthStatistics(@PathVariable Long employeeId) {
        LocalDate now = LocalDate.now();
        return getEmployeeLeaveStatistics(employeeId, now.getMonthValue(), now.getYear());
    }

    // ===== MISSING API ENDPOINTS FOR FRONTEND =====

    /**
     * Get my leave balance - fixed API
     * Used by: Dashboard and Leave History pages
     * Endpoint: /api/leaves/balance-fixed/my
     */
    @GetMapping("/balance-fixed/my")
    public ResponseEntity<?> getMyLeaveBalanceFixed() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            
            var userOpt = userRepository.findByUsername(email);
            if (userOpt.isEmpty() || userOpt.get().getEmployee() == null) {
                return ResponseEntity.status(404).body("Employee not found");
            }

            Employee employee = userOpt.get().getEmployee();
            
            // Delegate to LeaveService (single source of truth for DTO building)
            LeaveBalanceDTO balance = leaveService.getLeaveBalance(employee.getId());
            return ResponseEntity.ok(balance);
            
        } catch (Exception e) {
            log.error("Error in leave balance fixed API", e);
            return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Get my leaves
     * Used by: My Leaves page
     * Endpoint: /api/leaves/my
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyLeaves() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            
            var userOpt = userRepository.findByUsername(email);
            if (userOpt.isEmpty() || userOpt.get().getEmployee() == null) {
                return ResponseEntity.status(404).body("Employee not found");
            }

            Employee employee = userOpt.get().getEmployee();
            Long employeeId = employee.getId();
            
            // Get all leaves for the employee using LeaveService
            var leaves = leaveService.getLeavesByEmployee(employeeId);
            
            return ResponseEntity.ok(leaves);
            
        } catch (Exception e) {
            log.error("Error in get my leaves API", e);
            return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Get all leaves - HR/Admin only (full access).
     * Used by: Management Dashboard, Leave Management pages
     * Endpoint: /api/leaves
     */
    @GetMapping
    public ResponseEntity<?> getAllLeaves() {
        try {
            return ResponseEntity.ok(leaveService.getAllLeaves());
        } catch (Exception e) {
            log.error("Error fetching all leaves", e);
            return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Get leaves for approval queue based on role:
     * - HR/Admin → returns ALL leaves (full management view)
     * - Other management roles (Accountant, Leaves, Director, Manager) → returns ONLY leaves
     *   assigned to the current user as approver
     * Endpoint: /api/leaves/my-approvals
     */
    @GetMapping("/my-approvals")
    public ResponseEntity<?> getMyApprovalLeaves(Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }

            // Check if HR or Admin → full access
            boolean isHrOrAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_HR") || a.getAuthority().equals("ROLE_ADMIN"));

            if (isHrOrAdmin) {
                // HR and Admin see ALL leaves
                return ResponseEntity.ok(leaveService.getAllLeaves());
            }

            // Non-HR management: only see leaves assigned to them as approver
            String username = authentication.getName();
            var userOpt = userRepository.findByUsername(username);

            if (userOpt.isEmpty() || userOpt.get().getEmployee() == null) {
                // User has no linked employee — return empty list (not an error)
                log.warn("Management user '{}' has no linked employee profile; returning empty approvals list", username);
                return ResponseEntity.ok(java.util.Collections.emptyList());
            }

            Long approverEmployeeId = userOpt.get().getEmployee().getId();
            log.info("User '{}' (role: {}) loading approval queue for employee ID {}", 
                    username, authentication.getAuthorities(), approverEmployeeId);

            return ResponseEntity.ok(leaveService.getLeavesByApprover(approverEmployeeId));

        } catch (Exception e) {
            log.error("Error fetching approval queue", e);
            return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Get leave details by ID
     * Endpoint: /api/leaves/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getLeaveById(@PathVariable Long id) {
        try {
            var leave = leaveService.getLeaveById(id);
            return ResponseEntity.ok(leave);
        } catch (Exception e) {
            return ResponseEntity.status(404).body("Leave not found: " + e.getMessage());
        }
    }

    /**
     * Get leave balance of a specific employee (Admin view)
     * Endpoint: /api/leaves/balance/{employeeId}
     */
    @GetMapping("/balance/{employeeId}")
    public ResponseEntity<?> getEmployeeLeaveBalance(@PathVariable Long employeeId) {
        try {
            LeaveBalanceDTO balance = leaveService.getLeaveBalance(employeeId);
            return ResponseEntity.ok(balance);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /**
     * Apply for leave
     * Used by: Apply Leave modal
     * Endpoint: /api/leaves
     */
    @PostMapping
    public ResponseEntity<?> applyLeave(@RequestBody com.hrm.hrmsystem.dto.LeaveDTO leaveDTO) {
        try {
            return ResponseEntity.ok(leaveService.applyLeave(leaveDTO));
        } catch (Exception e) {
            log.error("Error applying for leave", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Alias for applyLeave to match frontend
     * Endpoint: /api/leaves/apply
     * SECURITY: Validates that the requesting employee can only apply leave for themselves.
     */
    @PostMapping("/apply")
    public ResponseEntity<?> applyLeaveAlias(
            @RequestBody com.hrm.hrmsystem.dto.LeaveDTO leaveDTO,
            Authentication authentication) {
        try {
            // --- Employee self-service guard -------------------------------------------
            // Resolve the authenticated user's linked employee
            String username = authentication.getName();
            var userOpt = userRepository.findByUsername(username);

            boolean isManagementRole = authentication.getAuthorities().stream()
                    .anyMatch(a -> {
                        String auth2 = a.getAuthority();
                        return auth2.equals("ROLE_ADMIN") || auth2.equals("ROLE_HR")
                            || auth2.equals("ROLE_ACCOUNTANT") || auth2.equals("ROLE_DIRECTOR")
                            || auth2.equals("ROLE_LEAVES") || auth2.equals("ROLE_MANAGER");
                    });

            if (!isManagementRole && userOpt.isPresent()) {
                var linkedEmployee = userOpt.get().getEmployee();
                if (linkedEmployee != null && leaveDTO.getEmployeeId() != null
                        && !linkedEmployee.getId().equals(leaveDTO.getEmployeeId())) {
                    log.warn("SECURITY: User {} attempted to apply leave for employee {} but is linked to employee {}",
                            username, leaveDTO.getEmployeeId(), linkedEmployee.getId());
                    return ResponseEntity.status(403)
                            .body("Access denied: You can only apply leave for your own account.");
                }
                // Also enforce: if no employeeId provided, inject from server
                if (leaveDTO.getEmployeeId() == null && linkedEmployee != null) {
                    leaveDTO.setEmployeeId(linkedEmployee.getId());
                }
            }
            // ---------------------------------------------------------------------------

            return applyLeave(leaveDTO);
        } catch (Exception e) {
            log.error("Error in applyLeaveAlias", e);
            return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Approve leave
     * Endpoint: /api/leaves/{id}/approve
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveLeave(@PathVariable Long id, @RequestBody java.util.Map<String, String> request) {
        try {
            String approvedBy = request != null ? request.getOrDefault("approvedBy", "Admin") : "Admin";
            LeaveDTO leaveDTO = leaveService.approveLeave(id, approvedBy);
            triggerRecalculationForLeave(leaveDTO);
            return ResponseEntity.ok(leaveDTO);
        } catch (Exception e) {
            log.error("Error approving leave", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Alias for approveLeave to match frontend (supports POST and query params)
     * Endpoint: /api/leaves/approve/{id}
     */
    @PostMapping("/approve/{id}")
    public ResponseEntity<?> approveLeaveAlias(
            @PathVariable Long id, 
            @RequestParam(required = false) String approvedBy) {
        try {
            LeaveDTO leaveDTO = leaveService.approveLeave(id, approvedBy != null ? approvedBy : "Admin");
            triggerRecalculationForLeave(leaveDTO);
            return ResponseEntity.ok(leaveDTO);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectLeave(@PathVariable Long id, @RequestBody java.util.Map<String, String> request) {
        try {
            String reason = request != null ? request.getOrDefault("reason", "Rejected by Admin") : "Rejected by Admin";
            LeaveDTO existing = leaveService.getLeaveById(id);
            boolean wasApproved = "APPROVED".equals(existing.getStatus());
            
            LeaveDTO leaveDTO = leaveService.rejectLeave(id, reason);
            if (wasApproved) {
                triggerRecalculationForLeave(leaveDTO);
            }
            return ResponseEntity.ok(leaveDTO);
        } catch (Exception e) {
            log.error("Error rejecting leave", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Alias for rejectLeave to match frontend (supports POST and query params)
     * Endpoint: /api/leaves/reject/{id}
     */
    @PostMapping("/reject/{id}")
    public ResponseEntity<?> rejectLeaveAlias(
            @PathVariable Long id, 
            @RequestParam(required = false, name = "rejectionReason") String reason) {
        try {
            LeaveDTO existing = leaveService.getLeaveById(id);
            boolean wasApproved = "APPROVED".equals(existing.getStatus());
            
            LeaveDTO leaveDTO = leaveService.rejectLeave(id, reason != null ? reason : "Rejected by Admin");
            if (wasApproved) {
                triggerRecalculationForLeave(leaveDTO);
            }
            return ResponseEntity.ok(leaveDTO);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelLeave(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> request) {
        try {
            String reason = request != null ? request.getOrDefault("reason", "Cancelled") : "Cancelled";
            LeaveDTO existing = leaveService.getLeaveById(id);
            boolean wasApproved = "APPROVED".equals(existing.getStatus());
            
            LeaveDTO leaveDTO = leaveService.cancelLeave(id, reason);
            if (wasApproved) {
                triggerRecalculationForLeave(leaveDTO);
            }
            return ResponseEntity.ok(leaveDTO);
        } catch (Exception e) {
            log.error("Error cancelling leave", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Alias for cancelLeave to match frontend
     * Endpoint: /api/leaves/cancel/{id}
     */
    @PostMapping("/cancel/{id}")
    public ResponseEntity<?> cancelLeaveAlias(
            @PathVariable Long id, 
            @RequestParam(required = false) String reason) {
        try {
            LeaveDTO existing = leaveService.getLeaveById(id);
            boolean wasApproved = "APPROVED".equals(existing.getStatus());
            
            LeaveDTO leaveDTO = leaveService.cancelLeave(id, reason != null ? reason : "Cancelled");
            if (wasApproved) {
                triggerRecalculationForLeave(leaveDTO);
            }
            return ResponseEntity.ok(leaveDTO);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Modify approved leave
     * Endpoint: /api/leaves/modify/{id}
     */
    @PostMapping("/modify/{id}")
    public ResponseEntity<?> modifyLeave(
            @PathVariable Long id,
            @RequestBody LeaveDTO leaveDTO,
            @RequestParam(required = false) String reason) {
        try {
            LeaveDTO oldLeave = leaveService.getLeaveById(id);
            LeaveDTO updatedLeave = leaveService.modifyLeave(id, leaveDTO, reason != null ? reason : "Leave modified");
            
            triggerRecalculationForLeave(oldLeave);
            triggerRecalculationForLeave(updatedLeave);
            
            return ResponseEntity.ok(updatedLeave);
        } catch (Exception e) {
            log.error("Error modifying leave", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Get my leave balance - legacy endpoint
     * Used by: Frontend pages calling old endpoint
     * Endpoint: /api/leaves/balance
     */
    @GetMapping("/balance")
    public ResponseEntity<?> getMyLeaveBalance() {
        // Alias for fixed endpoint
        return getMyLeaveBalanceFixed();
    }

    @DeleteMapping("/clear-all")
    public ResponseEntity<?> clearAllLeaves() {
        try {
            log.info("Request to clear all leaves received.");
            java.util.Set<String> affected = leaveService.deleteAllLeaves();
            
            log.info("Affected employee-months: {}", affected);
            for (String key : affected) {
                try {
                    String[] parts = key.split("_");
                    Long employeeId = Long.parseLong(parts[0]);
                    int year = Integer.parseInt(parts[1]);
                    int month = Integer.parseInt(parts[2]);
                    
                    log.info("Recalculating payroll/payslip after clearing leaves: employee={}, month={}, year={}", employeeId, month, year);
                    payrollService.generatePayroll(employeeId, month, year);
                    
                    String monthYear = year + "-" + String.format("%02d", month);
                    payslipService.generatePayslip(employeeId, monthYear);
                } catch (Exception ex) {
                    log.error("Error recalculating payroll for key {}: {}", key, ex.getMessage());
                }
            }
            
            return ResponseEntity.ok(java.util.Map.of("message", "All leave records cleared successfully"));
        } catch (RuntimeException e) {
            log.error("Error clearing leaves: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error clearing leaves", e);
            return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
        }
    }

    private void triggerRecalculationForLeave(LeaveDTO leaveDTO) {
        if (leaveDTO == null || leaveDTO.getEmployeeId() == null || leaveDTO.getStartDate() == null || leaveDTO.getEndDate() == null) {
            return;
        }
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            Long employeeId = leaveDTO.getEmployeeId();
            LocalDate current = leaveDTO.getStartDate();
            LocalDate end = leaveDTO.getEndDate();
            
            java.util.Set<YearMonth> affectedMonths = new java.util.HashSet<>();
            while (!current.isAfter(end)) {
                affectedMonths.add(YearMonth.from(current));
                current = current.plusDays(1);
            }
            
            for (YearMonth ym : affectedMonths) {
                try {
                    log.info("Triggering async payroll/payslip recalculation for employee {} in month {}", employeeId, ym);
                    payrollService.generatePayroll(employeeId, ym.getMonthValue(), ym.getYear());
                    
                    String monthYear = ym.getYear() + "-" + String.format("%02d", ym.getMonthValue());
                    payslipService.generatePayslip(employeeId, monthYear);
                } catch (Exception e) {
                    log.error("Failed to recalculate payroll/payslip for employee {} in month {}: {}", employeeId, ym, e.getMessage());
                }
            }
        });
    }
}
