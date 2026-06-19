# 📋 LEAVE CALCULATION RULES (CORRECT)

## YOUR SCENARIO: April 30 → May 3, 2026

### Step 1: Check Calendar
| Date | Day | Count? |
|------|-----|--------|
| April 30 | Thursday | ✅ 1 day |
| May 1 | Friday | ✅ 1 day |
| May 2 | Saturday | ✅ 1 day |
| May 3 | Sunday | ❌ Excluded |

**Total Days: 3** (NOT 4)

### Step 2: Split by Month
```
April Portion: April 30 = 1 day
May Portion: May 1 + May 2 = 2 days (May 3 is Sunday, excluded)
```

### Step 3: Calculate Paid/Unpaid Per Month

**Assumption:** Employee has already Total Used Leaves 1 day in April (previous leave)

**April Calculation:**
- April earned: 1.5 days
- Already Total Used Leaves: 1 day (old leave)
- Remaining: 0.5 days
- April leave portion: 1 day
- Paid: 0.5 (max remaining)
- Unpaid: 0.5 (1 - 0.5)

**May Calculation:**
- May earned: 1.5 days
- New month, fresh balance: 1.5 days
- May leave portion: 2 days
- Paid: 1.5 (max available)
- Unpaid: 0.5 (2 - 1.5)

**Total:**
- Paid: 0.5 + 1.5 = 2.0 days
- Unpaid: 0.5 + 0.5 = 1.0 day
- **Total: 3 days** ✅

---

## 🔥 CRITICAL BUG FOUND

The current code has a bug at line 193:
```java
double monthPaid = Math.min(daysInMonth, Math.max(0, monthBalance - paidDays));
```

**Problem:** It subtracts `paidDays` which accumulates from previous months, reducing the Total Earned incorrectly for each subsequent month.

**Fix:** Track remaining balance separately, don't subtract accumulated paid days.
