package com.hrm.hrmsystem.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PayrollDTO {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String department;
    private String designation;

    private Integer month;
    private Integer year;

    private BigDecimal basicSalary;
    private BigDecimal hra;

    private BigDecimal ta;
    private BigDecimal specialAllowance;
    private BigDecimal bonus;
    private BigDecimal incentive;
    private BigDecimal otherAllowances;

    private BigDecimal providentFund;
    private BigDecimal esic;
    private BigDecimal professionalTax;
    private BigDecimal tds;
    private BigDecimal loanDeduction;
    private BigDecimal lwf;
    private BigDecimal tax;
    private BigDecimal insurance;
    private BigDecimal otherDeductions;

    private BigDecimal grossSalary;
    private BigDecimal totalDeductions;
    private BigDecimal netSalary;

    private LocalDate paymentDate;
    private String status;
    private Boolean accountantApproved;
    private Boolean directorApproved;
    
    // Attendance details (for display in frontend)
    private Double presentDays;
    private Double absentDays;
    private Integer leaveDays;
    private Double paidLeaveDays;
    private Double unpaidLeaveDays;
    private Integer halfDays;
    private Double absentLeaveDeduction;
    private BigDecimal unpaidLeaveDeduction;
    private BigDecimal absentPenaltyDeduction;
    
    // Leave balance info (for display in payslip modal)
    private LeaveBalanceInfo leaveBalance;
    private Boolean inProbation;
    private String probationStatus;
    private Integer probationMonths;
    private LocalDate joinDate;
    private LocalDate probationCompletionDate;
    
    // Nested class for leave balance
    @Data
    @Builder
    public static class LeaveBalanceInfo {
        private Double totalEarnedLeaves;
        private Double usedLeaves;
        private Double availableLeaves;
        private Double carriedForwardLeaves;
        private Double unpaidLeaves;
        private Integer cycle; // 1 = Jan-Jun, 2 = Jul-Dec
    }

}
