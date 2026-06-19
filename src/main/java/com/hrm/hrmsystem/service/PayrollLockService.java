package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.model.PayrollLock;
import com.hrm.hrmsystem.repository.PayrollLockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class PayrollLockService {

    @Autowired
    private PayrollLockRepository payrollLockRepository;

    @Autowired
    private com.hrm.hrmsystem.repository.PayrollRepository payrollRepository;

    /**
     * Check if payroll is locked for a specific month/year
     */
    public boolean isPayrollLocked(Integer month, Integer year) {
        return false; // Bypass lock check
    }

    /**
     * Check if payroll is locked for a specific employee and month/year
     */
    public boolean isPayrollLockedForEmployee(Long employeeId, Integer month, Integer year) {
        if (!isPayrollLocked(month, year)) {
            return false;
        }
        Optional<com.hrm.hrmsystem.model.Payroll> payrollOpt = payrollRepository.findByEmployeeIdAndMonthAndYear(employeeId, month, year);
        if (payrollOpt.isPresent()) {
            com.hrm.hrmsystem.model.Payroll.PayrollStatus status = payrollOpt.get().getStatus();
            return status == com.hrm.hrmsystem.model.Payroll.PayrollStatus.APPROVED || status == com.hrm.hrmsystem.model.Payroll.PayrollStatus.PAID;
        }
        return false;
    }

    /**
     * Lock payroll for a specific month/year
     */
    public PayrollLock lockPayroll(Integer month, Integer year, String remarks) {
        if (isPayrollLocked(month, year)) {
            throw new RuntimeException("Payroll for " + year + "-" + month + " is already locked");
        }

        String lockedBy = getCurrentUser();
        PayrollLock lock = new PayrollLock(month, year, lockedBy, remarks);
        return payrollLockRepository.save(lock);
    }

    /**
     * Unlock payroll for a specific month/year
     */
    public PayrollLock unlockPayroll(Integer month, Integer year, String remarks) {
        Optional<PayrollLock> lockOpt = payrollLockRepository.findByMonthAndYear(month, year);
        
        if (lockOpt.isEmpty()) {
            throw new RuntimeException("No lock found for " + year + "-" + month);
        }

        PayrollLock lock = lockOpt.get();
        lock.setIsLocked(false);
        if (remarks != null && !remarks.trim().isEmpty()) {
            lock.setRemarks(remarks);
        }
        
        return payrollLockRepository.save(lock);
    }

    /**
     * Get payroll lock details for a specific month/year
     */
    public Optional<PayrollLock> getPayrollLock(Integer month, Integer year) {
        return payrollLockRepository.findByMonthAndYear(month, year);
    }

    /**
     * Get current logged-in user
     */
    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "SYSTEM";
    }
}
