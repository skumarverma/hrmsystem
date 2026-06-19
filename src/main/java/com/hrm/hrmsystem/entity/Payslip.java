package com.hrm.hrmsystem.entity;

import com.hrm.hrmsystem.model.Employee;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payslips")
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "month_year", nullable = false)
    private String monthYear;

    @Column(name = "salary_month")
    private Integer salaryMonth;

    @Column(name = "salary_year")
    private Integer salaryYear;

    @Column(name = "basic_salary", precision = 10, scale = 2)
    private BigDecimal basicSalary;



    @Column(name = "hra", precision = 10, scale = 2)
    private BigDecimal hra;

    @Column(name = "special_allowance", precision = 10, scale = 2)
    private BigDecimal specialAllowance;

    @Column(name = "bonus", precision = 10, scale = 2)
    private BigDecimal bonus;

    @Column(name = "incentive", precision = 10, scale = 2)
    private BigDecimal incentive;

    @Column(name = "other_allowance", precision = 10, scale = 2)
    private BigDecimal otherAllowance;

    @Column(name = "gross_salary", precision = 10, scale = 2)
    private BigDecimal grossSalary;

    @Column(name = "pf", precision = 10, scale = 2)
    private BigDecimal pf;

    @Column(name = "esi", precision = 10, scale = 2)
    private BigDecimal esi;

    @Column(name = "esic", precision = 10, scale = 2)
    private BigDecimal esic;

    @Column(name = "professional_tax", precision = 10, scale = 2)
    private BigDecimal professionalTax;

    @Column(name = "tds", precision = 10, scale = 2)
    private BigDecimal tds;

    @Column(name = "income_tax", precision = 10, scale = 2)
    private BigDecimal incomeTax;

    @Column(name = "loan_deduction", precision = 10, scale = 2)
    private BigDecimal loanDeduction;

    @Column(name = "lwf", precision = 10, scale = 2)
    private BigDecimal lwf;

    @Column(name = "other_deduction", precision = 10, scale = 2)
    private BigDecimal otherDeduction;

    @Column(name = "insurance", precision = 10, scale = 2)
    private BigDecimal insurance;

    @Column(name = "total_deduction", precision = 10, scale = 2)
    private BigDecimal totalDeduction;

    @Column(name = "net_salary", precision = 10, scale = 2)
    private BigDecimal netSalary;

    @Column(name = "total_days")
    private Integer totalDays = 30;

    @Column(name = "working_days")
    private Integer workingDays = 30;

    @Column(name = "present_days")
    private Double presentDays;

    @Column(name = "absent_days")
    private Double absentDays;

    @Column(name = "leave_days")
    private Double leaveDays;
    
    @Column(name = "paid_leave_days")
    private Double paidLeaveDays;
    
    @Column(name = "unpaid_leave_days")
    private Double unpaidLeaveDays;

    @Column(name = "half_days")
    private Integer halfDays;

    @Column(name = "absent_leave_deduction", precision = 10, scale = 2)
    private BigDecimal absentLeaveDeduction;

    @Column(name = "unpaid_leave_deduction", precision = 10, scale = 2)
    private BigDecimal unpaidLeaveDeduction;

    @ElementCollection
    @CollectionTable(name = "payslip_absent_dates", joinColumns = @JoinColumn(name = "payslip_id"))
    @Column(name = "absent_date")
    private java.util.List<String> absentDates;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PayslipStatus status = PayslipStatus.DRAFT;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(name = "generated_date")
    private LocalDate generatedDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(name = "approved_date")
    private LocalDate approvedDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(name = "sent_date")
    private LocalDate sentDate;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "pdf_generated")
    private Boolean pdfGenerated = false;

    @Column(name = "pdf_file_path")
    private String pdfFilePath;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum PayslipStatus {
        DRAFT, GENERATED, APPROVED, SENT, REJECTED
    }

    // Default Constructor
    public Payslip() {}

    // Getters
    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public String getMonthYear() { return monthYear; }
    public Integer getSalaryMonth() { return salaryMonth; }
    public Integer getSalaryYear() { return salaryYear; }
    public BigDecimal getBasicSalary() { return basicSalary; }
    public BigDecimal getHra() { return hra; }
    public BigDecimal getSpecialAllowance() { return specialAllowance; }
    public BigDecimal getBonus() { return bonus; }
    public BigDecimal getIncentive() { return incentive; }
    public BigDecimal getOtherAllowance() { return otherAllowance; }
    public BigDecimal getGrossSalary() { return grossSalary; }
    public BigDecimal getPf() { return pf; }
    public BigDecimal getEsi() { return esi; }
    public BigDecimal getEsic() { return esic; }
    public BigDecimal getProfessionalTax() { return professionalTax; }
    public BigDecimal getTds() { return tds; }
    public BigDecimal getIncomeTax() { return incomeTax; }
    public BigDecimal getLoanDeduction() { return loanDeduction; }
    public BigDecimal getLwf() { return lwf; }
    public BigDecimal getOtherDeduction() { return otherDeduction; }
    public BigDecimal getInsurance() { return insurance; }
    public BigDecimal getTotalDeduction() { return totalDeduction; }
    public BigDecimal getNetSalary() { return netSalary; }
    public Integer getTotalDays() { return totalDays; }
    public Integer getWorkingDays() { return workingDays; }
    public Double getPresentDays() { return presentDays; }
    public Double getAbsentDays() { return absentDays; }
    public Double getLeaveDays() { return leaveDays; }
    public Double getPaidLeaveDays() { return paidLeaveDays; }
    public Double getUnpaidLeaveDays() { return unpaidLeaveDays; }
    public Integer getHalfDays() { return halfDays; }
    public BigDecimal getAbsentLeaveDeduction() { return absentLeaveDeduction; }

    public BigDecimal getUnpaidLeaveDeduction() { return unpaidLeaveDeduction; }

    public java.util.List<String> getAbsentDates() { return absentDates; }
    public PayslipStatus getStatus() { return status; }
    public LocalDate getGeneratedDate() { return generatedDate; }
    public LocalDate getApprovedDate() { return approvedDate; }
    public LocalDate getSentDate() { return sentDate; }
    public String getApprovedBy() { return approvedBy; }
    public String getRemarks() { return remarks; }
    public Boolean getPdfGenerated() { return pdfGenerated; }
    public String getPdfFilePath() { return pdfFilePath; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public void setMonthYear(String monthYear) { this.monthYear = monthYear; }
    public void setSalaryMonth(Integer salaryMonth) { this.salaryMonth = salaryMonth; }
    public void setSalaryYear(Integer salaryYear) { this.salaryYear = salaryYear; }
    public void setBasicSalary(BigDecimal basicSalary) { this.basicSalary = basicSalary; }
    public void setHra(BigDecimal hra) { this.hra = hra; }
    public void setSpecialAllowance(BigDecimal specialAllowance) { this.specialAllowance = specialAllowance; }
    public void setBonus(BigDecimal bonus) { this.bonus = bonus; }
    public void setIncentive(BigDecimal incentive) { this.incentive = incentive; }
    public void setOtherAllowance(BigDecimal otherAllowance) { this.otherAllowance = otherAllowance; }
    public void setGrossSalary(BigDecimal grossSalary) { this.grossSalary = grossSalary; }
    public void setPf(BigDecimal pf) { this.pf = pf; }
    public void setEsi(BigDecimal esi) { this.esi = esi; }
    public void setEsic(BigDecimal esic) { this.esic = esic; }
    public void setProfessionalTax(BigDecimal professionalTax) { this.professionalTax = professionalTax; }
    public void setTds(BigDecimal tds) { this.tds = tds; }
    public void setIncomeTax(BigDecimal incomeTax) { this.incomeTax = incomeTax; }
    public void setLoanDeduction(BigDecimal loanDeduction) { this.loanDeduction = loanDeduction; }
    public void setLwf(BigDecimal lwf) { this.lwf = lwf; }
    public void setOtherDeduction(BigDecimal otherDeduction) { this.otherDeduction = otherDeduction; }
    public void setInsurance(BigDecimal insurance) { this.insurance = insurance; }
    public void setTotalDeduction(BigDecimal totalDeduction) { this.totalDeduction = totalDeduction; }
    public void setNetSalary(BigDecimal netSalary) { this.netSalary = netSalary; }
    public void setTotalDays(Integer totalDays) { this.totalDays = totalDays; }
    public void setWorkingDays(Integer workingDays) { this.workingDays = workingDays; }
    public void setPresentDays(Double presentDays) { this.presentDays = presentDays; }
    public void setAbsentDays(Double absentDays) { this.absentDays = absentDays; }
    public void setLeaveDays(Double leaveDays) { this.leaveDays = leaveDays; }
    public void setPaidLeaveDays(Double paidLeaveDays) { this.paidLeaveDays = paidLeaveDays; }
    public void setUnpaidLeaveDays(Double unpaidLeaveDays) { this.unpaidLeaveDays = unpaidLeaveDays; }
    public void setHalfDays(Integer halfDays) { this.halfDays = halfDays; }
    public void setAbsentLeaveDeduction(BigDecimal absentLeaveDeduction) { this.absentLeaveDeduction = absentLeaveDeduction; }

    public void setUnpaidLeaveDeduction(BigDecimal unpaidLeaveDeduction) { this.unpaidLeaveDeduction = unpaidLeaveDeduction; }

    public void setAbsentDates(java.util.List<String> absentDates) { this.absentDates = absentDates; }
    public void setStatus(PayslipStatus status) { this.status = status; }
    public void setGeneratedDate(LocalDate generatedDate) { this.generatedDate = generatedDate; }
    public void setApprovedDate(LocalDate approvedDate) { this.approvedDate = approvedDate; }
    public void setSentDate(LocalDate sentDate) { this.sentDate = sentDate; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public void setPdfGenerated(Boolean pdfGenerated) { this.pdfGenerated = pdfGenerated; }
    public void setPdfFilePath(String pdfFilePath) { this.pdfFilePath = pdfFilePath; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
