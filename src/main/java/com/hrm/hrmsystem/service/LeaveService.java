package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.dto.LeaveDTO;
import com.hrm.hrmsystem.dto.LeaveBalanceDTO;
import com.hrm.hrmsystem.engine.AttendanceEngine;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.Leave;
import com.hrm.hrmsystem.model.Attendance;
import com.hrm.hrmsystem.repository.LeaveRepository;
import com.hrm.hrmsystem.repository.AttendanceRepository;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.service.UnifiedCalculationService;
import com.hrm.hrmsystem.service.AuditLogService;
import com.hrm.hrmsystem.service.PayrollLockService;
import com.hrm.hrmsystem.model.User;
import com.hrm.hrmsystem.repository.UserRepository;
import com.hrm.hrmsystem.util.EmailUtil;
import com.hrm.hrmsystem.service.NotificationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
// ✅ REMOVED: ChronoUnit - Use AttendanceEngine for all calculations
// import java.time.temporal.ChronoUnit;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class LeaveService {

    private static final Logger logger = LoggerFactory.getLogger(LeaveService.class);

    private final LeaveRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final UnifiedCalculationService unifiedCalculationService;
    private final AttendanceEngine attendanceEngine;
    private final AuditLogService auditLogService;
    private final PayrollLockService payrollLockService;
    private final EmailUtil emailUtil;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final LeaveRecalculationService leaveRecalculationService;

    public LeaveService(LeaveRepository leaveRepository,
                        EmployeeRepository employeeRepository, AttendanceRepository attendanceRepository,
                        UnifiedCalculationService unifiedCalculationService, AttendanceEngine attendanceEngine,
                        AuditLogService auditLogService, PayrollLockService payrollLockService, EmailUtil emailUtil,
                        NotificationService notificationService, UserRepository userRepository,
                        @Lazy LeaveRecalculationService leaveRecalculationService) {
        this.leaveRepository = leaveRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.unifiedCalculationService = unifiedCalculationService;
        this.attendanceEngine = attendanceEngine;
        this.auditLogService = auditLogService;
        this.payrollLockService = payrollLockService;
        this.emailUtil = emailUtil;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.leaveRecalculationService = leaveRecalculationService;
    }

    @Transactional
    public LeaveDTO applyLeave(LeaveDTO dto) {
        Employee employee = employeeRepository.findByIdentifier(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        // Check if payroll is locked for the leave start date
        int startMonth = dto.getStartDate().getMonthValue();
        int startYear = dto.getStartDate().getYear();
        if (payrollLockService.isPayrollLockedForEmployee(employee.getId(), startMonth, startYear)) {
            throw new RuntimeException("Cannot apply leave: Payroll is already locked for this employee for " + startYear + "-" + startMonth);
        }

        // CRITICAL FIX: Validate dates are not in the past
        LocalDate today = LocalDate.now();
        if (dto.getStartDate().isBefore(today)) {
            throw new RuntimeException("Cannot apply leave for past dates. Start date " + 
                dto.getStartDate() + " is before today " + today);
        }

        // CRITICAL FIX: Detect target month for leave application
        YearMonth leaveMonth = YearMonth.from(dto.getStartDate());
        YearMonth currentMonth = YearMonth.now();
        YearMonth nextMonth = currentMonth.plusMonths(1);
        
        if (leaveMonth.isAfter(nextMonth)) {
            throw new RuntimeException("You can apply leaves only for the current month (" + currentMonth + 
                ") or the next month (" + nextMonth + "). Applications for future months are not permitted.");
        }
        
        logger.info("Applying leave for employee {} in month: {}", employee.getId(), leaveMonth);

        // ✅ Use AttendanceEngine for working days calculation
        double totalDays = attendanceEngine.calculateWorkingDays(dto.getStartDate(), dto.getEndDate());

        // Handle half day
        boolean isHalfDay = dto.getIsHalfDay() != null && dto.getIsHalfDay();
        if (isHalfDay || "HALF".equals(dto.getLeaveType())) {
            totalDays = 0.5;
            isHalfDay = true;
        }

        // VALIDATION RULE: Check if both halves are already marked as leave for same date
        if (isHalfDay && dto.getHalfType() != null) {
            LocalDate leaveDate = dto.getStartDate();
            List<Leave> existingLeaves = leaveRepository.findByEmployeeId(employee.getId()).stream()
                    .filter(l -> l.getStatus() == Leave.LeaveStatus.APPROVED || l.getStatus() == Leave.LeaveStatus.PENDING)
                    .filter(l -> l.getStartDate() != null && l.getStartDate().equals(leaveDate))
                    .filter(l -> l.getIsHalfDay() != null && l.getIsHalfDay())
                    .collect(Collectors.toList());

            for (Leave existingLeave : existingLeaves) {
                if (existingLeave.getHalfType() != null) {
                    // If existing leave is for the other half, reject or convert to full day
                    Leave.HalfType existingHalfType = existingLeave.getHalfType();
                    Leave.HalfType newHalfType = Leave.HalfType.valueOf(dto.getHalfType());

                    if (existingHalfType != newHalfType) {
                        // Different halves - this would result in both halves being leave
                        throw new RuntimeException(
                            "Cannot apply half-day leave for " + newHalfType + " - " + existingHalfType + 
                            " is already marked as leave on " + leaveDate + ". " +
                            "Please apply for a full-day leave instead."
                        );
                    }
                }
            }
        }

        // STEP 4: PREVENT OVERLAPPING LEAVES - Check only APPROVED leaves
        // Per business rule: only block if there's a date overlap with an APPROVED leave.
        // PENDING leaves are not checked to allow the employee to apply without blocking.
        List<Leave> approvedLeaves = leaveRepository.findByEmployeeId(employee.getId()).stream()
                .filter(l -> l.getStatus() == Leave.LeaveStatus.APPROVED)
                .filter(l -> l.getStartDate() != null && l.getEndDate() != null)
                .collect(Collectors.toList());

        for (Leave existingLeave : approvedLeaves) {
            // Check if new leave dates overlap with an existing approved leave
            boolean overlap = !dto.getStartDate().isAfter(existingLeave.getEndDate()) &&
                            !dto.getEndDate().isBefore(existingLeave.getStartDate());

            if (overlap) {
                throw new RuntimeException(
                    "You are already on approved leave for the selected date(s). " +
                    "Please choose different date(s) or contact HR to modify your existing leave request."
                );
            }
        }

        // Parse half type if provided
        Leave.HalfType halfType = null;
        if (isHalfDay && dto.getHalfType() != null) {
            try {
                halfType = Leave.HalfType.valueOf(dto.getHalfType());
            } catch (IllegalArgumentException e) {
                // Invalid half type, default to null
                logger.warn("Invalid half type provided: {}", dto.getHalfType());
            }
        }

        // ✅ BUSINESS RULE FIX: Calculate paid/unpaid split using CURRENT available balance.
        // Do NOT use calculateLeaveSplit() here — it excludes future approved leaves to compute
        // "balance just before this leave", which inflates available balance and makes everything
        // appear paid even when the employee has insufficient balance.
        // Rule: paid = min(totalDays, availableBalance), unpaid = totalDays - paid.
        LeaveBalanceDTO currentBalance = getLeaveBalance(employee.getId(), YearMonth.from(dto.getStartDate()));
        double availableBalance = currentBalance != null ? Math.max(0, currentBalance.getAvailableLeaves()) : 0.0;

        double paidDaysCalc;
        double unpaidDaysCalc;

        if (isHalfDay) {
            // Half day is always treated as paid if any balance exists, else unpaid
            paidDaysCalc = availableBalance >= 0.5 ? 0.5 : 0.0;
            unpaidDaysCalc = 0.5 - paidDaysCalc;
        } else {
            paidDaysCalc = Math.min(totalDays, availableBalance);
            unpaidDaysCalc = totalDays - paidDaysCalc;
        }

        logger.info("applyLeave - Employee {}: totalDays={}, availableBalance={}, paid={}, unpaid={}",
            employee.getId(), totalDays, availableBalance, paidDaysCalc, unpaidDaysCalc);

        // Wrap in LeaveSplit for compatibility with the rest of the builder below
        AttendanceEngine.LeaveSplit split = new AttendanceEngine.LeaveSplit(paidDaysCalc, unpaidDaysCalc, isHalfDay ? 0.5 : 0.0, totalDays);

        // Resolve approver name if approver ID is provided
        String approverName = null;
        Long approverIdResolved = null;
        if (dto.getApproverId() != null) {
            Employee approver = employeeRepository.findByIdentifier(dto.getApproverId()).orElse(null);
            if (approver != null) {
                approverName = approver.getFirstName() + " " + approver.getLastName();
                approverIdResolved = approver.getId();
            }
        }

        Leave leave = Leave.builder()
                .employee(employee)
                .leaveType(Leave.LeaveType.valueOf(dto.getLeaveType()))
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .totalDays(totalDays)
                .paidDays(split.paidDays)
                .unpaidDays(split.unpaidDays)
                .isHalfDay(isHalfDay)
                .halfType(halfType)
                .reason(dto.getReason())
                .contactNumber(dto.getContactNumber())
                .status(Leave.LeaveStatus.PENDING)
                .appliedDate(LocalDate.now())
                .approverId(approverIdResolved)
                .approverName(approverName)
                .build();

        leave = leaveRepository.save(leave);

        // Send notification to selected approver and HR/Admin
        try {
            String recipientEmail = null;
            if (dto.getApproverId() != null) {
                Employee approver = employeeRepository.findByIdentifier(dto.getApproverId()).orElse(null);
                if (approver != null && approver.getEmail() != null && !approver.getEmail().trim().isEmpty()) {
                    recipientEmail = approver.getEmail().trim();
                    logger.info("Found selected approver email: {}", recipientEmail);
                }
            }
            
            // Send to HR/Admin
            String hrEmail = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.ROLE_ADMIN || u.getRole() == User.Role.ROLE_HR)
                .map(User::getEmail)
                .findFirst()
                .orElse("admin@hrmsystem.com");
            
            if (recipientEmail != null) {
                // Send to selected approver
                notificationService.sendNewLeaveApplicationNotification(leave, recipientEmail);
                // Also send a copy to HR
                if (!hrEmail.equalsIgnoreCase(recipientEmail)) {
                    notificationService.sendNewLeaveApplicationNotification(leave, hrEmail);
                }
            } else {
                // Fallback: send only to HR
                notificationService.sendNewLeaveApplicationNotification(leave, hrEmail);
            }
        } catch (Exception e) {
            logger.error("Failed to send new leave notification: {}", e.getMessage());
        }

        LeaveDTO result = convertToDTO(leave);
        result.setPaidDays(split.paidDays);
        result.setUnpaidDays(split.unpaidDays);

        return result;
    }

    /**
     * 🔒 VALIDATION: Prevent modification of approved leaves
     * This is the CORE protection for the freeze mechanism
     */
    private void validateLeaveNotApproved(Leave leave) {
        if (leave.getStatus() == Leave.LeaveStatus.APPROVED) {
            throw new RuntimeException("Cannot modify approved leave. Leave is frozen and cannot be changed.");
        }
    }

    @Transactional
    public LeaveDTO approveLeave(Long leaveId, String approvedBy) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave not found"));

        // Validate state
        if (leave.getStatus() != Leave.LeaveStatus.PENDING) {
            throw new RuntimeException("Leave is not in pending state");
        }

        // ✅ CRITICAL: Payroll freeze protection - prevent historical data corruption
        int leaveMonth = leave.getStartDate().getMonthValue();
        int leaveYear = leave.getStartDate().getYear();
        if (payrollLockService.isPayrollLockedForEmployee(leave.getEmployee().getId(), leaveMonth, leaveYear)) {
            throw new RuntimeException("Cannot approve leave: Payroll is already locked for this employee for " + 
                leaveYear + "-" + leaveMonth + ". This would corrupt historical salary data.");
        }

        // ✅ BUSINESS RULE: Recalculate paid/unpaid on approval using CURRENT available balance.
        // Using the simple rule: paid = min(totalDays, availableBalance), unpaid = remainder.
        // This excludes THIS leave from the balance calculation (it's still PENDING at this point)
        // so we correctly see "what balance did the employee have before this leave was applied".
        // Note: getLeaveBalance counts only APPROVED leaves — this leave is still PENDING, so it
        // is naturally excluded, giving the correct pre-leave balance.
        LeaveBalanceDTO balanceAtApproval = getLeaveBalance(
            leave.getEmployee().getId(),
            YearMonth.from(leave.getStartDate())
        );
        double availableAtApproval = balanceAtApproval != null ? Math.max(0, balanceAtApproval.getAvailableLeaves()) : 0.0;

        boolean isHalfDayLeave = leave.getIsHalfDay() != null && leave.getIsHalfDay();
        double totalDays = isHalfDayLeave ? 0.5 : attendanceEngine.calculateWorkingDays(leave.getStartDate(), leave.getEndDate());

        double paidDays;
        double unpaidDays;
        if (isHalfDayLeave) {
            paidDays = availableAtApproval >= 0.5 ? 0.5 : 0.0;
            unpaidDays = 0.5 - paidDays;
        } else {
            paidDays = Math.min(totalDays, availableAtApproval);
            unpaidDays = totalDays - paidDays;
        }

        logger.info("approveLeave - Employee {}: totalDays={}, availableAtApproval={}, paid={}, unpaid={}",
            leave.getEmployee().getId(), totalDays, availableAtApproval, paidDays, unpaidDays);

        leave.setStatus(Leave.LeaveStatus.APPROVED);
        leave.setApprovedBy(approvedBy);
        
        // Update these fields too in case they changed during split calculation
        leave.setPaidDays(paidDays);
        leave.setUnpaidDays(unpaidDays);
        leave.setTotalDays(totalDays);

        // 🔒 FREEZE DATA AT APPROVAL TIME - Prevent recalculation
        leave.setFinalPaidDays(paidDays);
        leave.setFinalUnpaidDays(unpaidDays);
        leave.setFinalTotalDays(totalDays);
        leave.setFrozenAt(LocalDate.now());

        // Save the leave first
        leave = leaveRepository.save(leave);
        leaveRepository.flush();

        // ✅ SINGLE recalculation via LeaveRecalculationService — no duplicate paths.
        // This replaces the old recalculateLeaveSplitsForCycle() + calculateLeaveSplit() loop.
        // The recalculation will overwrite paidDays/finalPaidDays set above with the
        // chronologically correct values (considering prior absences in the cycle).
        leaveRecalculationService.recalculateFromDate(leave.getEmployee().getId(), leave.getStartDate());

        // ✅ STEP 2: REMOVED - Stop storing Total Used Leaves in database
        // used are now calculated dynamically from AttendanceEngine
        // No need to manually deduct/restore from database

        // Initialize attendance for half-day leave
        if (leave.getIsHalfDay() != null && leave.getIsHalfDay() && leave.getHalfType() != null) {
            initializeHalfDayAttendance(leave);
        }

        // Reload from database to get fresh data
        leave = leaveRepository.findById(leave.getId())
                .orElseThrow(() -> new RuntimeException("Leave not found after save"));

        // Send notification to employee
        try {
            notificationService.sendLeaveStatusNotification(leave, true);
        } catch (Exception e) {
            logger.error("Failed to send leave approval notification: {}", e.getMessage());
        }

        return convertToDTO(leave);
    }

    @Transactional
    public LeaveDTO rejectLeave(Long leaveId, String rejectionReason) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave not found"));

        int month = leave.getStartDate().getMonthValue();
        int year = leave.getStartDate().getYear();
        if (payrollLockService.isPayrollLockedForEmployee(leave.getEmployee().getId(), month, year)) {
            throw new RuntimeException("Cannot reject leave: Payroll is already locked for this employee for " + year + "-" + month);
        }

        Leave.LeaveStatus oldStatus = leave.getStatus();

        leave.setStatus(Leave.LeaveStatus.REJECTED);
        leave.setRejectionReason(rejectionReason);

        leave = leaveRepository.save(leave);
        leaveRepository.flush();

        if (oldStatus == Leave.LeaveStatus.APPROVED) {
            // Remove leave effect from attendance - mark as PENDING for admin resolution
            LocalDate currentDate = leave.getStartDate();
            while (!currentDate.isAfter(leave.getEndDate())) {
                List<Attendance> attendances = attendanceRepository.findAllByEmployeeIdAndDate(
                        leave.getEmployee().getId(), currentDate);

                if (!attendances.isEmpty()) {
                    Attendance attendance = attendances.get(0);
                    // If attendance was marked as ON_LEAVE due to this leave, change to PENDING
                    if (attendance.getStatus() == Attendance.AttendanceStatus.ON_LEAVE ||
                        attendance.getStatus() == Attendance.AttendanceStatus.PENDING) {
                        attendance.setStatus(Attendance.AttendanceStatus.PENDING);
                        attendance.setRemarks("Leave rejected - pending admin resolution");
                        attendanceRepository.save(attendance);
                    }
                }
                currentDate = currentDate.plusDays(1);
            }

            // ✅ Recalculate via LeaveRecalculationService — single path, no duplicates
            leaveRecalculationService.recalculateFromDate(leave.getEmployee().getId(), leave.getStartDate());
        }

        // Send notification to employee
        try {
            notificationService.sendLeaveStatusNotification(leave, false);
        } catch (Exception e) {
            logger.error("Failed to send leave rejection notification: {}", e.getMessage());
        }

        return convertToDTO(leave);
    }

    @Transactional
    public LeaveDTO cancelLeave(Long leaveId) {
        return cancelLeave(leaveId, "Cancelled");
    }

    public List<LeaveDTO> getLeavesByEmployee(Long employeeId) {
        Employee employee = employeeRepository.findByIdentifier(employeeId).orElse(null);
        if (employee == null) {
            return new java.util.ArrayList<>();
        }
        return leaveRepository.findByEmployeeId(employee.getId())
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<LeaveDTO> getPendingLeaves() {
        return leaveRepository.findByStatus(Leave.LeaveStatus.PENDING)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public LeaveDTO getLeaveById(Long leaveId) {
        return leaveRepository.findById(leaveId)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Leave not found with id: " + leaveId));
    }

    public List<LeaveDTO> getAllLeaves() {
        return leaveRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all leaves assigned to a specific approver (by approver employee ID).
     * Used for non-HR management users who should only see their own approval queue.
     */
    public List<LeaveDTO> getLeavesByApprover(Long approverEmployeeId) {
        return leaveRepository.findByApproverId(approverEmployeeId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Optional<LeaveDTO> findById(Long id) {
        return leaveRepository.findById(id)
                .map(this::convertToDTO);
    }

    public LeaveBalanceDTO getLeaveBalance(Long employeeId) {
        return getLeaveBalance(employeeId, YearMonth.now());
    }

    public LeaveBalanceDTO getLeaveBalance(Long employeeId, YearMonth targetMonth) {
        return getLeaveBalance(employeeId, targetMonth, null);
    }

    public LeaveBalanceDTO getLeaveBalance(Long employeeId, YearMonth targetMonth, LocalDate excludeLeavesFromDate) {
        Employee employee = employeeRepository.findByIdentifier(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        int currentYear = targetMonth.getYear();
        int currentMonth = targetMonth.getMonthValue();
        int currentCycle = currentMonth <= 6 ? 1 : 2;

        // 🔥 SINGLE SOURCE OF TRUTH: Use UnifiedCalculationService for all leave calculations
        UnifiedCalculationService.LeaveBalanceResult summary =
                unifiedCalculationService.getLeaveSummary(employee.getId(), targetMonth, excludeLeavesFromDate);

        logger.info("DEBUG SERVICE VALUES - Employee {}: earned={}, used={}, remaining={}",
                employee.getId(), summary.earned, summary.used, summary.remaining);

        // ✅ REMOVED: LeaveBalance table dependency - Use AttendanceEngine only
        // Balance is calculated dynamically from database leaves

        // ✅ REMOVED: checkProbationStatus() - Use AttendanceEngine only

        // ✅ REMOVED: Manual cycle calculation - Use AttendanceEngine for all calculations
        // Calculate cycle expiry warning
        // LocalDate cycleEndDate = currentCycle == 1 ? LocalDate.of(currentYear, 6, 30) : LocalDate.of(currentYear, 12, 31);
        // long daysUntilCycleEnd = ChronoUnit.DAYS.between(today, cycleEndDate);
        long daysUntilCycleEnd = 0; // Placeholder - use AttendanceEngine for cycle calculations

        boolean cycleExpiryWarning = false;
        String cycleExpiryMessage = null;

        // Show warning if within 30 days of cycle end and has unused
        if (daysUntilCycleEnd <= 30 && daysUntilCycleEnd >= 0 && summary.remaining > 0) {
            cycleExpiryWarning = true;
            cycleExpiryMessage = "Cycle ends in " + daysUntilCycleEnd + " days. Unused will expire.";
        }

        // DEBUG: Log values before building DTO
        System.out.println("DEBUG LEAVE BALANCE - Employee: " + employee.getId());
        System.out.println("DEBUG - Total Earned: " + summary.earned);
        System.out.println("DEBUG - Used: " + summary.used);
        System.out.println("DEBUG - Remaining: " + summary.remaining);
        
        // DEBUG: Log what we're setting in DTO
        System.out.println("DEBUG SETTING - openingBalance: " + summary.earned);
        System.out.println("DEBUG SETTING - earnedThisMonth: 0.0");
        System.out.println("DEBUG SETTING - usedThisMonth: " + summary.used);
        System.out.println("DEBUG SETTING - remaining: " + summary.remaining);

        // ✅ STEP 8: Add debug logs for verification
        logger.info(
            "Leave Balance: earned={}, paidUsed={}, unpaid={}, remaining={}",
            summary.earned,
            summary.used,
            summary.unpaidLeaves,
            summary.remaining
        );

        // Calculate earned this month (1.5 if probation completed)
        boolean isProbationCompleted = attendanceEngine.isProbationCompleted(employee, targetMonth.atEndOfMonth());
        double earnedThisMonth = isProbationCompleted ? 1.5 : 0.0;

        LeaveBalanceDTO dto = LeaveBalanceDTO.builder()
                .id(null) // ✅ No LeaveBalance table dependency
                .employeeId(employee.getId())
                .employeeName(employee.getFirstName() + " " + employee.getLastName())
                .totalEarnedLeaves(summary.earned)
                .usedLeaves(summary.totalUsed) 
                .paidLeaves(summary.used) 
                .unpaidLeaves(summary.unpaidLeaves) // ✅ Unpaid cumulative = 0.5
                .carriedForwardLeaves(0) // No carry forward allowed
                .availableLeaves(summary.remaining)
                // UI fields - Map calculated values to frontend fields
                .openingBalance(summary.earned - earnedThisMonth) // Opening = Total - current month credit
                .earnedThisMonth(earnedThisMonth) 
                .usedThisMonth(summary.usedThisMonth) // ✅ Monthly Paid Used Leaves only
                .remaining(summary.remaining) 
                .cycle(currentCycle)
                .year(currentYear)
                .inProbation(!attendanceEngine.isProbationCompleted(employee, targetMonth.atEndOfMonth()))
                .probationStatus(attendanceEngine.isProbationCompleted(employee, targetMonth.atEndOfMonth()) ? "Completed" : "In Progress")
                .joiningDate(employee.getJoiningDate() != null ? employee.getJoiningDate().toString() : null)
                .probationEndDate(employee.getJoiningDate() != null ? employee.getJoiningDate().plusMonths(employee.getProbationPeriodMonths() != null ? employee.getProbationPeriodMonths() : 3).toString() : null)
                .probationMonths(employee.getProbationPeriodMonths() != null ? employee.getProbationPeriodMonths() : 3)
                .cycleExpiryWarning(cycleExpiryWarning)
                .cycleExpiryMessage(cycleExpiryMessage)
                .daysUntilCycleEnd((int) daysUntilCycleEnd)
                .workingDaysForNextLeave(null) // Not applicable for monthly accrual
                .nextLeaveProgress(null) // Not applicable for monthly accrual
                .build();

        return dto;
    }

    /**
     * Initialize or get leave balance - delegates to getLeaveBalance to avoid duplicate code
     */
    public LeaveBalanceDTO initializeLeaveBalance(Long employeeId) {
        // CRITICAL FIX: Remove duplicate code - simply delegate to getLeaveBalance
        // Both methods do the same calculation
        return getLeaveBalance(employeeId);
    }

    /**
     * Calculate projected balance as of a specific leave month.
     * This considers future accrual when validating leaves applied in advance.
     * 
     * Example: In April, user applies for May leave:
     * - April earned: 6.0 (4 months × 1.5)
     * - May earned: 7.5 (5 months × 1.5) ← includes May accrual
     * - used: Only count leaves ending on or before May 31
     * - Available: 7.5 - used
     */
    /**
     * 🔥 SINGLE SOURCE OF TRUTH: Use AttendanceEngine for projected balance
     */
    public double calculateProjectedBalance(Employee employee, LocalDate leaveStartDate) {
        if (employee.getJoiningDate() == null) {
            return 0.0;
        }
        
        int leaveYear = leaveStartDate.getYear();
        int leaveMonth = leaveStartDate.getMonthValue();
        
        // 🔥 DATABASE-FIRST: Use UnifiedCalculationService for Total Earned
        UnifiedCalculationService.LeaveBalanceResult summary = 
                unifiedCalculationService.getLeaveSummary(employee.getId(), YearMonth.of(leaveYear, leaveMonth));
        
        // Available = remaining (from database)
        double available = Math.max(0, summary.remaining);
        
        logger.info("Projected balance for emp {} ({}-{}): earned={}, used={}, available={}",
                employee.getId(), leaveMonth, leaveYear, summary.earned, summary.used, available);
        
        return available;
    }

    // ✅ REMOVED: getOrCreateCurrentBalance() and createLeaveBalance() - No LeaveBalance table dependency for calculations
    
    // ✅ STEP 2: REMOVED - deductLeaveBalance and restoreLeaveBalance methods
    // used are now calculated dynamically from AttendanceEngine
    // No need to manually deduct/restore from database

    // Payroll calculation method
    public BigDecimal calculateLeaveDeduction(Employee employee, double unpaidDays, boolean isHalfDay) {
        if (employee.getSalary() == null || employee.getSalary().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // ✅ REMOVED: Manual salary calculation - Use AttendanceEngine.calculateSalary() only
        // Per day salary = Monthly Salary / Working Days (26)
        // BigDecimal perDaySalary = employee.getSalary().divide(BigDecimal.valueOf(WORKING_DAYS_PER_MONTH), 2, RoundingMode.HALF_UP);
        // For half day, deduct half of per day salary
        // BigDecimal deductionMultiplier = isHalfDay ? new BigDecimal("0.5") : BigDecimal.ONE;
        // return perDaySalary.multiply(BigDecimal.valueOf(unpaidDays)).multiply(deductionMultiplier);
        throw new UnsupportedOperationException("Use AttendanceEngine.calculateSalary() for all salary calculations");
    }

    private LeaveDTO convertToDTO(Leave leave) {
        double paidDays, unpaidDays, totalDays;

        if (leave.getStatus() == Leave.LeaveStatus.APPROVED &&
            leave.getFinalPaidDays() != null &&
            leave.getFinalUnpaidDays() != null &&
            (leave.getFinalPaidDays() > 0 || leave.getFinalUnpaidDays() > 0)) {
            // ✅ APPROVED: Use frozen final values — ensures payroll stability
            paidDays = leave.getFinalPaidDays();
            unpaidDays = leave.getFinalUnpaidDays();
            totalDays = leave.getFinalTotalDays() != null ? leave.getFinalTotalDays() :
                        attendanceEngine.calculateWorkingDays(leave.getStartDate(), leave.getEndDate());

        } else if ((leave.getStatus() == Leave.LeaveStatus.CANCELLED ||
                    leave.getStatus() == Leave.LeaveStatus.REJECTED) &&
                   leave.getFinalPaidDays() != null && leave.getFinalUnpaidDays() != null &&
                   (leave.getFinalPaidDays() > 0 || leave.getFinalUnpaidDays() > 0)) {
            // ✅ CANCELLED/REJECTED (was previously APPROVED): Use frozen approval-time values.
            // Do NOT recalculate — the cancelled leave is excluded from approved list, so the engine
            // returns inflated balance and wrong split values.
            paidDays = leave.getFinalPaidDays();
            unpaidDays = leave.getFinalUnpaidDays();
            totalDays = leave.getFinalTotalDays() != null ? leave.getFinalTotalDays() :
                        attendanceEngine.calculateWorkingDays(leave.getStartDate(), leave.getEndDate());

        } else if (leave.getPaidDays() != null && leave.getUnpaidDays() != null &&
                   leave.getTotalDays() != null && leave.getTotalDays() > 0) {
            // ✅ ALL OTHER STATUSES (PENDING, CANCELLED before approval, REJECTED before approval):
            // Use stored values from apply time — set correctly by applyLeave.
            // Do NOT recalculate — calculateLeaveSplit uses inflated "exclude future leaves" balance.
            paidDays = leave.getPaidDays();
            unpaidDays = leave.getUnpaidDays();
            totalDays = leave.getTotalDays();

        } else {
            // ✅ FALLBACK: Legacy records with no stored values — recalculate using AttendanceEngine
            AttendanceEngine.LeaveSplit split = attendanceEngine.calculateLeaveSplit(
                leave.getEmployee().getId(),
                leave.getStartDate(),
                leave.getEndDate(),
                leave.getIsHalfDay() != null && leave.getIsHalfDay()
            );
            paidDays = split.paidDays;
            unpaidDays = split.unpaidDays;
            totalDays = split.totalDays;
        }


        return LeaveDTO.builder()
                .id(leave.getId())
                .employeeId(leave.getEmployee().getId())
                .employeeCode(leave.getEmployee().getEmployeeCode())
                .employeeName(leave.getEmployee().getFirstName() + " " + leave.getEmployee().getLastName())
                .leaveType(leave.getLeaveType().name())
                .startDate(leave.getStartDate())
                .endDate(leave.getEndDate())
                .totalDays(totalDays)
                .paidDays(paidDays)
                .unpaidDays(unpaidDays)
                .isHalfDay(leave.getIsHalfDay())
                .halfType(leave.getHalfType() != null ? leave.getHalfType().name() : null)
                .reason(leave.getReason())
                .contactNumber(leave.getContactNumber())
                .status(leave.getStatus().name())
                .appliedDate(leave.getAppliedDate())
                .approvedBy(leave.getApprovedBy())
                .rejectionReason(leave.getRejectionReason())
                .approverId(leave.getApproverId())
                .approverName(leave.getApproverName())
                .build();
    }

    /**
     * Initialize attendance for half-day leave
     * STRICT RULE: When half-day leave is approved, the entire day is marked as PENDING
     * Admin must resolve whether the employee worked the remaining half
     * DO NOT auto-mark as HALF_DAY or LEAVE - always PENDING for admin resolution
     */
    private void initializeHalfDayAttendance(Leave leave) {
        LocalDate leaveDate = leave.getStartDate();
        if (leaveDate == null) {
            logger.warn("Cannot initialize attendance for half-day leave: no start date");
            return;
        }

        // Check if attendance already exists for this date
        List<Attendance> existingAttendance = attendanceRepository.findAllByEmployeeIdAndDate(
                leave.getEmployee().getId(), leaveDate);

        if (!existingAttendance.isEmpty()) {
            logger.info("Attendance already exists for employee {} on {}, skipping initialization",
                    leave.getEmployee().getId(), leaveDate);
            return;
        }

        // STRICT RULE: Always mark as PENDING for half-day leave
        // Admin must resolve: was the remaining half worked (PRESENT) or not (ABSENT)?
        Attendance attendance = Attendance.builder()
                .employee(leave.getEmployee())
                .date(leaveDate)
                .status(Attendance.AttendanceStatus.PENDING)
                .remarks("Half-day leave approved - " + leave.getHalfType().name() + ". Admin must resolve remaining half.")
                .build();

        attendanceRepository.save(attendance);
    }

    /**
 * Cancel a leave request (for approved leaves only)
 * 🔒 UPDATED: Prevent cancellation of approved leaves - freeze mechanism
 */
@Transactional
public LeaveDTO cancelLeave(Long leaveId, String reason) {
    Leave leave = leaveRepository.findById(leaveId)
            .orElseThrow(() -> new RuntimeException("Leave not found"));

    // 🔒 VALIDATION: Prevent cancellation of approved leaves if payroll is locked
    int month = leave.getStartDate().getMonthValue();
    int year = leave.getStartDate().getYear();
    if (leave.getStatus() == Leave.LeaveStatus.APPROVED && payrollLockService.isPayrollLocked(month, year)) {
        throw new RuntimeException("Cannot cancel approved leave: Payroll is already locked for " + 
            year + "-" + month + ". This would corrupt historical salary data.");
    }

    // Log old status for audit
    String oldStatus = leave.getStatus().name();

        // Change status to CANCELLED and save cancellation reason
        leave.setStatus(Leave.LeaveStatus.CANCELLED);
        leave.setCancellationReason(reason);
        leave = leaveRepository.save(leave);
        leaveRepository.flush();

        if ("APPROVED".equals(oldStatus)) {
            recalculateLeaveSplitsForCycle(leave.getEmployee().getId(), leave.getStartDate());
        }

        // Remove leave effect from attendance - mark as PENDING for admin resolution
        LocalDate currentDate = leave.getStartDate();
        while (!currentDate.isAfter(leave.getEndDate())) {
            List<Attendance> attendances = attendanceRepository.findAllByEmployeeIdAndDate(
                    leave.getEmployee().getId(), currentDate);

            if (!attendances.isEmpty()) {
                Attendance attendance = attendances.get(0);
                // If attendance was marked as ON_LEAVE due to this leave, change to PENDING
                if (attendance.getStatus() == Attendance.AttendanceStatus.ON_LEAVE ||
                    attendance.getStatus() == Attendance.AttendanceStatus.PENDING) {
                    attendance.setStatus(Attendance.AttendanceStatus.PENDING);
                    attendance.setRemarks("Leave cancelled - pending admin resolution");
                    attendanceRepository.save(attendance);
                    logger.info("Marked attendance as PENDING for cancelled leave: employee {}, date {}",
                            leave.getEmployee().getId(), currentDate);
                }
            }
            currentDate = currentDate.plusDays(1);
        }

        // Audit log
        auditLogService.logChange(
            "LEAVE",
            leaveId,
            "CANCEL",
            oldStatus,
            "CANCELLED",
            leave.getEmployee().getId(),
            reason != null ? reason : "Leave cancelled by admin"
        );

        logger.info("Cancelled leave ID {} for employee {}. Attendance marked as PENDING.",
                leaveId, leave.getEmployee().getId());

        // Send email notification to employee with cancellation reason
        try {
            String employeeEmail = leave.getEmployee().getEmail();
            String employeeName = leave.getEmployee().getFirstName() + " " + leave.getEmployee().getLastName();
            if (employeeEmail != null && !employeeEmail.trim().isEmpty()) {
                emailUtil.sendLeaveCancellationNotification(
                    employeeEmail,
                    employeeName,
                    leave.getLeaveType().name(),
                    leave.getStartDate().toString(),
                    leave.getEndDate().toString(),
                    reason
                );
                logger.info("Sent cancellation email to employee: {}", employeeEmail);
            } else {
                logger.warn("Employee email not found, skipping cancellation notification");
            }
        } catch (Exception e) {
            logger.error("Failed to send cancellation email: {}", e.getMessage());
            // Don't fail the cancellation if email fails
        }
        
        // Note: Payroll recalculation should be handled separately by the calling service/controller
        // to avoid circular dependency between LeaveService and PayrollService

        return convertToDTO(leave);
    }

    /**
     * Modify an approved leave
     * Updates leave details and recalculates attendance
     */
    @Transactional
    public LeaveDTO modifyLeave(Long leaveId, LeaveDTO updatedLeaveDTO, String reason) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave not found"));

        // Only allow modifying APPROVED leaves
        if (leave.getStatus() != Leave.LeaveStatus.APPROVED) {
            throw new RuntimeException("Can only modify APPROVED leaves. Current status: " + leave.getStatus());
        }

        // Check payroll lock for the leave dates
        int month = leave.getStartDate().getMonthValue();
        int year = leave.getStartDate().getYear();
        if (payrollLockService.isPayrollLocked(month, year)) {
            throw new RuntimeException("Cannot modify leave: Payroll is locked for " + year + "-" + month + ". Please use salary adjustment system instead.");
        }

        if (updatedLeaveDTO.getStartDate() != null) {
            int newMonth = updatedLeaveDTO.getStartDate().getMonthValue();
            int newYear = updatedLeaveDTO.getStartDate().getYear();
            if (payrollLockService.isPayrollLocked(newMonth, newYear)) {
                throw new RuntimeException("Cannot modify leave: The new leave start date falls in a locked payroll month: " + newYear + "-" + newMonth);
            }
        }

        // Log old values for audit
        String oldStatus = leave.getStatus().name();
        String oldDates = leave.getStartDate() + " to " + leave.getEndDate();
        String oldHalfDay = leave.getIsHalfDay() ? (leave.getHalfType() != null ? leave.getHalfType().name() : "YES") : "NO";

        // Store old values for attendance recalculation
        LocalDate oldStartDate = leave.getStartDate();
        LocalDate oldEndDate = leave.getEndDate();
        boolean oldIsHalfDay = leave.getIsHalfDay();
        Leave.HalfType oldHalfType = leave.getHalfType();

        // Update leave details
        if (updatedLeaveDTO.getStartDate() != null) {
            leave.setStartDate(updatedLeaveDTO.getStartDate());
        }
        if (updatedLeaveDTO.getEndDate() != null) {
            leave.setEndDate(updatedLeaveDTO.getEndDate());
        }
        if (updatedLeaveDTO.getIsHalfDay() != null) {
            leave.setIsHalfDay(updatedLeaveDTO.getIsHalfDay());
        }
        if (updatedLeaveDTO.getHalfType() != null) {
            try {
                leave.setHalfType(Leave.HalfType.valueOf(updatedLeaveDTO.getHalfType()));
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid half type provided: {}", updatedLeaveDTO.getHalfType());
            }
        }
        if (updatedLeaveDTO.getReason() != null) {
            leave.setReason(updatedLeaveDTO.getReason());
        }

        // ✅ Use AttendanceEngine for working days calculation
        double totalDays = attendanceEngine.calculateWorkingDays(leave.getStartDate(), leave.getEndDate());
        if (leave.getIsHalfDay()) {
            totalDays = 0.5;
        }
        leave.setTotalDays(totalDays);

        // Change status to APPROVED
        leave.setStatus(Leave.LeaveStatus.APPROVED);
        leave = leaveRepository.save(leave);
        leaveRepository.flush();

        // Recalculate splits for the cycle(s) of both old and new dates
        recalculateLeaveSplitsForCycle(leave.getEmployee().getId(), oldStartDate);
        if (leave.getStartDate().getYear() != oldStartDate.getYear() ||
            (leave.getStartDate().getMonthValue() <= 6) != (oldStartDate.getMonthValue() <= 6)) {
            recalculateLeaveSplitsForCycle(leave.getEmployee().getId(), leave.getStartDate());
        }

        // Recalculate attendance: remove old leave effect, apply new leave effect
        // First, remove old leave effect from attendance
        LocalDate currentDate = oldStartDate;
        while (!currentDate.isAfter(oldEndDate)) {
            List<Attendance> attendances = attendanceRepository.findAllByEmployeeIdAndDate(
                    leave.getEmployee().getId(), currentDate);

            if (!attendances.isEmpty()) {
                Attendance attendance = attendances.get(0);
                // If attendance was marked as ON_LEAVE or PENDING due to this leave, change to PENDING
                if (attendance.getStatus() == Attendance.AttendanceStatus.ON_LEAVE ||
                    attendance.getStatus() == Attendance.AttendanceStatus.PENDING) {
                    attendance.setStatus(Attendance.AttendanceStatus.PENDING);
                    attendance.setRemarks("Leave modified - pending recalculation");
                    attendanceRepository.save(attendance);
                }
            }
            currentDate = currentDate.plusDays(1);
        }

        // Then, apply new leave effect to attendance
        currentDate = leave.getStartDate();
        while (!currentDate.isAfter(leave.getEndDate())) {
            List<Attendance> attendances = attendanceRepository.findAllByEmployeeIdAndDate(
                    leave.getEmployee().getId(), currentDate);

            if (attendances.isEmpty()) {
                // Create new attendance record
                Attendance attendance = Attendance.builder()
                        .employee(leave.getEmployee())
                        .date(currentDate)
                        .status(Attendance.AttendanceStatus.PENDING)
                        .remarks("Modified leave - pending admin resolution")
                        .build();
                attendanceRepository.save(attendance);
            } else {
                // Update existing attendance
                Attendance attendance = attendances.get(0);
                attendance.setStatus(Attendance.AttendanceStatus.PENDING);
                attendance.setRemarks("Modified leave - pending admin resolution");
                attendanceRepository.save(attendance);
            }
            currentDate = currentDate.plusDays(1);
        }

        // Audit log
        String newDates = leave.getStartDate() + " to " + leave.getEndDate();
        String newHalfDay = leave.getIsHalfDay() ? (leave.getHalfType() != null ? leave.getHalfType().name() : "YES") : "NO";

        auditLogService.logChange(
            "LEAVE",
            leaveId,
            "MODIFY",
            oldDates + " | " + oldHalfDay,
            newDates + " | " + newHalfDay,
            leave.getEmployee().getId(),
            reason != null ? reason : "Leave modified by admin"
        );

        logger.info("Modified leave ID {} for employee {}. Attendance recalculated.",
                leaveId, leave.getEmployee().getId());

        // Send email notification to employee
        try {
            notificationService.sendLeaveModificationNotification(leave, reason);
            logger.info("Sent leave modification email to employee: {}", leave.getEmployee().getEmail());
        } catch (Exception e) {
            logger.error("Failed to send leave modification email: {}", e.getMessage());
        }

        return convertToDTO(leave);
    }

    /**
     * Delete all leave records
     */
    @Transactional
    public java.util.Set<String> deleteAllLeaves() {
        List<Leave> allLeaves = leaveRepository.findAll();
        java.util.Set<String> affectedEmployeeMonths = new java.util.HashSet<>();
        
        for (Leave leave : allLeaves) {
            LocalDate currentDate = leave.getStartDate();
            while (currentDate != null && !currentDate.isAfter(leave.getEndDate())) {
                int month = currentDate.getMonthValue();
                int year = currentDate.getYear();
                if (payrollLockService.isPayrollLocked(month, year)) {
                    throw new RuntimeException("Cannot delete leave records: Leaves exist in locked payroll period " + year + "-" + month);
                }
                currentDate = currentDate.plusDays(1);
            }
        }
        
        for (Leave leave : allLeaves) {
            LocalDate currentDate = leave.getStartDate();
            while (currentDate != null && !currentDate.isAfter(leave.getEndDate())) {
                int month = currentDate.getMonthValue();
                int year = currentDate.getYear();
                
                String key = leave.getEmployee().getId() + "_" + year + "_" + month;
                affectedEmployeeMonths.add(key);
                
                List<Attendance> attendances = attendanceRepository.findAllByEmployeeIdAndDate(
                        leave.getEmployee().getId(), currentDate);

                if (!attendances.isEmpty()) {
                    Attendance attendance = attendances.get(0);
                    if (attendance.getStatus() == Attendance.AttendanceStatus.ON_LEAVE ||
                        attendance.getStatus() == Attendance.AttendanceStatus.PENDING) {
                        attendance.setStatus(Attendance.AttendanceStatus.PENDING);
                        attendance.setRemarks("Leave cleared - pending admin resolution");
                        attendanceRepository.save(attendance);
                    }
                }
                currentDate = currentDate.plusDays(1);
            }
        }
        
        leaveRepository.deleteAll();
        return affectedEmployeeMonths;
    }

    /**
     * @deprecated Replaced by LeaveRecalculationService.recalculateFromDate().
     * Kept for backward compatibility — delegates to the new service.
     * The old implementation used calculateLeaveSplit() per-leave which caused
     * duplicate/inconsistent calculations. This now uses a single authoritative path.
     */
    @Transactional
    public void recalculateLeaveSplitsForCycle(Long employeeId, LocalDate dateInCycle) {
        logger.info("recalculateLeaveSplitsForCycle delegating to LeaveRecalculationService for employee {} on {}",
            employeeId, dateInCycle);
        leaveRecalculationService.recalculateFromDate(employeeId, dateInCycle);
    }
}




