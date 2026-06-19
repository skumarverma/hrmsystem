package com.hrm.hrmsystem.controller;

import com.hrm.hrmsystem.dto.PayrollDTO;
import com.hrm.hrmsystem.service.PayrollService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payroll")
@CrossOrigin(origins = "*")
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    // Get single payroll by ID
    @GetMapping("/{payrollId}")
    public ResponseEntity<PayrollDTO> getPayrollById(@PathVariable Long payrollId) {
        PayrollDTO payroll = payrollService.getPayrollById(payrollId);
        return ResponseEntity.ok(payroll);
    }

    // Update payroll
    @PutMapping("/{payrollId}")
    public ResponseEntity<PayrollDTO> updatePayroll(@PathVariable Long payrollId, @RequestBody PayrollDTO payrollDTO) {
        PayrollDTO updatedPayroll = payrollService.updatePayroll(payrollId, payrollDTO);
        return ResponseEntity.ok(updatedPayroll);
    }

    // Get payrolls by month and year (query params) - used by frontend
    @GetMapping
    public ResponseEntity<List<PayrollDTO>> getPayrollByMonthYear(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        if (month != null && year != null) {
            return ResponseEntity.ok(payrollService.getPayrollByMonth(month, year));
        }
        List<PayrollDTO> payrolls = payrollService.getAllPayrolls();
        return ResponseEntity.ok(payrolls);
    }

    // Get all payrolls
    @GetMapping("/list")
    public ResponseEntity<List<PayrollDTO>> getAllPayrolls() {
        List<PayrollDTO> payrolls = payrollService.getAllPayrolls();
        return ResponseEntity.ok(payrolls);
    }

    // Generate payroll for one employee (POST with JSON body)
    @PostMapping("/generate")
    public ResponseEntity<PayrollDTO> generatePayroll(@RequestBody Map<String, Object> request) {
        Long employeeId = Long.parseLong(request.get("employeeId").toString());
        Integer month = Integer.parseInt(request.get("month").toString());
        Integer year = Integer.parseInt(request.get("year").toString());
        
        PayrollDTO payroll = payrollService.generatePayroll(employeeId, month, year);
        return ResponseEntity.ok(payroll);
    }

    // Generate payroll for all employees (POST with JSON body)
    @PostMapping("/generate-all")
    public ResponseEntity<List<PayrollDTO>> generatePayrollForAll(@RequestBody Map<String, Object> request) {
        Integer month = Integer.parseInt(request.get("month").toString());
        Integer year = Integer.parseInt(request.get("year").toString());
        
        List<PayrollDTO> payrolls = payrollService.generatePayrollForAll(month, year);
        return ResponseEntity.ok(payrolls);
    }

    // Process payroll
    @PostMapping("/process/{payrollId}")
    public ResponseEntity<PayrollDTO> processPayroll(@PathVariable Long payrollId) {
        PayrollDTO payroll = payrollService.processPayroll(payrollId);
        return ResponseEntity.ok(payroll);
    }

    // Mark as paid
    @PostMapping("/pay/{payrollId}")
    public ResponseEntity<PayrollDTO> markAsPaid(@PathVariable Long payrollId) {
        PayrollDTO payroll = payrollService.markAsPaid(payrollId);
        return ResponseEntity.ok(payroll);
    }

    // Mark as unpaid
    @PostMapping("/unpay/{payrollId}")
    public ResponseEntity<PayrollDTO> markAsUnpaid(@PathVariable Long payrollId) {
        PayrollDTO payroll = payrollService.markAsUnpaid(payrollId);
        return ResponseEntity.ok(payroll);
    }

    // Send payroll for accountant approval
    @PostMapping("/send-for-approval/{payrollId}")
    public ResponseEntity<PayrollDTO> sendForApproval(@PathVariable Long payrollId, @RequestBody Map<String, String> request) {
        String approvedBy = request.getOrDefault("approvedBy", "HR");
        PayrollDTO payroll = payrollService.sendForApproval(payrollId, approvedBy);
        return ResponseEntity.ok(payroll);
    }

    // Approve payroll by accountant
    @PostMapping("/approve/{payrollId}")
    public ResponseEntity<?> approveByAccountant(@PathVariable Long payrollId, @RequestBody Map<String, String> request) {
        try {
            String accountantName = request.getOrDefault("accountantName", "Accountant");
            PayrollDTO payroll = payrollService.approveByAccountant(payrollId, accountantName);
            return ResponseEntity.ok(payroll);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error approving payroll: " + e.getMessage());
        }
    }

    // Reject payroll by accountant
    @PostMapping("/reject/{payrollId}")
    public ResponseEntity<?> rejectByAccountant(@PathVariable Long payrollId, @RequestBody Map<String, String> request) {
        try {
            String reason = request.getOrDefault("reason", "");
            PayrollDTO payroll = payrollService.rejectByAccountant(payrollId, reason);
            return ResponseEntity.ok(payroll);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error rejecting payroll: " + e.getMessage());
        }
    }

    // Approve payroll by director
    @PostMapping("/approve-director/{payrollId}")
    public ResponseEntity<?> approveByDirector(@PathVariable Long payrollId, @RequestBody Map<String, String> request) {
        try {
            String directorName = request.getOrDefault("directorName", "Director");
            PayrollDTO payroll = payrollService.approveByDirector(payrollId, directorName);
            return ResponseEntity.ok(payroll);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error approving payroll: " + e.getMessage());
        }
    }

    // Reject payroll by director
    @PostMapping("/reject-director/{payrollId}")
    public ResponseEntity<?> rejectByDirector(@PathVariable Long payrollId, @RequestBody Map<String, String> request) {
        try {
            String reason = request.getOrDefault("reason", "");
            PayrollDTO payroll = payrollService.rejectByDirector(payrollId, reason);
            return ResponseEntity.ok(payroll);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error rejecting payroll: " + e.getMessage());
        }
    }

    // Unlock payroll
    @PostMapping("/unlock/{payrollId}")
    public ResponseEntity<PayrollDTO> unlockPayroll(@PathVariable Long payrollId) {
        PayrollDTO payroll = payrollService.unlockPayroll(payrollId);
        return ResponseEntity.ok(payroll);
    }

    // Delete all payroll records
    @DeleteMapping("/clear-all")
    public ResponseEntity<String> clearAllPayrolls() {
        try {
            payrollService.deleteAllPayrolls();
            return ResponseEntity.ok("All payroll records deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting payroll records: " + e.getMessage());
        }
    }
}
