package com.hrm.hrmsystem.dto;

public class LeaveBalanceDTO {

    private Long id;
    private Long employeeId;
    private String employeeName;
    
    // Simplified leave balance
    private Double totalEarnedLeaves;
    private Double usedLeaves;
    private Double paidLeaves;
    private Double unpaidLeaves;
    private Integer carriedForwardLeaves;
    private Double availableLeaves;
    
    // UI fields - Keep old names but populate with correct values
    private Double openingBalance;
    private Double earnedThisMonth;
    private Double usedThisMonth;
    private Double remaining;
    
    // Cycle info
    private Integer cycle;
    private Integer year;
    
    // Probation info
    private Boolean inProbation;
    private String probationStatus;
    private String joiningDate;
    private String probationEndDate;
    private Integer probationMonths;

    // Cycle expiry and progress info
    private Boolean cycleExpiryWarning;
    private String cycleExpiryMessage;
    private Integer daysUntilCycleEnd;
    private Integer workingDaysForNextLeave;
    private String nextLeaveProgress;

    public LeaveBalanceDTO() {}

    // Getters
    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public Double getTotalEarnedLeaves() { return totalEarnedLeaves; }
    public Double getUsedLeaves() { return usedLeaves; }
    public Double getPaidLeaves() { return paidLeaves; }
    public Double getUnpaidLeaves() { return unpaidLeaves; }
    public Integer getCarriedForwardLeaves() { return carriedForwardLeaves; }
    public Double getAvailableLeaves() { return availableLeaves; }
    
    // UI field getters - Use old field names that UI expects
    public Double getOpeningBalance() { return openingBalance; }
    public Double getEarnedThisMonth() { return earnedThisMonth; }
    public Double getUsedThisMonth() { return usedThisMonth; }
    public Double getRemaining() { return remaining; }
    
