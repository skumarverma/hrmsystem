# Future Modularization Plan - AttendanceEngine Split

## Current State
AttendanceEngine is becoming large with multiple responsibilities. For future maintainability, plan to split into specialized classes.

## Proposed Architecture

### 1. LeaveBalanceCalculator
**Responsibilities:**
- `calculateLeaveBalance()`
- `calculateEarnedLeavesDirect()`
- Probation logic (15th-day rule)
- Cycle reset logic
- Max 9 cap enforcement

**Methods:**
```java
public class LeaveBalanceCalculator {
    public LeaveBalanceSummary calculateBalance(Long employeeId, int year, int month)
    private double calculateEarnedLeaves(Long employeeId, int year, int month, int cycle)
    private boolean isInProbation(Long employeeId, LocalDate date)
    private YearMonth getEffectiveStartMonth(LocalDate probationEnd)
}
```

### 2. LeaveSplitCalculator  
**Responsibilities:**
- `calculateLeaveSplit()`
- Paid/unpaid split logic
- Balance availability checking

**Methods:**
```java
public class LeaveSplitCalculator {
    public LeaveSplit calculateSplit(Long employeeId, LocalDate start, LocalDate end, boolean halfDay)
    private double calculatePaidDays(double totalDays, double availableBalance)
    private double calculateUnpaidDays(double totalDays, double paidDays)
}
```

### 3. WorkingDayCalculator
**Responsibilities:**
- `calculateWorkingDays()`
- Sunday exclusion logic
- Half-day handling
- Date boundary calculations

**Methods:**
```java
public class WorkingDayCalculator {
    public double calculateWorkingDays(LocalDate start, LocalDate end)
    private boolean isWorkingDay(LocalDate date)
    private double handleHalfDay(LocalDate date, boolean isHalfDay)
}
```

### 4. ProbationPolicy
**Responsibilities:**
- All probation-related logic
- 15th-day rule implementation
- Effective accrual start calculation

**Methods:**
```java
public class ProbationPolicy {
    public boolean isInProbation(Long employeeId, LocalDate referenceDate)
    public YearMonth getEffectiveAccrualStart(LocalDate joinDate, int probationMonths)
    public long getRemainingProbationDays(Long employeeId)
}
```

### 5. PayrollProtectionService
**Responsibilities:**
- Payroll lock checking
- Historical data protection
- Freeze enforcement

**Methods:**
```java
public class PayrollProtectionService {
    public void validatePayrollLock(int month, int year, String operation)
    public boolean isPayrollLocked(int month, int year)
    public void validateLeaveModification(Leave leave, String operation)
}
```

## Integration Strategy

### Phase 1: Extract WorkingDayCalculator
- Lowest risk, most isolated
- Used by multiple other classes
- Clear interface

### Phase 2: Extract ProbationPolicy  
- Self-contained logic
- Clear business rules
- Easy to test

### Phase 3: Extract LeaveSplitCalculator
- Depends on LeaveBalanceCalculator
- Medium complexity
- Clear separation

### Phase 4: Extract LeaveBalanceCalculator
- Most complex
- Core business logic
- Extract last

### Phase 5: Extract PayrollProtectionService
- Cross-cutting concern
- Can be done anytime
- Minimal dependencies

## Refactored AttendanceEngine

```java
@Component
public class AttendanceEngine {
    private final WorkingDayCalculator workingDayCalculator;
    private final LeaveBalanceCalculator leaveBalanceCalculator;
    private final LeaveSplitCalculator leaveSplitCalculator;
    private final ProbationPolicy probationPolicy;
    private final PayrollProtectionService payrollProtection;
    
    // Delegate methods
    public LeaveBalanceSummary calculateLeaveBalance(Long employeeId, int year, int month) {
        return leaveBalanceCalculator.calculateBalance(employeeId, year, month);
    }
    
    public LeaveSplit calculateLeaveSplit(Long employeeId, LocalDate start, LocalDate end, boolean halfDay) {
        payrollProtection.validatePayrollLock(start.getMonthValue(), start.getYear(), "leave_split");
        return leaveSplitCalculator.calculateSplit(employeeId, start, end, halfDay);
    }
    
    public double calculateWorkingDays(LocalDate start, LocalDate end) {
        return workingDayCalculator.calculateWorkingDays(start, end);
    }
}
```

## Benefits

1. **Single Responsibility**: Each class has one clear purpose
2. **Testability**: Smaller classes easier to unit test
3. **Maintainability**: Changes isolated to specific domain
4. **Reusability**: Components can be Total Used Leaves independently
5. **Readability**: Smaller, focUsed Leaves classes

## Implementation Notes

- **Only after stabilization**: Current architecture works well
- **Backward compatibility**: Keep AttendanceEngine as facade
- **Gradual migration**: Phase by phase approach
- **Comprehensive testing**: Each component thoroughly tested
- **Documentation**: Clear interfaces and contracts

## Timeline

**Current**: Focus on stabilization and testing
**Next Quarter**: Begin Phase 1 (WorkingDayCalculator)
**Following Quarter**: Complete all phases based on stability

This modularization will make the system more maintainable while preserving the single-source-of-truth architecture.
