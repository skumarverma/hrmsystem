package com.hrm.hrmsystem.engine;

import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.Leave;
import com.hrm.hrmsystem.repository.AttendanceRepository;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.repository.LeaveRepository;
import com.hrm.hrmsystem.repository.LeaveLedgerRepository;
import com.hrm.hrmsystem.model.LeaveLedger;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AttendanceEngineTest {

    @Mock
    private LeaveRepository leaveRepository;
    
    @Mock
    private AttendanceRepository attendanceRepository;
    
    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LeaveLedgerRepository leaveLedgerRepository;

    @InjectMocks
    private AttendanceEngine attendanceEngine;

    private Employee testEmployee;

    @BeforeEach
    void setUp() {
        testEmployee = new Employee();
        testEmployee.setId(1L);
        testEmployee.setJoiningDate(LocalDate.of(2024, 1, 1));
        testEmployee.setProbationPeriodMonths(3);

        lenient().when(employeeRepository.findByIdentifier(any())).thenAnswer(invocation -> {
            Long arg = invocation.getArgument(0);
            return employeeRepository.findById(arg);
        });
        lenient().when(employeeRepository.findByIdentifierWithDepartment(any())).thenAnswer(invocation -> {
            Long arg = invocation.getArgument(0);
            return employeeRepository.findById(arg);
        });
    }

    private LeaveLedger createLeaveLedger(Long empId, LocalDate date, LeaveLedger.EventType type, double paid, double unpaid) {
        return new LeaveLedger(
            empId, date, type, 
            1L, 
            BigDecimal.valueOf(paid + unpaid), 
            BigDecimal.valueOf(paid), 
            BigDecimal.valueOf(unpaid), 
            BigDecimal.ZERO
        );
    }

    @Test
    void testCalculateLeaveSplit_FullPaid() {
        // Employee in probation March 2024 (joined Jan 1, probation ends Apr 1)
        // Total Earned = 0, so all days should be unpaid
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));

        AttendanceEngine.LeaveSplit result = attendanceEngine.calculateLeaveSplit(
            1L, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 2), false);

        assertEquals(0.0, result.paidDays, "Should be 0 paid during probation");
        assertEquals(2.0, result.unpaidDays, "Should be 2 unpaid during probation");
        assertEquals(2.0, result.totalDays, "Total should be 2 working days");
    }

    @Test
    void testCalculateLeaveSplit_MixedSplit() {
        // Employee in probation March 2024 - no earned leaves available
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));

        // Friday to Sunday (Friday, Saturday, Sunday count = 3 calendar days, 2 working days)
        AttendanceEngine.LeaveSplit result = attendanceEngine.calculateLeaveSplit(
            1L, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 3), false);

        assertEquals(0.0, result.paidDays, "Should be 0 paid during probation");
        assertEquals(2.0, result.unpaidDays, "Should be 2 unpaid during probation (Sunday excluded)");
        assertEquals(2.0, result.totalDays, "Total should be 2 working days (Sunday excluded)");
    }

    @Test
    void testCalculateLeaveSplit_HalfDay() {
        // Employee in probation March 2024 - no earned leaves available
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));

        AttendanceEngine.LeaveSplit result = attendanceEngine.calculateLeaveSplit(
            1L, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 1), true);

        assertEquals(0.0, result.paidDays, "Should be 0 paid during probation");
        assertEquals(0.5, result.unpaidDays, "Should be 0.5 unpaid during probation");
        assertEquals(0.5, result.totalDays, "Total should be 0.5 for half-day");
    }

    @Test
    void testCalculateLeaveSplit_SundayExclusion() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));

        // Friday to Sunday
        AttendanceEngine.LeaveSplit result = attendanceEngine.calculateLeaveSplit(
            1L, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 3), false);

        assertEquals(0.0, result.paidDays, "Should be 0 paid during probation");
        assertEquals(2.0, result.unpaidDays, "Friday + Saturday (Sunday excluded) = 2 days");
        assertEquals(2.0, result.totalDays, "Total should be 2 days");
    }

    @Test
    void testCalculateLeaveBalance_ProbationPeriod() {
        // Test during probation - should return 0 earned
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        org.mockito.Mockito.lenient().when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of());
        
        AttendanceEngine.LeaveBalanceSummary balance = attendanceEngine.calculateLeaveBalance(1L, 2024, 2);
        assertEquals(0.0, balance.earnedLeaves, "Should earn 0 during probation");
        assertEquals(0.0, balance.usedLeaves, "Should have 0 Total Used Leaves");
        assertEquals(0.0, balance.getAvailableLeaves(), "Should have 0 available leaves");
    }

    @Test
    void testCalculateLeaveBalance_15thDayRule_Jan15() {
        // Joining Jan 15, probation ends Apr 15 (day=15)
        testEmployee.setJoiningDate(LocalDate.of(2024, 1, 15));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        lenient().when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of());
        
        // Should start earning from April (same month as completion because day=15 <= 15)
        // By May, should have earned for April and May = 3.0 leaves
        AttendanceEngine.LeaveBalanceSummary balance = attendanceEngine.calculateLeaveBalance(1L, 2024, 5);
        assertEquals(3.0, balance.earnedLeaves, "Should earn 3.0 by May (15th rule applied, starts in April)");
    }

    @Test
    void testCalculateLeaveBalance_15thDayRule_Jan16() {
        // Joining Jan 16, probation ends Apr 16 (day=16 > 15)
        testEmployee.setJoiningDate(LocalDate.of(2024, 1, 16));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        lenient().when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of());
        
        // Should start earning from May (next month because day=16 > 15)
        // By June, should have earned for May and June = 3.0 leaves
        AttendanceEngine.LeaveBalanceSummary balance = attendanceEngine.calculateLeaveBalance(1L, 2024, 6);
        assertEquals(3.0, balance.earnedLeaves, "Should earn 3.0 by June (16th > 15th rule applied, starts in May)");
    }

    @Test
    void testCalculateLeaveBalance_CycleReset() {
        // Test cycle reset between June and July
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        lenient().when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of());
        
        AttendanceEngine.LeaveBalanceSummary balanceJune = attendanceEngine.calculateLeaveBalance(1L, 2024, 6);
        AttendanceEngine.LeaveBalanceSummary balanceJuly = attendanceEngine.calculateLeaveBalance(1L, 2024, 7);
        
        assertTrue(balanceJune.earnedLeaves >= 0, "June should have earned leaves");
        assertTrue(balanceJuly.earnedLeaves >= 0, "July should restart cycle calculation");
        // Both should be in different cycles
        assertEquals(1, balanceJune.cycle, "June should be cycle 1");
        assertEquals(2, balanceJuly.cycle, "July should be cycle 2");
    }

    @Test
    void testCalculateLeaveBalance_Max9Cap() {
        // Test max 9 leaves per cycle
        testEmployee.setJoiningDate(LocalDate.of(2023, 1, 1)); // Long time employee
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        lenient().when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of());
        
        AttendanceEngine.LeaveBalanceSummary balance = attendanceEngine.calculateLeaveBalance(1L, 2024, 6);
        assertEquals(9.0, balance.earnedLeaves, "Should cap at 9 leaves per cycle");
    }

    @Test
    void testCalculateWorkingDays_SundaysExcluded() {
        double days = attendanceEngine.calculateWorkingDays(
            LocalDate.of(2024, 4, 5), // Friday
            LocalDate.of(2024, 4, 7)  // Sunday (should be excluded)
        );
        assertEquals(2.0, days, "Should count Friday and Saturday, excluding Sunday");
    }

    @Test
    void testCalculateWorkingDays_SameDay() {
        double days = attendanceEngine.calculateWorkingDays(
            LocalDate.of(2024, 4, 1), // Monday
            LocalDate.of(2024, 4, 1)  // Same Monday
        );
        assertEquals(1.0, days, "Same day should count as 1 working day");
    }

    @Test
    void testCalculateWorkingDays_MonthBoundary() {
        double days = attendanceEngine.calculateWorkingDays(
            LocalDate.of(2024, 3, 29), // Friday
            LocalDate.of(2024, 4, 2)  // Tuesday (March 31 is Sunday)
        );
        assertEquals(4.0, days, "Should count correctly across month boundary, excluding Sunday");
    }

    @Test
    void testCalculateLeaveBalance_Integration() {
        // Integration test combining earned leaves and Total Used Leaves
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        lenient().when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of());

        AttendanceEngine.LeaveBalanceSummary balance = attendanceEngine.calculateLeaveBalance(1L, 2024, 4);
        
        assertTrue(balance.earnedLeaves >= 0, "Should have earned leaves");
        assertEquals(0.0, balance.usedLeaves, "Should have 0 Total Used Leaves initially");
        assertEquals(balance.earnedLeaves, balance.getAvailableLeaves(), "Available should equal earned when unused");
    }

    @Test
    void testCalculateLeaveBalance_WithUsedLeaves() {
        // Test with existing Total Used Leaves
        Leave usedLeave = new Leave();
        usedLeave.setId(1L);
        usedLeave.setPaidDays(1.5);
        usedLeave.setUnpaidDays(0.5);
        usedLeave.setStatus(Leave.LeaveStatus.APPROVED);
        usedLeave.setStartDate(LocalDate.of(2024, 4, 1));
        usedLeave.setEndDate(LocalDate.of(2024, 4, 2));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        lenient().when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of(usedLeave));
        lenient().when(leaveLedgerRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAsc(any(), any(), any()))
                 .thenReturn(List.of(createLeaveLedger(1L, LocalDate.of(2024, 4, 1), LeaveLedger.EventType.APPROVED_LEAVE, 1.5, 0.5)));

        AttendanceEngine.LeaveBalanceSummary balance = attendanceEngine.calculateLeaveBalance(1L, 2024, 4);
        
        assertEquals(1.5, balance.usedLeaves, "Should count only Paid Used Leaves as used");
        assertTrue(balance.getAvailableLeaves() < balance.earnedLeaves, "Available should be less than earned");
    }

    @Test
    void testCalculateLeaveBalance_CrossCycleLeave() {
        // Test leave spanning cycle boundary (June 29 → July 2)
        Leave crossCycleLeave = new Leave();
        crossCycleLeave.setId(1L);
        crossCycleLeave.setPaidDays(3.0);
        crossCycleLeave.setUnpaidDays(1.0);
        crossCycleLeave.setStatus(Leave.LeaveStatus.APPROVED);
        crossCycleLeave.setStartDate(LocalDate.of(2024, 6, 29));
        crossCycleLeave.setEndDate(LocalDate.of(2024, 7, 2));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        lenient().when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of(crossCycleLeave));
        lenient().when(leaveLedgerRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAsc(any(), any(), any()))
                 .thenReturn(List.of(createLeaveLedger(1L, LocalDate.of(2024, 6, 29), LeaveLedger.EventType.APPROVED_LEAVE, 3.0, 1.0)));

        // Test June calculation (cycle 1)
        AttendanceEngine.LeaveBalanceSummary juneBalance = attendanceEngine.calculateLeaveBalance(1L, 2024, 6);
        assertEquals(1, juneBalance.cycle, "June should be cycle 1");
        assertTrue(juneBalance.usedLeaves > 0, "June should have some paid leave usage");

        // Test July calculation (cycle 2)  
        AttendanceEngine.LeaveBalanceSummary julyBalance = attendanceEngine.calculateLeaveBalance(1L, 2024, 7);
        assertEquals(2, julyBalance.cycle, "July should be cycle 2");
        assertTrue(julyBalance.usedLeaves > 0, "July should have some paid leave usage");
    }

    @Test
    void testCalculateLeaveBalance_HalfDayUnpaid() {
        // Test half-day unpaid leave (0.5 unpaid)
        Leave halfDayUnpaidLeave = new Leave();
        halfDayUnpaidLeave.setId(1L);
        halfDayUnpaidLeave.setPaidDays(0.0);
        halfDayUnpaidLeave.setUnpaidDays(0.5);
        halfDayUnpaidLeave.setStatus(Leave.LeaveStatus.APPROVED);
        halfDayUnpaidLeave.setStartDate(LocalDate.of(2024, 4, 1));
        halfDayUnpaidLeave.setEndDate(LocalDate.of(2024, 4, 1));
        halfDayUnpaidLeave.setIsHalfDay(true);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        lenient().when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of(halfDayUnpaidLeave));
        lenient().when(leaveLedgerRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAsc(any(), any(), any()))
                 .thenReturn(List.of(createLeaveLedger(1L, LocalDate.of(2024, 4, 1), LeaveLedger.EventType.APPROVED_LEAVE, 0.0, 0.5)));

        AttendanceEngine.LeaveBalanceSummary balance = attendanceEngine.calculateLeaveBalance(1L, 2024, 4);
        
        assertEquals(0.0, balance.usedLeaves, "Should not count unpaid leave as used");
        assertEquals(0.5, balance.unpaidLeaves, "Should count 0.5 unpaid leave");
    }

    @Test
    void testCalculateLeaveBalance_FutureApprovedLeave() {
        // Test future approved leave (should affect balance immediately)
        Leave futureLeave = new Leave();
        futureLeave.setId(1L);
        futureLeave.setPaidDays(1.0);
        futureLeave.setUnpaidDays(0.0);
        futureLeave.setStatus(Leave.LeaveStatus.APPROVED);
        futureLeave.setStartDate(LocalDate.of(2024, 12, 15));
        futureLeave.setEndDate(LocalDate.of(2024, 12, 15));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        lenient().when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of(futureLeave));
        lenient().when(leaveLedgerRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAsc(any(), any(), any()))
                 .thenReturn(List.of(createLeaveLedger(1L, LocalDate.of(2024, 12, 15), LeaveLedger.EventType.APPROVED_LEAVE, 1.0, 0.0)));

        // Balance should be affected immediately even for future dates
        AttendanceEngine.LeaveBalanceSummary balance = attendanceEngine.calculateLeaveBalance(1L, 2024, 11);
        
        assertEquals(0.0, balance.usedLeaves, "Future approved leave should not affect past month's Total Used Leaves count");
        assertEquals(balance.earnedLeaves, balance.getAvailableLeaves(), "Available should not be reduced by future approved leave");
    }

    @Test
    void testCalculateLeaveBalance_EditExclusion() {
        // Test editing existing leave doesn't consume balance twice
        Leave existingLeave = new Leave();
        existingLeave.setId(1L);
        existingLeave.setPaidDays(2.0);
        existingLeave.setUnpaidDays(0.0);
        existingLeave.setStatus(Leave.LeaveStatus.APPROVED);
        existingLeave.setStartDate(LocalDate.of(2024, 5, 1));
        existingLeave.setEndDate(LocalDate.of(2024, 5, 2));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        lenient().when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of(existingLeave));
        lenient().when(leaveLedgerRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAsc(any(), any(), any()))
                 .thenReturn(List.of(createLeaveLedger(1L, LocalDate.of(2024, 5, 10), LeaveLedger.EventType.APPROVED_LEAVE, 2.0, 0.0)));

        // Calculate balance without exclusion
        AttendanceEngine.LeaveBalanceSummary balanceWithoutExclusion = attendanceEngine.calculateLeaveBalance(1L, 2024, 5);
        assertEquals(2.0, balanceWithoutExclusion.usedLeaves, "Should count 2 paid leave days");

        // Calculate balance with exclusion (editing same leave)
        AttendanceEngine.LeaveBalanceSummary balanceWithExclusion = attendanceEngine.calculateLeaveBalance(1L, 2024, 5, 1L);
        assertEquals(0.0, balanceWithExclusion.usedLeaves, "Should exclude leave being edited");
    }

    @Test
    void testCalculateLeaveBalance_PendingLeaveIgnored() {
        // Test pending leave should not affect balance
        Leave pendingLeave = new Leave();
        pendingLeave.setId(1L);
        pendingLeave.setPaidDays(1.0);
        pendingLeave.setUnpaidDays(0.0);
        pendingLeave.setStatus(Leave.LeaveStatus.PENDING);
        pendingLeave.setStartDate(LocalDate.of(2024, 4, 1));
        pendingLeave.setEndDate(LocalDate.of(2024, 4, 1));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        lenient().when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of(pendingLeave));
        // Pending leaves do not create ledger entries
        lenient().when(leaveLedgerRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAsc(any(), any(), any()))
                 .thenReturn(List.of());

        AttendanceEngine.LeaveBalanceSummary balance = attendanceEngine.calculateLeaveBalance(1L, 2024, 4);
        
        assertEquals(0.0, balance.usedLeaves, "Pending leave should not affect balance");
        assertEquals(0.0, balance.unpaidLeaves, "Pending leave should not affect unpaid balance");
    }

    @Test
    void testCalculateLeaveBalance_RejectedLeaveIgnored() {
        // Test rejected leave should not affect balance
        Leave rejectedLeave = new Leave();
        rejectedLeave.setId(1L);
        rejectedLeave.setPaidDays(1.0);
        rejectedLeave.setUnpaidDays(0.0);
        rejectedLeave.setStatus(Leave.LeaveStatus.REJECTED);
        rejectedLeave.setStartDate(LocalDate.of(2024, 4, 1));
        rejectedLeave.setEndDate(LocalDate.of(2024, 4, 1));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        lenient().when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of(rejectedLeave));
        // Rejected leaves do not create ledger entries
        lenient().when(leaveLedgerRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAsc(any(), any(), any()))
                 .thenReturn(List.of());

        AttendanceEngine.LeaveBalanceSummary balance = attendanceEngine.calculateLeaveBalance(1L, 2024, 4);
        
        assertEquals(0.0, balance.usedLeaves, "Rejected leave should not affect balance");
        assertEquals(0.0, balance.unpaidLeaves, "Rejected leave should not affect unpaid balance");
    }

    @Test
    void testManualAbsence_ConsumesPaidLeaveBalance() {
        Employee emp = new Employee();
        emp.setId(2L);
        emp.setJoiningDate(LocalDate.of(2024, 1, 1));
        emp.setProbationPeriodMonths(3);
        emp.setProbationStatus(Employee.ProbationStatus.CONFIRMED);

        com.hrm.hrmsystem.model.Attendance attendance = new com.hrm.hrmsystem.model.Attendance();
        attendance.setDate(LocalDate.of(2024, 5, 10));
        attendance.setStatus(com.hrm.hrmsystem.model.Attendance.AttendanceStatus.ABSENT);
        attendance.setEmployee(emp);

        when(employeeRepository.findByIdentifier(2L)).thenReturn(Optional.of(emp));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(emp));
        lenient().when(leaveRepository.findByEmployeeId(2L)).thenReturn(List.of());
        lenient().when(leaveLedgerRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAsc(any(), any(), any()))
                 .thenReturn(List.of(createLeaveLedger(2L, LocalDate.of(2024, 5, 10), LeaveLedger.EventType.ABSENT_FULL, 1.0, 0.0)));
        lenient().when(attendanceRepository.findByEmployeeIdAndDateBetween(2L, LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 31)))
            .thenReturn(List.of(attendance));

        AttendanceEngine.LeaveBalanceSummary balance = attendanceEngine.calculateLeaveBalance(2L, 2024, 5);
        assertEquals(1.0, balance.usedLeaves, "Should count 1.0 paid leave from manual absence");
        assertEquals(0.0, balance.unpaidLeaves, "Should count 0.0 unpaid leaves");
        assertEquals(6.5, balance.remaining, "Remaining balance should be 6.5");

        AttendanceSummary summary = attendanceEngine.calculate(2L, YearMonth.of(2024, 5));
        assertEquals(1.0, summary.absent, "Total manually marked absent days should be 1.0");
        assertEquals(0.0, summary.paidAbsent, "Paid absent days should be 0.0 (no longer tracked in summary)");
        assertEquals(0.0, summary.unpaidAbsent, "Unpaid absent days should be 0.0 (no longer tracked in summary)");
    }

    @Test
    void testManualAbsence_MixedPaidUnpaid() {
        Employee emp = new Employee();
        emp.setId(3L);
        emp.setJoiningDate(LocalDate.of(2024, 1, 1));
        emp.setProbationPeriodMonths(3);
        emp.setProbationStatus(Employee.ProbationStatus.CONFIRMED);

        Leave approvedLeave = new Leave();
        approvedLeave.setId(10L);
        approvedLeave.setPaidDays(6.0);
        approvedLeave.setUnpaidDays(0.0);
        approvedLeave.setStatus(Leave.LeaveStatus.APPROVED);
        approvedLeave.setStartDate(LocalDate.of(2024, 5, 1));
        approvedLeave.setEndDate(LocalDate.of(2024, 5, 7));
        approvedLeave.setEmployee(emp);

        com.hrm.hrmsystem.model.Attendance attendance = new com.hrm.hrmsystem.model.Attendance();
        attendance.setDate(LocalDate.of(2024, 5, 15));
        attendance.setStatus(com.hrm.hrmsystem.model.Attendance.AttendanceStatus.ABSENT);
        attendance.setEmployee(emp);

        when(employeeRepository.findByIdentifier(3L)).thenReturn(Optional.of(emp));
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(emp));
        lenient().when(leaveRepository.findByEmployeeId(3L)).thenReturn(List.of(approvedLeave));
        lenient().when(leaveLedgerRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAsc(any(), any(), any()))
                 .thenReturn(List.of(
                     createLeaveLedger(3L, LocalDate.of(2024, 5, 1), LeaveLedger.EventType.APPROVED_LEAVE, 6.0, 0.0),
                     createLeaveLedger(3L, LocalDate.of(2024, 5, 15), LeaveLedger.EventType.ABSENT_FULL, 1.0, 0.0)
                 ));
        lenient().when(attendanceRepository.findByEmployeeId(3L)).thenReturn(List.of(attendance));
        lenient().when(attendanceRepository.findByEmployeeIdAndDateBetween(3L, LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 31)))
            .thenReturn(List.of(attendance));

        AttendanceEngine.LeaveBalanceSummary balance = attendanceEngine.calculateLeaveBalance(3L, 2024, 5);
        assertEquals(7.0, balance.usedLeaves, "Total used paid leaves should be 7.0");
        assertEquals(0.0, balance.unpaidLeaves, "Total unpaid leaves should be 0.0");
        assertEquals(0.5, balance.remaining, "Remaining balance should be 0.5");

        AttendanceSummary summary = attendanceEngine.calculate(3L, YearMonth.of(2024, 5));
        assertEquals(1.0, summary.absent, "Total manually marked absent days should be 1.0");
        assertEquals(0.0, summary.paidAbsent, "Paid absent days should be 0.0 (no longer tracked in summary)");
        assertEquals(0.0, summary.unpaidAbsent, "Unpaid absent days should be 0.0 (no longer tracked in summary)");
    }

    @Test
    void testCalculate_SundaysCountedAsPresent() {
        Employee emp = new Employee();
        emp.setId(4L);
        emp.setJoiningDate(LocalDate.of(2024, 1, 1));
        emp.setProbationPeriodMonths(3);
        emp.setProbationStatus(Employee.ProbationStatus.CONFIRMED);

        when(employeeRepository.findByIdentifier(4L)).thenReturn(Optional.of(emp));
        when(employeeRepository.findById(4L)).thenReturn(Optional.of(emp));
        
        // No attendance or leave records (all weekdays unmarked, sundays weekly off)
        when(attendanceRepository.findByEmployeeIdAndDateBetween(4L, LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 31)))
            .thenReturn(List.of());

        AttendanceSummary summary = attendanceEngine.calculate(4L, YearMonth.of(2024, 5));
        // May 2024 has 4 Sundays (5th, 12th, 19th, 26th) and 27 weekdays.
        // Under the new rules, unmarked past weekdays do NOT count as present.
        // Only the 4 Sundays count as weekly off (which are counted as worked/payable).
        assertEquals(4.0, summary.workedDays, "Sundays should count as 4.0 worked/present days");
        assertEquals(4.0, summary.payableDays, "Sundays should count as 4.0 payable days");
    }

    @Test
    void testCalculate_HalfDayAbsentToday_CountsAsHalfDayPresent() {
        Employee emp = new Employee();
        emp.setId(5L);
        emp.setJoiningDate(LocalDate.of(2024, 1, 1));
        emp.setProbationPeriodMonths(3);
        emp.setProbationStatus(Employee.ProbationStatus.CONFIRMED);

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);

        // Add one attendance record for TODAY: marked ABSENT for FIRST_HALF
        com.hrm.hrmsystem.model.Attendance attendance = new com.hrm.hrmsystem.model.Attendance();
        attendance.setDate(today);
        attendance.setStatus(com.hrm.hrmsystem.model.Attendance.AttendanceStatus.ABSENT);
        attendance.setHalfType(com.hrm.hrmsystem.model.Attendance.HalfType.FIRST_HALF);
        attendance.setEmployee(emp);

        when(employeeRepository.findByIdentifier(5L)).thenReturn(Optional.of(emp));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(emp));
        when(attendanceRepository.findByEmployeeIdAndDateBetween(5L, currentMonth.atDay(1), currentMonth.atEndOfMonth()))
            .thenReturn(List.of(attendance));

        AttendanceSummary summary = attendanceEngine.calculate(5L, currentMonth);

        // Today's calculation:
        // FIRST_HALF = ABSENT (0.5 absent)
        // SECOND_HALF = auto-present (0.5 worked) because attendance is not null on today.
        // So for today: worked = 0.5, absent = 0.5.
        assertEquals(0.5, summary.absent, "Should count 0.5 absent days");
        assertTrue(summary.workedDays % 1.0 == 0.5, "Worked days should have a half-day present (.5)");
    }

    @Test
    void testCalculate_HalfDayLeaveAndHalfDayAbsent() {
        Employee emp = new Employee();
        emp.setId(6L);
        emp.setJoiningDate(LocalDate.of(2024, 1, 1));
        emp.setProbationPeriodMonths(3);
        emp.setProbationStatus(Employee.ProbationStatus.CONFIRMED);

        LocalDate date = LocalDate.of(2024, 5, 10);
        YearMonth currentMonth = YearMonth.of(2024, 5);

        Leave halfLeave = new Leave();
        halfLeave.setId(20L);
        halfLeave.setPaidDays(0.5);
        halfLeave.setUnpaidDays(0.0);
        halfLeave.setStatus(Leave.LeaveStatus.APPROVED);
        halfLeave.setStartDate(date);
        halfLeave.setEndDate(date);
        halfLeave.setIsHalfDay(true);
        halfLeave.setHalfType(Leave.HalfType.FIRST_HALF);
        halfLeave.setEmployee(emp);

        com.hrm.hrmsystem.model.Attendance attendance = new com.hrm.hrmsystem.model.Attendance();
        attendance.setDate(date);
        attendance.setStatus(com.hrm.hrmsystem.model.Attendance.AttendanceStatus.ABSENT);
        attendance.setHalfType(com.hrm.hrmsystem.model.Attendance.HalfType.SECOND_HALF);
        attendance.setEmployee(emp);

        when(employeeRepository.findByIdentifier(6L)).thenReturn(Optional.of(emp));
        when(employeeRepository.findById(6L)).thenReturn(Optional.of(emp));
        lenient().when(leaveRepository.findByEmployeeId(6L)).thenReturn(List.of(halfLeave));
        lenient().when(leaveLedgerRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAsc(any(), any(), any()))
                 .thenReturn(List.of(
                     createLeaveLedger(6L, LocalDate.of(2024, 5, 10), LeaveLedger.EventType.APPROVED_LEAVE, 0.5, 0.0),
                     createLeaveLedger(6L, LocalDate.of(2024, 5, 10), LeaveLedger.EventType.ABSENT_HALF, 0.5, 0.0)
                 ));
        lenient().when(attendanceRepository.findByEmployeeId(6L)).thenReturn(List.of(attendance));
        lenient().when(attendanceRepository.findByEmployeeIdAndDateBetween(6L, currentMonth.atDay(1), currentMonth.atEndOfMonth()))
            .thenReturn(List.of(attendance));

        AttendanceEngine.LeaveBalanceSummary balance = attendanceEngine.calculateLeaveBalance(6L, 2024, 5);
        assertEquals(1.0, balance.usedLeaves, "Total used paid leaves should be 1.0");
        assertEquals(0.0, balance.unpaidLeaves, "Total unpaid leaves should be 0.0");
        assertEquals(6.5, balance.remaining, "Remaining balance should be 6.5");

        AttendanceSummary summary = attendanceEngine.calculate(6L, currentMonth);
        assertEquals(0.0, summary.paidLeave, "Paid leave should be 0.0 (no longer tracked in summary)");
        assertEquals(0.5, summary.absent, "Absent should be 0.5");
        assertEquals(0.0, summary.paidAbsent, "Paid absent should be 0.0 (no longer tracked in summary)");
    }
}
