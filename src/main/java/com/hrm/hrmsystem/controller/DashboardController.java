package com.hrm.hrmsystem.controller;

import com.hrm.hrmsystem.dto.LeaveBalanceDTO;
import com.hrm.hrmsystem.dto.PayslipDTO;
import com.hrm.hrmsystem.engine.AttendanceEngine;
import com.hrm.hrmsystem.engine.AttendanceSummary;
import com.hrm.hrmsystem.model.Attendance;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.Leave;
import com.hrm.hrmsystem.model.User;
import com.hrm.hrmsystem.repository.PayrollRepository;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.repository.LeaveRepository;
import com.hrm.hrmsystem.repository.UserRepository;
import com.hrm.hrmsystem.service.AttendanceService;
import com.hrm.hrmsystem.service.EmployeeService;
import com.hrm.hrmsystem.service.LeaveService;
import com.hrm.hrmsystem.service.PayslipService;
import com.hrm.hrmsystem.service.UnifiedCalculationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final AttendanceService attendanceService;
    private final EmployeeService employeeService;
    private final LeaveService leaveService;
    private final PayslipService payslipService;
    private final EmployeeRepository employeeRepository;
    private final LeaveRepository leaveRepository;
    private final UserRepository userRepository;
    private final PayrollRepository payrollRepository;
    private final UnifiedCalculationService unifiedCalculationService;

    private final AttendanceEngine attendanceEngine;

    public DashboardController(
            AttendanceService attendanceService,
            EmployeeService employeeService,
            LeaveService leaveService,
            PayslipService payslipService,
            EmployeeRepository employeeRepository,
            LeaveRepository leaveRepository,
            UserRepository userRepository,
            PayrollRepository payrollRepository,
            UnifiedCalculationService unifiedCalculationService,
            AttendanceEngine attendanceEngine) {
        this.attendanceService = attendanceService;
        this.employeeService = employeeService;
        this.leaveService = leaveService;
        this.payslipService = payslipService;
        this.employeeRepository = employeeRepository;
        this.leaveRepository = leaveRepository;
        this.userRepository = userRepository;
        this.payrollRepository = payrollRepository;
        this.unifiedCalculationService = unifiedCalculationService;
        this.attendanceEngine = attendanceEngine;
    }

    /**
     * Get current user's dashboard data (for employees)
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyDashboard() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            // Get user
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }
            
            User user = userOpt.get();
            
            // Get employee data if linked
            Map<String, Object> dashboardData = new HashMap<>();
            dashboardData.put("username", user.getUsername());
            dashboardData.put("role", user.getRole());
            dashboardData.put("email", user.getEmail());
            
            if (user.getEmployee() != null) {
                Employee employee = user.getEmployee();
                Long employeeId = employee.getId();
                
                log.info("Dashboard DEBUG - User {} linked to employee ID: {}", username, employeeId);
                
                dashboardData.put("employeeId", employeeId);
                dashboardData.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
                dashboardData.put("department", employee.getDepartment() != null ? employee.getDepartment().getName() : "N/A");
                dashboardData.put("designation", employee.getDesignation());
                
                // Get attendance summary for current month - USE SINGLE SOURCE OF TRUTH
                try {
                    if (employeeService.isSystemUser(employee)) {
                        dashboardData.put("attendance", Map.of(
                            "presentDays", 0.0,
                            "absentDays", 0.0,
                            "totalHours", 0.0,
                            "paidUsedLeaves", 0.0,
                            "unpaidUsedLeaves", 0.0,
                            "usedLeaves", 0.0
                        ));
                        dashboardData.put("leaveBalance", LeaveBalanceDTO.builder()
                            .totalEarnedLeaves(0.0)
                            .usedLeaves(0.0)
                            .paidLeaves(0.0)
                            .unpaidLeaves(0.0)
                            .availableLeaves(0.0)
                            .remaining(0.0)
                            .build());
                    } else {
                        LocalDate today = LocalDate.now();
                        YearMonth currentMonth = YearMonth.of(today.getYear(), today.getMonthValue());

                        // ✅ Use centralized method to construct attendance stats map (Single Source of Truth)
                        Map<String, Object> attendancePayload = getMyAttendanceSummaryPayload(employeeId, currentMonth);
                        dashboardData.put("attendance", attendancePayload);
                        
                        // ✅ Use LeaveService for leave balance (Cumulative) - SINGLE SOURCE OF TRUTH
                        LeaveBalanceDTO balance = leaveService.getLeaveBalance(employeeId, currentMonth);
                        dashboardData.put("leaveBalance", balance);
                    }
                } catch (Exception e) {
                    dashboardData.put("attendance", Map.of("presentDays", 0, "absentDays", 0, "paidUsedLeaves", 0, "unpaidUsedLeaves", 0, "totalHours", 0, "payableDays", 0));
                }
                
            } else {
                log.warn("Dashboard DEBUG - User {} is not linked to any employee", username);
            } // End of if (user.getEmployee() != null)
            
            return ResponseEntity.ok(dashboardData);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to load dashboard: " + e.getMessage()));
        }
    }

    /**
     * Get management dashboard stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getDashboardStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            LocalDate today = LocalDate.now();
            
            // Employee stats
            List<Employee> nonSystemEmployees = employeeRepository.findAllWithDepartment().stream()
                    .filter(e -> !employeeService.isSystemUser(e))
                    .toList();
            long totalEmployees = nonSystemEmployees.size();
            List<Employee> activeEmployeesList = nonSystemEmployees.stream()
                    .filter(e -> e.getStatus() == Employee.EmployeeStatus.ACTIVE)
                    .toList();
            long activeEmployees = activeEmployeesList.size();
            
            // Leave stats for today
            List<Leave> approvedLeaves = leaveRepository.findByStatusWithEmployee(Leave.LeaveStatus.APPROVED);
            long onLeaveToday = approvedLeaves.stream()
                    .filter(l -> l.getEmployee() != null && !employeeService.isSystemUser(l.getEmployee()))
                    .filter(l -> !today.isBefore(l.getStartDate()) && !today.isAfter(l.getEndDate()))
                    .count();

            stats.put("employees", Map.of(
                "total", totalEmployees,
                "active", activeEmployees,
                "onLeave", onLeaveToday,
                "newThisMonth", 0
            ));
            
            // Attendance stats
            long presentToday = attendanceService.getPresentTodayCount();
            
            stats.put("attendance", Map.of(
                "presentToday", presentToday,
                "averageHours", 8.0, // Default or calculate if needed
                "onTime", presentToday, // Simplified
                "late", 0
            ));
            
            // Payroll stats
            try {
                // Determine target month and year based on the latest processed month/year
                // Fallback to the current calendar month/year.
                int targetMonth = today.getMonthValue();
                int targetYear = today.getYear();

                Optional<com.hrm.hrmsystem.model.Payroll> latestProcessed = payrollRepository.findAll().stream()
                        .filter(p -> p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.PAID || 
                                     p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.APPROVED ||
                                     p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.PENDING_APPROVAL ||
                                     p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.PENDING)
                        .max((p1, p2) -> {
                            int yearComp = p1.getYear().compareTo(p2.getYear());
                            if (yearComp != 0) return yearComp;
                            return p1.getMonth().compareTo(p2.getMonth());
                        });

                if (latestProcessed.isPresent()) {
                    targetMonth = latestProcessed.get().getMonth();
                    targetYear = latestProcessed.get().getYear();
                }

                // Read from existing payroll table instead of triggering regeneration
                List<com.hrm.hrmsystem.model.Payroll> monthPayrolls = payrollRepository.findByMonthAndYear(targetMonth, targetYear);
                List<com.hrm.hrmsystem.model.Payroll> filteredPayrolls = monthPayrolls.stream()
                        .filter(p -> p.getEmployee() != null && !employeeService.isSystemUser(p.getEmployee()))
                        .toList();

                // Total Monthly Payroll = Sum of actual processed payroll net salaries if exists, fallback to active employees' budgeted net salaries
                double totalPayroll;
                if (!filteredPayrolls.isEmpty()) {
                    totalPayroll = filteredPayrolls.stream()
                            .map(com.hrm.hrmsystem.model.Payroll::getNetSalary)
                            .filter(java.util.Objects::nonNull)
                            .map(BigDecimal::doubleValue)
                            .mapToDouble(Double::doubleValue)
                            .sum();
                } else {
                    totalPayroll = activeEmployeesList.stream()
                            .map(Employee::getTotalNetSalary)
                            .filter(java.util.Objects::nonNull)
                            .map(BigDecimal::doubleValue)
                            .mapToDouble(Double::doubleValue)
                            .sum();
                }
                
                // Salary Paid = Sum of net salaries of PAID or APPROVED payroll records
                double salaryPaid = filteredPayrolls.stream()
                        .filter(p -> p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.PAID || 
                                     p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.APPROVED)
                        .map(com.hrm.hrmsystem.model.Payroll::getNetSalary)
                        .filter(java.util.Objects::nonNull)
                        .map(BigDecimal::doubleValue)
                        .mapToDouble(Double::doubleValue)
                        .sum();
                
                double remainingPayroll = totalPayroll - salaryPaid;
                if (remainingPayroll < 0) remainingPayroll = 0;

                double pendingAmount = totalPayroll - salaryPaid;
                if (pendingAmount < 0) pendingAmount = 0;
                
                long pendingCount = filteredPayrolls.stream()
                        .filter(p -> p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.PENDING || 
                                     p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.PENDING_APPROVAL)
                        .count();
                
                stats.put("payroll", Map.of(
                    "totalAmount", remainingPayroll,
                    "salaryPaid", salaryPaid,
                    "pendingAmount", pendingAmount,
                    "processed", filteredPayrolls.size(),
                    "pending", pendingCount
                ));
            } catch (Exception e) {
                stats.put("payroll", Map.of("totalAmount", 0, "salaryPaid", 0, "processed", 0, "pending", 0));
            }
            
            // Leave stats
            try {
                List<com.hrm.hrmsystem.dto.LeaveDTO> rawPendingLeaves = leaveService.getPendingLeaves();
                List<com.hrm.hrmsystem.dto.LeaveDTO> pendingLeaves = rawPendingLeaves.stream()
                        .filter(l -> l.getEmployeeId() != null)
                        .filter(l -> {
                            Optional<Employee> empOpt = employeeRepository.findByIdentifier(l.getEmployeeId());
                            return empOpt.isPresent() && !employeeService.isSystemUser(empOpt.get());
                        })
                        .toList();
                
                // Count approved/rejected TODAY (simplified)
                long approvedToday = approvedLeaves.stream()
                        .filter(l -> l.getEmployee() != null && !employeeService.isSystemUser(l.getEmployee()))
                        .filter(l -> today.equals(l.getAppliedDate())) // Or check an approvalDate if exists
                        .count();

                stats.put("leaves", Map.of(
                    "pendingRequests", pendingLeaves.size(),
                    "approvedToday", approvedToday,
                    "rejectedToday", 0
                ));
            } catch (Exception e) {
                stats.put("leaves", Map.of("pendingRequests", 0, "approvedToday", 0, "rejectedToday", 0));
            }
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to load stats: " + e.getMessage()));
        }
    }
    
    /**
     * Get current user's latest payslip
     */
    @GetMapping("/my/payslip")
    public ResponseEntity<?> getMyLatestPayslip() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty() || userOpt.get().getEmployee() == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Employee not found"));
            }
            
            Long employeeId = userOpt.get().getEmployee().getId();
            if (employeeService.isSystemUser(userOpt.get().getEmployee())) {
                return ResponseEntity.ok(Map.of("message", "No payslips found"));
            }
            List<PayslipDTO> payslips = payslipService.getPayslipsByEmployee(employeeId);
            
            if (payslips.isEmpty()) {
                return ResponseEntity.ok(Map.of("message", "No payslips found"));
            }
            
            // Sort by monthYear descending
            payslips.sort((a, b) -> b.getMonthYear().compareTo(a.getMonthYear()));
            return ResponseEntity.ok(payslips.get(0));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get current user's attendance summary
     * Uses LeaveRepository for leave statistics (source of truth)
     */
    @GetMapping("/my/attendance/daily")
    public ResponseEntity<?> getMyDailyAttendance(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty() || userOpt.get().getEmployee() == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Employee not found"));
            }

            Long employeeId = userOpt.get().getEmployee().getId();
            if (employeeService.isSystemUser(userOpt.get().getEmployee())) {
                return ResponseEntity.ok(List.of());
            }
            LocalDate today = LocalDate.now();
            
            // Use provided month/year or default to current
            int targetMonth = month != null ? month : today.getMonthValue();
            int targetYear = year != null ? year : today.getYear();
            YearMonth yearMonth = YearMonth.of(targetYear, targetMonth);
            
            // ✅ Use AttendanceEngine for detailed daily calculation
            List<Map<String, Object>> dailyData = attendanceEngine.getDailyAttendanceDetails(employeeId, targetYear, targetMonth);
            
            return ResponseEntity.ok(dailyData);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    private Map<String, Object> getMyAttendanceSummaryPayload(Long employeeId, YearMonth yearMonth) {
        return getMyAttendanceSummaryPayload(employeeId, yearMonth, null);
    }

    private Map<String, Object> getMyAttendanceSummaryPayload(Long employeeId, YearMonth yearMonth, LocalDate excludeLeavesFromDate) {
        // Use UnifiedCalculationService to leverage caching
        AttendanceSummary summary = unifiedCalculationService.calculateForPayroll(employeeId, yearMonth.getYear(), yearMonth.getMonthValue());
        LeaveBalanceDTO balance = leaveService.getLeaveBalance(employeeId, yearMonth, excludeLeavesFromDate);

        // ✅ CRITICAL FIX: Use getLeaveSummary() for monthly paid/unpaid — SAME source as PayslipService.calculateAttendance()
        // PayslipService uses: leaveSummary.usedThisMonth (currentMonthUsed) and leaveSummary.unpaidThisMonth (currentMonthUnpaid)
        // Previously this used summary.paidLeave / summary.unpaidLeave which diverge from the payslip calculation
        UnifiedCalculationService.LeaveBalanceResult leaveSummary = unifiedCalculationService.getLeaveSummary(employeeId, yearMonth, excludeLeavesFromDate);

        Map<String, Object> result = new HashMap<>();
        
        // Attendance fields
        result.put("presentDays", summary.getPresentDays());
        result.put("absentDays", summary.absent);
        result.put("totalHours", summary.getPresentDays() * 8.0);
        
        // ✅ FIXED: Monthly paid/unpaid now uses same source as payslip (currentMonthUsed / currentMonthUnpaid)
        double monthlyPaid = leaveSummary.usedThisMonth;
        double monthlyUnpaid = leaveSummary.unpaidThisMonth;
        double monthlyUsed = monthlyPaid + monthlyUnpaid;

        result.put("paidUsedLeaves", monthlyPaid);
        result.put("unpaidUsedLeaves", monthlyUnpaid);
        // Generic usedLeaves field for UI
        // ✅ FIXED: UI uses this for "Total Used Leaves (Cycle)", so we must return the cycle total
        result.put("usedLeaves", leaveSummary.totalUsed);
        // Keep monthly used as separate if needed
        result.put("monthlyUsedLeaves", monthlyUsed);
        
        // Cumulative leave balance values (from LeaveService)
        double totalEarned = balance.getTotalEarnedLeaves();
        double remaining = balance.getAvailableLeaves();
        
        result.put("totalEarned", totalEarned);
        result.put("remainingLeaveBalance", remaining);
        
        // Alias keys for /leave-balance/ compatibility (unifying backend payloads)
        result.put("used", balance.getPaidLeaves());
        result.put("unpaid", balance.getUnpaidLeaves());
        result.put("totalUsed", balance.getUsedLeaves());
        result.put("totalPaidUsedLeaves", balance.getPaidLeaves()); // ✅ FIXED: Was getUsedLeaves()
        result.put("remaining", remaining);
        
        // Context/Date helper fields
        result.put("month", yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        result.put("year", yearMonth.getYear());
        result.put("monthValue", yearMonth.getMonthValue());
        
        return result;
    }

    @GetMapping("/my/attendance")
    public ResponseEntity<?> getMyAttendanceSummary(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty() || userOpt.get().getEmployee() == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Employee not found"));
            }

            Long employeeId = userOpt.get().getEmployee().getId();
            if (employeeService.isSystemUser(userOpt.get().getEmployee())) {
                Map<String, Object> result = new HashMap<>();
                result.put("presentDays", 0.0);
                result.put("absentDays", 0.0);
                result.put("paidUsedLeaves", 0.0);
                result.put("unpaidUsedLeaves", 0.0);
                result.put("usedLeaves", 0.0);
                result.put("remainingLeaveBalance", 0.0);
                result.put("totalEarned", 0.0);
                result.put("totalHours", 0.0);
                
                // Alias keys
                result.put("used", 0.0);
                result.put("unpaid", 0.0);
                result.put("totalUsed", 0.0);
                result.put("remaining", 0.0);
                
                result.put("month", LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")));
                result.put("year", LocalDate.now().getYear());
                result.put("monthValue", LocalDate.now().getMonthValue());
                return ResponseEntity.ok(result);
            }
            LocalDate today = LocalDate.now();
            int targetMonth = month != null ? month : today.getMonthValue();
            int targetYear = year != null ? year : today.getYear();
            YearMonth yearMonth = YearMonth.of(targetYear, targetMonth);

            return ResponseEntity.ok(getMyAttendanceSummaryPayload(employeeId, yearMonth));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get attendance summary for any employee (for admin/HR use)
     * Uses unified calculation logic to align with employee view and payroll
     */
    @GetMapping("/attendance/{employeeId}")
    public ResponseEntity<?> getEmployeeAttendanceSummary(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        try {
            Employee employee = employeeRepository.findByIdentifier(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
            LocalDate today = LocalDate.now();
            int targetMonth = (month != null) ? month : today.getMonthValue();
            int targetYear = (year != null) ? year : today.getYear();
            YearMonth yearMonth = YearMonth.of(targetYear, targetMonth);

            return ResponseEntity.ok(getMyAttendanceSummaryPayload(employee.getId(), yearMonth));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get leave balance for specific month (for admin leave application)
     * Delegates to the same unified helper method to guarantee same API fields and calculations
     */
    @GetMapping("/leave-balance/{employeeId}")
    public ResponseEntity<?> getEmployeeLeaveBalanceForMonth(
            @PathVariable Long employeeId,
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(required = false) String startDate) {
        try {
            Employee employee = employeeRepository.findByIdentifier(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
            YearMonth targetMonth = YearMonth.of(year, month);
            LocalDate excludeFromDate = null;
            if (startDate != null && !startDate.trim().isEmpty()) {
                excludeFromDate = LocalDate.parse(startDate);
            }
            return ResponseEntity.ok(getMyAttendanceSummaryPayload(employee.getId(), targetMonth, excludeFromDate));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    @GetMapping("/payroll-summary")
    public ResponseEntity<Map<String, Object>> getMonthlyPayrollSummary() {
        try {
            LocalDate today = LocalDate.now();
            int targetMonth = today.getMonthValue();
            int targetYear = today.getYear();

            Optional<com.hrm.hrmsystem.model.Payroll> latestProcessed = payrollRepository.findAll().stream()
                    .filter(p -> p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.PAID || 
                                 p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.APPROVED ||
                                 p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.PENDING_APPROVAL ||
                                 p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.PENDING)
                    .max((p1, p2) -> {
                        int yearComp = p1.getYear().compareTo(p2.getYear());
                        if (yearComp != 0) return yearComp;
                        return p1.getMonth().compareTo(p2.getMonth());
                    });

            if (latestProcessed.isPresent()) {
                targetMonth = latestProcessed.get().getMonth();
                targetYear = latestProcessed.get().getYear();
            }

            List<com.hrm.hrmsystem.model.Payroll> monthPayrolls = payrollRepository.findByMonthAndYear(targetMonth, targetYear);
            List<com.hrm.hrmsystem.model.Payroll> filteredPayrolls = monthPayrolls.stream()
                    .filter(p -> p.getEmployee() != null && !employeeService.isSystemUser(p.getEmployee()))
                    .toList();

            double netPayroll;
            if (!filteredPayrolls.isEmpty()) {
                netPayroll = filteredPayrolls.stream()
                        .map(com.hrm.hrmsystem.model.Payroll::getNetSalary)
                        .filter(java.util.Objects::nonNull)
                        .map(BigDecimal::doubleValue)
                        .mapToDouble(Double::doubleValue)
                        .sum();
            } else {
                netPayroll = employeeRepository.findAllWithDepartment().stream()
                        .filter(e -> e.getStatus() == Employee.EmployeeStatus.ACTIVE && !employeeService.isSystemUser(e))
                        .map(Employee::getTotalNetSalary)
                        .filter(java.util.Objects::nonNull)
                        .map(BigDecimal::doubleValue)
                        .mapToDouble(Double::doubleValue)
                        .sum();
            }

            // Fetch Net Salary from finalized payslips/payrolls for the target month
            double netSalary = filteredPayrolls.stream()
                    .filter(p -> p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.PAID || 
                                 p.getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.APPROVED)
                    .map(com.hrm.hrmsystem.model.Payroll::getNetSalary)
                    .filter(java.util.Objects::nonNull)
                    .map(BigDecimal::doubleValue)
                    .mapToDouble(Double::doubleValue)
                    .sum();

            Map<String, Object> response = new HashMap<>();
            response.put("salaryPaid", netSalary);
            response.put("monthlyPayroll", netPayroll);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

}
