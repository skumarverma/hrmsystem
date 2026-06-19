package com.hrm.hrmsystem.controller;

import com.hrm.hrmsystem.dto.PayslipDTO;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.service.EmployeeService;
import com.hrm.hrmsystem.service.PayslipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payslips")
@Tag(name = "Payslip Management", description = "API for managing employee payslips")
public class PayslipController {

    private static final Logger log = LoggerFactory.getLogger(PayslipController.class);

    @Autowired
    private PayslipService payslipService;

    @Autowired
    private EmployeeService employeeService;

    /**
     * Generate payslip for an employee
     */
    @PostMapping("/generate")
    @Operation(summary = "Generate payslip for employee", description = "Generate payslip for a specific employee and month")
    public ResponseEntity<?> generatePayslip(
            @Parameter(description = "Employee ID") @RequestParam Long employeeId,
            @Parameter(description = "Month-Year in format YYYY-MM") @RequestParam String monthYear) {
        try {
            log.info("Request to generate payslip for employee: {} for month: {}", employeeId, monthYear);
            PayslipDTO payslip = payslipService.generatePayslip(employeeId, monthYear);
            return ResponseEntity.ok(payslip);
        } catch (Exception e) {
            log.error("Error generating payslip: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error generating payslip: " + e.getMessage());
        }
    }

    /**
     * Create payslip for an employee (alternative path to avoid URL conflicts)
     */
    @PostMapping("/generate-for-employee")
    @Operation(summary = "Create payslip for employee", description = "Create payslip for a specific employee and month")
    public ResponseEntity<?> createPayslip(
            @Parameter(description = "Employee ID") @RequestParam Long employeeId,
            @Parameter(description = "Month-Year in format YYYY-MM") @RequestParam String monthYear) {
        return generatePayslip(employeeId, monthYear);
    }

    /**
     * Get all payslips
     */
    @GetMapping
    @Operation(summary = "Get all payslips", description = "Retrieve all payslips from the system")
    public ResponseEntity<?> getAllPayslips() {
        try {
            log.info("Fetching all payslips");
            List<PayslipDTO> payslips = payslipService.getAllPayslips();
            return ResponseEntity.ok(payslips);
        } catch (Exception e) {
            log.error("Error fetching payslips: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error fetching payslips: " + e.getMessage());
        }
    }

    /**
     * Get payslip by ID - constrained to only match numeric IDs
     */
    @GetMapping("/{payslipId:[0-9]+}")
    @Operation(summary = "Get payslip details", description = "Retrieve payslip details by payslip ID")
    public ResponseEntity<?> getPayslip(
            @Parameter(description = "Payslip ID") @PathVariable Long payslipId) {
        try {
            log.info("Fetching payslip with ID: {}", payslipId);
            PayslipDTO payslip = payslipService.getPayslipById(payslipId);
            return ResponseEntity.ok(payslip);
        } catch (Exception e) {
            log.error("Error fetching payslip: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Payslip not found: " + e.getMessage());
        }
    }

