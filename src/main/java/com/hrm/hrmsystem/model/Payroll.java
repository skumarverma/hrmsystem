package com.hrm.hrmsystem.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "payroll",
    uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "month", "year"})
)
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    private Integer month;
    private Integer year;

    @Column(precision = 12, scale = 2)
    private BigDecimal basicSalary;

    @Column(precision = 12, scale = 2)
    private BigDecimal hra;



    @Column(precision = 12, scale = 2)
    private BigDecimal ta;

    @Column(precision = 12, scale = 2)
    private BigDecimal otherAllowances;

    @Column(precision = 12, scale = 2)
    private BigDecimal providentFund;

    @Column(precision = 12, scale = 2)
    private BigDecimal tax;

    @Column(precision = 12, scale = 2)
    private BigDecimal insurance;

    @Column(precision = 12, scale = 2)
    private BigDecimal otherDeductions;

    // Attendance summary fields - store calculated values for payroll table display
    private Double presentDays;
    private Double absentDays;
    private Double paidLeaveDays;
    private Double unpaidLeaveDays;

    @Column(precision = 12, scale = 2)
    private BigDecimal grossSalary;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalDeductions;

    @Column(precision = 12, scale = 2)
    private BigDecimal netSalary;

    @Column(precision = 12, scale = 2)
    private BigDecimal unpaidLeaveDeduction;

    @Column(precision = 12, scale = 2)
    private BigDecimal absentDeduction;

    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PayrollStatus status;

    // Dual approval fields
    private Boolean accountantApproved = false;
    private Boolean directorApproved = false;
    private Boolean payslipGenerated = false;
    private LocalDate finalApprovalDate;

    public enum PayrollStatus {
        PENDING,           // Initial state when HR generates payroll
        PENDING_APPROVAL,  // After HR clicks "Send for Approval"
        APPROVED,          // After Accountant approves
        PAID,              // After payment is processed
        @Deprecated
        PROCESSED          // Legacy status - treated as PENDING_APPROVAL
    }

    // Default Constructor
    public Payroll() {}

    // All Args Constructor
    public Payroll(Long id, Employee employee, Integer month, Integer year,
                   BigDecimal basicSalary, BigDecimal hra, BigDecimal ta, BigDecimal otherAllowances,
                   BigDecimal providentFund, BigDecimal tax, BigDecimal insurance, BigDecimal otherDeductions,
                   BigDecimal grossSalary, BigDecimal totalDeductions, BigDecimal netSalary,
                   LocalDate paymentDate, PayrollStatus status) {
        this.id = id;
        this.employee = employee;
        this.month = month;
        this.year = year;
        this.basicSalary = basicSalary;
        this.hra = hra;
        this.ta = ta;
        this.otherAllowances = otherAllowances;
        this.providentFund = providentFund;
        this.tax = tax;
        this.insurance = insurance;
        this.otherDeductions = otherDeductions;
        this.grossSalary = grossSalary;
        this.totalDeductions = totalDeductions;
        this.netSalary = netSalary;
        this.paymentDate = paymentDate;
        this.status = status;
    }

    // Getters
    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public Integer getMonth() { return month; }
    public Integer getYear() { return year; }
    public BigDecimal getBasicSalary() { return basicSalary; }
    public BigDecimal getHra() { return hra; }
    public BigDecimal getTa() { return ta; }
    public BigDecimal getOtherAllowances() { return otherAllowances; }
    public BigDecimal getProvidentFund() { return providentFund; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getInsurance() { return insurance; }
    public BigDecimal getOtherDeductions() { return otherDeductions; }
    public Double getPresentDays() { return presentDays; }
    public Double getAbsentDays() { return absentDays; }
    public Double getPaidLeaveDays() { return paidLeaveDays; }
    public Double getUnpaidLeaveDays() { return unpaidLeaveDays; }
    public BigDecimal getGrossSalary() { return grossSalary; }
    public BigDecimal getTotalDeductions() { return totalDeductions; }
    public BigDecimal getNetSalary() { return netSalary; }
    public BigDecimal getUnpaidLeaveDeduction() { return unpaidLeaveDeduction; }
    public BigDecimal getAbsentDeduction() { return absentDeduction; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public PayrollStatus getStatus() { return status; }
    public Boolean getAccountantApproved() { return accountantApproved; }
    public Boolean getDirectorApproved() { return directorApproved; }
    public Boolean getPayslipGenerated() { return payslipGenerated; }
    public LocalDate getFinalApprovalDate() { return finalApprovalDate; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public void setMonth(Integer month) { this.month = month; }
    public void setYear(Integer year) { this.year = year; }
    public void setBasicSalary(BigDecimal basicSalary) { this.basicSalary = basicSalary; }
    public void setHra(BigDecimal hra) { this.hra = hra; }
    public void setTa(BigDecimal ta) { this.ta = ta; }
    public void setOtherAllowances(BigDecimal otherAllowances) { this.otherAllowances = otherAllowances; }
    public void setProvidentFund(BigDecimal providentFund) { this.providentFund = providentFund; }
    public void setTax(BigDecimal tax) { this.tax = tax; }
    public void setInsurance(BigDecimal insurance) { this.insurance = insurance; }
    public void setOtherDeductions(BigDecimal otherDeductions) { this.otherDeductions = otherDeductions; }
    public void setPresentDays(Double presentDays) { this.presentDays = presentDays; }
    public void setAbsentDays(Double absentDays) { this.absentDays = absentDays; }
    public void setPaidLeaveDays(Double paidLeaveDays) { this.paidLeaveDays = paidLeaveDays; }
    public void setUnpaidLeaveDays(Double unpaidLeaveDays) { this.unpaidLeaveDays = unpaidLeaveDays; }
    public void setGrossSalary(BigDecimal grossSalary) { this.grossSalary = grossSalary; }
    public void setTotalDeductions(BigDecimal totalDeductions) { this.totalDeductions = totalDeductions; }
    public void setNetSalary(BigDecimal netSalary) { this.netSalary = netSalary; }
    public void setUnpaidLeaveDeduction(BigDecimal unpaidLeaveDeduction) { this.unpaidLeaveDeduction = unpaidLeaveDeduction; }
    public void setAbsentDeduction(BigDecimal absentDeduction) { this.absentDeduction = absentDeduction; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public void setStatus(PayrollStatus status) { this.status = status; }
    public void setAccountantApproved(Boolean accountantApproved) { this.accountantApproved = accountantApproved; }
    public void setDirectorApproved(Boolean directorApproved) { this.directorApproved = directorApproved; }
    public void setPayslipGenerated(Boolean payslipGenerated) { this.payslipGenerated = payslipGenerated; }
    public void setFinalApprovalDate(LocalDate finalApprovalDate) { this.finalApprovalDate = finalApprovalDate; }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private Employee employee;
        private Integer month;
        private Integer year;
        private BigDecimal basicSalary;
        private BigDecimal hra;
        private BigDecimal ta;
        private BigDecimal otherAllowances;
        private BigDecimal providentFund;
        private BigDecimal tax;
        private BigDecimal insurance;
        private BigDecimal otherDeductions;
        private BigDecimal grossSalary;
        private BigDecimal totalDeductions;
        private BigDecimal netSalary;
        private BigDecimal unpaidLeaveDeduction;
        private BigDecimal absentDeduction;
        private LocalDate paymentDate;
        private PayrollStatus status;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder employee(Employee employee) { this.employee = employee; return this; }
        public Builder month(Integer month) { this.month = month; return this; }
        public Builder year(Integer year) { this.year = year; return this; }
        public Builder basicSalary(BigDecimal basicSalary) { this.basicSalary = basicSalary; return this; }
        public Builder hra(BigDecimal hra) { this.hra = hra; return this; }

        public Builder ta(BigDecimal ta) { this.ta = ta; return this; }
        public Builder otherAllowances(BigDecimal otherAllowances) { this.otherAllowances = otherAllowances; return this; }
        public Builder providentFund(BigDecimal providentFund) { this.providentFund = providentFund; return this; }
        public Builder tax(BigDecimal tax) { this.tax = tax; return this; }
        public Builder insurance(BigDecimal insurance) { this.insurance = insurance; return this; }
        public Builder otherDeductions(BigDecimal otherDeductions) { this.otherDeductions = otherDeductions; return this; }
        public Builder grossSalary(BigDecimal grossSalary) { this.grossSalary = grossSalary; return this; }
        public Builder totalDeductions(BigDecimal totalDeductions) { this.totalDeductions = totalDeductions; return this; }
        public Builder netSalary(BigDecimal netSalary) { this.netSalary = netSalary; return this; }
        public Builder unpaidLeaveDeduction(BigDecimal unpaidLeaveDeduction) { this.unpaidLeaveDeduction = unpaidLeaveDeduction; return this; }
        public Builder absentDeduction(BigDecimal absentDeduction) { this.absentDeduction = absentDeduction; return this; }
        public Builder paymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; return this; }
        public Builder status(PayrollStatus status) { this.status = status; return this; }

        public Payroll build() {
            Payroll payroll = new Payroll(id, employee, month, year, basicSalary, hra, ta, otherAllowances,
                    providentFund, tax, insurance, otherDeductions, grossSalary, totalDeductions, netSalary, paymentDate, status);
            payroll.setUnpaidLeaveDeduction(unpaidLeaveDeduction);
            payroll.setAbsentDeduction(absentDeduction);
            return payroll;
        }
    }
}
