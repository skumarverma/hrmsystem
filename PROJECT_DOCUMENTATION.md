# HRMS System - Project Documentation

## Project Overview
This is a Human Resource Management System (HRMS) built with Spring Boot that manages employee attendance, leave, payroll, and payslip generation. The system supports half-day leave, pending attendance resolution, audit logging, payroll locking, and attendance finalization.

---

## Payslip Calculation

### Salary Components
- **Basic Salary**: Base salary of the employee
- **HRA (House Rent Allowance)**: Housing allowance
- **DA (Dearness Allowance)**: Cost of living adjustment
- **Other Allowances**: Additional allowances
- **Gross Salary**: Basic + HRA + DA + Other Allowances

### Deductions
- **PF (Provident Fund)**: 12% of basic salary (or custom value)
- **ESI**: Employee State Insurance (currently set to 0)
- **Income Tax**: Calculated based on basic salary
- **Insurance**: Percentage of basic salary (if configured)
- **Absent/Leave Deduction**: Calculated based on attendance

### Deduction Formula
```
Deduction = (absent_days × daily_salary) + (unpaid_leave_days × daily_salary)
```

Where:
- `daily_salary = basic_salary / 26` (based on 26 working days per month)
- `absent_days` includes actual absent days + half-day contribution (0.5 per half-day)
- `unpaid_leave_days` includes unpaid approved leaves + half-day contribution (0.5 per half-day)

### Net Salary Calculation
```
Net Salary = Gross Salary - (PF + ESI + Income Tax + Insurance + Absent/Leave Deduction)
```

### Probation vs Post-Probation
- **During Probation**: All leaves are considered unpaid
- **Post-Probation**: Paid Used Leaves are deducted from leave balance first, then Unpaid Used Leaves trigger deductions

### Payroll Lock
- When payroll is locked for a month/year, attendance changes do NOT trigger payslip regeneration
- Admin can lock/unlock payroll via API endpoints
- Lock prevents accidental salary changes after payroll processing

### Salary Adjustment System
- When payroll is locked, attendance changes cannot affect salary directly
- Instead, admin can create salary adjustments to compensate for corrections
- Adjustments are only allowed when payroll is locked
- Adjustment types: BONUS, DEDUCTION, CORRECTION, OVERTIME, OTHER
- Adjustments can be positive (addition) or negative (deduction)
- Adjustments must be marked as "applied" after being processed
- Unapplied adjustments can be deleted; applied adjustments cannot
- This provides a controlled way to fix payroll errors after lock

---

## Leave Rules

### Leave Types
- **Full-Day Leave**: Entire day off
- **Half-Day Leave**: First half or second half of the day off
- **Paid Leave**: Deducted from leave balance, no salary deduction
- **Unpaid Leave**: Not deducted from balance, triggers salary deduction

### Leave Balance
- Employees have leave balance per leave type (e.g., Sick Leave, Casual Leave, Earned Leave)
- Leave balance is tracked and updated when leaves are approved
- During probation, all leaves are unpaid regardless of balance

### Leave Cycle Expiry
- Leave balance is tracked in two cycles per year:
  - **Cycle 1**: January - June
  - **Cycle 2**: July - December
- At the end of each cycle, unUsed Leaves leaves are handled as follows:
  - **Carry Forward Limit**: Maximum 10 leaves can be carried forward to next cycle
  - **Expired Leaves**: Any leaves beyond the carry-forward limit expire
- When Cycle 2 is created, unUsed Leaves leaves from Cycle 1 are carried forward (up to 10)
- Expired leaves are recorded for audit purposes
- This ensures leave balance doesn't accumulate indefinitely

### Leave Approval Process
1. Employee submits leave request with dates and type
2. Admin reviews and approves/rejects
3. Approved leaves update attendance records
4. Leave balance is adjusted accordingly

### Half-Day Leave Implementation
- Employees can select "Half Day" checkbox when requesting leave
- For half-day leave, employee must specify: `FIRST_HALF` or `SECOND_HALF`
- Half-day leaves contribute 0.5 to leave balance deduction
- Half-day leaves contribute 0.5 to salary deduction calculation
- **STRICT RULE**: When half-day leave is approved, the entire day is marked as PENDING
- Admin must resolve whether the employee worked the remaining half (PRESENT or ABSENT)
- DO NOT auto-mark as HALF_DAY or LEAVE - always PENDING for admin resolution

