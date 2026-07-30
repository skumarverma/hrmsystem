package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.config.PayrollPolicy;
import com.hrm.hrmsystem.dto.PayrollDTO;
import com.hrm.hrmsystem.dto.PayslipDTO;
import com.hrm.hrmsystem.engine.AttendanceSummary;
import com.hrm.hrmsystem.engine.AttendanceEngine;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.Payroll;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.repository.LeaveRepository;
import com.hrm.hrmsystem.repository.PayrollRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.hrm.hrmsystem.util.EmailUtil;
import com.hrm.hrmsystem.model.User;

@Service
public class PayrollService {

    private static final Logger log = LoggerFactory.getLogger(PayrollService.class);

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRepository leaveRepository;
    private final PayslipService payslipService;
    private final LeaveService leaveService;
    private final UnifiedCalculationService unifiedCalculationService;
    private final AttendanceEngine attendanceEngine;
    private final EmailUtil emailUtil;
    private final PayrollLockService payrollLockService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.hrm.hrmsystem.repository.PayrollApprovalRepository payrollApprovalRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.hrm.hrmsystem.repository.UserRepository userRepository;

    public PayrollService(PayrollRepository payrollRepository,
                          EmployeeRepository employeeRepository,
                          LeaveRepository leaveRepository,
                          PayslipService payslipService,
                          LeaveService leaveService,
                          UnifiedCalculationService unifiedCalculationService,
                          AttendanceEngine attendanceEngine,
                          EmailUtil emailUtil,
                          PayrollLockService payrollLockService) {
        this.payrollRepository = payrollRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRepository = leaveRepository;
        this.payslipService = payslipService;
        this.leaveService = leaveService;
        this.unifiedCalculationService = unifiedCalculationService;
        this.attendanceEngine = attendanceEngine;
        this.emailUtil = emailUtil;
        this.payrollLockService = payrollLockService;
    }

