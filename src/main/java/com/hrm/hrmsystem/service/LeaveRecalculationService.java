package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.config.PayrollPolicy;
import com.hrm.hrmsystem.engine.AttendanceEngine;
import com.hrm.hrmsystem.model.Attendance;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.Leave;
import com.hrm.hrmsystem.model.LeaveLedger;
import com.hrm.hrmsystem.repository.AttendanceRepository;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.repository.LeaveLedgerRepository;
import com.hrm.hrmsystem.repository.LeaveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ✅ LEAVE RECALCULATION SERVICE
 *
 * Professional real-time leave balance engine.
 * Processes ALL events (absences + approved leaves) chronologically,
 * consuming the paid balance in date-order.
 *
 * Architecture:
 *   Source tables  : attendance (absences), leaves (approved requests)
 *   Calculated table: leave_ledger (generated, never manually edited)
 *
 * Trigger on:
 *   - approveLeave()
 *   - cancelLeave() / rejectLeave()
 *   - markAbsent() / markHalfDay() / markPresent() / resolvePending()
 *
 * Business rules enforced:
 *   ✅ Chronological balance consumption (earliest date first)
 *   ✅ Same-date priority: attendance (1) before approved leave (2)
 *   ✅ Probation → all absent days are unpaid
 *   ✅ Sunday skip for leaves (not counted as working days)
 *   ✅ Cycle reset at Jun 30 / Dec 31
 *   ✅ Payroll-locked months are skipped
 *   ✅ Paid cap: paid = min(days, runningBalance)
 *   ✅ After calculation: updates Leave.paidDays/finalPaidDays for display compatibility
 */
@Service
public class LeaveRecalculationService {

    private static final Logger log = LoggerFactory.getLogger(LeaveRecalculationService.class);

    private final EmployeeRepository employeeRepository;
    private final LeaveRepository leaveRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveLedgerRepository leaveLedgerRepository;
    private final AttendanceEngine attendanceEngine;
    private final PayrollLockService payrollLockService;

