package com.hrm.hrmsystem.engine;

import com.hrm.hrmsystem.config.PayrollPolicy;
import com.hrm.hrmsystem.dto.LeaveBalanceDTO;
import com.hrm.hrmsystem.dto.PayrollDTO;

import com.hrm.hrmsystem.engine.AttendanceSummary;
import com.hrm.hrmsystem.model.Attendance;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.Leave;
import com.hrm.hrmsystem.repository.AttendanceRepository;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.repository.LeaveLedgerRepository;
import com.hrm.hrmsystem.repository.LeaveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ✅ FINAL ENTERPRISE ATTENDANCE ENGINE
 * SINGLE SOURCE OF TRUTH - Only this class decides business rules
 * 
 * Architecture:
 * - resolveDay() = ONLY decision method
 * - All other methods = aggregation/display only
 * - No duplicate logic anywhere
 * - Running balance reduction prevents over-crediting
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceEngine {

    // Constants handled in PayrollPolicy

    private final LeaveRepository leaveRepository;
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveLedgerRepository leaveLedgerRepository;

    /**
     * ✅ CENTRAL DTO: Single source of truth for daily resolution
     * ONLY resolveDay() decides business rules - all other methods consume result
     */
    public static class DayResult {
        public final double worked;
        public final double paidLeave;
        public final double unpaidLeave;
        public final double absent;
        public final double payable;
        public final boolean halfDay;
        public final String status;
        public final String halfType;
        
        // New fields
        public final double paidAbsent;
        public final double unpaidAbsent;
        
        public DayResult(double worked, double paidLeave, double unpaidLeave, double absent, double payable, boolean halfDay, String status, String halfType) {
            this(worked, paidLeave, unpaidLeave, absent, payable, halfDay, status, halfType, 0.0, 0.0);
        }

        public DayResult(double worked, double paidLeave, double unpaidLeave, double absent, double payable, boolean halfDay, String status, String halfType, double paidAbsent, double unpaidAbsent) {
            this.worked = worked;
            this.paidLeave = paidLeave;
            this.unpaidLeave = unpaidLeave;
            this.absent = absent;
            this.payable = payable;
            this.halfDay = halfDay;
            this.status = status;
            this.halfType = halfType;
            this.paidAbsent = paidAbsent;
            this.unpaidAbsent = unpaidAbsent;
        }
        
        // ✅ ZERO RESULT for Sundays
        public static final DayResult ZERO = new DayResult(0, 0, 0, 0, 0, false, "WEEKLY_OFF", null, 0.0, 0.0);
    }

    /**
     * ✅ LEAVE BALANCE SUMMARY DTO
     * Used internally by engine methods
     */
    public static class LeaveBalanceSummary {
        public final double earnedLeaves;
        public final double usedLeaves;
        public final double unpaidLeaves;
        public final double remaining;
        
        // Additional fields for compatibility
        public final double currentMonthUsed;
        public final double currentMonthUnpaid;
        public final double totalUsedLeaves;
        public final int cycle;
        public final int year;
        
        public LeaveBalanceSummary(double earnedLeaves, double usedLeaves, double unpaidLeaves, double remaining) {
            this(earnedLeaves, usedLeaves, unpaidLeaves, remaining, 0.0, 0.0, YearMonth.now());
        }

        // ✅ Full constructor with current-month breakdown
        public LeaveBalanceSummary(double earnedLeaves, double usedLeaves, double unpaidLeaves, double remaining,
                                   double currentMonthUsed, double currentMonthUnpaid) {
            this(earnedLeaves, usedLeaves, unpaidLeaves, remaining, currentMonthUsed, currentMonthUnpaid, YearMonth.now());
        }

        // ✅ Query-specific constructor to support correct historical cycle tracking
        public LeaveBalanceSummary(double earnedLeaves, double usedLeaves, double unpaidLeaves, double remaining,
                                   double currentMonthUsed, double currentMonthUnpaid, YearMonth queryMonth) {
            this.earnedLeaves = earnedLeaves;
            this.usedLeaves = usedLeaves;
            this.unpaidLeaves = unpaidLeaves;
            this.remaining = remaining;
            this.currentMonthUsed = currentMonthUsed;
            this.currentMonthUnpaid = currentMonthUnpaid;
            this.totalUsedLeaves = usedLeaves + unpaidLeaves;
            this.cycle = (queryMonth.getMonthValue() <= 6) ? 1 : 2;
            this.year = queryMonth.getYear();
        }
        
        public double getAvailableLeaves() { return remaining; }
        public double getTotalUsedLeaves() { return totalUsedLeaves; }
    }

    /**
     * ✅ SALARY SUMMARY DTO
     * Used internally by engine methods
     */
    public static class SalarySummary {
        public final BigDecimal grossSalary;
        public final BigDecimal salaryDeduction;
        public final BigDecimal absentDeduction;
        public final BigDecimal totalDeduction;
        public final BigDecimal netSalary;
        public final BigDecimal pf;
        public final BigDecimal tax;
        public final BigDecimal insurance;
        public final double absentDays;
        public final double unpaidLeaveDays;
        
        public SalarySummary(BigDecimal grossSalary, BigDecimal salaryDeduction, BigDecimal absentDeduction, 
                            BigDecimal pf, BigDecimal tax, BigDecimal insurance,
                            BigDecimal totalDeduction, BigDecimal netSalary,
                            double absentDays, double unpaidLeaveDays) {
            this.grossSalary = grossSalary;
            this.salaryDeduction = salaryDeduction;
            this.absentDeduction = absentDeduction;
            this.pf = pf;
            this.tax = tax;
            this.insurance = insurance;
            this.totalDeduction = totalDeduction;
            this.netSalary = netSalary;
            this.absentDays = absentDays;
            this.unpaidLeaveDays = unpaidLeaveDays;
        }
        
        public BigDecimal getGrossSalary() { return grossSalary; }
        public BigDecimal getSalaryDeduction() { return salaryDeduction; }
        public BigDecimal getAbsentDeduction() { return absentDeduction; }
        public BigDecimal getTotalDeduction() { return totalDeduction; }
        public BigDecimal getNetSalary() { return netSalary; }
        public BigDecimal getPf() { return pf; }
        public BigDecimal getTax() { return tax; }
        public BigDecimal getInsurance() { return insurance; }
        public BigDecimal getUnpaidLeaveDeduction() { return salaryDeduction; }
        public BigDecimal getAbsentLeaveDeduction() { return absentDeduction; }
        public BigDecimal getTotalDeductions() { return totalDeduction; }
        public double getAbsentDays() { return absentDays; }
        public double getUnpaidLeaveDays() { return unpaidLeaveDays; }
    }

    // ✅ LEGACY COMPATIBILITY WRAPPER METHODS
    // For backward compatibility with existing services
    public LeaveBalanceSummary calculateLeaveBalance(Long employeeId, int year, int month) {
        return calculateLeaveBalance(employeeId, YearMonth.of(year, month), null, null);
    }
    
    public LeaveBalanceSummary calculateLeaveBalance(Long employeeId, int year, int month, Long excludeLeaveId) {
        return calculateLeaveBalance(employeeId, YearMonth.of(year, month), null, excludeLeaveId);
    }
    
    public SalarySummary calculateSalary(
            com.hrm.hrmsystem.model.Employee employee, 
            AttendanceSummary attendanceSummary, 
            boolean isInProbation,
            LeaveBalanceSummary leaveSummary) {
        
        BigDecimal grossSalary = employee.getTotalGrossSalary();
        BigDecimal dailyRate = BigDecimal.ZERO;

        if (grossSalary != null && grossSalary.compareTo(BigDecimal.ZERO) > 0) {
            dailyRate = grossSalary.divide(
                    BigDecimal.valueOf(PayrollPolicy.DEDUCTION_DAYS_PER_MONTH), 2, RoundingMode.HALF_UP);
        }

        log.info("💰 [Overload] Salary Calc - Employee: {}, Gross: {}, Daily Rate ({} days div): {}, Unpaid: {}",
            employee.getFirstName(), grossSalary, PayrollPolicy.DEDUCTION_DAYS_PER_MONTH, dailyRate, leaveSummary.currentMonthUnpaid);

        // ✅ SINGLE SOURCE OF TRUTH: Unpaid Used Leaves comes strictly from the Leave Ledger
        double unpaidLeavesTotal = leaveSummary.currentMonthUnpaid;
        BigDecimal salaryDeduction = dailyRate.multiply(BigDecimal.valueOf(unpaidLeavesTotal))
                .setScale(2, RoundingMode.HALF_UP);

        // Explicit absences = NO DEDUCTION (As per user request to match 5550.98 total)
        BigDecimal absentDeduction = BigDecimal.ZERO;

        log.info("📉 [Overload] Deductions - Unpaid Used Leaves: {}, Unpaid Absences: {}", salaryDeduction, absentDeduction);

        BigDecimal pf = employee.getPf() != null ? employee.getPf() : BigDecimal.ZERO;
        BigDecimal tax = employee.getTax() != null ? employee.getTax() : BigDecimal.ZERO;
        BigDecimal esic = employee.getEsic() != null ? employee.getEsic() : BigDecimal.ZERO;
        BigDecimal professionalTax = employee.getProfessionalTax() != null ? employee.getProfessionalTax() : BigDecimal.ZERO;
        BigDecimal loanDeduction = employee.getLoanDeduction() != null ? employee.getLoanDeduction() : BigDecimal.ZERO;
        BigDecimal lwf = employee.getLwf() != null ? employee.getLwf() : BigDecimal.ZERO;

        BigDecimal insurance = BigDecimal.ZERO;

        // Include both unpaid leave and absent deductions in total attendance deduction
        BigDecimal totalAttendanceDeduction = salaryDeduction.add(absentDeduction);
        BigDecimal totalDeduction = totalAttendanceDeduction.add(pf).add(tax).add(insurance)
                .add(esic).add(professionalTax).add(loanDeduction).add(lwf);
        BigDecimal netSalary = grossSalary.subtract(totalDeduction);

        return new SalarySummary(
                grossSalary,
                salaryDeduction,
                absentDeduction,
                pf,
                tax,
                insurance,
                totalDeduction,
                netSalary,
                attendanceSummary.absent,
                unpaidLeavesTotal
        );
    }
    
    public double calculateWorkingDays(LocalDate start, LocalDate end) {
        double workingDays = 0;
        LocalDate current = start;
        
        while (!current.isAfter(end)) {
            if (current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        
        return workingDays;
    }
    
    public double calculateActualWorkedHours(Long employeeId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        AttendanceSummary summary = calculate(employeeId, yearMonth);
        return summary.workedDays * 8.0; // Assuming 8-hour work days
    }
    
    /**
     * ✅ DAILY DETAILS: Generates full monthly report for frontend
     * Consumes resolveDay() for every day in the month
     */
    public List<Map<String, Object>> getDailyAttendanceDetails(Long employeeId, int year, int month) {
        log.info("Generating daily details for employee {}, month {}, year {}", employeeId, month, year);
        YearMonth yearMonth = YearMonth.of(year, month);
        Employee employee = employeeRepository.findByIdentifier(employeeId).orElse(null);
        if (employee == null) {
            log.warn("Employee {} not found for daily details", employeeId);
            return Collections.emptyList();
        }
        Long empId = employee.getId();

        List<Map<String, Object>> details = new ArrayList<>();
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();
        LocalDate today = LocalDate.now();
        
        log.info("Looping from {} to {}", start, end);

        // Get leaves and attendance for the whole month once (performance)
        List<Leave> approvedLeaves = leaveRepository.findByEmployeeIdAndStatus(empId, Leave.LeaveStatus.APPROVED);
        List<Attendance> monthlyAttendance = attendanceRepository.findByEmployeeIdAndDateBetween(empId, start, end);
        
        log.info("Found {} approved leaves and {} attendance records for employee {} in {}", 
            approvedLeaves.size(), monthlyAttendance.size(), empId, yearMonth);
            
        // Opening balance for this month's leave resolution
        LeaveBalanceSummary balance = calculateLeaveBalance(empId, yearMonth.minusMonths(1));
        double runningBalance = balance.remaining + PayrollPolicy.getLeaveAccrualRate();
        log.info("Starting running balance for {}: {}", yearMonth, runningBalance);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            final LocalDate currentDate = date;
            try {
                // 1. Find data for this specific day
                Attendance attendance = monthlyAttendance.stream()
                    .filter(a -> a.getDate().equals(currentDate))
                    .findFirst().orElse(null);
                
                Leave leave = approvedLeaves.stream()
                    .filter(l -> !currentDate.isBefore(l.getStartDate()) && !currentDate.isAfter(l.getEndDate()))
                    .findFirst().orElse(null);

                // 2. Resolve using single source of truth
                DayResult res = resolveDay(employee, currentDate, attendance, leave, runningBalance);
                
                // Skip future dates unless they have an approved leave
                if (currentDate.isAfter(today) && res.paidLeave == 0 && res.unpaidLeave == 0) {
                    continue;
                }
                
                // Update running balance if paid leave or paid absent used
                runningBalance -= (res.paidLeave + res.paidAbsent);

                // 3. Map to frontend DTO
                Map<String, Object> dayMap = new HashMap<>();
                dayMap.put("date", currentDate.toString());
                dayMap.put("workingHours", res.worked * 8.0);
                
                // Status Mapping
                dayMap.put("status", res.status);
                if (res.halfType != null) dayMap.put("halfType", res.halfType);
                dayMap.put("paidAbsent", res.paidAbsent);
                dayMap.put("unpaidAbsent", res.unpaidAbsent);
                
                dayMap.put("worked", res.worked);
                dayMap.put("isPaid", res.paidLeave);
                dayMap.put("isUnpaid", res.unpaidLeave);
                
                if (res.paidLeave > 0 || res.unpaidLeave > 0) {
                    dayMap.put("leaveType", leave != null ? leave.getLeaveType() : "LEAVE");
                    dayMap.put("leaveReason", leave != null ? leave.getReason() : "");
                }

                if (attendance != null) {
                    dayMap.put("checkInTime", attendance.getCheckInTime());
                    dayMap.put("checkOutTime", attendance.getCheckOutTime());
                    if (attendance.getRemarks() != null) dayMap.put("remarks", attendance.getRemarks());
                }

                details.add(dayMap);
            } catch (Exception e) {
                log.error("Error resolving day {} for employee {}: {}", currentDate, employeeId, e.getMessage());
                // Add minimal data for this day so the table doesn't break
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("date", currentDate.toString());
                errorMap.put("status", "ERROR");
                details.add(errorMap);
            }
        }
        return details;
    }
    
    public double calculateDeductibleDays(int absentDays, int unpaidLeaves, int halfDays) {
        return absentDays + unpaidLeaves + (halfDays * 0.5);
    }
    
    public double calculateOpeningBalance(Long employeeId, YearMonth month) {
        // Simple opening balance calculation
        LeaveBalanceSummary current = calculateLeaveBalance(employeeId, month.minusMonths(1));
        return current.earnedLeaves - current.usedLeaves;
    }
    
    public LeaveDayResult resolveLeaveDay(
            Employee employee,
            Leave leave,
            LocalDate date,
            double remainingBalance) {
        // ✅ FIXED: Use the unified resolveDay logic instead of a stub
        DayResult res = resolveDay(employee, date, null, leave, remainingBalance);
        return new LeaveDayResult(res.paidLeave, (leave != null && leave.getIsHalfDay() != null && leave.getIsHalfDay()) ? 0.5 : 1.0);
    }
    
    public LeaveSplit calculateLeaveSplit(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate,
            boolean halfDay) {
        
        // ✅ CRITICAL FIX: Compute balance EXCLUDING leaves that start on or after
        // the requested leave's start date. This ensures "what balance did the employee
        // have just before this leave" is computed correctly, regardless of what other
        // future leaves are already approved in the same month.
        YearMonth month = YearMonth.from(startDate);
        LeaveBalanceSummary balance = calculateLeaveBalance(employeeId, month, startDate);
        
        // 2. Calculate total working days in the request
        double totalDays = calculateWorkingDays(startDate, endDate);
        if (halfDay) {
            totalDays = 0.5;
        }
        
        // 3. Proportional splitting based on Total Earned
        double available = Math.max(0, balance.remaining);
        double paidDays;
        double unpaidDays;
        
        if (available >= totalDays) {
            paidDays = totalDays;
            unpaidDays = 0;
        } else if (available > 0) {
            paidDays = available;
            unpaidDays = totalDays - available;
        } else {
            paidDays = 0;
            unpaidDays = totalDays;
        }
        
        log.info("Split Calculation - Employee {}: Request {} to {} (half={}) -> total={}, available={}, split: paid={}, unpaid={}",
            employeeId, startDate, endDate, halfDay, totalDays, available, paidDays, unpaidDays);
            
        return new LeaveSplit(paidDays, unpaidDays, halfDay ? 0.5 : 0.0, totalDays);
    }
    
    // Nested classes for compatibility
    public static class LeaveSplit {
        public final double paidDays;
        public final double unpaidDays;
        public final double halfDays;
        public final double totalDays;
        
        public LeaveSplit(double paidDays, double unpaidDays, double halfDays) {
            this.paidDays = paidDays;
            this.unpaidDays = unpaidDays;
            this.halfDays = halfDays;
            this.totalDays = paidDays + unpaidDays + halfDays;
        }
        
        public LeaveSplit(double paidDays, double unpaidDays, double halfDays, double totalDays) {
            this.paidDays = paidDays;
            this.unpaidDays = unpaidDays;
            this.halfDays = halfDays;
            this.totalDays = totalDays;
        }
    }
    
    public static class LeaveDayResult {
        public final double paid;
        public final double leaveDays;
        
        public LeaveDayResult(double paid, double leaveDays) {
            this.paid = paid;
            this.leaveDays = leaveDays;
        }
    }

    /**
     * ✅ SINGLE SOURCE OF TRUTH: ONLY method that decides ALL business rules
     * PURE FUNCTION: input → deterministic output, no side effects
     */
    public DayResult resolveDay(
            Employee employee,
            LocalDate date,
            Attendance attendance,
            Leave leave,
            double remainingBalance
    ) {
        // Rule 1: Sunday = ZERO everything, unless it's a Sunday-only leave
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            if (leave != null && leave.getStatus() == Leave.LeaveStatus.APPROVED && !shouldSkipSunday(leave, date)) {
                // Let it fall through to process Sunday-only leave
            } else {
                return DayResult.ZERO;
            }
        }
        
        double worked = 0.0;
        double paidLeave = 0.0;
        double unpaidLeave = 0.0;
        double absent = 0.0;
        boolean isHalfDay = false;
        String status = "ABSENT";
        String halfType = null;

        // Rule 2: Probation check (aligned to end of month for consistency)
        boolean isInProbation = !isProbationCompleted(employee, YearMonth.from(date).atEndOfMonth());

        // Rule 3: Process Attendance & Leave with Granular Half-Day logic
        // We track first and second halves independently for professional reporting.
        // PRECEDENCE: Leave > Manual Attendance > Auto-Absence.
        // NO DUPLICATE: A half-day is strictly counted as either Worked, Leave, or Absent.
        double firstHalfWorked = 0, firstHalfPaidLeave = 0, firstHalfUnpaidLeave = 0, firstHalfAbsent = 0;
        double secondHalfWorked = 0, secondHalfPaidLeave = 0, secondHalfUnpaidLeave = 0, secondHalfAbsent = 0;

        double firstHalfPaidAbsent = 0.0, firstHalfUnpaidAbsent = 0.0;
        double secondHalfPaidAbsent = 0.0, secondHalfUnpaidAbsent = 0.0;

        // 3a. Attendance logic
        if (attendance != null && attendance.getStatus() != null) {
            switch (attendance.getStatus()) {
                case PRESENT:
                case LATE:
                    firstHalfWorked = 0.5;
                    secondHalfWorked = 0.5;
                    break;
                case HALF_DAY:
                    isHalfDay = true;
                    if (attendance.getHalfType() == Attendance.HalfType.SECOND_HALF) {
                        secondHalfWorked = 0.5;
                        firstHalfAbsent = 0.5;
                    } else {
                        firstHalfWorked = 0.5; // Default to first half if not specified
                        secondHalfAbsent = 0.5;
                    }
                    break;
                case ABSENT:
                    if (attendance.getHalfType() == Attendance.HalfType.FIRST_HALF) {
                        firstHalfAbsent = 0.5;
                    } else if (attendance.getHalfType() == Attendance.HalfType.SECOND_HALF) {
                        secondHalfAbsent = 0.5;
                    } else {
                        firstHalfAbsent = 0.5;
                        secondHalfAbsent = 0.5;
                    }
                    break;
            }
        }

        // Manual absences check will be executed after leave logic to support precedence.

        // 3b. Leave logic (PRIORITY over attendance)
        if (leave != null) {
            double leaveUnit = 1.0;
            boolean leaveIsHalf = false;
            if (leave.getIsHalfDay() != null && leave.getIsHalfDay()) {
                leaveUnit = 0.5;
                leaveIsHalf = true;
                isHalfDay = true;
            }

            // Determine which half the leave covers
            boolean coversFirst = !leaveIsHalf || (leave.getHalfType() != Leave.HalfType.SECOND_HALF);
            boolean coversSecond = !leaveIsHalf || (leave.getHalfType() == Leave.HalfType.SECOND_HALF);

            // Decide paid/unpaid split for the leave unit
            double currentPaid = 0, currentUnpaid = 0;
            if (leave.getStatus() == Leave.LeaveStatus.APPROVED && leave.getPaidDays() != null) {
                double previousWorkingDays = 0.0;
                LocalDate current = leave.getStartDate();
                while (current.isBefore(date)) {
                    if (current.getDayOfWeek() != DayOfWeek.SUNDAY || !shouldSkipSunday(leave, current)) {
                        previousWorkingDays += 1.0;
                    }
                    current = current.plusDays(1);
                }
                
                double startRange = previousWorkingDays;
                double endRange = previousWorkingDays + leaveUnit;
                // ✅ KEY FIX: cap paidLimit against remainingBalance so that paid allocation
                // never exceeds what's actually available. This prevents absent days after this
                // leave from seeing a falsely positive balance.
                double paidLimit = Math.min(leave.getPaidDays(), remainingBalance + previousWorkingDays);
                
                currentPaid = Math.max(0.0, Math.min(endRange, paidLimit) - startRange);
                currentUnpaid = leaveUnit - currentPaid;

            } else {
                if (isInProbation) {
                    currentUnpaid = leaveUnit;
                } else if (remainingBalance >= leaveUnit) {
                    currentPaid = leaveUnit;
                } else if (remainingBalance > 0) {
                    currentPaid = remainingBalance;
                    currentUnpaid = leaveUnit - remainingBalance;
                } else {
                    currentUnpaid = leaveUnit;
                }
            }

            // Apply to specific halves (Leave overwrites attendance for that half)
            if (coversFirst) {
                firstHalfWorked = 0;
                firstHalfAbsent = 0;
                if (leaveIsHalf) {
                    firstHalfPaidLeave = currentPaid;
                    firstHalfUnpaidLeave = currentUnpaid;
                } else {
                    // Full day leave covers both halves, but we'll handle second half below
                    firstHalfPaidLeave = currentPaid / 2.0;
                    firstHalfUnpaidLeave = currentUnpaid / 2.0;
                }
            }
            if (coversSecond) {
                secondHalfWorked = 0;
                secondHalfAbsent = 0;
                if (leaveIsHalf) {
                    secondHalfPaidLeave = currentPaid;
                    secondHalfUnpaidLeave = currentUnpaid;
                } else {
                    secondHalfPaidLeave = currentPaid / 2.0;
                    secondHalfUnpaidLeave = currentUnpaid / 2.0;
                }
            }
        }
        // Manual absences consume remaining leave balance if available, otherwise they are unpaid
        double balanceForAbsence = isInProbation ? 0.0 : Math.max(0.0, remainingBalance - (firstHalfPaidLeave + secondHalfPaidLeave));

        if (firstHalfAbsent > 0) {
            if (balanceForAbsence >= firstHalfAbsent) {
                firstHalfPaidAbsent = firstHalfAbsent;
                balanceForAbsence -= firstHalfAbsent;
            } else if (balanceForAbsence > 0) {
                firstHalfPaidAbsent = balanceForAbsence;
                firstHalfUnpaidAbsent = firstHalfAbsent - balanceForAbsence;
                balanceForAbsence = 0.0;
            } else {
                firstHalfUnpaidAbsent = firstHalfAbsent;
            }
        }

        if (secondHalfAbsent > 0) {
            if (balanceForAbsence >= secondHalfAbsent) {
                secondHalfPaidAbsent = secondHalfAbsent;
                balanceForAbsence -= secondHalfAbsent;
            } else if (balanceForAbsence > 0) {
                secondHalfPaidAbsent = balanceForAbsence;
                secondHalfUnpaidAbsent = secondHalfAbsent - balanceForAbsence;
                balanceForAbsence = 0.0;
            } else {
                secondHalfUnpaidAbsent = secondHalfAbsent;
            }
        }

        // 3c. Calculate Gaps (Default to PRESENT for past days if attendance is not null)
        // If a half is empty, we assume the employee was present (Auto-Present) only if they checked in or HR marked some attendance
        // Unmarked days (attendance == null && leave == null) do not count as present or absent.
        if (attendance != null && attendance.getStatus() != Attendance.AttendanceStatus.PENDING) {
            if (firstHalfWorked + firstHalfPaidLeave + firstHalfUnpaidLeave + firstHalfAbsent < 0.5) firstHalfWorked = 0.5;
            if (secondHalfWorked + secondHalfPaidLeave + secondHalfUnpaidLeave + secondHalfAbsent < 0.5) secondHalfWorked = 0.5;
        }

        // 4. Aggregate Results
        worked = firstHalfWorked + secondHalfWorked;
        paidLeave = firstHalfPaidLeave + secondHalfPaidLeave;
        unpaidLeave = firstHalfUnpaidLeave + secondHalfUnpaidLeave;
        absent = firstHalfAbsent + secondHalfAbsent;
        double paidAbsent = firstHalfPaidAbsent + secondHalfPaidAbsent;
        double unpaidAbsent = firstHalfUnpaidAbsent + secondHalfUnpaidAbsent;
        double payable = worked + paidLeave + paidAbsent;

        // 5. Determine Professional Status String
        StringBuilder statusBuilder = new StringBuilder();
        if (firstHalfPaidLeave > 0) statusBuilder.append("1H:PAID_LEAVE");
        else if (firstHalfUnpaidLeave > 0) statusBuilder.append("1H:UNPAID_LEAVE");
        else if (firstHalfWorked > 0) statusBuilder.append("1H:PRESENT");
        else if (firstHalfAbsent > 0) {
            if (firstHalfPaidAbsent > 0) statusBuilder.append("1H:PAID_ABSENT");
            else statusBuilder.append("1H:UNPAID_ABSENT");
        }
        else statusBuilder.append("1H:NOT_MARKED");

        statusBuilder.append(", ");

        if (secondHalfPaidLeave > 0) statusBuilder.append("2H:PAID_LEAVE");
        else if (secondHalfUnpaidLeave > 0) statusBuilder.append("2H:UNPAID_LEAVE");
        else if (secondHalfWorked > 0) statusBuilder.append("2H:PRESENT");
        else if (secondHalfAbsent > 0) {
            if (secondHalfPaidAbsent > 0) statusBuilder.append("2H:PAID_ABSENT");
            else statusBuilder.append("2H:UNPAID_ABSENT");
        }
        else statusBuilder.append("2H:NOT_MARKED");

        // 5. Finalize status (Preserve detailed breakdown for professional reporting)
        String detailedStatus = statusBuilder.toString();

        // Return DayResult with the detailedStatus as the primary status string
        return new DayResult(worked, paidLeave, unpaidLeave, absent, payable, isHalfDay, detailedStatus, halfType, paidAbsent, unpaidAbsent);
    }

    /**
     * ✅ PROBATION CHECK: Uses dynamic probation months
     * FIXED: Now accepts a date for historical/future accuracy
     */
    public boolean isProbationCompleted(Employee employee) {
        return isProbationCompleted(employee, LocalDate.now());
    }

    public boolean isProbationCompleted(Employee employee, LocalDate date) {
        if (employee == null || employee.getJoiningDate() == null) {
            return false;
        }

        // ✅ FINAL SOURCE OF TRUTH: Explicit status overrides everything
        if (employee.getProbationStatus() == Employee.ProbationStatus.CONFIRMED) {
            return true;
        }
        
        int probationMonths = employee.getProbationPeriodMonths() != null 
                ? employee.getProbationPeriodMonths() 
                : PayrollPolicy.getDefaultProbationMonths();

        LocalDate probationEnd = employee.getJoiningDate().plusMonths(probationMonths);
        return !date.isBefore(probationEnd);
    }

    /**
     * ✅ MAIN CALCULATION: Aggregates DayResult ONLY
     * No business logic here - pure aggregation
     */
    public AttendanceSummary calculate(Long employeeId, YearMonth month) {
        AttendanceSummary result = new AttendanceSummary();
        result.year = month.getYear();
        result.month = month.getMonthValue();

        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        // Fetch employee and data
        Employee employee = employeeRepository.findByIdentifier(employeeId).orElse(null);
        if (employee == null) {
            log.warn("AttendanceEngine.calculate: Employee {} not found", employeeId);
            return result;
        }
        Long empId = employee.getId();

        log.info("AttendanceEngine.calculate: Processing employee {} for month {}", employeeId, month);

        // ✅ CRITICAL: Use opening balance from previous month only, excluding current month's leaves from projection
        LeaveBalanceSummary openingBalance = calculateLeaveBalance(empId, month.minusMonths(1), start);
        // ✅ FIX: Add current month's earned credit to opening balance
        // This must match the logic in calculateLeaveBalance (STEP 3 & 4)
        int empProbMonths = employee.getProbationPeriodMonths() != null ? employee.getProbationPeriodMonths() : 3;
        LocalDate empProbEnd = employee.getJoiningDate() != null
                ? employee.getJoiningDate().plusMonths(empProbMonths) : null;
        
        boolean earnedCreditThisMonth = false;
        if (employee.getProbationStatus() == Employee.ProbationStatus.CONFIRMED) {
            earnedCreditThisMonth = true;
        } else if (empProbEnd != null) {
            // Match the 15th-day rule:
            // If probation ends <= 15th, they earn in that month.
            // If probation ends > 15th, they earn from NEXT month.
            boolean probationEndsThisMonthOrBefore = !month.atEndOfMonth().isBefore(empProbEnd);
            if (probationEndsThisMonthOrBefore) {
                if (empProbEnd.isBefore(month.atDay(1))) {
                    // Probation ended in a previous month
                    earnedCreditThisMonth = true;
                } else {
                    // Probation ends this month - check 15th day rule
                    earnedCreditThisMonth = empProbEnd.getDayOfMonth() <= 15;
                }
            }
        }
        double runningBalance = openingBalance.remaining + (earnedCreditThisMonth ? PayrollPolicy.getLeaveAccrualRate() : 0.0);

        // Fetch all data upfront
        List<Leave> approvedLeaves = leaveRepository.findByEmployeeId(empId).stream()
                .filter(l -> l.getStatus() == Leave.LeaveStatus.APPROVED)
                .filter(l -> {
                    LocalDate leaveStart = l.getStartDate();
                    LocalDate leaveEnd = l.getEndDate();
                    return leaveStart != null && leaveEnd != null && 
                           !leaveEnd.isBefore(start) && !leaveStart.isAfter(end);
                })
                .toList();
        
        List<Attendance> attendances = attendanceRepository.findByEmployeeIdAndDateBetween(empId, start, end);
        
        log.info("AttendanceEngine.calculate: Found {} attendance records, {} approved leaves for employee {}", 
            attendances.size(), approvedLeaves.size(), empId);
        
        // Build maps for fast lookup
        Map<LocalDate, Attendance> attendanceMap = attendances.stream()
                .collect(Collectors.toMap(Attendance::getDate, a -> a, (a1, a2) -> a1));
        
        Map<LocalDate, Leave> leaveMap = new HashMap<>();
        for (Leave leave : approvedLeaves) {
            LocalDate current = leave.getStartDate();
            LocalDate leaveEnd = leave.getEndDate();

            while (current != null && !current.isAfter(leaveEnd)) {
                // Only map dates that fall within the payslip month (start to end)
                if (!current.isBefore(start) && !current.isAfter(end)) {
                    leaveMap.put(current, leave);
                }
                current = current.plusDays(1);
            }
        }

        LocalDate today = LocalDate.now();
        
        // ✅ MAIN PROCESSING LOOP: Use resolveDay ONLY
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {

            Attendance attendance = attendanceMap.get(date);
            Leave leave = leaveMap.get(date);
            
            // ✅ SINGLE SOURCE OF TRUTH: Only resolveDay decides business rules
            DayResult dayResult = resolveDay(employee, date, attendance, leave, runningBalance);
            
            // ✅ AGGREGATION ONLY: No business logic here
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY && "WEEKLY_OFF".equals(dayResult.status)) {
                if (!date.isAfter(today)) {
                    result.workedDays += 1.0;
                    result.payableDays += 1.0;
                }
            } else {
                result.workedDays += dayResult.worked;
                result.payableDays += dayResult.payable;
            }
            
            // ✅ Leave deductions are exclusively managed by LeaveLedger now
            // We still aggregate strict attendance status counts
            result.absent += dayResult.absent;
            
            // Do NOT aggregate paid/unpaid counts here to prevent double deductions
            result.paidLeave = 0.0;
            result.unpaidLeave = 0.0;
            result.paidAbsent = 0.0;
            result.unpaidAbsent = 0.0;
            
            // ✅ UNMARKED TRACKING: Still track unmarked days for admin dashboard
            if (date.getDayOfWeek() != DayOfWeek.SUNDAY && attendance == null && leave == null) {
                result.unmarked += 1;
            }
            
            // ✅ CRITICAL: Reduce running balance after paid leave and paid absent usage
            runningBalance -= (dayResult.paidLeave + dayResult.paidAbsent);
        }
        
        log.info("AttendanceEngine.calculate: Final result for employee {}: workedDays={}, paidLeave={}, unpaidLeave={}, absent={}, payableDays={}", 
            employeeId, result.workedDays, result.paidLeave, result.unpaidLeave, result.absent, result.payableDays);
        
        return result;
    }

    /**
     * ✅ LEAVE BALANCE: Single source of truth using employee-specific probation
     * Handles all scenarios: during probation, just completed, long-term employees
     * Calculates leave balance, optionally excluding leaves that start on or after a cutoff date.
     * This is used by calculateLeaveSplit to get "balance just before a specific leave".
     */
    public LeaveBalanceSummary calculateLeaveBalance(Long employeeId, YearMonth currentMonth) {
        return calculateLeaveBalance(employeeId, currentMonth, null, null);
    }

    public LeaveBalanceSummary calculateLeaveBalance(Long employeeId, YearMonth currentMonth, LocalDate excludeLeavesFromDate) {
        return calculateLeaveBalance(employeeId, currentMonth, excludeLeavesFromDate, null);
    }

    public LeaveBalanceSummary calculateLeaveBalance(Long employeeId, YearMonth currentMonth, LocalDate excludeLeavesFromDate, Long excludeLeaveId) {
        Employee employee = employeeRepository.findByIdentifier(employeeId).orElse(null);
        return calculateLeaveBalance(employee, currentMonth, excludeLeavesFromDate, excludeLeaveId, null);
    }

    public LeaveBalanceSummary calculateLeaveBalance(Employee employee, YearMonth currentMonth, LocalDate excludeLeavesFromDate, Long excludeLeaveId, List<com.hrm.hrmsystem.model.LeaveLedger> preFetchedLedgerEntries) {
        if (employee == null) {
            return new LeaveBalanceSummary(0, 0, 0, 0);
        }
        Long empId = employee.getId();

        LocalDate joiningDate = employee.getJoiningDate();
        if (joiningDate == null) {
            return new LeaveBalanceSummary(0, 0, 0, 0);
        }
        
        Integer probationMonths = employee.getProbationPeriodMonths() != null ? employee.getProbationPeriodMonths() : 3;
        double monthlyCredit = PayrollPolicy.getLeaveAccrualRate();

        LocalDate probationCompletedDate;
        if (employee.getProbationStatus() == Employee.ProbationStatus.CONFIRMED) {
            probationCompletedDate = joiningDate;
        } else {
            probationCompletedDate = joiningDate.plusMonths(probationMonths);
        }
        
        boolean isInProbation = currentMonth.atEndOfMonth().isBefore(probationCompletedDate);
        if (isInProbation) {
            return new LeaveBalanceSummary(0, 0, 0, 0);
        }
        
        YearMonth leaveStartMonth;
        if (probationCompletedDate.getDayOfMonth() <= 15) {
            leaveStartMonth = YearMonth.from(probationCompletedDate);
        } else {
            leaveStartMonth = YearMonth.from(probationCompletedDate).plusMonths(1);
        }

        YearMonth cycleStart = (currentMonth.getMonthValue() <= 6) 
            ? YearMonth.of(currentMonth.getYear(), 1) 
            : YearMonth.of(currentMonth.getYear(), 7);
            
        LocalDate cycleStartDate = cycleStart.atDay(1);
        LocalDate cycleEndDate = (currentMonth.getMonthValue() <= 6)
            ? LocalDate.of(currentMonth.getYear(), 6, 30)
            : LocalDate.of(currentMonth.getYear(), 12, 31);
            
        YearMonth actualCalculationStart = leaveStartMonth.isBefore(cycleStart) ? cycleStart : leaveStartMonth;
        
        long earnedMonths = ChronoUnit.MONTHS.between(actualCalculationStart, currentMonth) + 1;
        earnedMonths = Math.max(0, earnedMonths);
        
        if (excludeLeavesFromDate != null) {
            YearMonth limitMonth = YearMonth.from(excludeLeavesFromDate);
            if (currentMonth.isAfter(limitMonth)) {
                long adjustedMonths = ChronoUnit.MONTHS.between(actualCalculationStart, limitMonth) + 1;
                earnedMonths = Math.max(0, adjustedMonths);
            }
        }
        
        double totalEarnedCumulative = earnedMonths * monthlyCredit;

        // SINGLE SOURCE OF TRUTH: Query Ledger
        double usedPaidLeavesTotal = 0.0;
        double unpaidLeavesTotal = 0.0;
        double currentMonthUsedPaid = 0.0;
        double currentMonthUnpaidLeaves = 0.0;

        List<com.hrm.hrmsystem.model.LeaveLedger> ledgerEntries = (preFetchedLedgerEntries != null)
                ? preFetchedLedgerEntries
                : leaveLedgerRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAsc(empId, cycleStartDate, cycleEndDate);

        for (com.hrm.hrmsystem.model.LeaveLedger entry : ledgerEntries) {
            LocalDate eventDate = entry.getEventDate();
            
            if (eventDate.isAfter(currentMonth.atEndOfMonth())) {
                continue;
            }
            if (excludeLeavesFromDate != null && !eventDate.isBefore(excludeLeavesFromDate)) {
                continue;
            }
            if (excludeLeaveId != null && entry.getEventType() == com.hrm.hrmsystem.model.LeaveLedger.EventType.APPROVED_LEAVE 
                && excludeLeaveId.equals(entry.getReferenceId())) {
                continue;
            }

            double p = entry.getPaidDays() != null ? entry.getPaidDays().doubleValue() : 0.0;
            double u = entry.getUnpaidDays() != null ? entry.getUnpaidDays().doubleValue() : 0.0;

            usedPaidLeavesTotal += p;
            unpaidLeavesTotal += u;

            if (YearMonth.from(eventDate).equals(currentMonth)) {
                currentMonthUsedPaid += p;
                currentMonthUnpaidLeaves += u;
            }
        }

        double remaining = totalEarnedCumulative - usedPaidLeavesTotal;
        remaining = Math.max(0, remaining);

        return new LeaveBalanceSummary(
            totalEarnedCumulative,
            usedPaidLeavesTotal,
            unpaidLeavesTotal,
            remaining,
            currentMonthUsedPaid,
            currentMonthUnpaidLeaves,
            currentMonth
        );
    }

    public SalarySummary calculateSalary(Long employeeId, YearMonth month) {
        Employee employee = employeeRepository.findByIdentifier(employeeId).orElse(null);
        if (employee == null) {
            return new SalarySummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 
                                   BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                                   BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        }
        Long empId = employee.getId();

        AttendanceSummary summary = calculate(empId, month);
        LeaveBalanceSummary leaveSummary = calculateLeaveBalance(empId, month);
        
        boolean isInProbation = !isProbationCompleted(employee, month.atEndOfMonth());
        return calculateSalary(employee, summary, isInProbation, leaveSummary);
    }

    private boolean shouldSkipSunday(Leave leave, LocalDate date) {
        if (date.getDayOfWeek() != DayOfWeek.SUNDAY) {
            return false;
        }
        // If the leave has at least one non-Sunday day, we should skip Sundays.
        LocalDate current = leave.getStartDate();
        LocalDate end = leave.getEndDate();
        while (current != null && !current.isAfter(end)) {
            if (current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                return true;
            }
            current = current.plusDays(1);
        }
        return false;
    }
}
