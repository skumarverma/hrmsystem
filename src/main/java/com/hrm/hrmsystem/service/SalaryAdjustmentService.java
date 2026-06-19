package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.model.SalaryAdjustment;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.repository.SalaryAdjustmentRepository;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SalaryAdjustmentService {

    @Autowired
    private SalaryAdjustmentRepository salaryAdjustmentRepository;

    @Autowired
    private PayrollLockService payrollLockService;
    
    @Autowired
    private EmployeeRepository employeeRepository;

    /**
     * Create a salary adjustment for a specific month/year
     * Used when payroll is locked and attendance changes need to be compensated
     */
    public SalaryAdjustment createAdjustment(Long employeeId, Integer month, Integer year, 
                                            BigDecimal amount, String reason, String adjustmentType, 
                                            String remarks) {
        // ✅ CRITICAL BUG 6: Add validation for amount field (not null, not zero)
        if (amount == null) {
            throw new RuntimeException("Amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new RuntimeException("Amount cannot be zero");
        }
        
        // ✅ CRITICAL BUG 1: Fix employee NULL - fetch employee from repository
        Employee employee = employeeRepository.findByIdentifier(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found with ID/Code: " + employeeId));

        // Check if payroll is locked for this employee and month/year
        if (!payrollLockService.isPayrollLockedForEmployee(employee.getId(), month, year)) {
            throw new RuntimeException("Payroll is not locked for this employee for " + year + "-" + month + 
                    ". Use normal attendance updates instead of adjustments.");
        }

        String createdBy = getCurrentUser();
        
        // Convert String to Enum safely
        SalaryAdjustment.AdjustmentType type;
        try {
            type = SalaryAdjustment.AdjustmentType.valueOf(adjustmentType.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RuntimeException("Invalid adjustment type: '" + adjustmentType + 
                     "'. Valid types are: BONUS, DEDUCTION, CORRECTION, OVERTIME, OTHER");
        }

        SalaryAdjustment adjustment = new SalaryAdjustment(
                employee, // ✅ FIXED: Set employee before saving
                month,
                year,
                amount,
                reason,
                type,
                createdBy,
                remarks
        );
        
        return salaryAdjustmentRepository.save(adjustment);
    }

    /**
     * Get all adjustments for an employee in a specific month/year
     */
    public List<SalaryAdjustment> getAdjustmentsForEmployee(Long employeeId, Integer month, Integer year) {
        Employee employee = employeeRepository.findByIdentifier(employeeId).orElse(null);
        if (employee == null) {
            return new java.util.ArrayList<>();
        }
        return salaryAdjustmentRepository.findByEmployeeIdAndMonthAndYear(employee.getId(), month, year);
    }

    /**
     * Get all adjustments for a specific month/year (all employees)
     */
    public List<SalaryAdjustment> getAdjustmentsForMonth(Integer month, Integer year) {
        return salaryAdjustmentRepository.findByMonthAndYear(month, year);
    }

    /**
     * Get all unapplied adjustments
     */
    public List<SalaryAdjustment> getUnappliedAdjustments() {
        return salaryAdjustmentRepository.findByIsAppliedFalse();
    }

    /**
     * Mark an adjustment as applied
     */
    public SalaryAdjustment markAsApplied(Long adjustmentId) {
        Optional<SalaryAdjustment> adjustmentOpt = salaryAdjustmentRepository.findById(adjustmentId);
        if (adjustmentOpt.isEmpty()) {
            throw new RuntimeException("Adjustment not found with ID: " + adjustmentId);
        }

        SalaryAdjustment adjustment = adjustmentOpt.get();
        
        // ✅ CRITICAL BUG 5: Add validation to markAsApplied() to prevent double application
        if (Boolean.TRUE.equals(adjustment.getIsApplied())) {
            throw new RuntimeException("Adjustment already applied with ID: " + adjustmentId);
        }
        
        adjustment.setIsApplied(true);
        return salaryAdjustmentRepository.save(adjustment);
    }

    /**
     * Delete an adjustment (only if not yet applied)
     */
    public void deleteAdjustment(Long adjustmentId) {
        Optional<SalaryAdjustment> adjustmentOpt = salaryAdjustmentRepository.findById(adjustmentId);
        if (adjustmentOpt.isEmpty()) {
            throw new RuntimeException("Adjustment not found with ID: " + adjustmentId);
        }

        SalaryAdjustment adjustment = adjustmentOpt.get();
        
        // ✅ CRITICAL BUG 4: Fix null check for getIsApplied() using Boolean.TRUE.equals()
        if (Boolean.TRUE.equals(adjustment.getIsApplied())) {
            throw new RuntimeException("Cannot delete applied adjustment with ID: " + adjustmentId);
        }
        
        // ✅ CRITICAL BUG 3: Add payroll lock check to deleteAdjustment()
        if (!payrollLockService.isPayrollLockedForEmployee(adjustment.getEmployee().getId(), adjustment.getMonth(), adjustment.getYear())) {
            throw new RuntimeException("Cannot delete adjustment when payroll is not locked for this employee for " + 
                    adjustment.getYear() + "-" + adjustment.getMonth());
        }

        salaryAdjustmentRepository.delete(adjustment);
    }

    /**
     * Calculate total adjustment amount for an employee in a month/year
     */
    public BigDecimal getTotalAdjustmentAmount(Long employeeId, Integer month, Integer year) {
        List<SalaryAdjustment> adjustments = getAdjustmentsForEmployee(employeeId, month, year);
        // ✅ CRITICAL BUG 2: Fix getTotalAdjustmentAmount() to filter applied adjustments only
        return adjustments.stream()
                .filter(SalaryAdjustment::getIsApplied) // ✅ FIXED: Only include applied adjustments
                .map(SalaryAdjustment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get current logged-in user
     */
    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // ✅ MINOR BUG 8: Fix getCurrentUser() to handle anonymous user properly
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "SYSTEM";
    }
}