    public LeaveRecalculationService(
            EmployeeRepository employeeRepository,
            LeaveRepository leaveRepository,
            AttendanceRepository attendanceRepository,
            LeaveLedgerRepository leaveLedgerRepository,
            AttendanceEngine attendanceEngine,
            PayrollLockService payrollLockService) {
        this.employeeRepository = employeeRepository;
        this.leaveRepository = leaveRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveLedgerRepository = leaveLedgerRepository;
        this.attendanceEngine = attendanceEngine;
        this.payrollLockService = payrollLockService;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Recalculate leave balance for the full cycle containing `fromDate`.
     * Deletes and re-computes ALL ledger entries in that cycle.
     *
     * Always runs the full cycle (not partial) to guarantee correctness — avoids
     * stale-state bugs from partial prior runs and "first-run" edge cases.
     *
     * @param employeeId the employee to recalculate for
     * @param fromDate   any date in the cycle to recalculate (cycle boundaries auto-detected)
     */
    @Transactional
    @CacheEvict(value = {"leaveBalanceCache", "leaveStatsCache", "attendanceCache", "payrollCalcCache"}, allEntries = true)
    public void recalculateFromDate(Long employeeId, LocalDate fromDate) {
        Employee employee = employeeRepository.findByIdentifier(employeeId).orElse(null);
        if (employee == null) {
            log.warn("LeaveRecalculationService: Employee {} not found, skipping", employeeId);
            return;
        }

        // Always recalculate the entire cycle — eliminates first-run edge cases
        // and stale-state from partial ledger entries.
        LocalDate cycleStart = getCycleStart(fromDate);
        LocalDate cycleEnd = getCycleEnd(fromDate);

        log.info("Recalculating full cycle for employee {} ({} to {})", employeeId, cycleStart, cycleEnd);

        // 1. Delete ALL existing ledger entries for this cycle (full replace, no duplicates)
        leaveLedgerRepository.deleteBetween(employeeId, cycleStart, cycleEnd);

        // 2. Build ALL events in the cycle: absences + approved leaves, sorted by date+priority
        List<LeaveEvent> events = buildEvents(employeeId, cycleStart, cycleEnd);
        events.sort(Comparator.comparing(LeaveEvent::getDate)
                .thenComparingInt(LeaveEvent::getPriority));

        // 3. Sequential balance consumption — month-by-month credit accrual
        double runningBalance = 0.0; // Cycle always starts at 0 (no carry-forward)
        YearMonth currentMonth = YearMonth.from(cycleStart);
        YearMonth cycleEndMonth = YearMonth.from(cycleEnd);

        List<LeaveLedger> ledgerEntries = new ArrayList<>();
        // Track per-leave totals (leaveId → [paid, unpaid, total])
        Map<Long, double[]> leaveTotals = new LinkedHashMap<>();

        // Group events by month for credit-first processing
        Map<YearMonth, List<LeaveEvent>> eventsByMonth = events.stream()
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getDate()),
                        java.util.LinkedHashMap::new, Collectors.toList()));

        while (!currentMonth.isAfter(cycleEndMonth)) {
            // Add monthly credit at start of each month (if probation complete and Rule 3 satisfied)
            boolean probationDone = attendanceEngine.isProbationCompleted(
                    employee, currentMonth.atEndOfMonth());
            
            if (probationDone && employee.getJoiningDate() != null) {
                Integer probationMonths = employee.getProbationPeriodMonths() != null ? employee.getProbationPeriodMonths() : 3;
                LocalDate probationEnd;
                if (employee.getProbationStatus() == com.hrm.hrmsystem.model.Employee.ProbationStatus.CONFIRMED) {
                    probationEnd = employee.getJoiningDate();
                } else {
                    probationEnd = employee.getJoiningDate().plusMonths(probationMonths);
                }
                
                YearMonth probationEndMonth = YearMonth.from(probationEnd);
                
                if (currentMonth.equals(probationEndMonth)) {
                    if (probationEnd.getDayOfMonth() <= 15) {
                        runningBalance = round(runningBalance + PayrollPolicy.getLeaveAccrualRate());
                    }
                } else if (currentMonth.isAfter(probationEndMonth)) {
                    runningBalance = round(runningBalance + PayrollPolicy.getLeaveAccrualRate());
                }
            }

            // Process events in this month
            List<LeaveEvent> monthEvents = eventsByMonth.getOrDefault(currentMonth, List.of());
            for (LeaveEvent event : monthEvents) {
                // Skip payroll-locked months
                if (payrollLockService.isPayrollLockedForEmployee(employeeId,
                        event.getDate().getMonthValue(), event.getDate().getYear())) {
                    log.debug("Skipping {} event on {} — payroll locked", event.getType(), event.getDate());
                    continue;
                }

                double requestedDays = event.getDays();
                double paid;
                double unpaid;

                boolean inProbation = !attendanceEngine.isProbationCompleted(
                        employee, YearMonth.from(event.getDate()).atEndOfMonth());

                if (inProbation) {
                    paid = 0.0;
                    unpaid = requestedDays;
                } else {
                    paid = round(Math.min(requestedDays, runningBalance));
                    unpaid = round(requestedDays - paid);
                }

                runningBalance = round(runningBalance - paid);

                ledgerEntries.add(new LeaveLedger(
                        employeeId, event.getDate(), event.getType(), event.getReferenceId(),
                        toBD(requestedDays), toBD(paid), toBD(unpaid), toBD(runningBalance)));

                // Accumulate per-leave totals
                if (event.getType() == LeaveLedger.EventType.APPROVED_LEAVE && event.getReferenceId() != null) {
                    leaveTotals.computeIfAbsent(event.getReferenceId(), k -> new double[]{0, 0, 0});
                    leaveTotals.get(event.getReferenceId())[0] += paid;
                    leaveTotals.get(event.getReferenceId())[1] += unpaid;
                    leaveTotals.get(event.getReferenceId())[2] += requestedDays;
                }
            }

            // Cycle reset: balance expires at end of Jun / Dec
            if (currentMonth.getMonthValue() == 6 || currentMonth.getMonthValue() == 12) {
                if (!currentMonth.equals(cycleEndMonth)) {
                    log.info("Cycle reset at end of {} for employee {}, balance {} expired",
                            currentMonth, employeeId, runningBalance);
                    runningBalance = 0.0;
                }
            }

            currentMonth = currentMonth.plusMonths(1);
        }

        // 4. Save all ledger entries in one batch (no duplicates — deleted above)
        leaveLedgerRepository.saveAll(ledgerEntries);
        leaveLedgerRepository.flush();

        // 5. Update Leave entity fields from computed totals — done ONCE at the end,
        //    not inside the loop, preserving separation of source vs calculated data.
        updateLeaveEntities(leaveTotals);

        log.info("Recalculation complete for employee {}: {} events, {} ledger entries",
                employeeId, events.size(), ledgerEntries.size());
    }


    /**
     * Get the latest calculated ledger entry for a specific leave.
     * Used by convertToDTO to show accurate paid/unpaid from ledger.
     */
    public Optional<LeaveLedger> getLedgerEntryForLeave(Long leaveId) {
        return leaveLedgerRepository.findByReferenceIdAndEventType(leaveId, LeaveLedger.EventType.APPROVED_LEAVE);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // INTERNAL — Event Building
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Internal event model — represents a single day's leave or absence.
     * Priority 1 = attendance (processed before leaves on same date)
     * Priority 2 = approved leave
     */
    public static class LeaveEvent {
        private final LocalDate date;
        private final LeaveLedger.EventType type;
        private final double days; // 0.5 for half-day, 1.0 for full-day
        private final Long referenceId;
        private final int priority;

        LeaveEvent(LocalDate date, LeaveLedger.EventType type, double days, Long referenceId) {
            this.date = date;
            this.type = type;
            this.days = days;
            this.referenceId = referenceId;
            // Attendance events processed before leave events on the same date
            this.priority = (type == LeaveLedger.EventType.APPROVED_LEAVE) ? 2 : 1;
        }

        public LocalDate getDate() { return date; }
        public LeaveLedger.EventType getType() { return type; }
        public double getDays() { return days; }
        public Long getReferenceId() { return referenceId; }
        public int getPriority() { return priority; }
    }

    private List<LeaveEvent> buildEvents(Long employeeId, LocalDate fromDate, LocalDate toDate) {
        List<LeaveEvent> events = new ArrayList<>();

        // A. Absence events from attendance records
        List<Attendance> absences = attendanceRepository.findByEmployeeIdAndDateBetween(employeeId, fromDate, toDate)
                .stream()
                .filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT
                        || a.getStatus() == Attendance.AttendanceStatus.HALF_DAY
                        || (a.getStatus() == Attendance.AttendanceStatus.PRESENT && a.getHalfType() != null))
                // Skip attendance on a day fully covered by an approved leave (leave takes precedence)
                .collect(Collectors.toList());

        // Get approved leaves covering the date range (for cross-reference)
        List<Leave> approvedLeaves = leaveRepository.findByEmployeeId(employeeId).stream()
                .filter(l -> l.getStatus() == Leave.LeaveStatus.APPROVED)
                .filter(l -> l.getStartDate() != null && l.getEndDate() != null
                        && !l.getEndDate().isBefore(fromDate)
                        && !l.getStartDate().isAfter(toDate))
                .sorted(Comparator.comparing(Leave::getStartDate))
                .collect(Collectors.toList());

        // Build set of dates fully covered by approved full-day leaves
        Set<LocalDate> fullDayLeaveDates = new HashSet<>();
        for (Leave leave : approvedLeaves) {
            if (leave.getIsHalfDay() == null || !leave.getIsHalfDay()) {
                LocalDate d = leave.getStartDate();
                while (!d.isAfter(leave.getEndDate())) {
                    if (d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                        fullDayLeaveDates.add(d);
                    }
                    d = d.plusDays(1);
                }
            }
        }

        // Add absence events (skip dates covered by full-day approved leave)
        for (Attendance att : absences) {
            LocalDate date = att.getDate();
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            if (fullDayLeaveDates.contains(date)) continue; // leave takes precedence

            double days;
            LeaveLedger.EventType type;

            boolean hasHalfLeave = approvedLeaves.stream().anyMatch(l ->
                    l.getIsHalfDay() != null && l.getIsHalfDay()
                            && !date.isBefore(l.getStartDate()) && !date.isAfter(l.getEndDate()));

            if (att.getStatus() == Attendance.AttendanceStatus.HALF_DAY || 
               (att.getStatus() == Attendance.AttendanceStatus.ABSENT && att.getHalfType() != null)) {
                // Employee was only absent for HALF the day.
                // If they have a half-day leave, it covers this exact half, so NO absence deduction.
                if (hasHalfLeave) {
                    continue;
                }
                days = 0.5;
                type = LeaveLedger.EventType.ABSENT_HALF;
            } else if (att.getStatus() == Attendance.AttendanceStatus.PRESENT && att.getHalfType() != null) {
                // If marked PRESENT for one half but not the other, the other half is technically an absence unless covered.
                // However, AttendanceService markHalfDay sets status to ABSENT when the other half is truly absent.
                // So if status is PRESENT with a halfType, it means they worked half day and the OTHER half was covered by leave.
                // In this case, we DO NOT deduct anything here because the Leave event will cover the 0.5 absence.
                continue;
            } else {
                // Full-day ABSENT
                // If they have a half-day leave, it covers 0.5, leaving 0.5 as actual absence.
                days = hasHalfLeave ? 0.5 : 1.0;
                type = hasHalfLeave ? LeaveLedger.EventType.ABSENT_HALF : LeaveLedger.EventType.ABSENT_FULL;
            }

            events.add(new LeaveEvent(date, type, days, att.getId()));
        }

        // B. Approved leave events — one event per working day in the leave span
        for (Leave leave : approvedLeaves) {
            LocalDate d = leave.getStartDate().isBefore(fromDate) ? fromDate : leave.getStartDate();
            LocalDate end = leave.getEndDate().isAfter(toDate) ? toDate : leave.getEndDate();

            boolean isHalf = leave.getIsHalfDay() != null && leave.getIsHalfDay();
            double daysPerEntry = isHalf ? 0.5 : 1.0;

            while (!d.isAfter(end)) {
                if (d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    events.add(new LeaveEvent(d, LeaveLedger.EventType.APPROVED_LEAVE, daysPerEntry, leave.getId()));
                }
                d = d.plusDays(1);
            }
        }

        return events;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // INTERNAL — Balance Calculation
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Update Leave entity paid/unpaid fields from the computed totals.
     * Called ONCE after all ledger entries are saved — not inside the calculation loop.
     * This preserves backward compatibility with convertToDTO and resolveDay.
     */
    private void updateLeaveEntities(Map<Long, double[]> leaveTotals) {
        for (Map.Entry<Long, double[]> entry : leaveTotals.entrySet()) {
            Long leaveId = entry.getKey();
            double paid = round(entry.getValue()[0]);
            double unpaid = round(entry.getValue()[1]);
            double total = round(entry.getValue()[2]);

            leaveRepository.findById(leaveId).ifPresent(leave -> {
                leave.setPaidDays(paid);
                leave.setUnpaidDays(unpaid);
                leave.setTotalDays(total);
                // Also update frozen fields so convertToDTO picks them up correctly
                leave.setFinalPaidDays(paid);
                leave.setFinalUnpaidDays(unpaid);
                leave.setFinalTotalDays(total);
                leaveRepository.save(leave);
                log.debug("Updated Leave ID {}: paid={}, unpaid={}, total={}", leaveId, paid, unpaid, total);
            });
        }
        if (!leaveTotals.isEmpty()) {
            leaveRepository.flush();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // INTERNAL — Cycle Utilities
    // ─────────────────────────────────────────────────────────────────────────────

    /** Jan 1 for H1 cycle, Jul 1 for H2 cycle */
    private LocalDate getCycleStart(LocalDate date) {
        int year = date.getYear();
        return (date.getMonthValue() <= 6)
                ? LocalDate.of(year, 1, 1)
                : LocalDate.of(year, 7, 1);
    }

    /** Jun 30 for H1 cycle, Dec 31 for H2 cycle */
    private LocalDate getCycleEnd(LocalDate date) {
        int year = date.getYear();
        return (date.getMonthValue() <= 6)
                ? LocalDate.of(year, 6, 30)
                : LocalDate.of(year, 12, 31);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private BigDecimal toBD(double value) {
        return BigDecimal.valueOf(round(value)).setScale(1, RoundingMode.HALF_UP);
    }
}
