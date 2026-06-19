package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.engine.AttendanceEngine;
import com.hrm.hrmsystem.engine.AttendanceSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hrm.hrmsystem.dto.LeaveBalanceDTO;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/**
 * UNIFIED CALCULATION SERVICE
 * 🔥 UNIFIED CALCULATION SERVICE
 * Single service layer for ALL calculations
 * Enforces: Controller → Service → Engine flow
 * Eliminates duplicate calculations across services
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedCalculationService {

    private final AttendanceEngine attendanceEngine;

    private final EmployeeRepository employeeRepository;

    /**
     * Calculate attendance summary for payroll (salary calculation)
     * Ignores future dates - for salary/payslip calculations
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "attendanceCache", key = "#employeeId + '_' + #year + '_' + #month + '_payroll'")
    public AttendanceSummary calculateForPayroll(Long employeeId, int year, int month) {
        if (employeeId == null || year <= 0 || month <= 0 || month > 12) {
            throw new IllegalArgumentException("Invalid input: employeeId cannot be null, year must be positive, month must be 1-12");
        }
        Employee employee = employeeRepository.findByIdentifier(employeeId)
            .orElseThrow(() -> new RuntimeException("Employee not found"));
        YearMonth yearMonth = YearMonth.of(year, month);
        return attendanceEngine.calculate(employee.getId(), yearMonth);
    }

    /**
     * Calculate complete leave statistics for an employee
     * Unified replacement for LeaveCalculationService.calculateLeaveStatistics
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "leaveStatsCache", key = "#employeeId + '_' + #year + '_' + #month")
    public LeaveStatistics calculateLeaveStatistics(Long employeeId, int month, int year) {
        Employee employee = employeeRepository.findByIdentifier(employeeId)
            .orElseThrow(() -> new RuntimeException("Employee not found: " + employeeId));

        AttendanceEngine.LeaveBalanceSummary summary = attendanceEngine.calculateLeaveBalance(employee.getId(), year, month);
        
        LeaveStatistics stats = new LeaveStatistics();
        stats.setEmployeeId(employee.getId());
        stats.setEmployeeName(employee.getFirstName() + " " + employee.getLastName());
        stats.setMonth(month);
        stats.setYear(year);
        
        stats.setCurrentMonthPaidLeaves(summary.currentMonthUsed);
        stats.setCurrentMonthUnpaidLeaves(summary.currentMonthUnpaid);
        stats.setCurrentMonthUsedLeaves(summary.currentMonthUsed);
        
        stats.setTotalEarnedLeaves(applyRule3Adjustment(employee, YearMonth.of(year, month), summary.earnedLeaves));
        stats.setTotalUsedPaidLeaves(summary.usedLeaves);
        stats.setTotalUsedUnpaidLeaves(summary.unpaidLeaves);  // ✅ FIX: was missing
        stats.setRemainingPaidLeaves(Math.max(0, stats.getTotalEarnedLeaves() - summary.usedLeaves));
        stats.setAvailableLeaveBalance(stats.getRemainingPaidLeaves());
        stats.setTotalApprovedLeaves(summary.usedLeaves);

        return stats;
    }

    /**
     * Get leave summary for an employee
     * Unified replacement for LeaveBalanceService.getLeaveSummary
     */
    @Transactional(readOnly = true)
    public LeaveBalanceResult getLeaveSummary(Long empId, YearMonth month) {
        return getLeaveSummary(empId, month, null);
    }

    @Transactional(readOnly = true)
    public LeaveBalanceResult getLeaveSummary(Long empId, YearMonth month, LocalDate excludeLeavesFromDate) {
        Employee employee = employeeRepository.findByIdentifier(empId)
            .orElseThrow(() -> new RuntimeException("Employee not found"));
            
        AttendanceEngine.LeaveBalanceSummary summary = attendanceEngine.calculateLeaveBalance(employee.getId(), month, excludeLeavesFromDate);
        LeaveBalanceResult result = new LeaveBalanceResult();
        
        double earned = applyRule3Adjustment(employee, month, summary.earnedLeaves);
        
        result.earned = earned;
        result.used = summary.usedLeaves;
        result.unpaidLeaves = summary.unpaidLeaves;
        result.remaining = Math.max(0, earned - summary.usedLeaves);
        result.usedThisMonth = summary.currentMonthUsed;
        result.unpaidThisMonth = summary.currentMonthUnpaid;
        result.totalUsed = summary.totalUsedLeaves; // ✅ Paid + Unpaid
        return result;
    }

    /**
     * Calculate leave balance for an employee
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "leaveBalanceCache", key = "#employeeId + '_' + #year + '_' + #month")
    public AttendanceEngine.LeaveBalanceSummary calculateLeaveBalance(Long employeeId, YearMonth month) {
        if (employeeId == null || month == null) {
            throw new IllegalArgumentException("Invalid input: employeeId and month cannot be null");
        }
        return attendanceEngine.calculateLeaveBalance(employeeId, month);
    }

    /**
     * Calculate opening balance for a month
     */
    @Transactional(readOnly = true)
    public double calculateOpeningBalance(Long employeeId, YearMonth month) {
        if (employeeId == null || month == null) {
            throw new IllegalArgumentException("Invalid input: employeeId and month cannot be null");
        }
        return attendanceEngine.calculateOpeningBalance(employeeId, month);
    }

    /**
     * Calculate salary components based on attendance
     */
    public AttendanceEngine.SalarySummary calculateSalary(
            com.hrm.hrmsystem.model.Employee employee, 
            AttendanceSummary attendanceSummary, 
            YearMonth month) {
        if (employee == null || attendanceSummary == null) {
            throw new IllegalArgumentException("Invalid input: employee and attendanceSummary cannot be null");
        }
        
        boolean isInProbation = !attendanceEngine.isProbationCompleted(employee, month.atEndOfMonth());
        AttendanceEngine.LeaveBalanceSummary leaveSummary = attendanceEngine.calculateLeaveBalance(employee.getId(), month);
        return attendanceEngine.calculateSalary(employee, attendanceSummary, isInProbation, leaveSummary);
    }

    /**
     * Internal helper to apply Rule 3 (15th Date Eligibility)
     */
    private double applyRule3Adjustment(Employee employee, YearMonth month, double currentEarned) {
        // If engine already calculated credit, or probation isn't done, return as is
        if (currentEarned > 0 || !attendanceEngine.isProbationCompleted(employee, month.atEndOfMonth())) {
            return currentEarned;
        }

        LocalDate probationEnd = employee.getJoiningDate().plusMonths(
            employee.getProbationPeriodMonths() != null ? employee.getProbationPeriodMonths() : 3);
        
        if (probationEnd.getDayOfMonth() <= 15 && !month.isBefore(YearMonth.from(probationEnd))) {
            return 1.5;
        }
        return currentEarned;
    }

    // --- DTOs migrated from deleted services ---

    public static class LeaveStatistics {
        private Long employeeId;
        private String employeeName;
        private int month;
        private int year;
        private double currentMonthPaidLeaves;
        private double currentMonthUnpaidLeaves;
        private double currentMonthUsedLeaves;
        private double totalEarnedLeaves;
        private double totalUsedPaidLeaves;
        private double totalUsedUnpaidLeaves;  // ✅ FIX: was missing from DTO
        private double remainingPaidLeaves;
        private double availableLeaveBalance;
        private double totalApprovedLeaves;

        // Getters and Setters
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
        public int getMonth() { return month; }
        public void setMonth(int month) { this.month = month; }
        public int getYear() { return year; }
        public void setYear(int year) { this.year = year; }
        public double getCurrentMonthPaidLeaves() { return currentMonthPaidLeaves; }
        public void setCurrentMonthPaidLeaves(double currentMonthPaidLeaves) { this.currentMonthPaidLeaves = currentMonthPaidLeaves; }
        public double getCurrentMonthUnpaidLeaves() { return currentMonthUnpaidLeaves; }
        public void setCurrentMonthUnpaidLeaves(double currentMonthUnpaidLeaves) { this.currentMonthUnpaidLeaves = currentMonthUnpaidLeaves; }
        public double getCurrentMonthUsedLeaves() { return currentMonthUsedLeaves; }
        public void setCurrentMonthUsedLeaves(double currentMonthUsedLeaves) { this.currentMonthUsedLeaves = currentMonthUsedLeaves; }
        public double getTotalEarnedLeaves() { return totalEarnedLeaves; }
        public void setTotalEarnedLeaves(double totalEarnedLeaves) { this.totalEarnedLeaves = totalEarnedLeaves; }
        public double getTotalUsedPaidLeaves() { return totalUsedPaidLeaves; }
        public void setTotalUsedPaidLeaves(double totalUsedPaidLeaves) { this.totalUsedPaidLeaves = totalUsedPaidLeaves; }
        public double getTotalUsedUnpaidLeaves() { return totalUsedUnpaidLeaves; }  // ✅ FIX
        public void setTotalUsedUnpaidLeaves(double v) { this.totalUsedUnpaidLeaves = v; }  // ✅ FIX
        public double getRemainingPaidLeaves() { return remainingPaidLeaves; }
        public void setRemainingPaidLeaves(double remainingPaidLeaves) { this.remainingPaidLeaves = remainingPaidLeaves; }
        public double getAvailableLeaveBalance() { return availableLeaveBalance; }
        public void setAvailableLeaveBalance(double availableLeaveBalance) { this.availableLeaveBalance = availableLeaveBalance; }
        public double getTotalApprovedLeaves() { return totalApprovedLeaves; }
        public void setTotalApprovedLeaves(double totalApprovedLeaves) { this.totalApprovedLeaves = totalApprovedLeaves; }
    }

    public static class LeaveBalanceResult {
        public double earned = 0;
        public double used = 0;
        public double unpaidLeaves = 0;
        public double remaining = 0;
        public double usedThisMonth = 0;
        public double unpaidThisMonth = 0;
        public double totalUsed = 0; // ✅ Total (Paid + Unpaid)
        public int cycle = 1;
        public int year = YearMonth.now().getYear();
    }


}
