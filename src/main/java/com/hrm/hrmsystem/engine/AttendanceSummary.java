package com.hrm.hrmsystem.engine;

/**
 * ✅ FIXED: Attendance Summary DTO - Proper HR data model
 * Separates concepts correctly to avoid double deductions
 */
public class AttendanceSummary {
    // ✅ CORE HR FIELDS - Proper separation
    public double workedDays = 0;      // Actual attendance only
    public double paidLeave = 0;        // Approved paid leave
    public double unpaidLeave = 0;      // Approved unpaid leave
    public double absent = 0;          // Unauthorized absence only
    public double unmarked = 0;         // No attendance record
    public double payableDays = 0;      // workedDays + paidLeave
    public double paidAbsent = 0;       // Manual absences covered by paid balance
    public double unpaidAbsent = 0;     // Manual absences not covered by paid balance
    
    // ✅ COMPATIBILITY FIELDS
    public int year = 0;               // For wrapper methods
    public int month = 0;              // For wrapper methods
    public double workedHours = 0;       // For wrapper methods
    
    // ✅ LEGACY COMPATIBILITY - Remove after frontend migration
    @Deprecated
    public double getPresent() {
        return workedDays; // For backward compatibility
    }
    
    @Deprecated
    public double getAbsent() {
        return absent + unpaidLeave; // For backward compatibility
    }
    
    @Deprecated
    public double getActualWorkingHours() {
        return workedHours; // For wrapper methods
    }
    
    // ✅ CORRECT CALCULATIONS
    public double getTotalWorkingDays() {
        return workedDays + paidLeave + unpaidLeave + absent + unmarked;
    }
    
    public double getTotalLeaveDays() {
        return paidLeave + unpaidLeave;
    }
    
    public double getPayableDays() {
        return payableDays;
    }
    
    /**
     * PRESENT DAYS CALCULATION: Worked Days only
     * Includes: Present Marked Days
     * Excludes: Absent Days + Unpaid Leave Days + Paid Leave Days + Unmarked Days
     */
    public double getPresentDays() {
        return workedDays;
    }  
    
    @Override
    public String toString() {
        return String.format("AttendanceSummary{workedDays=%.1f, paidLeave=%.1f, unpaidLeave=%.1f, absent=%.1f, unmarked=%.1f, payableDays=%.1f}",
                workedDays, paidLeave, unpaidLeave, absent, unmarked, payableDays);
    }
}