### Validation Rules
- **Cannot mark FULL PRESENT** on a day with approved full-day leave
- This prevents double-counting (present + leave on same day)
- **Cannot apply half-day leave for both halves** of the same day
- If one half is already marked as leave, user must apply for full-day leave instead

---

## Attendance Management

### Attendance Statuses
- **PRESENT**: Employee attended full day
- **ABSENT**: Employee was absent
- **LATE**: Employee arrived late (after 9:00 AM)
- **HALF_DAY**: Employee attended half day
- **PENDING**: Attendance requires admin resolution
- **LEAVE**: Employee was on approved leave
- **NOT_MARKED**: Attendance not yet recorded

### PENDING Attendance System
- When attendance cannot be automatically determined, it's marked as PENDING
- Admin must manually resolve PENDING attendance as PRESENT or ABSENT
- PENDING attendance does NOT count as present until resolved
- PENDING status is displayed in yellow color in the UI

### Attendance Resolution
- Admin can resolve PENDING attendance via API endpoint:
  ```
  PUT /api/attendance/{attendanceId}/resolve-pending?newStatus=PRESENT|ABSENT&remarks=...
  ```
- Resolution triggers audit log entry
- Resolution triggers payslip regeneration (if payroll not locked)

### Attendance Finalization
- Admin can finalize attendance for a date range
- Finalized attendance cannot be modified
- Finalization prevents retroactive changes to historical attendance
- Can be unfinalized if corrections are needed

### Holiday System
- Admin can configure company holidays
- Holidays can be one-time or recurring (same date every year)
- On holidays, attendance is automatically skipped
- Holidays do not count as absent or present
- Holiday calendar can be managed via API
- Supports checking if a specific date is a holiday

---

## Half-Day Leave Logic

### Scenario: Employee takes half-day leave
1. Employee requests half-day leave with type (FIRST_HALF or SECOND_HALF)
2. Admin approves the leave
3. Attendance record shows status based on the other half:
   - If employee worked the other half → HALF_DAY status
   - If employee didn't work the other half → PENDING status (requires admin resolution)

### PENDING Status for Remaining Half
- When an employee takes half-day leave, the remaining half of the day is marked PENDING
- Admin must resolve whether the employee worked the remaining half:
  - Mark as PRESENT if they worked
  - Mark as ABSENT if they didn't work
- This ensures accurate attendance tracking and salary calculation

### Salary Impact
- Half-day leave contributes 0.5 to unpaid leave deduction
- If remaining half is marked ABSENT → additional 0.5 absent deduction
- Total deduction for full day absent with half-day leave: 1.0 × daily_salary

---

## Audit Logging

### What is Logged
- All attendance status changes
- PENDING attendance resolutions
- Attendance finalization actions
- Payroll lock/unlock actions

### Audit Log Fields
- **Entity Type**: ATTENDANCE, LEAVE, PAYROLL, etc.
- **Entity ID**: ID of the affected record
- **Action**: CREATE, UPDATE, DELETE, RESOLVE_PENDING, etc.
- **Old Value**: Previous state (JSON)
- **New Value**: New state (JSON)
- **Changed By**: Username of who made the change
- **Changed At**: Timestamp of the change
- **Remarks**: Optional notes
- **Employee ID**: Affected employee

### Audit Trail Usage
- Track who changed what and when
- Debug payroll discrepancies
- Compliance and audit requirements
- Investigate attendance disputes

---

## API Endpoints

### Attendance
- `POST /api/attendance/check-in/{employeeId}` - Check in employee
- `POST /api/attendance/check-out/{employeeId}` - Check out employee
- `POST /api/attendance/mark-absent/{employeeId}` - Mark absent
- `POST /api/attendance/mark-present/{employeeId}` - Mark present
- `PUT /api/attendance/{attendanceId}/resolve-pending` - Resolve PENDING attendance

### Payroll Lock
- `GET /api/payroll-lock/check/{month}/{year}` - Check if payroll is locked
- `POST /api/payroll-lock/lock/{month}/{year}` - Lock payroll
- `PUT /api/payroll-lock/unlock/{month}/{year}` - Unlock payroll

