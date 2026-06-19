package com.hrm.hrmsystem;

import com.hrm.hrmsystem.model.Leave;
import com.hrm.hrmsystem.repository.LeaveRepository;
import com.hrm.hrmsystem.engine.AttendanceEngine;
import com.hrm.hrmsystem.service.PayrollLockService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Utility to fix incorrect leave splits for all employees
 */
@Component
public class LeaveCorrectionRunner implements CommandLineRunner {
    private final LeaveRepository leaveRepository;
    private final AttendanceEngine attendanceEngine;
    private final PayrollLockService payrollLockService;
    private final com.hrm.hrmsystem.service.PayrollService payrollService;
    private final com.hrm.hrmsystem.service.PayslipService payslipService;

    public LeaveCorrectionRunner(LeaveRepository leaveRepository, AttendanceEngine attendanceEngine, PayrollLockService payrollLockService,
                                 com.hrm.hrmsystem.service.PayrollService payrollService, com.hrm.hrmsystem.service.PayslipService payslipService) {
        this.leaveRepository = leaveRepository;
        this.attendanceEngine = attendanceEngine;
        this.payrollLockService = payrollLockService;
        this.payrollService = payrollService;
        this.payslipService = payslipService;
    }

    @Override
    public void run(String... args) {
        // Run only if a specific flag is passed or on every start if needed
        // For now, let's run it once to fix data.
        System.out.println("--- LEAVE CORRECTION START ---");
        
        List<Leave> eligibleLeaves = leaveRepository.findAll().stream()
                .filter(l -> l.getStatus() == Leave.LeaveStatus.APPROVED || l.getStatus() == Leave.LeaveStatus.PENDING)
                .toList();

        for (Leave leave : eligibleLeaves) {
            int month = leave.getStartDate().getMonthValue();
            int year = leave.getStartDate().getYear();

            // Skip if payroll is locked
            if (payrollLockService.isPayrollLocked(month, year)) {
                System.out.println("Skipping Leave ID " + leave.getId() + " - Payroll locked for " + year + "-" + month);
                continue;
            }

            // Recalculate split
            AttendanceEngine.LeaveSplit split = attendanceEngine.calculateLeaveSplit(
                leave.getEmployee().getId(),
                leave.getStartDate(),
                leave.getEndDate(),
                leave.getIsHalfDay() != null && leave.getIsHalfDay()
            );



            System.out.println("Recalculating Leave ID " + leave.getId() + " (" + leave.getEmployee().getFirstName() + "): " +
                "Old: P=" + leave.getPaidDays() + "/U=" + leave.getUnpaidDays() + 
                " -> New: P=" + split.paidDays + "/U=" + split.unpaidDays);

            // Update
            leave.setPaidDays(split.paidDays);
            leave.setUnpaidDays(split.unpaidDays);
            leave.setTotalDays(split.totalDays);
            
            if (leave.getStatus() == Leave.LeaveStatus.APPROVED) {
                leave.setFinalPaidDays(split.paidDays);
                leave.setFinalUnpaidDays(split.unpaidDays);
                leave.setFinalTotalDays(split.totalDays);
            }
            
            leaveRepository.save(leave);

            // Update associated payroll and payslip if they exist and are current/next month
            try {
                java.time.LocalDate now = java.time.LocalDate.now();
                java.time.YearMonth currentMonth = java.time.YearMonth.from(now);
                java.time.YearMonth nextMonth = currentMonth.plusMonths(1);
                java.time.YearMonth requestedMonth = java.time.YearMonth.of(year, month);

                if (requestedMonth.equals(currentMonth) || requestedMonth.equals(nextMonth)) {
                    payrollService.generatePayroll(leave.getEmployee().getId(), month, year);
                    String monthYear = String.format("%d-%02d", year, month);
                    payslipService.generatePayslip(leave.getEmployee().getId(), monthYear);
                    System.out.println("Automatically updated Payroll & Payslip for Employee ID " + leave.getEmployee().getId() + " for " + monthYear);
                } else {
                    System.out.println("Skipped updating Payroll & Payslip for Employee ID " + leave.getEmployee().getId() + " for past/future month: " + year + "-" + month);
                }
            } catch (Exception e) {
                System.out.println("Could not update Payroll/Payslip for Employee ID " + leave.getEmployee().getId() + " for " + year + "-" + month + ": " + e.getMessage());
            }
        }
        
        System.out.println("--- LEAVE CORRECTION END ---");
    }
}
