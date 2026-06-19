package com.hrm.hrmsystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hrm.hrmsystem.entity.Payslip;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    @Column(name = "employee_code")
    private String employeeCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id")
    private Department department;

    private String designation;

    private LocalDate joiningDate;

    private BigDecimal salary;

    // Salary components for payslip calculation
    private BigDecimal basicSalary;
    private BigDecimal hra;
    private BigDecimal specialAllowance;
    private BigDecimal bonus;
    private BigDecimal incentive;
    private BigDecimal otherAllowance;
    
    // Deductions
    private BigDecimal pf;
    private BigDecimal esic;
    private BigDecimal professionalTax;
    private BigDecimal tds;
    private BigDecimal tax;
    private BigDecimal loanDeduction;
    private BigDecimal lwf;
    
    // Gender
    @Enumerated(EnumType.STRING)
    private Gender gender;

    // Probation tracking
    @Enumerated(EnumType.STRING)
    @Column(name = "probation_status")
    private ProbationStatus probationStatus = ProbationStatus.PROBATION;
    
    @Column(name = "probation_notes", length = 500)
    private String probationNotes;
    
    @Column(name = "probation_confirmed_by")
    private String probationConfirmedBy;
    
    @Column(name = "probation_confirmed_date")
    private LocalDate probationConfirmedDate;

    // Migrated from ProbationPeriod for payroll logic
    @Column(name = "pf_percentage")
    private Double pfPercentage = 12.0;

    @Column(name = "annual_tax")
    private Double annualTax = 0.0;
    
    // Removed insuranceName

    @Column(name = "uan_no")
    private String uanNo;

    public enum ProbationStatus {
        PROBATION,
        CONFIRMED,
        EXTENDED
    }
    
    // Probation period
    private Integer probationPeriodMonths;
    
    // Shift assignment
    @ManyToOne
    @JoinColumn(name = "shift_id")
    private Shift shift;
    
    // Audit timestamps
    private LocalDateTime createdAt;
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

    @Enumerated(EnumType.STRING)
    private EmployeeStatus status;

    private String address;

    // Cascade delete relationships - when employee is deleted, related records are also deleted
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Leave> leaves = new ArrayList<>();

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Attendance> attendances = new ArrayList<>();

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Payroll> payrolls = new ArrayList<>();



    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Payslip> payslips = new ArrayList<>();

    public enum EmployeeStatus {
        ACTIVE, INACTIVE, ON_LEAVE, TERMINATED, PROBATION
    }
    
    public enum Gender {
        MALE, FEMALE, OTHER
    }

    // Default Constructor
    public Employee() {}

    // All Args Constructor
    public Employee(Long id, String firstName, String lastName, String email, String phone,
                    Department department, String designation, LocalDate joiningDate, BigDecimal salary,
                    BigDecimal basicSalary, BigDecimal hra, BigDecimal otherAllowance,
                    BigDecimal pf, BigDecimal tax, Gender gender, Integer probationPeriodMonths, Shift shift, EmployeeStatus status, 
                    String address, List<Leave> leaves, List<Attendance> attendances,
                    List<Payroll> payrolls, List<Payslip> payslips) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.department = department;
        this.designation = designation;
        this.joiningDate = joiningDate;
        this.salary = salary;
        this.basicSalary = basicSalary;
        this.hra = hra;
        this.otherAllowance = otherAllowance;
        this.pf = pf;
        this.tax = tax;
        this.gender = gender;
        this.probationPeriodMonths = probationPeriodMonths;
        this.shift = shift;
        this.status = status;
        this.address = address;
        this.leaves = leaves;
        this.attendances = attendances;
        this.payrolls = payrolls;
        this.payslips = payslips;
    }

    // Getters
    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getEmployeeCode() { return employeeCode; }
    public Department getDepartment() { return department; }
    public String getDesignation() { return designation; }
    public LocalDate getJoiningDate() { return joiningDate; }
    public BigDecimal getSalary() { return salary; }
    public BigDecimal getBasicSalary() { return basicSalary; }
    public BigDecimal getHra() { return hra; }
    public BigDecimal getSpecialAllowance() { return specialAllowance; }
    public BigDecimal getBonus() { return bonus; }
    public BigDecimal getIncentive() { return incentive; }
    public BigDecimal getOtherAllowance() { return otherAllowance; }
    public BigDecimal getPf() { return pf; }
    public BigDecimal getEsic() { return esic; }
    public BigDecimal getProfessionalTax() { return professionalTax; }
    public BigDecimal getTds() { return tds; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getLoanDeduction() { return loanDeduction; }
    public BigDecimal getLwf() { return lwf; }
    public String getUanNo() { return uanNo; }
    public Gender getGender() { return gender; }
    public Integer getProbationPeriodMonths() { return probationPeriodMonths; }
    public Shift getShift() { return shift; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public EmployeeStatus getStatus() { return status; }
    public Double getPfPercentage() { return pfPercentage; }
    public Double getAnnualTax() { return annualTax; }
    public String getAddress() { return address; }
    public List<Leave> getLeaves() { return leaves; }
    public List<Attendance> getAttendances() { return attendances; }
    public List<Payroll> getPayrolls() { return payrolls; }
    public ProbationStatus getProbationStatus() { return probationStatus; }
    public String getProbationNotes() { return probationNotes; }
    public String getProbationConfirmedBy() { return probationConfirmedBy; }
    public LocalDate getProbationConfirmedDate() { return probationConfirmedDate; }

    public List<Payslip> getPayslips() { return payslips; }

    /**
     * ✅ NEW: Unified Gross Salary calculation
     * Returns the sum of all salary components.
     * Fallback to 'salary' field if components are zero.
     */
    public BigDecimal getTotalGrossSalary() {
        BigDecimal sum = BigDecimal.ZERO;
        if (basicSalary != null) sum = sum.add(basicSalary);
        if (hra != null) sum = sum.add(hra);
        if (specialAllowance != null) sum = sum.add(specialAllowance);
        if (bonus != null) sum = sum.add(bonus);
        if (incentive != null) sum = sum.add(incentive);
        if (otherAllowance != null) sum = sum.add(otherAllowance);
        
        if (sum.compareTo(BigDecimal.ZERO) > 0) {
            return sum;
        }
        
        return salary != null ? salary : BigDecimal.ZERO;
    }

    /**
     * ✅ NEW: Unified Net Salary calculation
     * Returns the gross salary minus all deduction components.
     */
    public BigDecimal getTotalNetSalary() {
        BigDecimal gross = getTotalGrossSalary();
        BigDecimal deductions = BigDecimal.ZERO;
        if (pf != null) deductions = deductions.add(pf);
        if (esic != null) deductions = deductions.add(esic);
        if (professionalTax != null) deductions = deductions.add(professionalTax);
        if (tds != null) deductions = deductions.add(tds);
        if (tax != null) deductions = deductions.add(tax);
        if (loanDeduction != null) deductions = deductions.add(loanDeduction);
        if (lwf != null) deductions = deductions.add(lwf);
        BigDecimal net = gross.subtract(deductions);
        return net.compareTo(BigDecimal.ZERO) > 0 ? net : BigDecimal.ZERO;
    }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public void setDepartment(Department department) { this.department = department; }
    public void setDesignation(String designation) { this.designation = designation; }
    public void setJoiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; }
    public void setSalary(BigDecimal salary) { this.salary = salary; }
    public void setBasicSalary(BigDecimal basicSalary) { this.basicSalary = basicSalary; }
    public void setHra(BigDecimal hra) { this.hra = hra; }
    public void setSpecialAllowance(BigDecimal specialAllowance) { this.specialAllowance = specialAllowance; }
    public void setBonus(BigDecimal bonus) { this.bonus = bonus; }
    public void setIncentive(BigDecimal incentive) { this.incentive = incentive; }
    public void setOtherAllowance(BigDecimal otherAllowance) { this.otherAllowance = otherAllowance; }
    public void setPf(BigDecimal pf) { this.pf = pf; }
    public void setEsic(BigDecimal esic) { this.esic = esic; }
    public void setProfessionalTax(BigDecimal professionalTax) { this.professionalTax = professionalTax; }
    public void setTds(BigDecimal tds) { this.tds = tds; }
    public void setTax(BigDecimal tax) { this.tax = tax; }
    public void setLoanDeduction(BigDecimal loanDeduction) { this.loanDeduction = loanDeduction; }
    public void setLwf(BigDecimal lwf) { this.lwf = lwf; }
    public void setUanNo(String uanNo) { this.uanNo = uanNo; }
    public void setGender(Gender gender) { this.gender = gender; }
    public void setProbationPeriodMonths(Integer probationPeriodMonths) { this.probationPeriodMonths = probationPeriodMonths; }
    public void setShift(Shift shift) { this.shift = shift; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setStatus(EmployeeStatus status) { this.status = status; }
    public void setAddress(String address) { this.address = address; }
    public void setPfPercentage(Double pfPercentage) { this.pfPercentage = pfPercentage; }
    public void setAnnualTax(Double annualTax) { this.annualTax = annualTax; }
    public void setLeaves(List<Leave> leaves) { this.leaves = leaves; }
    public void setAttendances(List<Attendance> attendances) { this.attendances = attendances; }
    public void setPayrolls(List<Payroll> payrolls) { this.payrolls = payrolls; }
    public void setProbationStatus(ProbationStatus probationStatus) { this.probationStatus = probationStatus; }
    public void setProbationNotes(String probationNotes) { this.probationNotes = probationNotes; }
    public void setProbationConfirmedBy(String probationConfirmedBy) { this.probationConfirmedBy = probationConfirmedBy; }
    public void setProbationConfirmedDate(LocalDate probationConfirmedDate) { this.probationConfirmedDate = probationConfirmedDate; }

    public void setPayslips(List<Payslip> payslips) { this.payslips = payslips; }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String employeeCode;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private Department department;
        private String designation;
        private LocalDate joiningDate;
        private BigDecimal salary;
        private BigDecimal basicSalary;
        private BigDecimal hra;
        private BigDecimal specialAllowance;
        private BigDecimal bonus;
        private BigDecimal incentive;
        private BigDecimal otherAllowance;
        private BigDecimal pf;
        private BigDecimal esic;
        private BigDecimal professionalTax;
        private BigDecimal tds;
        private BigDecimal tax;
        private BigDecimal loanDeduction;
        private BigDecimal lwf;
        private String uanNo;
        private Gender gender;
        private Integer probationPeriodMonths;
        private Shift shift;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private EmployeeStatus status;
        private String address;
        private List<Leave> leaves = new ArrayList<>();
        private List<Attendance> attendances = new ArrayList<>();
        private List<Payroll> payrolls = new ArrayList<>();
        private List<Payslip> payslips = new ArrayList<>();

        public Builder id(Long id) { this.id = id; return this; }
        public Builder employeeCode(String employeeCode) { this.employeeCode = employeeCode; return this; }
        public Builder firstName(String firstName) { this.firstName = firstName; return this; }
        public Builder lastName(String lastName) { this.lastName = lastName; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder phone(String phone) { this.phone = phone; return this; }
        public Builder department(Department department) { this.department = department; return this; }
        public Builder designation(String designation) { this.designation = designation; return this; }
        public Builder joiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; return this; }
        public Builder salary(BigDecimal salary) { this.salary = salary; return this; }
        public Builder basicSalary(BigDecimal basicSalary) { this.basicSalary = basicSalary; return this; }

        public Builder hra(BigDecimal hra) { this.hra = hra; return this; }
        public Builder specialAllowance(BigDecimal specialAllowance) { this.specialAllowance = specialAllowance; return this; }
        public Builder bonus(BigDecimal bonus) { this.bonus = bonus; return this; }
        public Builder incentive(BigDecimal incentive) { this.incentive = incentive; return this; }
        public Builder otherAllowance(BigDecimal otherAllowance) { this.otherAllowance = otherAllowance; return this; }
        public Builder pf(BigDecimal pf) { this.pf = pf; return this; }
        public Builder esic(BigDecimal esic) { this.esic = esic; return this; }
        public Builder professionalTax(BigDecimal professionalTax) { this.professionalTax = professionalTax; return this; }
        public Builder tds(BigDecimal tds) { this.tds = tds; return this; }
        public Builder tax(BigDecimal tax) { this.tax = tax; return this; }
        public Builder loanDeduction(BigDecimal loanDeduction) { this.loanDeduction = loanDeduction; return this; }
        public Builder lwf(BigDecimal lwf) { this.lwf = lwf; return this; }
        public Builder uanNo(String uanNo) { this.uanNo = uanNo; return this; }
        public Builder gender(Gender gender) { this.gender = gender; return this; }
        public Builder probationPeriodMonths(Integer probationPeriodMonths) { this.probationPeriodMonths = probationPeriodMonths; return this; }
        public Builder shift(Shift shift) { this.shift = shift; return this; }
        public Builder status(EmployeeStatus status) { this.status = status; return this; }
        public Builder address(String address) { this.address = address; return this; }
        public Builder leaves(List<Leave> leaves) { this.leaves = leaves; return this; }
        public Builder attendances(List<Attendance> attendances) { this.attendances = attendances; return this; }
        public Builder payrolls(List<Payroll> payrolls) { this.payrolls = payrolls; return this; }
        public Builder payslips(List<Payslip> payslips) { this.payslips = payslips; return this; }

        public Employee build() {
            Employee employee = new Employee();
            employee.setId(id);
            employee.setEmployeeCode(employeeCode);
            employee.setFirstName(firstName);
            employee.setLastName(lastName);
            employee.setEmail(email);
            employee.setPhone(phone);
            employee.setDepartment(department);
            employee.setDesignation(designation);
            employee.setJoiningDate(joiningDate);
            employee.setSalary(salary);
            employee.setBasicSalary(basicSalary);
            employee.setHra(hra);
            employee.setSpecialAllowance(specialAllowance);
            employee.setBonus(bonus);
            employee.setIncentive(incentive);
            employee.setOtherAllowance(otherAllowance);
            employee.setPf(pf);
            employee.setEsic(esic);
            employee.setProfessionalTax(professionalTax);
            employee.setTds(tds);
            employee.setTax(tax);
            employee.setLoanDeduction(loanDeduction);
            employee.setLwf(lwf);
            employee.setUanNo(uanNo);
            employee.setGender(gender);
            employee.setProbationPeriodMonths(probationPeriodMonths);
            employee.setShift(shift);
            employee.setStatus(status);
            employee.setAddress(address);
            employee.setLeaves(leaves);
            employee.setAttendances(attendances);
            employee.setPayrolls(payrolls);
            employee.setPayslips(payslips);
            return employee;
        }
    }
}