### Attendance Finalization
- `GET /api/attendance-finalization/check/{date}` - Check if attendance is finalized
- `POST /api/attendance-finalization/finalize` - Finalize attendance for date range
- `PUT /api/attendance-finalization/unfinalize` - Unfinalize attendance

### Salary Adjustments
- `POST /api/salary-adjustments/create` - Create salary adjustment (only when payroll locked)
- `GET /api/salary-adjustments/employee/{employeeId}/{month}/{year}` - Get adjustments for employee
- `GET /api/salary-adjustments/month/{month}/{year}` - Get all adjustments for month
- `GET /api/salary-adjustments/unapplied` - Get all unapplied adjustments
- `PUT /api/salary-adjustments/{adjustmentId}/apply` - Mark adjustment as applied
- `DELETE /api/salary-adjustments/{adjustmentId}` - Delete unapplied adjustment
- `GET /api/salary-adjustments/total/{employeeId}/{month}/{year}` - Get total adjustment amount

### Holidays
- `GET /api/holidays/check/{date}` - Check if date is a holiday
- `GET /api/holidays/range/{startDate}/{endDate}` - Get holidays in date range
- `GET /api/holidays/all` - Get all active holidays
- `GET /api/holidays/{date}` - Get holiday by date
- `POST /api/holidays/create` - Create new holiday
- `PUT /api/holidays/{holidayId}` - Update holiday
- `DELETE /api/holidays/{holidayId}` - Delete holiday

---

## Key Business Rules

1. **No Auto-Present**: PENDING attendance never auto-converts to PRESENT. Admin must resolve.
2. **No Double-Counting**: Cannot mark PRESENT on a day with approved full-day leave.
3. **Payroll Lock Protection**: Locked payroll prevents attendance changes from affecting salary.
4. **Attendance Finalization**: Finalized attendance cannot be modified without unfinalizing.
5. **Audit Trail**: All critical changes are logged for accountability.
6. **Probation Rules**: All leaves are unpaid during probation period.
7. **Half-Day Handling**: Half-day leaves always mark the entire day as PENDING for admin resolution.
8. **Leave Cycle Expiry**: UnUsed Leaves leaves expire at cycle end (max 10 carried forward).
9. **Salary Adjustments**: Post-lock corrections must use adjustment system, not direct attendance changes.
10. **Holiday Handling**: Holidays skip attendance automatically, no present/absent marking.
11. **Both Halves Validation**: Cannot apply half-day leave for both halves of same day (use full-day leave).

---

## Database Tables

### Core Tables
- `employees` - Employee information
- `attendance` - Daily attendance records
- `leaves` - Leave requests
- `leave_balances` - Leave balance tracking (with cycle expiry fields)
- `payrolls` - Monthly payroll data
- `payslips` - Individual payslip records
- `audit_logs` - Change history
- `payroll_locks` - Payroll lock status
- `attendance_finalizations` - Attendance finalization records
- `salary_adjustments` - Post-lock salary corrections
- `holidays` - Company holiday calendar

---

## UI Features

### Employee Dashboard
- View personal attendance
- Request leave (with half-day option)
- View payslips
- Check leave balance

### Admin Dashboard
- Manage all employee attendance
- Resolve PENDING attendance
- Filter by "Pending Only" to see items requiring action
- Lock/unlock payroll
- Finalize/unfinalize attendance
- View audit logs

### Status Colors
- **Present**: Green
- **Absent**: Red
- **Half Day**: Yellow/Orange
- **Pending**: Yellow (requires action)
- **Leave**: Blue
- **OFF/Sunday**: Gray

---

## Configuration

### Working Days
- Standard working days per month: 26
- Standard check-in time: 9:00 AM
- Standard working hours: 8.0 hours
- Half-day threshold: < 4 hours

### Salary Calculation Base
- Daily salary = Basic Salary / 26
- This ensures consistent monthly salary regardless of month length

---

## Summary

This HRMS system provides comprehensive attendance and leave management with:
- Accurate salary calculation based on actual attendance
- Flexible half-day leave support
- Admin control over PENDING attendance resolution
- Payroll protection through locking mechanism
- Attendance finalization for historical accuracy
- Complete audit trail for compliance
- Validation rules to prevent data inconsistencies

The system ensures that employees are paid accurately based on their actual attendance and leave usage, while giving administrators the tools to manage exceptions and maintain data integrity.
