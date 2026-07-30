package com.hrm.hrmsystem.engine;

import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.Leave;
import com.hrm.hrmsystem.repository.AttendanceRepository;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.repository.LeaveRepository;
import com.hrm.hrmsystem.repository.LeaveLedgerRepository;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AttendanceEngineFutureLeaveTest {

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
        testEmployee.setJoiningDate(LocalDate.of(2023, 1, 1));
        testEmployee.setProbationPeriodMonths(3);
        testEmployee.setProbationStatus(Employee.ProbationStatus.CONFIRMED);

        lenient().when(employeeRepository.findByIdentifier(any())).thenAnswer(invocation -> {
            Long arg = invocation.getArgument(0);
            return employeeRepository.findById(arg);
        });
        lenient().when(employeeRepository.findByIdentifierWithDepartment(any())).thenAnswer(invocation -> {
            Long arg = invocation.getArgument(0);
            return employeeRepository.findById(arg);
        });
    }

    @Test
    void testCalculate_WithFutureApprovedLeave_NotCounted() {
        // Leave approved for next month (June) while we calculate May
        Leave futureLeave = new Leave();
        futureLeave.setId(1L);
        futureLeave.setStatus(Leave.LeaveStatus.APPROVED);
        futureLeave.setStartDate(LocalDate.of(2024, 6, 5));
        futureLeave.setEndDate(LocalDate.of(2024, 6, 6));
        futureLeave.setPaidDays(2.0);
        futureLeave.setUnpaidDays(0.0);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of(futureLeave));
        // No attendance records for May
        when(attendanceRepository.findByEmployeeIdAndDateBetween(1L, YearMonth.of(2024, 5).atDay(1), YearMonth.of(2024, 5).atEndOfMonth()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(leaveLedgerRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        AttendanceSummary summary = attendanceEngine.calculate(1L, YearMonth.of(2024, 5));
        // Future leave should not affect May's Total Used Leaves
        assertEquals(0.0, summary.paidLeave, "Future approved leave must not be counted in current month");
        assertEquals(0.0, summary.unpaidLeave, "Future approved leave must not be counted in current month");
    }
}