    public Integer getCycle() { return cycle; }
    public Integer getYear() { return year; }
    public Boolean getInProbation() { return inProbation; }
    public String getProbationStatus() { return probationStatus; }
    public String getJoiningDate() { return joiningDate; }
    public String getProbationEndDate() { return probationEndDate; }
    public Boolean getCycleExpiryWarning() { return cycleExpiryWarning; }
    public String getCycleExpiryMessage() { return cycleExpiryMessage; }
    public Integer getDaysUntilCycleEnd() { return daysUntilCycleEnd; }
    public Integer getWorkingDaysForNextLeave() { return workingDaysForNextLeave; }
    public String getNextLeaveProgress() { return nextLeaveProgress; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public void setTotalEarnedLeaves(Double totalEarnedLeaves) { this.totalEarnedLeaves = totalEarnedLeaves; }
    public void setUsedLeaves(Double usedLeaves) { this.usedLeaves = usedLeaves; }
    public void setPaidLeaves(Double paidLeaves) { this.paidLeaves = paidLeaves; }
    public void setUnpaidLeaves(Double unpaidLeaves) { this.unpaidLeaves = unpaidLeaves; }
    public void setCarriedForwardLeaves(Integer carriedForwardLeaves) { this.carriedForwardLeaves = carriedForwardLeaves; }
    public void setAvailableLeaves(Double availableLeaves) { this.availableLeaves = availableLeaves; }
    
    // UI field setters - Use old field names that UI expects
    public void setOpeningBalance(Double openingBalance) { this.openingBalance = openingBalance; }
    public void setEarnedThisMonth(Double earnedThisMonth) { this.earnedThisMonth = earnedThisMonth; }
    public void setUsedThisMonth(Double usedThisMonth) { this.usedThisMonth = usedThisMonth; }
    public void setRemaining(Double remaining) { this.remaining = remaining; }
    
    public void setCycle(Integer cycle) { this.cycle = cycle; }
    public void setYear(Integer year) { this.year = year; }
    public void setInProbation(Boolean inProbation) { this.inProbation = inProbation; }
    public void setProbationStatus(String probationStatus) { this.probationStatus = probationStatus; }
    public void setJoiningDate(String joiningDate) { this.joiningDate = joiningDate; }
    public void setProbationEndDate(String probationEndDate) { this.probationEndDate = probationEndDate; }
    public Integer getProbationMonths() { return probationMonths; }
    public void setProbationMonths(Integer probationMonths) { this.probationMonths = probationMonths; }
    public void setCycleExpiryWarning(Boolean cycleExpiryWarning) { this.cycleExpiryWarning = cycleExpiryWarning; }
    public void setCycleExpiryMessage(String cycleExpiryMessage) { this.cycleExpiryMessage = cycleExpiryMessage; }
    public void setDaysUntilCycleEnd(Integer daysUntilCycleEnd) { this.daysUntilCycleEnd = daysUntilCycleEnd; }
    public void setWorkingDaysForNextLeave(Integer workingDaysForNextLeave) { this.workingDaysForNextLeave = workingDaysForNextLeave; }
    public void setNextLeaveProgress(String nextLeaveProgress) { this.nextLeaveProgress = nextLeaveProgress; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private Long employeeId;
        private String employeeName;
        private Double totalEarnedLeaves;
        private Double usedLeaves;
        private Double paidLeaves;
        private Double unpaidLeaves;
        private Integer carriedForwardLeaves;
        private Double availableLeaves;
        
        // UI fields - Use old field names that UI expects
        private Double openingBalance;
        private Double earnedThisMonth;
        private Double usedThisMonth;
        private Double remaining;
        
        private Integer cycle;
        private Integer year;
        private Boolean inProbation;
        private String probationStatus;
        private String joiningDate;
        private String probationEndDate;
        private Boolean cycleExpiryWarning;
        private String cycleExpiryMessage;
        private Integer daysUntilCycleEnd;
        private Integer workingDaysForNextLeave;
        private String nextLeaveProgress;
        private Integer probationMonths;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder employeeId(Long employeeId) { this.employeeId = employeeId; return this; }
        public Builder employeeName(String employeeName) { this.employeeName = employeeName; return this; }
        public Builder totalEarnedLeaves(Double totalEarnedLeaves) { this.totalEarnedLeaves = totalEarnedLeaves; return this; }
        public Builder usedLeaves(Double usedLeaves) { this.usedLeaves = usedLeaves; return this; }
        public Builder paidLeaves(Double paidLeaves) { this.paidLeaves = paidLeaves; return this; }
        public Builder unpaidLeaves(Double unpaidLeaves) { this.unpaidLeaves = unpaidLeaves; return this; }
        public Builder carriedForwardLeaves(Integer carriedForwardLeaves) { this.carriedForwardLeaves = carriedForwardLeaves; return this; }
        public Builder availableLeaves(Double availableLeaves) { this.availableLeaves = availableLeaves; return this; }
        
        // UI field builders - Use old field names that UI expects
        public Builder openingBalance(Double openingBalance) { this.openingBalance = openingBalance; return this; }
        public Builder earnedThisMonth(Double earnedThisMonth) { this.earnedThisMonth = earnedThisMonth; return this; }
        public Builder usedThisMonth(Double usedThisMonth) { this.usedThisMonth = usedThisMonth; return this; }
        public Builder remaining(Double remaining) { this.remaining = remaining; return this; }
        
        public Builder cycle(Integer cycle) { this.cycle = cycle; return this; }
        public Builder year(Integer year) { this.year = year; return this; }
        public Builder inProbation(Boolean inProbation) { this.inProbation = inProbation; return this; }
        public Builder probationStatus(String probationStatus) { this.probationStatus = probationStatus; return this; }
        public Builder joiningDate(String joiningDate) { this.joiningDate = joiningDate; return this; }
        public Builder probationEndDate(String probationEndDate) { this.probationEndDate = probationEndDate; return this; }
        public Builder cycleExpiryWarning(Boolean cycleExpiryWarning) { this.cycleExpiryWarning = cycleExpiryWarning; return this; }
        public Builder cycleExpiryMessage(String cycleExpiryMessage) { this.cycleExpiryMessage = cycleExpiryMessage; return this; }
        public Builder daysUntilCycleEnd(Integer daysUntilCycleEnd) { this.daysUntilCycleEnd = daysUntilCycleEnd; return this; }
        public Builder workingDaysForNextLeave(Integer workingDaysForNextLeave) { this.workingDaysForNextLeave = workingDaysForNextLeave; return this; }
        public Builder nextLeaveProgress(String nextLeaveProgress) { this.nextLeaveProgress = nextLeaveProgress; return this; }
        public Builder probationMonths(Integer probationMonths) { this.probationMonths = probationMonths; return this; }

        public LeaveBalanceDTO build() {
            LeaveBalanceDTO dto = new LeaveBalanceDTO();
            dto.id = this.id;
            dto.employeeId = this.employeeId;
            dto.employeeName = this.employeeName;
            dto.totalEarnedLeaves = this.totalEarnedLeaves;
            dto.usedLeaves = this.usedLeaves;
            dto.paidLeaves = this.paidLeaves;
            dto.unpaidLeaves = this.unpaidLeaves;
            dto.carriedForwardLeaves = this.carriedForwardLeaves;
            dto.availableLeaves = this.availableLeaves;
            
            // UI fields - Use old field names that UI expects
            dto.openingBalance = this.openingBalance;
            dto.earnedThisMonth = this.earnedThisMonth;
            dto.usedThisMonth = this.usedThisMonth;
            dto.remaining = this.remaining;
            
            dto.cycle = this.cycle;
            dto.year = this.year;
            dto.inProbation = this.inProbation;
            dto.probationStatus = this.probationStatus;
            dto.joiningDate = this.joiningDate;
            dto.probationEndDate = this.probationEndDate;
            dto.cycleExpiryWarning = this.cycleExpiryWarning;
            dto.cycleExpiryMessage = this.cycleExpiryMessage;
            dto.daysUntilCycleEnd = this.daysUntilCycleEnd;
            dto.workingDaysForNextLeave = this.workingDaysForNextLeave;
            dto.nextLeaveProgress = this.nextLeaveProgress;
            dto.probationMonths = this.probationMonths;
            return dto;
        }
    }
}
