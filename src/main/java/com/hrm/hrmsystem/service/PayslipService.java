package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.dto.AttendanceDTO;
import com.hrm.hrmsystem.dto.PayslipDTO;
import com.hrm.hrmsystem.service.UnifiedCalculationService.LeaveStatistics;
import com.hrm.hrmsystem.model.Attendance;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.Leave;
import com.hrm.hrmsystem.model.Payroll;
import com.hrm.hrmsystem.entity.Payslip;
import com.hrm.hrmsystem.engine.AttendanceEngine;
import com.hrm.hrmsystem.config.PayrollPolicy;
import com.hrm.hrmsystem.engine.AttendanceSummary;
import com.hrm.hrmsystem.exception.ResourceNotFoundException;
import com.hrm.hrmsystem.exception.BadRequestException;
import com.hrm.hrmsystem.repository.PayslipRepository;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.repository.AttendanceRepository;
import com.hrm.hrmsystem.repository.LeaveRepository;
import com.hrm.hrmsystem.repository.PayrollRepository;
import jakarta.persistence.EntityManager;
import com.hrm.hrmsystem.util.PdfGeneratorUtil;
import com.hrm.hrmsystem.util.EmailUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class PayslipService {

    private static final Logger log = LoggerFactory.getLogger(PayslipService.class);

    @Autowired
    private PayslipRepository payslipRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    @Lazy
    private AttendanceService attendanceService;

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private UnifiedCalculationService unifiedCalculationService;



    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private PdfGeneratorUtil pdfGeneratorUtil;

    @Autowired
    private EmailUtil emailUtil;

    @Autowired
    private com.hrm.hrmsystem.repository.UserRepository userRepository;

    @Autowired
    private com.hrm.hrmsystem.repository.NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AttendanceEngine attendanceEngine;

    @Autowired
    private PayrollLockService payrollLockService;




    /**
     * Generate payslip for a single employee (always regenerate with fresh data)
     * Uses REQUIRES_NEW to run in a separate transaction so it can see committed data
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PayslipDTO generatePayslip(Long employeeId, String monthYear) {
        log.info("Generating payslip for employee: {} for month: {}", employeeId, monthYear);
        
        try {
            // Fetch employee - use findById to get fresh data from database
            log.info("Step 1: Fetching employee...");
            Employee employee = employeeRepository.findByIdentifier(employeeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
            log.info("Step 1: Employee found: {} {}", employee.getFirstName(), employee.getLastName());

            if (isSystemUser(employee)) {
                throw new RuntimeException("Cannot generate payslip for System Users");
            }

            // Extract month and year
            log.info("Step 2: Parsing month/year...");
            String[] parts = monthYear.split("-");
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[0]);
            log.info("Step 2: Parsed month={}, year={}", month, year);

            // Validation: Payslips can be generated for the current month or any past month (no future months)
            java.time.YearMonth nowYM = java.time.YearMonth.now();
            java.time.YearMonth requestedYM = java.time.YearMonth.of(year, month);
            if (requestedYM.isAfter(nowYM)) {
                throw new RuntimeException("Payslips cannot be generated for future months.");
            }

            if (payrollLockService.isPayrollLockedForEmployee(employeeId, month, year)) {
                log.warn("Payroll is locked for employee {} for {}-{}. Cannot regenerate payslip.", employeeId, year, month);
                java.util.List<Payslip> existingPayslips = payslipRepository.findAllByEmployeeIdAndMonthYear(employeeId, monthYear);
                if (existingPayslips != null && !existingPayslips.isEmpty()) {
                    return convertToDTO(existingPayslips.get(0));
                }
                throw new RuntimeException("Cannot generate payslip: Payroll for employee " + employeeId + " for " + monthYear + " is locked.");
            }

            // Check if a payslip already exists for this employee and month-year
            log.info("Step 3: Checking for existing payslip...");
            java.util.List<Payslip> existingPayslips = payslipRepository.findAllByEmployeeIdAndMonthYear(employeeId, monthYear);
            log.info("Step 3: Found {} existing payslip(s)", existingPayslips != null ? existingPayslips.size() : 0);
            Payslip payslip;
            if (existingPayslips != null && !existingPayslips.isEmpty()) {
                // Reuse the first existing payslip and update its fields
                payslip = existingPayslips.get(0);
                payslip.setMonthYear(monthYear);
                payslip.setGeneratedDate(LocalDate.now());
                payslip.setStatus(Payslip.PayslipStatus.GENERATED);
                payslip.setSalaryMonth(month);
                payslip.setSalaryYear(year);
                log.info("Updating existing payslip id: {} for employee {}", payslip.getId(), employeeId);
            } else {
                // Create a new payslip
                payslip = new Payslip();
                payslip.setEmployee(employee);
                payslip.setMonthYear(monthYear);
                payslip.setGeneratedDate(LocalDate.now());
                payslip.setStatus(Payslip.PayslipStatus.GENERATED);
                payslip.setSalaryMonth(month);
                payslip.setSalaryYear(year);
                log.info("Creating new payslip for employee {} monthYear {}", employeeId, monthYear);
            }
            log.info("Step 4: Payslip object ready");

            // Calculate attendance for the month (with fresh attendance data)
            log.info("Step 5: Calculating attendance...");
            calculateAttendance(payslip, month, year);
            log.info("Step 5: Attendance calculated");

            // Calculate salary with attendance & leave policy based on probation status
            log.info("Step 6: Calculating salary...");
            calculateSalary(payslip, employee, monthYear);
            log.info("Step 6: Salary calculated");

            // Save payslip
            log.info("Step 7: Saving payslip...");
            Payslip savedPayslip = payslipRepository.save(payslip);
            log.info("Payslip generated successfully with id: {}", savedPayslip.getId());

            return convertToDTO(savedPayslip);
        } catch (Exception e) {
            log.error("ERROR in generatePayslip at step: employee={}, monthYear={}", employeeId, monthYear, e);
            throw new RuntimeException("Failed to generate payslip: " + e.getMessage(), e);
        }
    }

    /**
     * Clear all payslips for a month and regenerate them with fresh attendance/leave data.
     */
    public List<PayslipDTO> regeneratePayslipsForMonth(String monthYear) {
        log.info("Regenerating all payslips for month: {}", monthYear);

        // Fetch all existing payslips for the month
        java.util.List<Payslip> existingPayslips = payslipRepository.findByMonthYear(monthYear);
        
        // Delete only the unlocked ones
        for (Payslip payslip : existingPayslips) {
            if (payslip.getStatus() != Payslip.PayslipStatus.APPROVED && 
                payslip.getStatus() != Payslip.PayslipStatus.SENT) {
                payslipRepository.delete(payslip);
            }
        }

        List<Employee> employees = employeeRepository.findByStatus(Employee.EmployeeStatus.ACTIVE)
                .stream()
                .filter(emp -> !isSystemUser(emp))
                .collect(Collectors.toList());
        return employees.stream()
                .map(emp -> {
                    try {
                        return generatePayslip(emp.getId(), monthYear);
                    } catch (Exception e) {
                        log.warn("Could not regenerate payslip for employee {} month {}: {}", emp.getId(), monthYear, e.getMessage());
                        return null;
                    }
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    /**
     * Calculate attendance for the month using LeaveRepository for leave statistics (source of truth)
     * AttendanceEngine is used ONLY for present/absent calculation, NOT for leave
     */
    private void calculateAttendance(Payslip payslip, int month, int year) {
        // Clear entity manager cache to get fresh attendance data
        entityManager.clear();

        Long employeeId = payslip.getEmployee().getId();
        log.info("Calculating attendance for employee: {} for {}-{}", employeeId, year, month);

        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

        // ✅ Use AttendanceEngine for attendance and leave calculation (single source of truth)
        AttendanceSummary summary = unifiedCalculationService.calculateForPayroll(employeeId, year, month);
        UnifiedCalculationService.LeaveBalanceResult leaveSummary = unifiedCalculationService.getLeaveSummary(employeeId, java.time.YearMonth.of(year, month));

        double workedDays = summary.workedDays; // ✅ FIXED: Use workedDays instead of present
        double absentDays = summary.absent;
        double paidLeaveDays = leaveSummary.usedThisMonth; // ✅ FIXED: Use engine calculation directly
        double unpaidLeaveDays = leaveSummary.unpaidThisMonth; // ✅ FIXED: Use engine calculation directly

        log.info("AttendanceEngine Result for employee {} in {}-{}: worked={}, absent={}, paidLeave={}, unpaidLeave={}",
                 employeeId, year, month, workedDays, absentDays, paidLeaveDays, unpaidLeaveDays);

        // Get absent dates for record
        List<AttendanceDTO> attendanceDTOList = attendanceService.getAttendanceReport(employeeId, monthStart, monthEnd);
        java.util.List<String> absentDates = attendanceDTOList.stream()
                .filter(a -> a.getStatus() != null && a.getStatus().contains("ABSENT"))
                .map(a -> a.getDate().toString())
                .collect(Collectors.toList());

        payslip.setPresentDays(summary.getPresentDays()); 
        payslip.setAbsentDays(absentDays);
        payslip.setLeaveDays(paidLeaveDays + unpaidLeaveDays); // ✅ FIXED: Total = Paid + Unpaid
        payslip.setPaidLeaveDays(paidLeaveDays);
        payslip.setUnpaidLeaveDays(unpaidLeaveDays);
        payslip.setHalfDays(0); // Half days are already handled in present/absent counts
        payslip.setWorkingDays(PayrollPolicy.DEDUCTION_DAYS_PER_MONTH);

        // HRMS STANDARD: Keep metrics separate
        // presentDays = actual physical attendance
        // leaveDays = paid + unpaid leave
        // ✅ REMOVED: totalDays calculation - engine should provide this or frontend should calculate
        payslip.setAbsentDates(absentDates);
    }

    /**
     * Calculate salary components based on attendance and leave policy
     * - Salary calculated based on 26 working days
     * - Probation: No paid leave, all leaves/absences deducted
     * - Post-probation: 1.5 paid leave per month,
     * - Leave cycles: Jan-Jun, Jul-Dec with expiry
     */
    private void calculateSalary(Payslip payslip, Employee employee, String monthYear) {
        log.info("Calculating salary for employee: {} for month: {}", employee.getId(), monthYear);

        // Parse month and year from monthYear (format: "2026-04" or "April 2026")
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();
        try {
            if (monthYear != null && monthYear.contains("-")) {
                String[] parts = monthYear.split("-");
                year = Integer.parseInt(parts[0]);
                month = Integer.parseInt(parts[1]);
            }
        } catch (Exception e) {
            log.warn("Could not parse monthYear: {}, using current date", monthYear);
        }

        // Check probation status
        // ✅ FIXED: Use centralized probation method from AttendanceEngine
        boolean isInProbation = !attendanceEngine.isProbationCompleted(employee, YearMonth.of(year, month).atEndOfMonth());
        
        // Calculate leave cycle info
        int currentCycle = month <= 6 ? 1 : 2;
        log.info("Employee {} is {} probation. Leave cycle: {} for {}/{}", 
                employee.getId(), isInProbation ? "in" : "completed", currentCycle, month, year);

        // Try to get payroll data first - payslip should match payroll exactly
        Optional<Payroll> payrollOpt = payrollRepository.findFirstByEmployeeIdAndMonthAndYearOrderByIdDesc(employee.getId(), month, year);
        
        BigDecimal basicSalary = BigDecimal.ZERO, hra = BigDecimal.ZERO, otherAllowance = BigDecimal.ZERO, grossSalary = BigDecimal.ZERO;
        BigDecimal pf = BigDecimal.ZERO, esi = BigDecimal.ZERO, incomeTax = BigDecimal.ZERO, insurance = BigDecimal.ZERO, totalDeduction = BigDecimal.ZERO;
        BigDecimal absentLeaveDeduction = BigDecimal.ZERO;
        BigDecimal unpaidLeaveDeduction = BigDecimal.ZERO;
        String calculationMessage = "";

        // Calculate per day salary (based on 26 working days in month)
        double perDaySalary = 0.0;
        double absentDaysCount = 0.0;
        AttendanceEngine.SalarySummary salarySummary = null;
        
        Payroll payroll = null;
        if (payrollOpt.isPresent()) {
            payroll = payrollOpt.get();
            log.info("Found payroll for employee {} month {}-{}: ID={}", 
                    employee.getId(), year, month, payroll.getId());
                        
            // Use payroll data directly - NO recalculation
            basicSalary = payroll.getBasicSalary() != null ? payroll.getBasicSalary() : BigDecimal.ZERO;
            hra = payroll.getHra() != null ? payroll.getHra() : BigDecimal.ZERO;
            otherAllowance = payroll.getOtherAllowances() != null ? payroll.getOtherAllowances() : BigDecimal.ZERO;
            grossSalary = payroll.getGrossSalary() != null ? payroll.getGrossSalary() : BigDecimal.ZERO;
        
            // ✅ REMOVED: Manual per-day salary calculation - Use AttendanceEngine.calculateSalary() only
            // Calculate per day salary for debug logging
            // perDaySalary = basicSalary.doubleValue() / AttendanceEngine.WORKING_DAYS_PER_MONTH;
            perDaySalary = 0; // Placeholder - will be calculated by AttendanceEngine
            
            pf = payroll.getProvidentFund() != null ? payroll.getProvidentFund() : BigDecimal.ZERO;
            esi = BigDecimal.ZERO;
            incomeTax = payroll.getTax() != null ? payroll.getTax() : BigDecimal.ZERO;
            insurance = payroll.getInsurance() != null ? payroll.getInsurance() : BigDecimal.ZERO;

            unpaidLeaveDeduction = payroll.getUnpaidLeaveDeduction() != null ? payroll.getUnpaidLeaveDeduction() : BigDecimal.ZERO;
            absentLeaveDeduction = payroll.getAbsentDeduction() != null ? payroll.getAbsentDeduction() : BigDecimal.ZERO;

            BigDecimal esic = employee.getEsic() != null ? employee.getEsic() : BigDecimal.ZERO;
            BigDecimal professionalTax = employee.getProfessionalTax() != null ? employee.getProfessionalTax() : BigDecimal.ZERO;
            BigDecimal loanDeduction = employee.getLoanDeduction() != null ? employee.getLoanDeduction() : BigDecimal.ZERO;
            BigDecimal lwf = employee.getLwf() != null ? employee.getLwf() : BigDecimal.ZERO;

            // ✅ SINGLE SOURCE OF TRUTH: Use the stored payroll totalDeductions
            // This was computed by AttendanceEngine at generation time and includes ALL deductions
            // (unpaid leaves + absent penalty + PF + ESIC + PT + TDS + loan + LWF)
            // This ensures payslip view matches payroll table — both show the same API-backed value.
            totalDeduction = payroll.getTotalDeductions() != null ? payroll.getTotalDeductions() : BigDecimal.ZERO;

            log.info("✅ Payroll Branch - Gross={}, UnpaidDed={}, AbsentDed={}, PF={}, Tax={}, Insurance={}, Total={}",
                    grossSalary, unpaidLeaveDeduction, absentLeaveDeduction, pf, incomeTax, insurance, totalDeduction);

            calculationMessage = "Calculated from Payroll ID: " + payroll.getId();
        } else {
            // Fallback: Calculate from AttendanceEngine (single source of truth)
            log.warn("No payroll found for employee {} month {}-{}, using AttendanceEngine for calculation",
                    employee.getId(), year, month);

            // Initialize fallback values
            basicSalary = BigDecimal.ZERO;
            hra = BigDecimal.ZERO;
            otherAllowance = BigDecimal.ZERO;
            grossSalary = BigDecimal.ZERO;
            pf = BigDecimal.ZERO;
            esi = BigDecimal.ZERO;
            incomeTax = BigDecimal.ZERO;
            insurance = BigDecimal.ZERO;
            totalDeduction = BigDecimal.ZERO;
            
            // Get attendance summary from service
            AttendanceSummary attendanceSummary =
                    unifiedCalculationService.calculateForPayroll(employee.getId(), year, month);

            // Use UnifiedCalculationService for salary calculation (single source of truth)
            salarySummary =
                    unifiedCalculationService.calculateSalary(employee, attendanceSummary, YearMonth.of(year, month));

            // Use ALL salary components from engine result
            basicSalary = employee.getBasicSalary() != null ? employee.getBasicSalary() : BigDecimal.ZERO;
            hra = employee.getHra() != null ? employee.getHra() : BigDecimal.ZERO;
            otherAllowance = employee.getOtherAllowance() != null ? employee.getOtherAllowance() : BigDecimal.ZERO;
            grossSalary = salarySummary.getGrossSalary();
            pf = salarySummary.getPf();
            esi = BigDecimal.ZERO;
            incomeTax = salarySummary.getTax();
            insurance = salarySummary.getInsurance();
            totalDeduction = salarySummary.getTotalDeductions();
            absentLeaveDeduction = salarySummary.getAbsentLeaveDeduction();
            unpaidLeaveDeduction = salarySummary.getUnpaidLeaveDeduction();
            absentDaysCount = salarySummary.getAbsentDays();
            double unpaidLeaveDaysCount = salarySummary.getUnpaidLeaveDays();

            log.info("ENGINE CALCULATION - Absent={}, AbsentDeduction={}, UnpaidDeduction={}",
                    absentDaysCount, absentLeaveDeduction, unpaidLeaveDeduction);

            calculationMessage = "Calculated from AttendanceEngine (single source of truth)";
        }

        // Calculate net salary BEFORE setting to payslip
        BigDecimal netSalary;
        if (payrollOpt.isPresent()) {
            // ✅ FIX: Always recalculate netSalary from grossSalary - fresh totalDeduction
            // This ensures any previously stale stored net values are corrected
            netSalary = grossSalary.subtract(totalDeduction);
            if (netSalary.compareTo(BigDecimal.ZERO) < 0) netSalary = BigDecimal.ZERO;
            payslip.setTotalDeduction(totalDeduction);
        } else {
            // No payroll - use engine-calculated net salary (single source of truth)
            netSalary = salarySummary.getNetSalary();
            payslip.setTotalDeduction(totalDeduction);

            log.info("SALARY DEBUG - Basic={}, NetSalary from engine={}", basicSalary, netSalary);
        }
        
        // Set all values to payslip
        payslip.setBasicSalary(basicSalary);
        payslip.setHra(hra);
        payslip.setSpecialAllowance(employee.getSpecialAllowance() != null ? employee.getSpecialAllowance() : BigDecimal.ZERO);
        payslip.setBonus(employee.getBonus() != null ? employee.getBonus() : BigDecimal.ZERO);
        payslip.setIncentive(employee.getIncentive() != null ? employee.getIncentive() : BigDecimal.ZERO);
        payslip.setOtherAllowance(otherAllowance);
        payslip.setGrossSalary(grossSalary);
        payslip.setPf(pf);
        payslip.setEsi(esi);
        payslip.setEsic(employee.getEsic() != null ? employee.getEsic() : BigDecimal.ZERO);
        payslip.setProfessionalTax(employee.getProfessionalTax() != null ? employee.getProfessionalTax() : BigDecimal.ZERO);
        payslip.setTds(employee.getTds() != null ? employee.getTds() : BigDecimal.ZERO);
        payslip.setIncomeTax(incomeTax);
        payslip.setLoanDeduction(employee.getLoanDeduction() != null ? employee.getLoanDeduction() : BigDecimal.ZERO);
        payslip.setLwf(employee.getLwf() != null ? employee.getLwf() : BigDecimal.ZERO);
        payslip.setInsurance(insurance);
        payslip.setOtherDeduction(insurance);
        payslip.setAbsentLeaveDeduction(absentLeaveDeduction);
        payslip.setUnpaidLeaveDeduction(unpaidLeaveDeduction);
        payslip.setNetSalary(netSalary);
        
        log.info("DEBUG - After setting: NetSalary from payslip={}", payslip.getNetSalary());
        log.info("Payslip calculation complete: Gross={}, TotalDeduction={}, AbsentDeduction={}, Net={}",
                grossSalary, totalDeduction, absentLeaveDeduction, netSalary);
        log.info(calculationMessage);
    }



    /**
     * Get payslip by ID
     */
    public PayslipDTO getPayslipById(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found with id: " + payslipId));
        return convertToDTO(payslip);
    }

    /**
     * Get payslip by employee and month/year.
     * ✅ ALWAYS regenerates with fresh leave/attendance data if payroll exists and is not locked.
     * This ensures the Attendance & Leave Summary on the payslip reflects the latest approved leaves.
     */
    public PayslipDTO getPayslipByEmployeeAndMonth(Long employeeId, String monthYear) {
        // If payroll is not locked, regenerate the payslip with fresh data
        try {
            String[] parts = monthYear.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);

            // Only regenerate if payroll exists for this employee+month (not locked)
            if (!payrollLockService.isPayrollLockedForEmployee(employeeId, month, year)) {
                Optional<com.hrm.hrmsystem.model.Payroll> payrollOpt =
                        payrollRepository.findFirstByEmployeeIdAndMonthAndYearOrderByIdDesc(employeeId, month, year);
                if (payrollOpt.isPresent()) {
                    log.info("Regenerating payslip with fresh data for employee {} month {}", employeeId, monthYear);
                    return generatePayslip(employeeId, monthYear);
                }
            }
        } catch (Exception e) {
            log.warn("Could not regenerate payslip for employee {} month {}: {}", employeeId, monthYear, e.getMessage());
        }

        // Fallback: return stored payslip
        java.util.List<Payslip> payslips = payslipRepository.findAllByEmployeeIdAndMonthYear(employeeId, monthYear);
        if (payslips == null || payslips.isEmpty()) {
            throw new ResourceNotFoundException("Payslip not found for employee " + employeeId + " and month " + monthYear);
        }
        payslips.sort((a, b) -> b.getId().compareTo(a.getId()));
        Payslip payslip = payslips.get(0);
        if (payslips.size() > 1) {
            log.warn("Found {} duplicate payslips for employee {} month {}, using most recent (ID: {})",
                    payslips.size(), employeeId, monthYear, payslip.getId());
        }
        return convertToDTO(payslip);
    }

    /**
     * Update payslip status by employee and month/year
     */
    public void updatePayslipStatusByEmployeeAndMonth(Long employeeId, String monthYear, String status, String approvedBy, String remarks) {
        // Handle duplicate payslips by getting all and updating the most recent
        java.util.List<Payslip> payslips = payslipRepository.findAllByEmployeeIdAndMonthYear(employeeId, monthYear);
        if (payslips == null || payslips.isEmpty()) {
            log.warn("Payslip not found for employee {} and month {} to update status", employeeId, monthYear);
            return;
        }
        // Sort by ID descending to get the most recent one
        payslips.sort((a, b) -> b.getId().compareTo(a.getId()));
        Payslip payslip = payslips.get(0);
        if (payslips.size() > 1) {
            log.warn("Found {} duplicate payslips for employee {} month {}, updating most recent (ID: {})", 
                    payslips.size(), employeeId, monthYear, payslip.getId());
        }
            try {
                Payslip.PayslipStatus newStatus = Payslip.PayslipStatus.valueOf(status);
                payslip.setStatus(newStatus);
                if (approvedBy != null) payslip.setApprovedBy(approvedBy);
                if (remarks != null) payslip.setRemarks(remarks);
                if (status.equals("APPROVED") || status.equals("PAID")) {
                    payslip.setApprovedDate(LocalDate.now());
                }
                payslipRepository.save(payslip);
                log.info("Updated payslip {} status to {}", payslip.getId(), status);
            } catch (IllegalArgumentException e) {
                log.error("Invalid payslip status: {}. Valid values are: DRAFT, GENERATED, APPROVED, SENT, REJECTED", status);
                throw new RuntimeException("Invalid payslip status: " + status, e);
            }
    }

    /**
     * Get all payslips
     */
    public List<PayslipDTO> getAllPayslips() {
        log.info("Fetching all payslips");
        List<Payslip> payslips = payslipRepository.findAll();
        return payslips.stream()
                .filter(p -> p.getEmployee() != null && !isSystemUser(p.getEmployee()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all payslips for an employee
     */
    public List<PayslipDTO> getPayslipsByEmployee(Long employeeId) {
        Employee employee = employeeRepository.findByIdentifier(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        if (isSystemUser(employee)) {
            return new java.util.ArrayList<>();
        }

        List<Payslip> payslips = payslipRepository.findByEmployeeOrderByMonthYearDesc(employee);
        return payslips.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * Get payslips for a specific month
     */
    public List<PayslipDTO> getPayslipsByMonth(String monthYear) {
        // Rule: show payslip on UI only if payroll is PAID for that month/year.
        // For unpaid employees, still return employee details but all salary/deduction values as 0.
        String[] parts = monthYear.split("-");
        int month = Integer.parseInt(parts[1]);
        int year = Integer.parseInt(parts[0]);

        List<Employee> employees = employeeRepository.findByStatus(Employee.EmployeeStatus.ACTIVE)
                .stream()
                .filter(emp -> !isSystemUser(emp))
                .collect(Collectors.toList());

        // Existing payslips for this month/year (may be missing for some employees)
        List<Payslip> existingPayslips = payslipRepository.findByMonthYear(monthYear);
        Map<Long, Payslip> payslipByEmployeeId = new HashMap<>();
        if (existingPayslips != null) {
            for (Payslip p : existingPayslips) {
                if (p != null && p.getEmployee() != null) {
                    payslipByEmployeeId.put(p.getEmployee().getId(), p);
                }
            }
        }

        List<PayslipDTO> result = new ArrayList<>();
        for (Employee employee : employees) {
            if (employee == null) continue;

            java.util.Optional<com.hrm.hrmsystem.model.Payroll> payrollOpt =
                    payrollRepository.findFirstByEmployeeIdAndMonthAndYearOrderByIdDesc(employee.getId(), month, year);

            String payrollStatus = payrollOpt.map(p -> p.getStatus().name()).orElse(null);
            boolean isPaid = payrollOpt.isPresent() && payrollOpt.get().getStatus() == com.hrm.hrmsystem.model.Payroll.PayrollStatus.PAID;

            if (payrollOpt.isPresent()) {
                // If payroll exists (PENDING, APPROVED, or PAID), return actual values
                // We call generatePayslip to ensure values are in-sync with latest attendance/leave
                PayslipDTO dto = generatePayslip(employee.getId(), monthYear);
                dto.setPayrollStatus(payrollStatus);
                result.add(dto);
            } else {
                // No payroll generated yet: show employee details but zero everything.
                PayslipDTO dto = new PayslipDTO();
                dto.setId(employee.getId()); // unique key for UI lists
                dto.setEmployeeId(employee.getId());
                dto.setEmployeeName(employee.getFirstName() + " " + employee.getLastName());
                dto.setMonthYear(monthYear);
                dto.setPayrollStatus("UNPAID");

                dto.setBasicSalary(BigDecimal.ZERO);
                dto.setGrossSalary(BigDecimal.ZERO);
                dto.setNetSalary(BigDecimal.ZERO);
                dto.setPf(BigDecimal.ZERO);
                dto.setEsi(BigDecimal.ZERO);
                dto.setIncomeTax(BigDecimal.ZERO);
                dto.setOtherDeduction(BigDecimal.ZERO);
                dto.setTotalDeduction(BigDecimal.ZERO);
                dto.setAbsentLeaveDeduction(BigDecimal.ZERO);

                dto.setPresentDays(0.0);
                dto.setAbsentDays(0.0);
                dto.setLeaveDays(0.0);
                dto.setHalfDays(0);
                dto.setWorkingDays(PayrollPolicy.DEDUCTION_DAYS_PER_MONTH);
                dto.setTotalDays(30);
                dto.setAbsentDates(new ArrayList<>());

                dto.setStatus("UNPAID");
                result.add(dto);
            }
        }

        return result.stream()
                .sorted((a, b) -> {
                    String an = a.getEmployeeName() == null ? "" : a.getEmployeeName();
                    String bn = b.getEmployeeName() == null ? "" : b.getEmployeeName();
                    return an.compareToIgnoreCase(bn);
                })
                .collect(Collectors.toList());
    }

    /**
     * Approve payslip
     */
    public PayslipDTO approvePayslip(Long payslipId, String approvedBy, String remarks) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found with id: " + payslipId));

        if (!payslip.getStatus().equals(Payslip.PayslipStatus.GENERATED)) {
            throw new BadRequestException("Only GENERATED payslips can be approved");
        }

        payslip.setStatus(Payslip.PayslipStatus.APPROVED);
        payslip.setApprovedBy(approvedBy);
        payslip.setApprovedDate(LocalDate.now());
        payslip.setRemarks(remarks);

        Payslip updatedPayslip = payslipRepository.save(payslip);
        log.info("Payslip {} approved by {}", payslipId, approvedBy);

        return convertToDTO(updatedPayslip);
    }

    /**
     * Reject payslip
     */
    public PayslipDTO rejectPayslip(Long payslipId, String rejectionReason) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found with id: " + payslipId));

        payslip.setStatus(Payslip.PayslipStatus.REJECTED);
        payslip.setRemarks(rejectionReason);

        Payslip updatedPayslip = payslipRepository.save(payslip);
        log.info("Payslip {} rejected", payslipId);

        return convertToDTO(updatedPayslip);
    }

    /**
     * Update payslip calculations (for admin to modify before approval)
     */
    public PayslipDTO updatePayslip(Long payslipId, Map<String, Object> updates) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found with id: " + payslipId));

        // Only allow updates for DRAFT or GENERATED payslips
        if (!payslip.getStatus().equals(Payslip.PayslipStatus.GENERATED) &&
            !payslip.getStatus().equals(Payslip.PayslipStatus.DRAFT)) {
            throw new BadRequestException("Cannot update payslip that is already " + payslip.getStatus());
        }

        // Update fields if provided
        if (updates.containsKey("basicSalary")) payslip.setBasicSalary(BigDecimal.valueOf(((Number) updates.get("basicSalary")).doubleValue()));
        if (updates.containsKey("hra")) payslip.setHra(BigDecimal.valueOf(((Number) updates.get("hra")).doubleValue()));

        if (updates.containsKey("otherAllowance")) payslip.setOtherAllowance(BigDecimal.valueOf(((Number) updates.get("otherAllowance")).doubleValue()));
        if (updates.containsKey("pf")) payslip.setPf(BigDecimal.valueOf(((Number) updates.get("pf")).doubleValue()));
        if (updates.containsKey("esi")) payslip.setEsi(BigDecimal.valueOf(((Number) updates.get("esi")).doubleValue()));
        if (updates.containsKey("incomeTax")) payslip.setIncomeTax(BigDecimal.valueOf(((Number) updates.get("incomeTax")).doubleValue()));
        if (updates.containsKey("insurance")) {
            BigDecimal ins = BigDecimal.valueOf(((Number) updates.get("insurance")).doubleValue());
            payslip.setInsurance(ins);
        }
        if (updates.containsKey("otherDeduction")) {
            BigDecimal other = BigDecimal.valueOf(((Number) updates.get("otherDeduction")).doubleValue());
            payslip.setOtherDeduction(other);
            if (!updates.containsKey("insurance")) {
                payslip.setInsurance(other);
            }
        }

        // Recalculate totals
        BigDecimal grossSalary = payslip.getBasicSalary()
                .add(payslip.getHra() != null ? payslip.getHra() : BigDecimal.ZERO)
                .add(payslip.getOtherAllowance() != null ? payslip.getOtherAllowance() : BigDecimal.ZERO);

        // Include both unpaid leave and absent deductions in the recalculated totalDeduction
        BigDecimal totalDeduction = payslip.getPf()
                .add(payslip.getEsi() != null ? payslip.getEsi() : BigDecimal.ZERO)
                .add(payslip.getIncomeTax() != null ? payslip.getIncomeTax() : BigDecimal.ZERO)
                .add(payslip.getOtherDeduction() != null ? payslip.getOtherDeduction() : BigDecimal.ZERO)
                .add(payslip.getAbsentLeaveDeduction() != null ? payslip.getAbsentLeaveDeduction() : BigDecimal.ZERO)
                .add(payslip.getUnpaidLeaveDeduction() != null ? payslip.getUnpaidLeaveDeduction() : BigDecimal.ZERO);

        BigDecimal netSalary = grossSalary.subtract(totalDeduction);

        payslip.setGrossSalary(grossSalary);
        payslip.setTotalDeduction(totalDeduction);
        payslip.setNetSalary(netSalary);

        Payslip savedPayslip = payslipRepository.save(payslip);
        log.info("Payslip {} updated by admin", payslipId);

        return convertToDTO(savedPayslip);
    }

    /**
     * Generate PDF for payslip
     */
    public String generatePayslipPdf(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found with id: " + payslipId));

        try {
            String pdfPath = pdfGeneratorUtil.generatePayslipPdf(payslip);
            payslip.setPdfGenerated(true);
            payslip.setPdfFilePath(pdfPath);
            payslipRepository.save(payslip);
            log.info("PDF generated for payslip {} at path: {}", payslipId, pdfPath);
            return pdfPath;
        } catch (IOException e) {
            log.error("Error generating PDF for payslip {}: {}", payslipId, e.getMessage());
            throw new RuntimeException("Error generating PDF: " + e.getMessage());
        }
    }

    /**
     * Send payslip to employee via email
     */
    public void sendPayslipToEmployee(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found with id: " + payslipId));

        if (!payslip.getStatus().equals(Payslip.PayslipStatus.APPROVED)) {
            throw new BadRequestException("Only APPROVED payslips can be sent");
        }

        try {
            // Generate PDF if not already generated
            if (!payslip.getPdfGenerated()) {
                generatePayslipPdf(payslipId);
            }

            // Check if email notification is enabled for this employee
            boolean shouldEmail = true;
            Employee employee = payslip.getEmployee();
            if (employee != null) {
                shouldEmail = userRepository.findByEmployeeId(employee.getId())
                    .flatMap(user -> preferenceRepository.findByUser(user))
                    .map(pref -> pref.getEmailNotifications() != null && pref.getEmailNotifications() && (pref.getPayrollUpdates() != null && pref.getPayrollUpdates()))
                    .orElse(true);
            }

            // Send email with payslip if allowed
            if (shouldEmail) {
                emailUtil.sendPayslipEmail(payslip);
                log.info("Payslip {} sent to employee: {}", payslipId, employee.getEmail());
            } else {
                log.info("⏩ Skipping payslip email for {} (notification preference disabled)", employee.getEmail());
            }

            payslip.setStatus(Payslip.PayslipStatus.SENT);
            payslip.setSentDate(LocalDate.now());
            payslipRepository.save(payslip);

            log.info("Payslip {} sent to employee: {}", payslipId, payslip.getEmployee().getEmail());
        } catch (Exception e) {
            log.error("Error sending payslip {}: {}", payslipId, e.getMessage());
            throw new RuntimeException("Error sending payslip: " + e.getMessage());
        }
    }

    /**
     * Send payslip email for paid payroll
     */
    public void sendPaidPayslipEmail(Long employeeId, String monthYear) {
        log.info("Preparing to send paid payslip email for employee {} month {}", employeeId, monthYear);
        try {
            java.util.List<Payslip> payslips = payslipRepository.findAllByEmployeeIdAndMonthYear(employeeId, monthYear);
            if (payslips == null || payslips.isEmpty()) {
                log.warn("Payslip not found for employee {} and month {} to send paid email", employeeId, monthYear);
                return;
            }
            payslips.sort((a, b) -> b.getId().compareTo(a.getId()));
            Payslip payslip = payslips.get(0);

            // Always regenerate PDF to ensure it has the final status, net salary, and paid dates
            try {
                String pdfPath = pdfGeneratorUtil.generatePayslipPdf(payslip);
                payslip.setPdfGenerated(true);
                payslip.setPdfFilePath(pdfPath);
                payslipRepository.save(payslip);
                log.info("PDF regenerated for paid payslip {} at path: {}", payslip.getId(), pdfPath);
            } catch (IOException e) {
                log.error("Error generating/regenerating PDF for paid payslip {}: {}", payslip.getId(), e.getMessage());
            }

            // Check if email notification is enabled for this employee
            boolean shouldEmail = true;
            Employee employee = payslip.getEmployee();
            if (employee != null) {
                shouldEmail = userRepository.findByEmployeeId(employee.getId())
                    .flatMap(user -> preferenceRepository.findByUser(user))
                    .map(pref -> pref.getEmailNotifications() != null && pref.getEmailNotifications() && (pref.getPayrollUpdates() != null && pref.getPayrollUpdates()))
                    .orElse(true);
            }

            // Send email with payslip if allowed
            if (shouldEmail) {
                emailUtil.sendPayslipEmail(payslip);
                log.info("Paid payslip email sent to employee {} for monthYear {}", employee.getEmail(), monthYear);
            } else {
                log.info("⏩ Skipping payslip email for {} (notification preference disabled)", employee.getEmail());
            }

            payslip.setStatus(Payslip.PayslipStatus.SENT);
            payslip.setSentDate(LocalDate.now());
            payslipRepository.save(payslip);
        } catch (Exception e) {
            log.error("Error sending paid payslip email: {}", e.getMessage());
        }
    }

    /**
     * Bulk generate payslips for all employees in a month
     */
    public List<PayslipDTO> bulkGeneratePayslips(String monthYear) {
        log.info("Bulk generating payslips for month: {}", monthYear);

        List<Employee> employees = employeeRepository.findAll()
                .stream()
                .filter(emp -> !isSystemUser(emp))
                .collect(Collectors.toList());
        List<PayslipDTO> generatedPayslips = employees.stream()
                .filter(emp -> !payslipRepository.existsByEmployeeAndMonthYear(emp, monthYear))
                .map(emp -> {
                    try {
                        return generatePayslip(emp.getId(), monthYear);
                    } catch (Exception e) {
                        log.warn("Failed to generate payslip for employee {}: {}", emp.getId(), e.getMessage());
                        return null;
                    }
                })
                .filter(p -> p != null)
                .collect(Collectors.toList());

        log.info("Bulk generation completed. Generated {} payslips", generatedPayslips.size());
        return generatedPayslips;
    }

    /**
     * Get pending approvals
     */
    public List<PayslipDTO> getPendingApprovals() {
        List<Payslip> payslips = payslipRepository.findPendingApprovals();
        return payslips.stream()
                .filter(p -> p.getEmployee() != null && !isSystemUser(p.getEmployee()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Delete payslip permanently from database
     */
    public void deletePayslip(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found with id: " + payslipId));

        if (payrollLockService.isPayrollLockedForEmployee(payslip.getEmployee().getId(), payslip.getSalaryMonth(), payslip.getSalaryYear())) {
            throw new RuntimeException("Cannot delete payslip: Payroll is locked for employee " + payslip.getEmployee().getId() + " for " + payslip.getSalaryYear() + "-" + payslip.getSalaryMonth());
        }

        // Clear absent dates to avoid FK constraint
        if (payslip.getAbsentDates() != null) {
            payslip.getAbsentDates().clear();
        }
        payslipRepository.save(payslip);
        
        payslipRepository.delete(payslip);
        log.info("Payslip {} deleted permanently", payslipId);
    }

    /**
     * Delete all unlocked payslips from database
     */
    @Transactional
    public void deleteUnlockedPayslips() {
        java.util.List<Payslip> allPayslips = payslipRepository.findAll();
        log.info("Checking {} payslips for deletion...", allPayslips.size());
        int deletedCount = 0;
        for (Payslip payslip : allPayslips) {
            if (payslip.getSalaryMonth() != null && payslip.getSalaryYear() != null) {
                if (!payrollLockService.isPayrollLockedForEmployee(payslip.getEmployee().getId(), payslip.getSalaryMonth(), payslip.getSalaryYear())) {
                    if (payslip.getAbsentDates() != null) {
                        payslip.getAbsentDates().clear();
                    }
                    payslipRepository.save(payslip);
                    payslipRepository.delete(payslip);
                    deletedCount++;
                }
            } else {
                if (payslip.getAbsentDates() != null) {
                    payslip.getAbsentDates().clear();
                }
                payslipRepository.save(payslip);
                payslipRepository.delete(payslip);
                deletedCount++;
            }
        }
        log.info("Deleted {} unlocked payslips", deletedCount);
    }

    /**
     * Convert Payslip entity to DTO
     */
    private PayslipDTO convertToDTO(Payslip payslip) {
        PayslipDTO dto = new PayslipDTO();
        Employee employee = payslip.getEmployee();
        dto.setId(payslip.getId());
        dto.setEmployeeId(employee.getId());
        dto.setEmployeeName(employee.getFirstName() + " " + employee.getLastName());
        dto.setDepartment(employee.getDepartment() != null ? employee.getDepartment().getName() : null);
        dto.setDesignation(employee.getDesignation());
        dto.setEmail(employee.getEmail());
        dto.setPhone(employee.getPhone());
        dto.setJoinDate(employee.getJoiningDate());
        dto.setMonthYear(payslip.getMonthYear());
        dto.setBasicSalary(payslip.getBasicSalary());
        dto.setHra(payslip.getHra());
        dto.setSpecialAllowance(payslip.getSpecialAllowance());
        dto.setBonus(payslip.getBonus());
        dto.setIncentive(payslip.getIncentive());
        dto.setOtherAllowance(payslip.getOtherAllowance());
        dto.setGrossSalary(payslip.getGrossSalary());
        dto.setPf(payslip.getPf());
        dto.setEsi(payslip.getEsi());
        dto.setEsic(payslip.getEsic());
        dto.setProfessionalTax(payslip.getProfessionalTax());
        dto.setTds(payslip.getTds());
        dto.setIncomeTax(payslip.getIncomeTax());
        dto.setLoanDeduction(payslip.getLoanDeduction());
        dto.setLwf(payslip.getLwf());
        dto.setOtherDeduction(payslip.getOtherDeduction());
        dto.setInsurance(payslip.getInsurance() != null ? payslip.getInsurance() : BigDecimal.ZERO);
        BigDecimal grossSalary = payslip.getGrossSalary() != null ? payslip.getGrossSalary() : BigDecimal.ZERO;
        BigDecimal dailyRate = BigDecimal.ZERO;
        if (grossSalary.compareTo(BigDecimal.ZERO) > 0) {
            dailyRate = grossSalary.divide(BigDecimal.valueOf(30), 2, java.math.RoundingMode.HALF_UP);
        }
        
        // ✅ FIX: Use stored deduction values directly — do NOT recompute from days × dailyRate
        // unpaidLeaveDays can include unpaid-absent portion which causes inflation if multiplied again
        BigDecimal correctAbsentDed = payslip.getAbsentLeaveDeduction() != null
                ? payslip.getAbsentLeaveDeduction() : BigDecimal.ZERO;
        BigDecimal correctUnpaidDed = payslip.getUnpaidLeaveDeduction() != null
                ? payslip.getUnpaidLeaveDeduction() : BigDecimal.ZERO;

        BigDecimal pfAmt = payslip.getPf() != null ? payslip.getPf() : BigDecimal.ZERO;
        BigDecimal esiAmt = payslip.getEsi() != null ? payslip.getEsi() : BigDecimal.ZERO;
        BigDecimal taxAmt = payslip.getIncomeTax() != null ? payslip.getIncomeTax() : BigDecimal.ZERO;
        BigDecimal insAmt = payslip.getInsurance() != null ? payslip.getInsurance() : BigDecimal.ZERO;
        BigDecimal esicAmt = payslip.getEsic() != null ? payslip.getEsic() : BigDecimal.ZERO;
        BigDecimal profTaxAmt = payslip.getProfessionalTax() != null ? payslip.getProfessionalTax() : BigDecimal.ZERO;
        BigDecimal loanAmt = payslip.getLoanDeduction() != null ? payslip.getLoanDeduction() : BigDecimal.ZERO;
        BigDecimal lwfAmt = payslip.getLwf() != null ? payslip.getLwf() : BigDecimal.ZERO;
        
        // ✅ correctAbsentDed is DISPLAY ONLY — do NOT include in totalDeduction
        BigDecimal correctTotalDed = pfAmt.add(esiAmt).add(taxAmt).add(insAmt)
                .add(correctUnpaidDed)
                .add(esicAmt).add(profTaxAmt).add(loanAmt).add(lwfAmt);
        
        dto.setTotalDeduction(correctTotalDed);
        dto.setNetSalary(grossSalary.subtract(correctTotalDed));
        
        log.info("DEBUG - DTO values: PF={}, Tax={}, Insurance={}, TotalDed={}, Net={}", 
                 dto.getPf(), dto.getIncomeTax(), dto.getOtherDeduction(), dto.getTotalDeduction(), dto.getNetSalary());
        dto.setPresentDays(payslip.getPresentDays());
        dto.setAbsentDays(payslip.getAbsentDays());
        dto.setLeaveDays(payslip.getLeaveDays());
        dto.setPaidLeaveDays(payslip.getPaidLeaveDays());
        dto.setUnpaidLeaveDays(payslip.getUnpaidLeaveDays());
        dto.setHalfDays(payslip.getHalfDays());
        dto.setWorkingDays(payslip.getWorkingDays());
        dto.setTotalDays(payslip.getTotalDays());
        dto.setAbsentLeaveDeduction(correctAbsentDed);
        dto.setUnpaidLeaveDeduction(correctUnpaidDed);
        dto.setAbsentPenaltyDeduction(correctAbsentDed);
        dto.setStatus(payslip.getStatus().toString());
        dto.setGeneratedDate(payslip.getGeneratedDate());
        dto.setApprovedDate(payslip.getApprovedDate());
        dto.setApprovedBy(payslip.getApprovedBy());
        dto.setRemarks(payslip.getRemarks());
        dto.setPdfFilePath(payslip.getPdfFilePath());
        
        // ✅ REMOVED: Manual probation calculation - Use AttendanceEngine for ALL calculations
        // Calculate and set probation status using CURRENT date (not payslip date)
        // This ensures the status reflects the current state, not historical state
        // boolean currentlyInProbation = com.hrm.hrmsystem.util.ProbationUtil.isInProbation(employee, LocalDate.now());
        // dto.setInProbation(currentlyInProbation);
        // dto.setProbationStatus(currentlyInProbation ? "In Progress" : "Completed");
        
        // Use AttendanceEngine for probation calculations based on payslip month
        Integer payslipMonthForProb = payslip.getSalaryMonth();
        Integer payslipYearForProb = payslip.getSalaryYear();
        if (payslipMonthForProb == null || payslipYearForProb == null) {
            String monthYear = payslip.getMonthYear();
            if (monthYear != null && monthYear.contains(" ")) {
                String[] parts = monthYear.split(" ");
                payslipMonthForProb = getMonthNumber(parts[0]);
                payslipYearForProb = Integer.parseInt(parts[1]);
            }
        }
        if (payslipMonthForProb == null || payslipYearForProb == null) {
            payslipMonthForProb = LocalDate.now().getMonthValue();
            payslipYearForProb = LocalDate.now().getYear();
        }
        LocalDate targetDate = YearMonth.of(payslipYearForProb, payslipMonthForProb).atEndOfMonth();
        boolean currentlyInProbation = !attendanceEngine.isProbationCompleted(employee, targetDate);
        dto.setInProbation(currentlyInProbation);
        dto.setProbationStatus(currentlyInProbation ? "In Progress" : "Completed");
        dto.setProbationMonths(employee.getProbationPeriodMonths() != null ? employee.getProbationPeriodMonths() : 3);

        // Calculate probation completion date
        LocalDate probationCompletionDate = null;
        if (employee.getJoiningDate() != null) {
            Integer probationMonths = employee.getProbationPeriodMonths() != null ? employee.getProbationPeriodMonths() : 3;
            probationCompletionDate = employee.getJoiningDate().plusMonths(probationMonths);
        }
        dto.setProbationCompletionDate(probationCompletionDate);

        // 🔥 SINGLE SOURCE OF TRUTH: Get leave balance from AttendanceEngine ONLY
        PayslipDTO.LeaveBalanceInfo leaveBalanceInfo = new PayslipDTO.LeaveBalanceInfo();

        try {
            // Get payslip month/year for accurate leave balance calculation
            Integer payslipMonth = payslip.getSalaryMonth();
            Integer payslipYear = payslip.getSalaryYear();
            
            // Fallback to parsing monthYear if salaryMonth/salaryYear not set
            if (payslipMonth == null || payslipYear == null) {
                String monthYear = payslip.getMonthYear();
                if (monthYear != null && monthYear.contains(" ")) {
                    String[] parts = monthYear.split(" ");
                    payslipMonth = getMonthNumber(parts[0]);
                    payslipYear = Integer.parseInt(parts[1]);
                }
            }
            
            // Default to current date if still not available
            if (payslipMonth == null || payslipYear == null) {
                payslipMonth = LocalDate.now().getMonthValue();
                payslipYear = LocalDate.now().getYear();
            }
            
            // ✅ ONE CALL TO SERVICE - ALL CALCULATIONS INSIDE SERVICE
            UnifiedCalculationService.LeaveBalanceResult summary = 
                    unifiedCalculationService.getLeaveSummary(employee.getId(), YearMonth.of(payslipYear, payslipMonth));
            
            leaveBalanceInfo.setTotalEarnedLeaves(summary.earned);
            leaveBalanceInfo.setUsedLeaves(summary.totalUsed); // ✅ Fixed: show Paid + Unpaid
            leaveBalanceInfo.setAvailableLeaves(summary.remaining);
            leaveBalanceInfo.setCarriedForwardLeaves(0.0);
            leaveBalanceInfo.setUnpaidLeaves(summary.unpaidLeaves);
        } catch (Exception e) {
            log.error("Error calculating leave balance for employee {}: {}", employee.getId(), e.getMessage());
            leaveBalanceInfo.setTotalEarnedLeaves(0.0);
            leaveBalanceInfo.setUsedLeaves(0.0);
            leaveBalanceInfo.setAvailableLeaves(0.0);
            leaveBalanceInfo.setCarriedForwardLeaves(0.0);
            leaveBalanceInfo.setUnpaidLeaves(0.0);
        }
        
        dto.setLeaveBalance(leaveBalanceInfo);
        
        return dto;
    }

    /**
     * Update payslip from payroll data when payroll is edited
     */
    @Transactional
    public void updatePayslipFromPayroll(Long employeeId, String monthYear, com.hrm.hrmsystem.dto.PayrollDTO payrollDTO) {
        List<Payslip> payslips = payslipRepository.findAllByEmployeeIdAndMonthYear(employeeId, monthYear);
        if (payslips == null || payslips.isEmpty()) {
            log.warn("No payslip found for employee {} month {} to update", employeeId, monthYear);
            return;
        }
        
        // Sort by ID descending to get the most recent one
        payslips.sort((a, b) -> b.getId().compareTo(a.getId()));
        Payslip payslip = payslips.get(0);
        
        // Update payslip fields from payroll data
        payslip.setBasicSalary(payrollDTO.getBasicSalary());
        payslip.setHra(payrollDTO.getHra());
        payslip.setOtherAllowance(payrollDTO.getOtherAllowances());
        payslip.setPf(payrollDTO.getProvidentFund());
        payslip.setIncomeTax(payrollDTO.getTax());
        payslip.setInsurance(payrollDTO.getInsurance());
        payslip.setOtherDeduction(payrollDTO.getInsurance()); // For backward compatibility
        payslip.setEsi(BigDecimal.ZERO);
        payslip.setAbsentLeaveDeduction(payrollDTO.getOtherDeductions());
        payslip.setUnpaidLeaveDeduction(payrollDTO.getUnpaidLeaveDeduction());
        
        // Recalculate totals
        BigDecimal grossSalary = payrollDTO.getBasicSalary()
                .add(payrollDTO.getHra())
                .add(payrollDTO.getOtherAllowances());
        payslip.setGrossSalary(grossSalary);
        
        BigDecimal pfAmt = payrollDTO.getProvidentFund() != null ? payrollDTO.getProvidentFund() : BigDecimal.ZERO;
        BigDecimal taxAmt = payrollDTO.getTax() != null ? payrollDTO.getTax() : BigDecimal.ZERO;
        BigDecimal insAmt = payrollDTO.getInsurance() != null ? payrollDTO.getInsurance() : BigDecimal.ZERO;
        BigDecimal otherDed = payrollDTO.getOtherDeductions() != null ? payrollDTO.getOtherDeductions() : BigDecimal.ZERO;

        BigDecimal esicAmt = payslip.getEsic() != null ? payslip.getEsic() : BigDecimal.ZERO;
        BigDecimal profTaxAmt = payslip.getProfessionalTax() != null ? payslip.getProfessionalTax() : BigDecimal.ZERO;
        BigDecimal tdsAmt = payslip.getTds() != null ? payslip.getTds() : BigDecimal.ZERO;
        BigDecimal loanAmt = payslip.getLoanDeduction() != null ? payslip.getLoanDeduction() : BigDecimal.ZERO;
        BigDecimal lwfAmt = payslip.getLwf() != null ? payslip.getLwf() : BigDecimal.ZERO;

        BigDecimal unpaidLeaveDed = payslip.getUnpaidLeaveDeduction() != null ? payslip.getUnpaidLeaveDeduction() : BigDecimal.ZERO;
        BigDecimal absentDed = payslip.getAbsentLeaveDeduction() != null ? payslip.getAbsentLeaveDeduction() : BigDecimal.ZERO;

        BigDecimal totalDeductions = pfAmt.add(taxAmt).add(insAmt).add(otherDed)
                .add(esicAmt).add(profTaxAmt).add(tdsAmt).add(loanAmt).add(lwfAmt)
                .add(unpaidLeaveDed).add(absentDed);
        payslip.setTotalDeduction(totalDeductions);
        
        BigDecimal netSalary = grossSalary.subtract(totalDeductions);
        if (netSalary.compareTo(BigDecimal.ZERO) < 0) {
            netSalary = BigDecimal.ZERO;
        }
        payslip.setNetSalary(netSalary);
        
        payslipRepository.save(payslip);
        log.info("Updated payslip {} from payroll data for employee {} month {}", 
                payslip.getId(), employeeId, monthYear);
    }

    


    /**
     * Count active months between two dates
     * Counts months where the 1st day has passed (employee was active on 1st of month)
     * Rule: 1.5 leave is credited on the 1st day of every month if employee is active
     */
    private long countActiveMonths(LocalDate startDate, LocalDate endDate) {
        long months = 0;
        LocalDate current = startDate.withDayOfMonth(1);

        while (!current.isAfter(endDate)) {
            // If we've reached or passed the 1st of this month, count it
            months++;
            current = current.plusMonths(1);
        }

        return months;
    }

    /**
     * Convert month name to month number (1-12)
     */
    private Integer getMonthNumber(String monthName) {
        if (monthName == null) return null;
        return switch (monthName.trim().toLowerCase()) {
            case "january" -> 1;
            case "february" -> 2;
            case "march" -> 3;
            case "april" -> 4;
            case "may" -> 5;
            case "june" -> 6;
            case "july" -> 7;
            case "august" -> 8;
            case "september" -> 9;
            case "october" -> 10;
            case "november" -> 11;
            case "december" -> 12;
            default -> null;
        };
    }

    /**
     * Count days between two dates EXCLUDING Sundays
     * Sundays are not counted as leave days
     */
    // ✅ REMOVED: countDaysExcludingSundays() - Use AttendanceEngine.calculateWorkingDays() only

    private boolean isSystemUser(Employee emp) {
        if (emp.getDepartment() == null || emp.getDepartment().getName() == null) return false;
        String dept = emp.getDepartment().getName().toLowerCase();
        return dept.contains("hr") || dept.contains("director") || 
               dept.contains("leave") || dept.contains("accountant");
    }

    /**
     * Unlock payslip status and clear approval fields
     */
    @Transactional
    public void unlockPayslip(Long employeeId, String monthYear) {
        log.info("Unlocking payslip for employee {} for month {}", employeeId, monthYear);
        java.util.List<Payslip> payslips = payslipRepository.findAllByEmployeeIdAndMonthYear(employeeId, monthYear);
        for (Payslip p : payslips) {
            p.setStatus(Payslip.PayslipStatus.GENERATED);
            p.setApprovedBy(null);
            p.setApprovedDate(null);
            p.setRemarks(null);
            p.setPdfGenerated(false);
            p.setPdfFilePath(null);
            payslipRepository.save(p);
        }
    }
}