    @Transactional
    public PayrollDTO generatePayroll(Long employeeId, Integer month, Integer year) {
        if (payrollLockService.isPayrollLocked(month, year)) {
            throw new RuntimeException("Cannot generate or update payroll: Payroll is locked for " + year + "-" + month);
        }

        // Validation: Payroll can be generated for any past month, current month, or next month
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.YearMonth currentMonth = java.time.YearMonth.from(now);
        java.time.YearMonth nextMonth = currentMonth.plusMonths(1);
        java.time.YearMonth requestedMonth = java.time.YearMonth.of(year, month);
        
        if (requestedMonth.isAfter(nextMonth)) {
            throw new RuntimeException("Payroll cannot be generated for future months beyond next month.");
        }

        Employee employee = employeeRepository.findByIdentifier(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (isSystemUser(employee)) {
            // Previously we threw an exception here, but HR/Directors also get salaries.
            // Keeping the method for future role-based checks if needed, but not blocking generation.
        }

        Optional<Payroll> existing = payrollRepository
                .findFirstByEmployeeIdAndMonthAndYearOrderByIdDesc(employee.getId(), month, year);
        Payroll payroll = existing.orElseGet(Payroll::new);
        Payroll.PayrollStatus statusToUse = existing.map(Payroll::getStatus).orElse(Payroll.PayrollStatus.PENDING);
        java.time.LocalDate paymentDateToUse = existing.map(Payroll::getPaymentDate).orElse(null);

        // 🔥 SINGLE SOURCE OF TRUTH: Use engine for all calculations
        AttendanceSummary summary = unifiedCalculationService.calculateForPayroll(employeeId, year, month);
        
        double workedDays = summary.workedDays; // ✅ FIXED: Use workedDays instead of present
        double absentDays = summary.absent;
        
        // ✅ Use calculateLeaveBalance for leave data (same source as dashboard and other services)
        // ✅ FIXED: Use summary from AttendanceEngine for THIS MONTH'S values
        // leaveBalance.usedLeaves is the CUMULATIVE total for the 6-month cycle
        UnifiedCalculationService.LeaveBalanceResult leaveSummary = unifiedCalculationService.getLeaveSummary(employeeId, java.time.YearMonth.of(year, month));
        double paidLeaveDays = leaveSummary.usedThisMonth;
        double unpaidLeaveDays = leaveSummary.unpaidThisMonth;
        
        log.info("🔥 AttendanceEngine Result for Employee {}: {}", employeeId, summary);

        BigDecimal basicSalary = employee.getBasicSalary() != null
                ? employee.getBasicSalary()
                : BigDecimal.ZERO;
        BigDecimal hra = employee.getHra() != null
                ? employee.getHra()
                : BigDecimal.ZERO;
        BigDecimal otherAllowance = employee.getOtherAllowance() != null
                ? employee.getOtherAllowance()
                : BigDecimal.ZERO;
        
        // ✅ GROSS SALARY FORMULA: Deductions always from full Gross Salary (salary field takes priority)
        // getTotalGrossSalary() returns employee.salary if set, otherwise sums components.
        // The AttendanceEngine uses this same method internally for daily-rate calculation.
        BigDecimal grossSalaryBase = employee.getTotalGrossSalary();

        // Use AttendanceEngine for ALL salary calculations (single source of truth)
        // Daily Rate = grossSalaryBase / 30 working days
        // Unpaid deduction = Daily Rate × unpaid days × 1
        // Absent deduction = Daily Rate × absent days × 1 (penalty)
        AttendanceEngine.LeaveBalanceSummary engineLeaveSummary = attendanceEngine.calculateLeaveBalance(employeeId, YearMonth.of(year, month));
        AttendanceEngine.SalarySummary salarySummary = attendanceEngine.calculateSalary(
                employee, summary, !attendanceEngine.isProbationCompleted(employee, YearMonth.of(year, month).atEndOfMonth()), engineLeaveSummary);

        log.info("💰 PayrollService - GrossBase: {}, UnpaidDeduction: {}, AbsentDeduction: {}",
                 grossSalaryBase,
                 salarySummary.getUnpaidLeaveDeduction(),
                 salarySummary.getAbsentLeaveDeduction());

        BigDecimal ta = BigDecimal.ZERO;
        BigDecimal totalAttendanceDeduction = salarySummary.getUnpaidLeaveDeduction().add(salarySummary.getAbsentLeaveDeduction());
        BigDecimal providentFund = salarySummary.getPf();
        BigDecimal tax = salarySummary.getTax();
        BigDecimal insurance = salarySummary.getInsurance();
        BigDecimal grossSalary = salarySummary.getGrossSalary();
        BigDecimal totalDeductions = salarySummary.getTotalDeductions();
        BigDecimal netSalary = salarySummary.getNetSalary();
        // otherDeductions is for miscellaneous deductions only — attendance deductions are stored separately
        BigDecimal otherDeductions = BigDecimal.ZERO;

        payroll.setEmployee(employee);
        payroll.setMonth(month);
        payroll.setYear(year);
        payroll.setBasicSalary(basicSalary);
        payroll.setHra(hra.setScale(2, java.math.RoundingMode.HALF_UP));
        payroll.setTa(ta.setScale(2, java.math.RoundingMode.HALF_UP));
        payroll.setOtherAllowances(otherAllowance);
        payroll.setProvidentFund(providentFund.setScale(2, java.math.RoundingMode.HALF_UP));
        payroll.setTax(tax.setScale(2, java.math.RoundingMode.HALF_UP));
        payroll.setInsurance(insurance.setScale(2, java.math.RoundingMode.HALF_UP));
        payroll.setOtherDeductions(otherDeductions);
        payroll.setGrossSalary(grossSalary.setScale(2, java.math.RoundingMode.HALF_UP));
        payroll.setTotalDeductions(totalDeductions.setScale(2, java.math.RoundingMode.HALF_UP));
        payroll.setNetSalary(netSalary.setScale(2, java.math.RoundingMode.HALF_UP));
        payroll.setUnpaidLeaveDeduction(salarySummary.getUnpaidLeaveDeduction());
        payroll.setAbsentDeduction(salarySummary.getAbsentLeaveDeduction());
        payroll.setPresentDays(summary.getPresentDays());
        payroll.setAbsentDays(absentDays);
        payroll.setPaidLeaveDays(paidLeaveDays);
        payroll.setUnpaidLeaveDays(unpaidLeaveDays);
        payroll.setStatus(statusToUse);
        payroll.setPaymentDate(paymentDateToUse);

        payroll = payrollRepository.save(payroll);
        return convertToDTO(payroll);
    }

    public List<PayrollDTO> generatePayrollForAll(Integer month, Integer year) {
        if (payrollLockService.isPayrollLocked(month, year)) {
            throw new RuntimeException("Cannot generate payroll for all: Payroll is locked for " + year + "-" + month);
        }

        List<Employee> employees = employeeRepository.findByStatus(Employee.EmployeeStatus.ACTIVE)
            .stream()
            .filter(emp -> !isSystemUser(emp))
            .collect(Collectors.toList());

        List<PayrollDTO> generatedPayrolls = employees.stream()
                .map(emp -> generatePayroll(emp.getId(), month, year))
                .collect(Collectors.toList());

        String monthYear = year + "-" + String.format("%02d", month);

        for (Employee emp : employees) {
            try {
                payslipService.generatePayslip(emp.getId(), monthYear);
            } catch (Exception e) {
                log.warn("Could not generate payslip for employee {}: {}", emp.getId(), e.getMessage());
            }
        }

        return generatedPayrolls;
    }

    @Transactional
    public PayrollDTO sendForApproval(Long payrollId, String approvedBy) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));

        if (payroll.getStatus() != Payroll.PayrollStatus.PENDING) {
            throw new RuntimeException("Payroll must be in PENDING status to send for approval");
        }

        payroll.setStatus(Payroll.PayrollStatus.PENDING_APPROVAL);
        payroll = payrollRepository.save(payroll);

        generatePayslipWithStatus(payroll, "DRAFT", approvedBy);

        // Send email notification to all Accountants asynchronously
        final Payroll finalPayroll = payroll;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                List<User> accountants = userRepository.findByRole(User.Role.ROLE_ACCOUNTANT);
                if (accountants != null && !accountants.isEmpty()) {
                    String monthName = java.time.Month.of(finalPayroll.getMonth()).getDisplayName(
                            java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
                    String employeeName = finalPayroll.getEmployee().getFirstName() + " " + finalPayroll.getEmployee().getLastName();
                    String subject = "Payroll Pending Approval: " + employeeName + " - " + monthName + " " + finalPayroll.getYear();
                    String body = String.format(
                            "Dear Accountant,\n\n" +
                            "A new payroll record has been submitted and is pending your approval.\n\n" +
                            "Employee: %s\n" +
                            "Month/Year: %s %d\n" +
                            "Net Salary: %s\n\n" +
                            "Please login to the HRM system to review and approve/reject the payroll.\n\n" +
                            "Best Regards,\n" +
                            "HRM System Support",
                            employeeName, monthName, finalPayroll.getYear(), 
                            "Rs. " + String.format("%,.2f", finalPayroll.getNetSalary())
                    );

                    for (User accountant : accountants) {
                        if (accountant.getEmail() != null && !accountant.getEmail().trim().isEmpty()) {
                            emailUtil.sendSimpleEmail(accountant.getEmail(), subject, body);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to send email notification to accountant: {}", e.getMessage());
            }
        });

        return convertToDTO(payroll);
    }

    @Transactional
    public PayrollDTO approveByAccountant(Long payrollId, String accountantName) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found with id: " + payrollId));

        log.info("Approving payroll {} - current status: {}, expected: {}", 
                 payrollId, payroll.getStatus(), Payroll.PayrollStatus.PENDING_APPROVAL);
        
        if (!Payroll.PayrollStatus.PENDING_APPROVAL.equals(payroll.getStatus())) {
            throw new RuntimeException("Payroll must be in PENDING_APPROVAL status to approve. Current status: " 
                + payroll.getStatus() + " (ID: " + payrollId + ")");
        }

        payroll.setStatus(Payroll.PayrollStatus.APPROVED);
        payroll.setAccountantApproved(true);
        payroll.setFinalApprovalDate(LocalDate.now());
        payroll = payrollRepository.save(payroll);

        try {
            generatePayslipWithStatus(payroll, "APPROVED", accountantName);
        } catch (Exception e) {
            log.warn("Could not update payslip status, but payroll was approved: {}", e.getMessage());
        }

        // Send approval notification email to employee asynchronously
        final Payroll approvedPayroll = payroll;
        if (approvedPayroll.getEmployee() != null && approvedPayroll.getEmployee().getEmail() != null && !approvedPayroll.getEmployee().getEmail().isEmpty()) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    String subject = "Your Salary Slip has been Approved - " + String.format("%02d", approvedPayroll.getMonth()) + "/" + approvedPayroll.getYear();
                    String htmlBody = String.format(
                            "<html><body style='font-family: Arial, sans-serif;'>" +
                            "<h2 style='color: #4CAF50;'>Payroll Approved</h2>" +
                            "<p>Dear <strong>%s %s</strong>,</p>" +
                            "<p>Your payroll for the month of <strong>%02d/%d</strong> has been successfully approved by the accountant.</p>" +
                            "<p>Your net salary of <strong>₹%,.2f</strong> will be processed shortly.</p>" +
                            "<br>" +
                            "<p>You can view and download your detailed payslip by logging into the HRM Portal.</p>" +
                            "<br>" +
                            "<p>Best Regards,</p>" +
                            "<p><strong>HR Department</strong></p>" +
                            "<p style='color: #888; font-size: 12px;'>This is an automated email. Please do not reply.</p>" +
                            "</body></html>",
                            approvedPayroll.getEmployee().getFirstName(), approvedPayroll.getEmployee().getLastName(),
                            approvedPayroll.getMonth(), approvedPayroll.getYear(), approvedPayroll.getNetSalary()
                    );
                    emailUtil.sendHtmlEmail(approvedPayroll.getEmployee().getEmail(), subject, htmlBody);
                    log.info("Approval email sent to employee: {}", approvedPayroll.getEmployee().getEmail());
                } catch (Exception e) {
                    log.warn("Failed to send approval email to employee {}: {}", approvedPayroll.getEmployee().getEmail(), e.getMessage());
                }
            });
        }

        log.info("Payroll {} approved successfully", payrollId);
        return convertToDTO(payroll);
    }

    @Transactional
    public PayrollDTO approveByDirector(Long payrollId, String approvedBy) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found with id: " + payrollId));

        if (payroll.getStatus() == Payroll.PayrollStatus.PENDING) {
            throw new RuntimeException("Payroll must be sent for approval first");
        }

        payroll.setDirectorApproved(true);
        payroll.setFinalApprovalDate(LocalDate.now());

        if (Boolean.TRUE.equals(payroll.getAccountantApproved())) {
            payroll.setStatus(Payroll.PayrollStatus.PAID);
            payroll.setPaymentDate(LocalDate.now());
            log.info("Payroll {} fully approved by both accountant and admin — marked PAID", payrollId);
        } else {
            log.info("Payroll {} admin-approved. Awaiting accountant approval.", payrollId);
        }

        payroll = payrollRepository.save(payroll);

        try {
            generatePayslipWithStatus(payroll, payroll.getStatus() == Payroll.PayrollStatus.PAID ? "SENT" : "APPROVED", approvedBy);
        } catch (Exception e) {
            log.warn("Could not update payslip status after director approval: {}", e.getMessage());
        }

        return convertToDTO(payroll);
    }

    @Transactional
    public PayrollDTO rejectByDirector(Long payrollId, String reason) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found with id: " + payrollId));

        payroll.setDirectorApproved(false);
        payroll.setStatus(Payroll.PayrollStatus.PENDING_APPROVAL);
        payroll = payrollRepository.save(payroll);

        log.info("Payroll {} rejected by admin/director. Reason: {}", payrollId, reason);
        return convertToDTO(payroll);
    }

    @Transactional
    public PayrollDTO rejectByAccountant(Long payrollId, String reason) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));

        if (payroll.getStatus() != Payroll.PayrollStatus.PENDING_APPROVAL) {
            throw new RuntimeException("Payroll must be in PENDING_APPROVAL status to reject");
        }

        payroll.setStatus(Payroll.PayrollStatus.PENDING);
        payroll.setAccountantApproved(false);
        payroll = payrollRepository.save(payroll);

        return convertToDTO(payroll);
    }

    @Transactional
    public PayrollDTO markAsPaid(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));

        if (payroll.getStatus() != Payroll.PayrollStatus.APPROVED) {
            throw new RuntimeException("Payroll must be APPROVED by accountant before payment");
        }

        payroll.setStatus(Payroll.PayrollStatus.PAID);
        payroll.setDirectorApproved(true);
        payroll.setFinalApprovalDate(LocalDate.now());
        payroll.setPaymentDate(LocalDate.now());
        payroll = payrollRepository.save(payroll);

        updatePayslipStatusToPaid(payroll);

        // Lock the payroll for this month and year if not already locked
        if (!payrollLockService.isPayrollLocked(payroll.getMonth(), payroll.getYear())) {
            payrollLockService.lockPayroll(payroll.getMonth(), payroll.getYear(), 
                "Locked automatically after marking payroll ID " + payrollId + " as PAID");
        }

        return convertToDTO(payroll);
    }

    private void generatePayslipWithStatus(Payroll payroll, String status, String approvedBy) {
        try {
            String monthYear = payroll.getYear() + "-" + String.format("%02d", payroll.getMonth());
            
            payslipService.generatePayslip(payroll.getEmployee().getId(), monthYear);
            
            payslipService.updatePayslipStatusByEmployeeAndMonth(
                payroll.getEmployee().getId(), 
                monthYear, 
                status, 
                approvedBy, 
                "Approved by HR and Accountants"
            );
            
            log.info("Updated payslip status to {} for employee {} month {}", status, payroll.getEmployee().getId(), monthYear);

            if ("SENT".equals(status)) {
                payslipService.sendPaidPayslipEmail(payroll.getEmployee().getId(), monthYear);
            }
        } catch (Exception e) {
            log.warn("Could not update payslip status: {}", e.getMessage());
        }
    }

    private void updatePayslipStatusToPaid(Payroll payroll) {
        try {
            String monthYear = payroll.getYear() + "-" + String.format("%02d", payroll.getMonth());
            
            payslipService.generatePayslip(payroll.getEmployee().getId(), monthYear);
            
            payslipService.updatePayslipStatusByEmployeeAndMonth(
                payroll.getEmployee().getId(), 
                monthYear, 
                "SENT", 
                "HR", 
                "Payment processed. Approved by HR and Accountants"
            );
            
            log.info("Updated payslip status to SENT for employee {} month {}", payroll.getEmployee().getId(), monthYear);

            // Automatically email final payslip PDF to employee
            payslipService.sendPaidPayslipEmail(payroll.getEmployee().getId(), monthYear);
        } catch (Exception e) {
            log.warn("Could not update payslip to paid status: {}", e.getMessage());
        }
    }

    public PayrollDTO processPayroll(Long payrollId) {
        return sendForApproval(payrollId, "HR");
    }

    @Transactional
    public PayrollDTO markAsUnpaid(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));

        payroll.setStatus(Payroll.PayrollStatus.APPROVED);
        payroll.setPaymentDate(null);

        return convertToDTO(payroll);
    }

    public List<PayrollDTO> getPayrollByEmployee(Long employeeId) {
        Employee employee = employeeRepository.findByIdentifier(employeeId).orElse(null);
        if (employee == null) {
            return new java.util.ArrayList<>();
        }
        if (isSystemUser(employee)) {
            return new java.util.ArrayList<>();
        }
        return payrollRepository.findByEmployeeId(employee.getId())
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PayrollDTO> getPayrollByMonth(Integer month, Integer year) {
        return payrollRepository.findByMonthAndYear(month, year)
                .stream()
                .filter(p -> p.getEmployee() != null && !isSystemUser(p.getEmployee()))
                .sorted((a, b) -> a.getEmployee().getFirstName().compareToIgnoreCase(b.getEmployee().getFirstName()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<PayrollDTO> getAllPayrolls() {
        return payrollRepository.findAll()
                .stream()
                .filter(p -> p.getEmployee() != null &&
                        p.getEmployee().getStatus() == Employee.EmployeeStatus.ACTIVE &&
                        !isSystemUser(p.getEmployee()))
                .sorted((p1, p2) -> {
                    int yearCompare = p2.getYear().compareTo(p1.getYear());
                    if (yearCompare != 0) return yearCompare;
                    return p2.getMonth().compareTo(p1.getMonth());
                })
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PayrollDTO getPayrollById(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found with id: " + payrollId));
        if (payroll.getEmployee() != null && isSystemUser(payroll.getEmployee())) {
            // allow
        }
        return convertToDTO(payroll);
    }

    @Transactional
    public PayrollDTO updatePayroll(Long payrollId, PayrollDTO payrollDTO) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found with id: " + payrollId));

        if (payrollLockService.isPayrollLocked(payroll.getMonth(), payroll.getYear())) {
            throw new RuntimeException("Cannot update payroll: Payroll is locked for " + payroll.getYear() + "-" + payroll.getMonth());
        }

        payroll.setBasicSalary(payrollDTO.getBasicSalary());
        payroll.setHra(payrollDTO.getHra());
        payroll.setOtherAllowances(payrollDTO.getOtherAllowances());
        payroll.setProvidentFund(payrollDTO.getProvidentFund());
        payroll.setTax(payrollDTO.getTax());
        payroll.setInsurance(payrollDTO.getInsurance());
        payroll.setOtherDeductions(payrollDTO.getOtherDeductions());

        BigDecimal grossSalary = payrollDTO.getBasicSalary()
                .add(payrollDTO.getHra())
                .add(payrollDTO.getOtherAllowances());
        payroll.setGrossSalary(grossSalary);

        Employee employee = payroll.getEmployee();
        BigDecimal pfAmt = payroll.getProvidentFund() != null ? payroll.getProvidentFund() : BigDecimal.ZERO;
        BigDecimal taxAmt = payroll.getTax() != null ? payroll.getTax() : BigDecimal.ZERO;
        BigDecimal insAmt = payroll.getInsurance() != null ? payroll.getInsurance() : BigDecimal.ZERO;
        BigDecimal otherDed = payroll.getOtherDeductions() != null ? payroll.getOtherDeductions() : BigDecimal.ZERO;

        BigDecimal esicAmt = employee.getEsic() != null ? employee.getEsic() : BigDecimal.ZERO;
        BigDecimal profTaxAmt = employee.getProfessionalTax() != null ? employee.getProfessionalTax() : BigDecimal.ZERO;
        BigDecimal tdsAmt = employee.getTds() != null ? employee.getTds() : BigDecimal.ZERO;
        BigDecimal loanAmt = employee.getLoanDeduction() != null ? employee.getLoanDeduction() : BigDecimal.ZERO;
        BigDecimal lwfAmt = employee.getLwf() != null ? employee.getLwf() : BigDecimal.ZERO;

        BigDecimal unpaidDed = payroll.getUnpaidLeaveDeduction() != null ? payroll.getUnpaidLeaveDeduction() : BigDecimal.ZERO;
        BigDecimal absentDed = payroll.getAbsentDeduction() != null ? payroll.getAbsentDeduction() : BigDecimal.ZERO;

        BigDecimal totalDeductions = pfAmt.add(taxAmt).add(insAmt).add(otherDed)
                .add(esicAmt).add(profTaxAmt).add(tdsAmt).add(loanAmt).add(lwfAmt)
                .add(unpaidDed).add(absentDed);
        payroll.setTotalDeductions(totalDeductions);

        BigDecimal netSalary = grossSalary.subtract(totalDeductions);
        if (netSalary.compareTo(BigDecimal.ZERO) < 0) {
            netSalary = BigDecimal.ZERO;
        }
        payroll.setNetSalary(netSalary);

        payroll = payrollRepository.save(payroll);

        try {
            String monthYear = payroll.getYear() + "-" + String.format("%02d", payroll.getMonth());
            payslipService.updatePayslipFromPayroll(payroll.getEmployee().getId(), monthYear, convertToDTO(payroll));
        } catch (Exception e) {
            log.warn("Could not update payslip for payroll {}: {}", payrollId, e.getMessage());
        }

        return convertToDTO(payroll);
    }

    @Transactional
    public void deleteAllPayrolls() {
        List<Payroll> allPayrolls = payrollRepository.findAll();
        for (Payroll p : allPayrolls) {
            if (payrollLockService.isPayrollLocked(p.getMonth(), p.getYear())) {
                throw new RuntimeException("Cannot delete payroll records: Payroll is locked for " + p.getYear() + "-" + p.getMonth());
            }
        }
        log.info("Deleting unlocked payslips before clearing payrolls");
        payslipService.deleteUnlockedPayslips();
        
        log.info("Deleting all payroll records");
        payrollRepository.deleteAll();
        log.info("All payroll records deleted successfully");
    }

    private boolean isSystemUser(Employee emp) {
        if (emp.getDepartment() == null || emp.getDepartment().getName() == null) return false;
        String dept = emp.getDepartment().getName().toLowerCase();
        return dept.contains("hr") || dept.contains("director") || 
               dept.contains("leave") || dept.contains("accountant");
    }

    private PayrollDTO convertToDTO(Payroll payroll) {
        Employee employee = payroll.getEmployee();

        double presentDays = payroll.getPresentDays() != null ? payroll.getPresentDays() : 0.0;
        double absentDays = payroll.getAbsentDays() != null ? payroll.getAbsentDays() : 0.0;
        double paidLeaveDays = payroll.getPaidLeaveDays() != null ? payroll.getPaidLeaveDays() : 0.0;
        double unpaidLeaveDays = payroll.getUnpaidLeaveDays() != null ? payroll.getUnpaidLeaveDays() : 0.0;
        double leaveDays = paidLeaveDays + unpaidLeaveDays;
        int halfDays = 0;

        BigDecimal pfAmt = payroll.getProvidentFund() != null ? payroll.getProvidentFund() : BigDecimal.ZERO;
        BigDecimal esicAmt = employee.getEsic() != null ? employee.getEsic() : BigDecimal.ZERO;
        BigDecimal profTaxAmt = employee.getProfessionalTax() != null ? employee.getProfessionalTax() : BigDecimal.ZERO;
        BigDecimal tdsAmt = employee.getTds() != null ? employee.getTds() : BigDecimal.ZERO;
        BigDecimal employeeTax = employee.getTax() != null ? employee.getTax() : tdsAmt;
        BigDecimal taxAmt = payroll.getTax() != null ? payroll.getTax() : employeeTax;
        BigDecimal loanAmt = employee.getLoanDeduction() != null ? employee.getLoanDeduction() : BigDecimal.ZERO;
        BigDecimal lwfAmt = employee.getLwf() != null ? employee.getLwf() : BigDecimal.ZERO;
        BigDecimal insAmt = payroll.getInsurance() != null ? payroll.getInsurance() : BigDecimal.ZERO;
        BigDecimal otherDeductionsAmt = payroll.getOtherDeductions() != null ? payroll.getOtherDeductions() : BigDecimal.ZERO;

        BigDecimal freshTotalDeductions = payroll.getTotalDeductions() != null ? payroll.getTotalDeductions() : BigDecimal.ZERO;
        BigDecimal grossForNet = payroll.getGrossSalary() != null ? payroll.getGrossSalary() : BigDecimal.ZERO;
        BigDecimal freshNetSalary = grossForNet.subtract(freshTotalDeductions);
        if (freshNetSalary.compareTo(BigDecimal.ZERO) < 0) {
            freshNetSalary = BigDecimal.ZERO;
        }

        BigDecimal freshUnpaidLeaveDeduction = payroll.getUnpaidLeaveDeduction() != null ? payroll.getUnpaidLeaveDeduction() : BigDecimal.ZERO;
        BigDecimal freshAbsentDeduction = payroll.getAbsentDeduction() != null ? payroll.getAbsentDeduction() : BigDecimal.ZERO;
        double absentLeaveDeduction = freshAbsentDeduction.doubleValue();

        Boolean inProbation = !attendanceEngine.isProbationCompleted(employee, YearMonth.of(payroll.getYear(), payroll.getMonth()).atEndOfMonth());
        String probationStatus = inProbation ? "In Progress" : "Completed";
        Integer probationMonths = employee.getProbationPeriodMonths() != null ? employee.getProbationPeriodMonths() : 3;
        
        LocalDate probationCompletionDate = null;
        if (employee.getJoiningDate() != null) {
            probationCompletionDate = employee.getJoiningDate().plusMonths(probationMonths);
        }

        PayrollDTO.LeaveBalanceInfo leaveBalanceInfo = PayrollDTO.LeaveBalanceInfo.builder()
                .totalEarnedLeaves(0.0)
                .usedLeaves(paidLeaveDays)
                .availableLeaves(0.0)
                .carriedForwardLeaves(0.0)
                .unpaidLeaves(unpaidLeaveDays)
                .cycle(payroll.getMonth() <= 6 ? 1 : 2)
                .build();

        return PayrollDTO.builder()
                .id(payroll.getId())
                .employeeId(employee.getId())
                .employeeName(employee.getFirstName() + " " + employee.getLastName())
                .department(employee.getDepartment() != null ? employee.getDepartment().getName() : null)
                .designation(employee.getDesignation())
                .month(payroll.getMonth())
                .year(payroll.getYear())
                .basicSalary(payroll.getBasicSalary())
                .hra(payroll.getHra())
                .ta(payroll.getTa())
                .specialAllowance(employee.getSpecialAllowance())
                .bonus(employee.getBonus())
                .incentive(employee.getIncentive())
                .otherAllowances(payroll.getOtherAllowances())
                .providentFund(payroll.getProvidentFund())
                .esic(employee.getEsic())
                .professionalTax(employee.getProfessionalTax())
                .tds(taxAmt)
                .loanDeduction(employee.getLoanDeduction())
                .lwf(employee.getLwf())
                .tax(taxAmt)
                .insurance(insAmt)
                .otherDeductions(otherDeductionsAmt)
                .grossSalary(payroll.getGrossSalary())
                .totalDeductions(freshTotalDeductions)
                .netSalary(freshNetSalary)
                .paymentDate(payroll.getPaymentDate())
                .status(payroll.getStatus().name())
                .accountantApproved(payroll.getAccountantApproved())
                .directorApproved(payroll.getDirectorApproved())
                .presentDays(presentDays)
                .absentDays(absentDays)
                .leaveDays((int) leaveDays)
                .paidLeaveDays(paidLeaveDays)
                .unpaidLeaveDays(unpaidLeaveDays)
                .halfDays(halfDays)
                .absentLeaveDeduction(absentLeaveDeduction)
                .unpaidLeaveDeduction(freshUnpaidLeaveDeduction)
                .absentPenaltyDeduction(freshAbsentDeduction)
                .leaveBalance(leaveBalanceInfo)
                .inProbation(inProbation)
                .probationStatus(probationStatus)
                .probationMonths(probationMonths)
                .joinDate(employee.getJoiningDate())
                .probationCompletionDate(probationCompletionDate)
                .build();
    }

    public PayslipDTO generatePayslipForEmployee(Long employeeId, String monthYear) {
        return payslipService.generatePayslip(employeeId, monthYear);
    }

    /**
     * Unlock a payroll record and reset its status/approvals
     */
    @Transactional
    public PayrollDTO unlockPayroll(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));
        
        log.info("Unlocking payroll {} for employee {} for month {}/{}", payrollId, payroll.getEmployee().getId(), payroll.getMonth(), payroll.getYear());

        // Revert status to PENDING and clear approvals
        payroll.setStatus(Payroll.PayrollStatus.PENDING);
        payroll.setAccountantApproved(false);
        payroll.setDirectorApproved(false);
        payroll.setPayslipGenerated(false);
        payroll.setFinalApprovalDate(null);
        payroll = payrollRepository.save(payroll);

        // Delete any dual approval records
        payrollApprovalRepository.deleteByPayrollId(payrollId);

        // Reset/Unlock matching payslip as well
        String monthYear = payroll.getYear() + "-" + String.format("%02d", payroll.getMonth());
        try {
            payslipService.unlockPayslip(payroll.getEmployee().getId(), monthYear);
        } catch (Exception e) {
            log.warn("Could not unlock payslip for employee {} month {}: {}", payroll.getEmployee().getId(), monthYear, e.getMessage());
        }

        return convertToDTO(payroll);
    }
}