    /**
     * Get all payslips for an employee
     */
    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "Get employee payslips", description = "Retrieve all payslips for a specific employee")
    public ResponseEntity<?> getEmployeePayslips(
            @Parameter(description = "Employee ID") @PathVariable Long employeeId) {
        try {
            log.info("Fetching payslips for employee: {}", employeeId);
            List<PayslipDTO> payslips = payslipService.getPayslipsByEmployee(employeeId);
            return ResponseEntity.ok(payslips);
        } catch (Exception e) {
            log.error("Error fetching employee payslips: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Employee not found: " + e.getMessage());
        }
    }

    /**
     * Get payslips for a specific month
     */
    @GetMapping("/month/{monthYear}")
    @Operation(summary = "Get payslips by month", description = "Retrieve all payslips for a specific month")
    public ResponseEntity<?> getPayslipsByMonth(
            @Parameter(description = "Month-Year in format YYYY-MM") @PathVariable String monthYear) {
        try {
            log.info("Fetching payslips for month: {}", monthYear);
            List<PayslipDTO> payslips = payslipService.getPayslipsByMonth(monthYear);
            return ResponseEntity.ok(payslips);
        } catch (Exception e) {
            log.error("Error fetching payslips for month: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error fetching payslips: " + e.getMessage());
        }
    }

    /**
     * Clear and regenerate payslips for a selected month.
     */
    @PostMapping("/regenerate-month")
    @Operation(summary = "Regenerate payslips for month", description = "Deletes existing payslips for the month and regenerates them")
    public ResponseEntity<?> regeneratePayslipsForMonth(
            @Parameter(description = "Month-Year in format YYYY-MM") @RequestParam String monthYear) {
        try {
            log.info("Regenerating payslips for month: {}", monthYear);
            List<PayslipDTO> regenerated = payslipService.regeneratePayslipsForMonth(monthYear);
            return ResponseEntity.ok(regenerated);
        } catch (Exception e) {
            log.error("Error regenerating payslips: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error regenerating payslips: " + e.getMessage());
        }
    }

    /**
     * Get my payslips
     */
    @GetMapping("/my")
    @Operation(summary = "Get my payslips", description = "Retrieve payslips for the currently authenticated employee")
    public ResponseEntity<?> getMyPayslips(@RequestParam(required = false) Integer year) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            Employee employee = employeeService.getEmployeeByEmail(email);
            if (employee == null || employeeService.isSystemUser(employee)) {
                log.warn("No employee found or user is system user: {}", email);
                return ResponseEntity.ok(java.util.Collections.emptyList());
            }
            log.info("Fetching payslips for employee: {}", employee.getId());
            List<PayslipDTO> payslips = payslipService.getPayslipsByEmployee(employee.getId());
            return ResponseEntity.ok(payslips);
        } catch (Exception e) {
            log.error("Error fetching payslips: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error fetching payslips: " + e.getMessage());
        }
    }

    /**
     * Get payslip by employee and month/year
     */
    @GetMapping("/by-employee-month")
    @Operation(summary = "Get payslip by employee and month", description = "Retrieve payslip for a specific employee and month/year")
    public ResponseEntity<?> getPayslipByEmployeeAndMonth(
            @RequestParam Long employeeId,
            @RequestParam String monthYear) {
        try {
            log.info("Fetching payslip for employee: {} and month: {}", employeeId, monthYear);
            PayslipDTO payslip = payslipService.getPayslipByEmployeeAndMonth(employeeId, monthYear);
            return ResponseEntity.ok(payslip);
        } catch (Exception e) {
            log.error("Error fetching payslip: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Payslip not found: " + e.getMessage());
        }
    }

    /**
     * Approve payslip
     */
    @PutMapping("/{payslipId}/approve")
    @Operation(summary = "Approve payslip", description = "Approve a generated payslip")
    public ResponseEntity<?> approvePayslip(
            @Parameter(description = "Payslip ID") @PathVariable Long payslipId,
            @Parameter(description = "Approved by (User ID/Name)") @RequestParam String approvedBy,
            @Parameter(description = "Approval remarks") @RequestParam(required = false) String remarks) {
        try {
            log.info("Approving payslip: {} by: {}", payslipId, approvedBy);
            PayslipDTO payslip = payslipService.approvePayslip(payslipId, approvedBy, remarks);
            return ResponseEntity.ok(payslip);
        } catch (Exception e) {
            log.error("Error approving payslip: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error approving payslip: " + e.getMessage());
        }
    }

    /**
     * Reject payslip
     */
    @PutMapping("/{payslipId}/reject")
    @Operation(summary = "Reject payslip", description = "Reject a generated payslip")
    public ResponseEntity<?> rejectPayslip(
            @Parameter(description = "Payslip ID") @PathVariable Long payslipId,
            @Parameter(description = "Rejection reason") @RequestParam String rejectionReason) {
        try {
            log.info("Rejecting payslip: {}", payslipId);
            PayslipDTO payslip = payslipService.rejectPayslip(payslipId, rejectionReason);
            return ResponseEntity.ok(payslip);
        } catch (Exception e) {
            log.error("Error rejecting payslip: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error rejecting payslip: " + e.getMessage());
        }
    }

    /**
     * Update payslip calculations (admin only)
     */
    @PutMapping("/{payslipId}/update")
    @Operation(summary = "Update payslip", description = "Update payslip calculations before approval")
    public ResponseEntity<?> updatePayslip(
            @Parameter(description = "Payslip ID") @PathVariable Long payslipId,
            @RequestBody Map<String, Object> updates) {
        try {
            log.info("Updating payslip: {} with data: {}", payslipId, updates);
            PayslipDTO payslip = payslipService.updatePayslip(payslipId, updates);
            return ResponseEntity.ok(payslip);
        } catch (Exception e) {
            log.error("Error updating payslip: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error updating payslip: " + e.getMessage());
        }
    }

    /**
     * Generate PDF for payslip
     */
    @GetMapping("/{payslipId}/generate-pdf")
    @Operation(summary = "Generate PDF", description = "Generate PDF file for a payslip")
    public ResponseEntity<?> generatePayslipPdf(
            @Parameter(description = "Payslip ID") @PathVariable Long payslipId) {
        try {
            log.info("Generating PDF for payslip: {}", payslipId);
            String pdfPath = payslipService.generatePayslipPdf(payslipId);
            return ResponseEntity.ok(new ResponseDTO(true, "PDF generated successfully", pdfPath));
        } catch (Exception e) {
            log.error("Error generating PDF: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error generating PDF: " + e.getMessage());
        }
    }

    /**
     * Download PDF payslip
     */
    @GetMapping("/{payslipId}/download-pdf")
    @Operation(summary = "Download/Preview PDF", description = "Download or preview PDF payslip file")
    public ResponseEntity<?> downloadPayslipPdf(
            @Parameter(description = "Payslip ID") @PathVariable Long payslipId,
            @RequestParam(required = false, defaultValue = "false") boolean inline) {
        try {
            log.info("Downloading PDF for payslip: {}, inline={}", payslipId, inline);
            PayslipDTO payslip = payslipService.getPayslipById(payslipId);

            // If PDF isn't generated yet (common after regenerate/attendance edits),
            // generate it automatically before downloading.
            if (payslip == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Payslip not found");
            }

            // Always generate a fresh PDF to ensure the latest layout is generated
            log.info("Generating fresh PDF for payslip {} to apply latest layout changes...", payslipId);
            payslipService.generatePayslipPdf(payslipId);
            payslip = payslipService.getPayslipById(payslipId);

            if (payslip.getPdfFilePath() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("PDF file not found");
            }

            File file = new File(payslip.getPdfFilePath());
            if (!file.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("PDF file not found on server");
            }

            Resource resource = new FileSystemResource(file);
            String formattedMonthYear = "";
            try {
                java.time.YearMonth ym = java.time.YearMonth.parse(payslip.getMonthYear());
                formattedMonthYear = ym.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"));
            } catch (Exception e) {
                formattedMonthYear = payslip.getMonthYear();
            }
            String filename = payslip.getEmployeeName() + " " + formattedMonthYear + " Salary Slip_page-0001.pdf";

            String disposition = inline ? "inline" : "attachment";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("Error downloading/previewing PDF: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error downloading/previewing PDF: " + e.getMessage());
        }
    }

    /**
     * Send payslip to employee
     */
    @PostMapping("/{payslipId}/send")
    @Operation(summary = "Send payslip", description = "Send payslip to employee via email")
    public ResponseEntity<?> sendPayslipToEmployee(
            @Parameter(description = "Payslip ID") @PathVariable Long payslipId) {
        try {
            log.info("Sending payslip: {} to employee", payslipId);
            payslipService.sendPayslipToEmployee(payslipId);
            return ResponseEntity.ok(new ResponseDTO(true, "Payslip sent to employee successfully", null));
        } catch (Exception e) {
            log.error("Error sending payslip: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error sending payslip: " + e.getMessage());
        }
    }

    /**
     * Bulk generate payslips for all employees
     */
    @PostMapping("/bulk-generate")
    @Operation(summary = "Bulk generate payslips", description = "Generate payslips for all employees in a month")
    public ResponseEntity<?> bulkGeneratePayslips(
            @Parameter(description = "Month-Year in format YYYY-MM") @RequestParam String monthYear) {
        try {
            log.info("Bulk generating payslips for month: {}", monthYear);
            List<PayslipDTO> payslips = payslipService.bulkGeneratePayslips(monthYear);
            return ResponseEntity.ok(payslips);
        } catch (Exception e) {
            log.error("Error bulk generating payslips: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error bulk generating payslips: " + e.getMessage());
        }
    }

    /**
     * Get pending approvals
     */
    @GetMapping("/pending-approvals")
    @Operation(summary = "Get pending payslips", description = "Get all payslips pending approval")
    public ResponseEntity<?> getPendingApprovals() {
        try {
            log.info("Fetching pending payslip approvals");
            List<PayslipDTO> payslips = payslipService.getPendingApprovals();
            return ResponseEntity.ok(payslips);
        } catch (Exception e) {
            log.error("Error fetching pending approvals: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching pending approvals: " + e.getMessage());
        }
    }

    /**
     * Delete payslip
     */
    @DeleteMapping("/{payslipId}")
    @Operation(summary = "Delete payslip", description = "Delete a payslip (only DRAFT can be deleted)")
    public ResponseEntity<?> deletePayslip(
            @Parameter(description = "Payslip ID") @PathVariable Long payslipId) {
        try {
            log.info("Deleting payslip: {}", payslipId);
            payslipService.deletePayslip(payslipId);
            return ResponseEntity.ok(new ResponseDTO(true, "Payslip deleted successfully", null));
        } catch (Exception e) {
            log.error("Error deleting payslip: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error deleting payslip: " + e.getMessage());
        }
    }

    /**
     * Inner class for response DTO
     */
    public static class ResponseDTO {
        public boolean success;
        public String message;
        public Object data;

        public ResponseDTO(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
    }
}
