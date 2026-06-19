package com.hrm.hrmsystem.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Employee Data Transfer Object
 * Used for API request/response
 */
public class EmployeeDTO {
    
    private Long id;
    
    private String employeeId;
    
    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Phone number must be a valid 10-digit Indian mobile number starting with 6, 7, 8, or 9")
    private String phone;
    
    @Size(max = 100, message = "Designation must not exceed 100 characters")
    private String designation;
    
    @NotNull(message = "Joining date is required")
    @JsonDeserialize(using = LocalDateDeserializer.class)
    private LocalDate joiningDate;
    
    @PositiveOrZero(message = "Salary must be positive or zero")
    @JsonDeserialize(using = BigDecimalDeserializer.class)
    private BigDecimal salary;
    
    private String status;
    
    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;
    
    private Long departmentId;
    
    private String departmentName;
    
    private Long shiftId;
    
    private String gender;
    
    private Integer probationPeriodMonths;
    
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
    // Removed insuranceName
    
    private String uanNo;

    // Constructors
    public EmployeeDTO() {}

    public EmployeeDTO(Long id, String employeeId, String firstName, String lastName, 
                       String email, String phone, String designation, LocalDate joiningDate,
                       BigDecimal salary, String status, String address, Long departmentId, String departmentName,
                       Long shiftId, String gender, Integer probationPeriodMonths,
                       BigDecimal basicSalary, BigDecimal hra, BigDecimal otherAllowance,
                       BigDecimal pf, BigDecimal tax) {
        this.id = id;
        this.employeeId = employeeId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.designation = designation;
        this.joiningDate = joiningDate;
        this.salary = salary;
        this.status = status;
        this.address = address;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.shiftId = shiftId;
        this.gender = gender;
        this.probationPeriodMonths = probationPeriodMonths;
        this.basicSalary = basicSalary;
        this.hra = hra;
        this.otherAllowance = otherAllowance;
        this.pf = pf;
        this.tax = tax;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getDesignation() {
        return designation;
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public String getStatus() {
        return status;
    }

    public String getAddress() {
        return address;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public Long getShiftId() {
        return shiftId;
    }

    public String getGender() {
        return gender;
    }

    public Integer getProbationPeriodMonths() {
        return probationPeriodMonths;
    }

    public BigDecimal getBasicSalary() {
        return basicSalary;
    }

    public BigDecimal getHra() {
        return hra;
    }

    public BigDecimal getSpecialAllowance() {
        return specialAllowance;
    }

    public BigDecimal getBonus() {
        return bonus;
    }

    public BigDecimal getIncentive() {
        return incentive;
    }

    public BigDecimal getOtherAllowance() {
        return otherAllowance;
    }

    public BigDecimal getPf() {
        return pf;
    }

    public BigDecimal getEsic() {
        return esic;
    }

    public BigDecimal getProfessionalTax() {
        return professionalTax;
    }

    public BigDecimal getTds() {
        return tds;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public BigDecimal getLoanDeduction() {
        return loanDeduction;
    }

    public BigDecimal getLwf() {
        return lwf;
    }

    public String getUanNo() {
        return uanNo;
    }

    // Setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public void setJoiningDate(LocalDate joiningDate) {
        this.joiningDate = joiningDate;
    }

    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public void setShiftId(Long shiftId) {
        this.shiftId = shiftId;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public void setProbationPeriodMonths(Integer probationPeriodMonths) {
        this.probationPeriodMonths = probationPeriodMonths;
    }

    public void setBasicSalary(BigDecimal basicSalary) {
        this.basicSalary = basicSalary;
    }

    public void setHra(BigDecimal hra) {
        this.hra = hra;
    }

    public void setSpecialAllowance(BigDecimal specialAllowance) {
        this.specialAllowance = specialAllowance;
    }

    public void setBonus(BigDecimal bonus) {
        this.bonus = bonus;
    }

    public void setIncentive(BigDecimal incentive) {
        this.incentive = incentive;
    }

    public void setOtherAllowance(BigDecimal otherAllowance) {
        this.otherAllowance = otherAllowance;
    }

    public void setPf(BigDecimal pf) {
        this.pf = pf;
    }

    public void setEsic(BigDecimal esic) {
        this.esic = esic;
    }

    public void setProfessionalTax(BigDecimal professionalTax) {
        this.professionalTax = professionalTax;
    }

    public void setTds(BigDecimal tds) {
        this.tds = tds;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public void setLoanDeduction(BigDecimal loanDeduction) {
        this.loanDeduction = loanDeduction;
    }

    public void setLwf(BigDecimal lwf) {
        this.lwf = lwf;
    }

    public void setUanNo(String uanNo) {
        this.uanNo = uanNo;
    }

    @Override
    public String toString() {
        return "EmployeeDTO{" +
                "id=" + id +
                ", employeeId='" + employeeId + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", designation='" + designation + '\'' +
                ", basicSalary=" + basicSalary +
                ", hra=" + hra +
                ", pf=" + pf +
                '}';
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String employeeId;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String designation;
        private LocalDate joiningDate;
        private BigDecimal salary;
        private String status;
        private String address;
        private Long departmentId;
        private String departmentName;
        private Long shiftId;
        private String gender;
        private Integer probationPeriodMonths;
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

        public Builder id(Long id) { this.id = id; return this; }
        public Builder employeeId(String employeeId) { this.employeeId = employeeId; return this; }
        public Builder firstName(String firstName) { this.firstName = firstName; return this; }
        public Builder lastName(String lastName) { this.lastName = lastName; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder phone(String phone) { this.phone = phone; return this; }
        public Builder designation(String designation) { this.designation = designation; return this; }
        public Builder joiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; return this; }
        public Builder salary(BigDecimal salary) { this.salary = salary; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder address(String address) { this.address = address; return this; }
        public Builder departmentId(Long departmentId) { this.departmentId = departmentId; return this; }
        public Builder departmentName(String departmentName) { this.departmentName = departmentName; return this; }
        public Builder shiftId(Long shiftId) { this.shiftId = shiftId; return this; }
        public Builder gender(String gender) { this.gender = gender; return this; }
        public Builder probationPeriodMonths(Integer probationPeriodMonths) { this.probationPeriodMonths = probationPeriodMonths; return this; }
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

        public EmployeeDTO build() {
            EmployeeDTO dto = new EmployeeDTO();
            dto.setId(id);
            dto.setEmployeeId(employeeId);
            dto.setFirstName(firstName);
            dto.setLastName(lastName);
            dto.setEmail(email);
            dto.setPhone(phone);
            dto.setDesignation(designation);
            dto.setJoiningDate(joiningDate);
            dto.setSalary(salary);
            dto.setStatus(status);
            dto.setAddress(address);
            dto.setDepartmentId(departmentId);
            dto.setDepartmentName(departmentName);
            dto.setShiftId(shiftId);
            dto.setGender(gender);
            dto.setProbationPeriodMonths(probationPeriodMonths);
            dto.setBasicSalary(basicSalary);
            dto.setHra(hra);
            dto.setSpecialAllowance(specialAllowance);
            dto.setBonus(bonus);
            dto.setIncentive(incentive);
            dto.setOtherAllowance(otherAllowance);
            dto.setPf(pf);
            dto.setEsic(esic);
            dto.setProfessionalTax(professionalTax);
            dto.setTds(tds);
            dto.setTax(tax);
            dto.setLoanDeduction(loanDeduction);
            dto.setLwf(lwf);
            dto.setUanNo(uanNo);
            return dto;
        }
    }

    // Custom deserializers to handle string inputs from frontend
    public static class BigDecimalDeserializer extends JsonDeserializer<BigDecimal> {
        @Override
        public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            if (value == null || value.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid number format: " + value);
            }
        }
    }

    public static class LocalDateDeserializer extends JsonDeserializer<LocalDate> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            if (value == null || value.isEmpty()) {
                return null;
            }
            try {
                return LocalDate.parse(value, FORMATTER);
            } catch (Exception e) {
                throw new IOException("Invalid date format: " + value);
            }
        }
    }
}
