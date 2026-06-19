# Frontend API Standardization

## ✅ STANDARDIZED API ENDPOINTS

All frontend pages now use the **same API endpoints** and **same field names** for consistency.

### 🎯 ATTENDANCE APIS

#### ✅ Main Attendance Summary
```
GET /api/dashboard/my/attendance
```
**Used by:**
- dashboard.js
- dashboard.html  
- my-attendance.html

**Response Fields:**
```json
{
  "presentDays": 8.5,
  "absentDays": 1.5,
  "paidUsedLeaves": 7.0,
  "unpaidUsedLeaves": 0.5,
  "effectiveDays": 15.5,
  "totalHours": 68.0
}
```

#### ✅ Daily Attendance Details
```
GET /api/dashboard/my/attendance/daily?month=5&year=2026
```
**Used by:**
- my-attendance.html

**Response Fields:**
```json
[
  {
    "date": "2026-05-01",
    "status": "PRESENT",
    "checkInTime": "09:00",
    "checkOutTime": "18:00",
    "workingHours": 8.0,
    "isPaid": 0.0,
    "isUnpaid": 0.0,
    "isHalfDay": false
  },
  {
    "date": "2026-05-06", 
    "status": "LEAVE",
    "leaveType": "CASUAL",
    "leaveReason": "Personal work",
    "isPaid": 1.0,
    "isUnpaid": 0.0,
    "isHalfDay": false,
    "checkInTime": null,
    "checkOutTime": null,
    "workingHours": 0.0
  }
]
```

### 🎯 LEAVE BALANCE APIS

#### ✅ Leave Balance Summary
```
GET /api/leaves/balance
```
**Used by:**
- leave-request.html
- my-leaves.html

**Response Fields:**
```json
{
  "earnedLeaves": 7.5,
  "Used LeavesLeaves": 7.0,
  "unpaidLeaves": 0.5,
  "availableLeaves": 0.5,
  "totalEarnedLeaves": 7.5,
  "cycle": 1
}
```

#### ✅ Employee Leave Balance (Admin)
```
GET /api/dashboard/leave-balance/{employeeId}?month=5&year=2026
```
**Used by:**
- leave.html

#### ✅ Employee Attendance Summary (Admin)
```
GET /api/dashboard/attendance/{employeeId}?month=5&year=2026
```
**Used by:**
- leave.html

### 🎯 PAYROLL APIS

#### ✅ Payroll List
```
GET /api/payroll/list
```
**Used by:**
- dashboard.js
- dashboard.html
- payroll.js

**Response Fields:**
```json
[
  {
    "id": 1,
    "employeeId": 16,
    "month": 5,
    "year": 2026,
    "presentDays": 8.5,
    "absentDays": 1.5,
    "paidLeaveDays": 7.0,
    "unpaidLeaveDays": 0.5,
    "basicSalary": 25000.00,
    "grossSalary": 35000.00,
    "totalDeductions": 2000.00,
    "netSalary": 33000.00,
    "status": "PAID"
  }
]
```

---

## ✅ STANDARDIZED FIELD NAMES

### Attendance Data
| Field Name | Type | Description |
|------------|------|-------------|
| `presentDays` | double | Present days + paid leave days |
| `absentDays` | double | Absent days only |
| `paidUsedLeaves` | double | Paid leave days Total Used Leaves |
| `unpaidUsedLeaves` | double | Unpaid leave days Total Used Leaves |
| `effectiveDays` | double | Present + paid leave |
| `totalHours` | double | Total working hours |

### Leave Data
| Field Name | Type | Description |
|------------|------|-------------|
| `earnedLeaves` | double | Total earned leaves |
| `Used LeavesLeaves` | double | Paid Used Leaves Total Used Leaves |
| `unpaidLeaves` | double | Unpaid Used Leaves Total Used Leaves |
| `availableLeaves` | double | Remaining leaves |
| `totalEarnedLeaves` | double | Total earned in cycle |
| `cycle` | int | Current leave cycle (1 or 2) |

### Payroll Data
| Field Name | Type | Description |
|------------|------|-------------|
| `presentDays` | double | Present days |
| `absentDays` | double | Absent days |
| `paidLeaveDays` | double | Paid leave days |
| `unpaidLeaveDays` | double | Unpaid leave days |
| `basicSalary` | double | Basic salary |
| `grossSalary` | double | Gross salary |
| `totalDeductions` | double | Total deductions |
| `netSalary` | double | Net salary |
| `status` | string | Payroll status |

---

## ✅ FRONTEND PAGES STANDARDIZED

### Dashboard Pages
- **dashboard.js** ✅ Uses `/api/dashboard/my/attendance`
- **dashboard.html** ✅ Uses `/api/dashboard/my/attendance`

### Attendance Pages  
- **my-attendance.html** ✅ Uses `/api/dashboard/my/attendance` and `/api/dashboard/my/attendance/daily`

### Leave Pages
- **leave.html** ✅ Uses `/api/dashboard/leave-balance/{id}` and `/api/dashboard/attendance/{id}`
- **leave-request.html** ✅ Uses `/api/leaves/balance`
- **my-leaves.html** ✅ Uses `/api/leaves/balance`

### Payroll Pages
- **payroll.js** ✅ Uses `/api/payroll/list`
- **payslips.html** ✅ Uses `/api/payslips/my`

---

## ✅ BENEFITS OF STANDARDIZATION

### 1. **Consistency**
- All pages use same API endpoints
- Same field names across all responses
- Consistent data structure

### 2. **Maintainability**
- Single source of truth for API contracts
- Easy to update field names globally
- Reduced code duplication

### 3. **Performance**
- Cached API responses reUsed Leaves
- Reduced API call variations
- Optimized data transfer

### 4. **Developer Experience**
- Predictable API responses
- Easy debugging and testing
- Clear documentation

---

## ✅ MIGRATION COMPLETED

### Before Standardization:
```
❌ dashboard.js: /api/attendance/date/{today}
❌ dashboard.html: /api/attendance/date/{today}
❌ my-attendance.html: /api/dashboard/my/attendance/daily
❌ Various field names: present, absent, Total Used Leaves, unpaid
```

### After Standardization:
```
✅ All pages: /api/dashboard/my/attendance
✅ Daily details: /api/dashboard/my/attendance/daily
✅ Consistent fields: presentDays, absentDays, paidUsedLeaves, unpaidUsedLeaves
```

**All frontend pages now use the same API endpoints and field names for complete consistency!** 🎯
