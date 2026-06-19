package com.hrm.hrmsystem;

import com.hrm.hrmsystem.engine.AttendanceEngine;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.Attendance;
import com.hrm.hrmsystem.model.Leave;
import com.hrm.hrmsystem.model.Payroll;
import com.hrm.hrmsystem.entity.Payslip;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.repository.LeaveRepository;
import com.hrm.hrmsystem.repository.AttendanceRepository;
import com.hrm.hrmsystem.repository.PayrollRepository;
import com.hrm.hrmsystem.repository.PayslipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.YearMonth;
import java.time.LocalDate;
import java.util.List;

@SpringBootTest
public class DebugDataTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @Autowired
    private AttendanceEngine attendanceEngine;

    @Test
    public void printJitendraData() {
        System.out.println("=== DEBUG DATA FOR JITENDRA TIWARI ===");
        Employee jitendra = employeeRepository.findById(14L).orElse(null);

        if (jitendra == null) {
            System.out.println("JITENDRA NOT FOUND");
            return;
        }

        System.out.println("Found: " + jitendra.getFirstName() + " " + jitendra.getLastName() + ", ID: " + jitendra.getId());
        System.out.println("Joining Date: " + jitendra.getJoiningDate());
        System.out.println("Probation Status: " + jitendra.getProbationStatus());
        System.out.println("Probation Period (Months): " + jitendra.getProbationPeriodMonths());

        List<Leave> leaves = leaveRepository.findByEmployeeId(jitendra.getId());
        System.out.println("\nAll Leaves:");
        for (Leave l : leaves) {
            System.out.println("Leave ID: " + l.getId() + ", Status: " + l.getStatus() + 
                               ", Type: " + l.getLeaveType() +
                               ", Dates: " + l.getStartDate() + " to " + l.getEndDate() + 
                               ", TotalDays: " + l.getTotalDays() + 
                               ", PaidDays: " + l.getPaidDays() + 
                               ", UnpaidDays: " + l.getUnpaidDays() +
                               ", FinalPaidDays: " + l.getFinalPaidDays() +
                               ", FinalUnpaidDays: " + l.getFinalUnpaidDays() +
                               ", FinalTotalDays: " + l.getFinalTotalDays());
        }

        List<Attendance> attendances = attendanceRepository.findByEmployeeIdAndDateBetween(
            jitendra.getId(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        System.out.println("\nAttendance Records for June 2026:");
        for (Attendance a : attendances) {
            System.out.println("Date: " + a.getDate() + ", Status: " + a.getStatus() + ", Remarks: " + a.getRemarks());
        }

        System.out.println("\n=== LEAVE BALANCE (June 2026) ===");
        YearMonth ym = YearMonth.of(2026, 6);
        AttendanceEngine.LeaveBalanceSummary summary = attendanceEngine.calculateLeaveBalance(jitendra.getId(), ym);
        System.out.println("Earned Leaves: " + summary.earnedLeaves);
        System.out.println("Used (Paid): " + summary.usedLeaves);
        System.out.println("Unpaid Leaves: " + summary.unpaidLeaves);
        System.out.println("Remaining Balance: " + summary.remaining);
        
        System.out.println("\n=== ATTENDANCE ENGINE SUMMARY (June 2026) ===");
        com.hrm.hrmsystem.engine.AttendanceSummary attSummary = attendanceEngine.calculate(jitendra.getId(), ym);
        System.out.println("Worked Days: " + attSummary.workedDays);
        System.out.println("Absent Days: " + attSummary.absent);
        System.out.println("Paid Leave Days: " + attSummary.paidLeave);
        System.out.println("Unpaid Leave Days: " + attSummary.unpaidLeave);
        System.out.println("Payable Days: " + attSummary.payableDays);

        System.out.println("=== END DEBUG DATA ===");
    }

    @Test
    public void printGovindData() {
        System.out.println("=== DEBUG DATA FOR GOVIND KUMAR ===");
        Employee govind = employeeRepository.findAll().stream()
            .filter(e -> "9901".equals(e.getEmployeeCode()) || (e.getFirstName() != null && e.getFirstName().toLowerCase().contains("govind")))
            .findFirst().orElse(null);

        if (govind == null) {
            System.out.println("GOVIND NOT FOUND");
            return;
        }

        System.out.println("Found: " + govind.getFirstName() + " " + govind.getLastName() + ", ID: " + govind.getId() + ", Code: " + govind.getEmployeeCode());
        System.out.println("Joining Date: " + govind.getJoiningDate());
        System.out.println("Probation Status: " + govind.getProbationStatus());
        System.out.println("Probation Period (Months): " + govind.getProbationPeriodMonths());

        List<Leave> leaves = leaveRepository.findByEmployeeId(govind.getId());
        System.out.println("\nAll Leaves:");
        for (Leave l : leaves) {
            System.out.println("Leave ID: " + l.getId() + ", Status: " + l.getStatus() + 
                               ", Type: " + l.getLeaveType() +
                               ", Dates: " + l.getStartDate() + " to " + l.getEndDate() + 
                               ", TotalDays: " + l.getTotalDays() + 
                               ", PaidDays: " + l.getPaidDays() + 
                               ", UnpaidDays: " + l.getUnpaidDays() +
                               ", FinalPaidDays: " + l.getFinalPaidDays() +
                               ", FinalUnpaidDays: " + l.getFinalUnpaidDays() +
                               ", FinalTotalDays: " + l.getFinalTotalDays());
        }

        List<Attendance> attendances = attendanceRepository.findByEmployeeIdAndDateBetween(
            govind.getId(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        System.out.println("\nAttendance Records for June 2026:");
        for (Attendance a : attendances) {
            System.out.println("Date: " + a.getDate() + ", Status: " + a.getStatus() + ", HalfType: " + a.getHalfType() + ", Hours: " + a.getWorkingHours() + ", Remarks: " + a.getRemarks());
        }

        System.out.println("\n=== LEAVE BALANCE (June 2026) ===");
        YearMonth ym = YearMonth.of(2026, 6);
        AttendanceEngine.LeaveBalanceSummary summary = attendanceEngine.calculateLeaveBalance(govind.getId(), ym);
        System.out.println("Earned Leaves: " + summary.earnedLeaves);
        System.out.println("Used (Paid): " + summary.usedLeaves);
        System.out.println("Unpaid Leaves: " + summary.unpaidLeaves);
        System.out.println("Remaining Balance: " + summary.remaining);
        
        System.out.println("\n=== ATTENDANCE ENGINE SUMMARY (June 2026) ===");
        com.hrm.hrmsystem.engine.AttendanceSummary attSummary = attendanceEngine.calculate(govind.getId(), ym);
        System.out.println("Worked Days: " + attSummary.workedDays);
        System.out.println("Absent Days: " + attSummary.absent);
        System.out.println("Paid Leave Days: " + attSummary.paidLeave);
        System.out.println("Unpaid Leave Days: " + attSummary.unpaidLeave);
        System.out.println("Payable Days: " + attSummary.payableDays);

        System.out.println("\n=== STORED PAYROLL (June 2026) ===");
        List<Payroll> payrollList = payrollRepository.findByMonthAndYear(6, 2026);
        for (Payroll p : payrollList) {
            if (p.getEmployee() != null && p.getEmployee().getId().equals(govind.getId())) {
                System.out.println("Payroll ID: " + p.getId() + ", Status: " + p.getStatus() +
                                   ", Gross: " + p.getGrossSalary() + ", Net: " + p.getNetSalary() +
                                   ", UnpaidLeaves: " + p.getUnpaidLeaveDays() + ", AbsentDays: " + p.getAbsentDays());
            }
        }

        System.out.println("\n=== STORED PAYSLIP (June 2026) ===");
        List<Payslip> payslipList = payslipRepository.findByEmployeeIdOrderByMonthYearDesc(govind.getId());
        for (Payslip p : payslipList) {
            if (p.getMonthYear() != null && (p.getMonthYear().contains("06") || p.getMonthYear().toLowerCase().contains("june"))) {
                System.out.println("Payslip ID: " + p.getId() + ", MonthYear: " + p.getMonthYear() +
                                   ", Net: " + p.getNetSalary() + ", PaidLeaveDays: " + p.getPaidLeaveDays() +
                                   ", UnpaidLeaveDays: " + p.getUnpaidLeaveDays() + ", AbsentDays: " + p.getAbsentDays() +
                                   ", PresentDays: " + p.getPresentDays());
            }
        }

        System.out.println("=== END DEBUG DATA ===");
    }

    @Test
    public void printAllEmployees() {
        System.out.println("=== ALL EMPLOYEES DEBUG ===");
        List<Employee> employees = employeeRepository.findAll();
        for (Employee e : employees) {
            System.out.printf("ID: %d | Name: %s %s | Phone: %s | Joined: %s | Probation Period (Months): %s | Status: %s | Probation Status: %s\n",
                e.getId(),
                e.getFirstName(),
                e.getLastName(),
                e.getPhone(),
                e.getJoiningDate(),
                e.getProbationPeriodMonths(),
                e.getStatus(),
                e.getProbationStatus()
            );
        }
        System.out.println("=== END ALL EMPLOYEES ===");
    }
}
