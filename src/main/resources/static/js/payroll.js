// Payroll Management JavaScript
// Extracted from payroll.html to remove inline script duplication

if (!auth.requireAuth()) throw new Error('Auth required');

const monthNames = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
let currentPayrollData = null;
let allPayrollData = [];

async function loadPayroll() {
    const tbody = document.getElementById('payroll-table');
    tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; padding: 2rem;">⏳ Loading payroll data...</td></tr>';

    try {
        const selectedMonthVal = document.getElementById('month-filter').value;
        const selectedYearVal = document.getElementById('year-filter').value;

        let url = '/api/payroll';
        if (selectedMonthVal && selectedYearVal) {
            url += `?month=${selectedMonthVal}&year=${selectedYearVal}`;
        } else {
            url += '/list';
        }
        url += (url.includes('?') ? '&' : '?') + 't=' + Date.now();

        const res = await auth.apiCall(url);
        console.log('Payroll API Response Status:', res?.status);

        if (!res || !res.ok) {
            const errorText = res ? await res.text() : 'No response';
            tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; padding: 2rem; color: #dc2626;">❌ Error loading payroll: ${res?.status || 'Network Error'} - ${errorText.substring(0, 100)}</td></tr>`;
            console.error('Payroll load error:', res?.status, errorText);
            return;
        }

        let data = await res.json();
        console.log('Payroll data received:', data ? data.length : 0, 'records');

        // Filter safeguard (already filtered by API if parameters were provided)
        if (selectedMonthVal && selectedYearVal) {
            const selectedMonth = parseInt(selectedMonthVal);
            const selectedYear = parseInt(selectedYearVal);
            data = data.filter(p => parseInt(p.month) === selectedMonth && parseInt(p.year) === selectedYear);
            console.log('Filtered data:', data.length, 'records');
        }

        if (!data || data.length === 0) {
            const monthText = selectedMonthVal ? monthNames[parseInt(selectedMonthVal) - 1] : '';
            const msg = selectedMonthVal && selectedYearVal ? `No payroll records for ${monthText} ${selectedYearVal}. Generate payroll first.` : 'No payroll records found.';
            tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; padding: 2rem;">${msg}</td></tr>`;
            return;
        }

        allPayrollData = data;
        renderPayrollTable(data);

        // Hide administrative buttons for anyone who is not HR/Admin (Accountant, Director, Leaves, Employee)
        const isHRorAdmin = auth.hasRole('ROLE_HR') || auth.hasRole('ROLE_ADMIN');
        if (!isHRorAdmin) {
            const clearBtn = document.querySelector('button[onclick="clearAllPayrolls()"]');
            const genBtn = document.querySelector('button[onclick="generatePayroll()"]');
            if (clearBtn) clearBtn.style.display = 'none';
            if (genBtn) genBtn.style.display = 'none';
        }
    } catch (e) {
        console.error('Payroll load error:', e);
        document.getElementById('payroll-table').innerHTML = `<tr><td colspan="8" style="text-align: center; padding: 2rem; color: #dc2626;">❌ Error: ${e.message}</td></tr>`;
    }
}

function renderPayrollTable(data) {
    const tbody = document.getElementById('payroll-table');
    if (!data || data.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; padding: 2rem;">No matching payroll records found.</td></tr>`;
        return;
    }
    
    tbody.innerHTML = data.map(p => {
        const statusClass = (p.status || 'PENDING').toLowerCase().replace('_', '-');
        const statusLabels = {
            'PENDING': 'Pending',
            'PENDING_APPROVAL': 'Pending Approval',
            'APPROVED': 'Approved',
            'PAID': 'Paid'
        };
        const statusLabel = statusLabels[p.status] || p.status || 'Pending';

        // Build action buttons based on status workflow (Only for Admins/HR, not Accountant/Director/Leaves/Employee)
        let actions = '';
        const isHRorAdmin = auth.hasRole('ROLE_HR') || auth.hasRole('ROLE_ADMIN');

        if (isHRorAdmin) {
            if (p.status === 'PENDING') {
                actions = `<button onclick="sendForApproval(${p.id})" class="btn-small" style="background: #dbeafe; color: #1e40af;">Send for Approval</button>`;
            } else if (p.status === 'PENDING_APPROVAL') {
                actions = `<span style="color: #f59e0b; font-size: 0.875rem; margin-right: 0.25rem;">⏳ Waiting for Accountant</span>
                           <button onclick="unlockPayroll(${p.id})" class="btn-small" style="background: #ef4444; color: white; font-weight: 600;">🔓 Unlock</button>`;
            } else if (p.status === 'APPROVED') {
                actions = `<button onclick="payPayroll(${p.id})" class="btn-small" style="background: #16a34a; color: white; font-weight: 600;">💰 Pay Now</button>
                           <button onclick="unlockPayroll(${p.id})" class="btn-small" style="background: #ef4444; color: white; font-weight: 600; margin-left: 0.25rem;">🔓 Unlock</button>`;
            } else if (p.status === 'PAID') {
                actions = `<span class="badge-paid" style="background: #dcfce7; color: #166534; padding: 0.25rem 0.75rem; border-radius: 9999px; font-size: 0.875rem; font-weight: 600;">✓ Successfully Paid</span>
                           <button onclick="unlockPayroll(${p.id})" class="btn-small" style="background: #ef4444; color: white; font-weight: 600; margin-left: 0.5rem;">🔓 Unlock</button>`;
            }
        } else {
            // For Accountant/Director, display read-only status labels instead of action buttons
            if (p.status === 'PENDING') {
                actions = `<span style="color: #6b7280; font-size: 0.875rem; margin-right: 0.5rem;">Draft</span>`;
            } else if (p.status === 'PENDING_APPROVAL') {
                actions = `<span style="color: #f59e0b; font-size: 0.875rem; margin-right: 0.5rem;">Pending Approval</span>`;
            } else if (p.status === 'APPROVED') {
                actions = `<span style="color: #2563eb; font-size: 0.875rem; margin-right: 0.5rem;">Approved</span>`;
            } else if (p.status === 'PAID') {
                actions = `<span class="badge-paid" style="background: #dcfce7; color: #166534; padding: 0.25rem 0.75rem; border-radius: 9999px; font-size: 0.875rem; font-weight: 600; margin-right: 0.5rem;">Paid</span>`;
            }
        }

        // ✅ Simplified: Use backend AttendanceEngine calculations directly
        const _gross = p.grossSalary || 0;
        const _totalDed = p.totalDeduction ?? p.totalDeductions ?? 0;
        const _netSal = p.netSalary ?? Math.max(0, _gross - _totalDed);

        return `
        <tr>
            <td>${p.employeeName || 'Unknown'}</td>
            <td>${monthNames[p.month - 1]} ${p.year}</td>
            <td>${auth.formatCurrency(p.basicSalary || 0)}</td>
            <td>${auth.formatCurrency(_gross)}</td>
            <td>${auth.formatCurrency(p.totalDeductions ?? p.totalDeduction ?? 0)}</td>
            <td><strong>${auth.formatCurrency(p.netSalary ?? Math.max(0, _gross - (p.totalDeductions ?? p.totalDeduction ?? 0)))}</strong></td>
            <td><span class="status-badge ${statusClass}">${statusLabel}</span></td>
            <td>
                ${actions}
                <button onclick="viewPayslipDetails(${p.id})" class="btn-small" style="background: #f3f4f6; color: #374151;">👁️ View</button>
            </td>
        </tr>
    `}).join('');
}

function filterPayroll() {
    const query = document.getElementById('payroll-search').value.toLowerCase().trim();
    if (!query) {
        renderPayrollTable(allPayrollData);
        return;
    }
    
    const filtered = allPayrollData.filter(p => {
        return p.employeeName && p.employeeName.toLowerCase().includes(query);
    });
    
    renderPayrollTable(filtered);
}

async function generatePayroll() {
    const month = document.getElementById('month-filter').value;
    const year = document.getElementById('year-filter').value;
    const monthNames = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

    if (!month || !year) {
        auth.showNotification('Please select a specific month and year to generate payroll.', 'error');
        return;
    }

    const btn = document.querySelector('button[onclick="generatePayroll()"]');
    const originalText = btn ? btn.innerHTML : 'Generate Payroll';
    
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<svg class="spinner" style="width: 16px; height: 16px; display: inline-block; animation: spin 1s linear infinite; margin-right: 8px;" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path></svg> Generating...';
        btn.style.opacity = '0.8';
        btn.style.cursor = 'wait';
    }

    console.log(`Generating payroll for ${monthNames[month - 1]} ${year}...`);

    try {
        const res = await auth.apiCall('/api/payroll/generate-all', {
            method: 'POST',
            body: JSON.stringify({ month: parseInt(month), year: parseInt(year) })
        });

        if (res && res.ok) {
            const data = await res.json();
            console.log('Generated payrolls:', data);
            auth.showNotification('Payroll generated Successfully', 'success');
            setTimeout(() => loadPayroll(), 500); // Small delay to ensure DB commit
        } else {
            const error = await res.text();
            console.error('Generate payroll failed:', error);
            auth.showNotification('Failed: ' + error, 'error');
        }
    } catch (e) {
        console.error('Generate error:', e);
        auth.showNotification(e.message, 'error');
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = originalText;
            btn.style.opacity = '1';
            btn.style.cursor = 'pointer';
        }
    }
}

async function clearAllPayrolls() {
    if (!confirm('Are you sure you want to delete ALL payroll records? This action cannot be undone. After deletion, you will need to regenerate payroll to see the updated calculations.')) return;
    try {
        const res = await auth.apiCall('/api/payroll/clear-all', {
            method: 'DELETE'
        });
        if (res && res.ok) {
            auth.showNotification('All payroll records deleted successfully', 'success');
            loadPayroll();
        }
    } catch (e) { auth.showNotification(e.message, 'error'); }
}

async function sendForApproval(id) {
    try {
        const res = await auth.apiCall(`/api/payroll/send-for-approval/${id}`, {
            method: 'POST',
            body: JSON.stringify({ approvedBy: 'HR' })
        });
        if (res && res.ok) {
            auth.showNotification('Payroll sent for approval', 'success');
            loadPayroll();
        }
    } catch (e) { auth.showNotification(e.message, 'error'); }
}

async function payPayroll(id) {
    if (!confirm('Are you sure you want to mark this payroll as PAID? This will finalize the payment process.')) return;
    try {
        const res = await auth.apiCall(`/api/payroll/pay/${id}`, { method: 'POST' });
        if (res && res.ok) {
            auth.showNotification('✓ Payroll paid successfully! Employee can now view and download payslip.', 'success');
            loadPayroll();
        }
    } catch (e) { auth.showNotification(e.message, 'error'); }
}

async function approvePayroll(id) {
    try {
        const res = await auth.apiCall(`/api/payroll/approve/${id}`, {
            method: 'POST',
            body: JSON.stringify({ accountantName: 'Accountant' })
        });
        if (res && res.ok) {
            auth.showNotification('Payroll approved by accountant', 'success');
            loadPayroll();
        }
    } catch (e) { auth.showNotification(e.message, 'error'); }
}

async function rejectPayroll(id) {
    try {
        const res = await auth.apiCall(`/api/payroll/reject/${id}`, {
            method: 'POST',
            body: JSON.stringify({ reason: 'Rejected by accountant' })
        });
        if (res && res.ok) {
            auth.showNotification('Payroll rejected', 'success');
            loadPayroll();
        }
    } catch (e) { auth.showNotification(e.message, 'error'); }
}

async function unlockPayroll(id) {
    if (!confirm('Are you sure you want to UNLOCK this payroll? This will revert its status and allow modifications.')) return;
    try {
        const res = await auth.apiCall(`/api/payroll/unlock/${id}`, { method: 'POST' });
        if (res && res.ok) {
            auth.showNotification('🔓 Payroll unlocked successfully!', 'success');
            loadPayroll();
        } else {
            const error = await res.text();
            auth.showNotification('Failed to unlock payroll: ' + error, 'error');
        }
    } catch (e) { auth.showNotification(e.message, 'error'); }
}

async function viewPayslipDetails(id) {
    try {
        // Fetch payroll data from API for a single ID
        const payrollRes = await auth.apiCall(`/api/payroll/${id}?t=` + Date.now());
        if (!payrollRes || !payrollRes.ok) {
            console.error('Payroll not found for id:', id);
            alert('Payroll not found');
            return;
        }
        const payroll = await payrollRes.json();
        console.log('Found payroll:', payroll);
        currentPayrollData = payroll;
        const monthYear = `${payroll.year}-${String(payroll.month).padStart(2, '0')}`;
        console.log('Fetching payslip for employeeId:', payroll.employeeId, 'monthYear:', monthYear);

        // Fetch payslip by employee and month/year
        const url = `/api/payslips/by-employee-month?employeeId=${payroll.employeeId}&monthYear=${monthYear}`;
        console.log('API URL:', url);
        const res = await auth.apiCall(url);
        console.log('Payslip API response:', res);

        let p;
        if (!res || !res.ok) {
            // Payslip not found - use payroll data directly
            console.log('Payslip not found, using payroll data directly');
            const errorText = await res.text();
            console.log('API error (expected for missing payslip):', res.status, errorText);

            // Use payroll data as payslip data
            p = {
                ...payroll,
                monthYear: monthYear,
                status: payroll.status || 'PENDING',
                absentLeaveDeduction: payroll.absentLeaveDeduction || 0,
                unpaidLeaveDeduction: payroll.unpaidLeaveDeduction || 0,
                absentPenaltyDeduction: payroll.absentPenaltyDeduction || 0,
                // Map payroll field names to payslip field names
                pf: payroll.providentFund,
                incomeTax: payroll.tax,
                otherDeduction: payroll.otherDeductions,
                totalDeduction: (payroll.totalDeductions || 0),
                paidLeaveDays: payroll.paidLeaveDays || 0,
                unpaidLeaveDays: payroll.unpaidLeaveDays || 0,
                leaveBalance: payroll.leaveBalance
            };
            console.log('Using payroll data as payslip directly:', p);
        } else {
            p = await res.json();
            console.log('Payslip data:', p);
        }

        const modal = document.getElementById('payslip-modal');
        const content = document.getElementById('modal-content-body');

        const statusColors = {
            'PENDING': '#f59e0b',
            'PENDING_APPROVAL': '#3b82f6',
            'APPROVED': '#8b5cf6',
            'PAID': '#16a34a'
        };
        const statusColor = statusColors[p.status] || '#6b7280';

        content.innerHTML = `
            <div style="text-align: center; margin-bottom: 1.5rem; padding-bottom: 1rem; border-bottom: 2px solid #e5e7eb;">
                <h3 style="margin: 0; font-size: 1.5rem;">PAYSLIP</h3>
                <p style="margin: 0.25rem 0; color: #6b7280;">${p.monthYear || 'N/A'}</p>
                <p style="margin: 0; font-weight: 600;">${p.employeeName || 'Employee'}</p>
                <span style="display: inline-block; margin-top: 0.5rem; background: ${statusColor}20; color: ${statusColor}; padding: 0.25rem 0.75rem; border-radius: 9999px; font-size: 0.875rem; font-weight: 600;">${p.status || 'Pending'}</span>
            </div>
            
            <!-- Paid Leave Balance Section -->
            <div style="margin-bottom: 1rem;">
                <h4 style="margin: 0 0 0.5rem 0; font-size: 1rem; color: #374151;">💰 Paid Leave Balance</h4>
                <div style="background: #f0fdf4; padding: 0.75rem; border-radius: 8px; border: 1px solid #86efac;">
                    <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 0.5rem; margin-bottom: 0.5rem;">
                        <div style="text-align: center; padding: 0.5rem; background: white; border-radius: 6px;">
                            <p style="margin: 0; font-size: 0.75rem; color: #6b7280;">Total Earned</p>
                            <p style="margin: 0; font-size: 1.25rem; font-weight: 600; color: #16a34a;">${((p.leaveBalance && typeof p.leaveBalance.totalEarnedLeaves === 'number') ? p.leaveBalance.totalEarnedLeaves : 0).toFixed(1)}</p>
                        </div>
                        <div style="text-align: center; padding: 0.5rem; background: white; border-radius: 6px;">
                            ${p.inProbation || p.probationStatus === 'In Progress' ?
                `<p style="margin: 0; font-size: 0.75rem; color: #6b7280;">Probation Status</p>
                                <p style="margin: 0; font-size: 1rem; font-weight: 600; color: #dc2626;">⏳ In Progress (${(p.probationMonths !== null && p.probationMonths !== undefined) ? p.probationMonths : 3} months)</p>
                                <p style="margin: 4px 0 0 0; font-size: 0.7rem; color: #64748b;">Joined: ${p.joinDate ? new Date(p.joinDate).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : 'N/A'}<br>Complete: ${p.joinDate ? new Date(new Date(p.joinDate).setMonth(new Date(p.joinDate).getMonth() + ((p.probationMonths !== null && p.probationMonths !== undefined) ? p.probationMonths : 3))).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : 'N/A'}</p>` :
                `<p style="margin: 0; font-size: 0.75rem; color: #6b7280;">Probation Status</p>
                                <p style="margin: 0; font-size: 1rem; font-weight: 600; color: #16a34a;">✅ Completed (${(p.probationMonths !== null && p.probationMonths !== undefined) ? p.probationMonths : 3} months)</p>
                                <p style="margin: 4px 0 0 0; font-size: 0.7rem; color: #64748b;">Joined: ${p.joinDate ? new Date(p.joinDate).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : 'N/A'}<br>Completed: ${p.joinDate ? new Date(new Date(p.joinDate).setMonth(new Date(p.joinDate).getMonth() + ((p.probationMonths !== null && p.probationMonths !== undefined) ? p.probationMonths : 3))).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : 'N/A'}</p>`
            }
                        </div>
                    </div>
                    <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 0.5rem;">
                        <div style="text-align: center; padding: 0.5rem; background: white; border-radius: 6px;">
                            <p style="margin: 0; font-size: 0.75rem; color: #6b7280;">Total Used Leaves</p>
                            <p style="margin: 0; font-size: 1.25rem; font-weight: 600; color: #d97706;">${((p.leaveBalance && typeof p.leaveBalance.usedLeaves === 'number') ? p.leaveBalance.usedLeaves : 0).toFixed(1)}</p>
                        </div>
                        <div style="text-align: center; padding: 0.5rem; background: white; border-radius: 6px;">
                            <p style="margin: 0; font-size: 0.75rem; color: #6b7280;">Remaining</p>
                            <p style="margin: 0; font-size: 1.25rem; font-weight: 600; color: #16a34a;">${((p.leaveBalance && typeof p.leaveBalance.availableLeaves === 'number') ? p.leaveBalance.availableLeaves : 0).toFixed(1)}</p>
                        </div>
                </div >
            </div >
            

            <div style="margin-bottom: 1rem;">
                <h4 style="margin: 0 0 0.5rem 0; font-size: 1rem; color: #374151;">📊 Attendance & Leave Summary</h4>
                <div style="background: #f0f9ff; padding: 0.75rem; border-radius: 8px; border: 1px solid #bae6fd;">
                    <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 0.5rem; margin-bottom: 0.5rem;">
                        <div style="text-align: center; padding: 0.5rem; background: white; border-radius: 6px;">
                            <p style="margin: 0; font-size: 0.75rem; color: #6b7280;">Present Days</p>
                            <p style="margin: 0; font-size: 1.25rem; font-weight: 600; color: #059669;">${(p.presentDays || 0).toFixed(1)}</p>
                        </div>
                        <div style="text-align: center; padding: 0.5rem; background: white; border-radius: 6px;">
                            <p style="margin: 0; font-size: 0.75rem; color: #6b7280;">Absent Days</p>
                            <p style="margin: 0; font-size: 1.25rem; font-weight: 600; color: #dc2626;">${(p.absentDays || 0).toFixed(1)}</p>
                        </div>
                    </div>
                    <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 0.5rem;">
                        <div style="text-align: center; padding: 0.5rem; background: #f0fdf4; border-radius: 6px;">
                            <p style="margin: 0; font-size: 0.75rem; color: #6b7280;">Paid Used Leaves</p>
                            <p style="margin: 0; font-size: 1.25rem; font-weight: 600; color: #16a34a;">${(p.paidLeaveDays || 0).toFixed(1)}</p>
                        </div>
                        <div style="text-align: center; padding: 0.5rem; background: #fef2f2; border-radius: 6px;">
                            <p style="margin: 0; font-size: 0.75rem; color: #6b7280;">Unpaid Used Leaves</p>
                            <p style="margin: 0; font-size: 1.25rem; font-weight: 600; color: #dc2626;">${(p.unpaidLeaveDays || 0).toFixed(1)}</p>
                        </div>
                    </div>
                </div>
            </div>
            
            <div style="margin-bottom: 1rem;">
                <h4 style="margin: 0 0 0.5rem 0; font-size: 1rem; color: #374151;">Earnings</h4>
                <div style="background: #f0fdf4; padding: 0.75rem; border-radius: 8px;">
                    <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Basic Salary</span><span>${auth.formatCurrency(p.basicSalary || 0)}</span></div>
                    <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>HRA</span><span>${auth.formatCurrency(p.hra || 0)}</span></div>
                    <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Special Allowance</span><span>${auth.formatCurrency(p.specialAllowance || 0)}</span></div>
                    <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Bonus</span><span>${auth.formatCurrency(p.bonus || 0)}</span></div>
                    <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Incentive</span><span>${auth.formatCurrency(p.incentive || 0)}</span></div>
                    <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Other Allowance</span><span>${auth.formatCurrency(p.otherAllowance || 0)}</span></div>
                    <div style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-top: 1px solid #86efac; margin-top: 0.5rem; font-weight: 600;">
                        <span>Gross Salary</span><span style="color: #16a34a;">${auth.formatCurrency(p.grossSalary || 0)}</span>
                    </div>
                </div>
            </div>
            
            <div style="margin-bottom: 1rem;">
                <h4 style="margin: 0 0 0.5rem 0; font-size: 1rem; color: #374151;">Attendance Deductions</h4>
                <div style="background: #fef2f2; padding: 0.75rem; border-radius: 8px;">
                    ${(() => {
                const unpaidDed = p.unpaidLeaveDeduction || 0;
                const absentDed = p.absentPenaltyDeduction || p.absentDeduction || 0;
                const totalAttDed = unpaidDed;
                let html = '';
                if ((p.absentDays || 0) > 0) {
                    html += `<div style="display: flex; justify-content: space-between; margin-bottom: 0.25rem;"><span>Absent (${p.absentDays} days &times; 1&times; daily rate)</span><span style="color: #dc2626;">-${auth.formatCurrency(absentDed)}</span></div>`;
                }
                if ((p.unpaidLeaveDays || 0) > 0) {
                    html += `<div style="display: flex; justify-content: space-between; margin-bottom: 0.25rem;"><span>Unpaid Used Leaves (${p.unpaidLeaveDays} days &times; 1&times; daily rate)</span><span style="color: #dc2626;">-${auth.formatCurrency(unpaidDed)}</span></div>`;
                }
                if (totalAttDed > 0) {
                    html += `<div style="display: flex; justify-content: space-between; margin-top: 0.5rem; padding-top: 0.5rem; border-top: 1px solid #e5e7eb;"><span><strong>Total Attendance Deduction:</strong></span><strong>-${auth.formatCurrency(totalAttDed)}</strong></div>`;
                } else {
                    html += '<div style="color: #059669; text-align: center;">✓ No attendance deductions</div>';
                }
                return html;
            })()}
                </div>
            </div>

            <div style="margin-bottom: 1rem;">
                <h4 style="margin: 0 0 0.5rem 0; font-size: 1rem; color: #374151;">Other Deductions</h4>
                <div style="background: #fef2f2; padding: 0.75rem; border-radius: 8px;">
                    <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Provident Fund</span><span style="color: #dc2626;">-${auth.formatCurrency(p.pf || p.providentFund || 0)}</span></div>
                    <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>ESIC</span><span style="color: #dc2626;">-${auth.formatCurrency(p.esic || 0)}</span></div>
                    <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Prof Tax</span><span style="color: #dc2626;">-${auth.formatCurrency(p.professionalTax || 0)}</span></div>
                    <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>TDS</span><span style="color: #dc2626;">-${auth.formatCurrency(p.tds || p.tax || p.incomeTax || 0)}</span></div>
                    <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Loan/Other Deduction</span><span style="color: #dc2626;">-${auth.formatCurrency(p.loanDeduction || 0)}</span></div>
                    <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>LWF</span><span style="color: #dc2626;">-${auth.formatCurrency(p.lwf || 0)}</span></div>
                    <div style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-top: 1px solid #fca5a5; margin-top: 0.5rem; font-weight: 600;">
                        <span>Total Other Deductions</span>
                        <span style="color: #dc2626;">-${auth.formatCurrency((p.pf || p.providentFund || 0) + (p.esic || 0) + (p.professionalTax || 0) + (p.tds || p.tax || p.incomeTax || 0) + (p.loanDeduction || 0) + (p.lwf || 0))}</span>
                    </div>
                </div>
            </div>

            <div style="margin-bottom: 1rem;">
                <h4 style="margin: 0 0 0.5rem 0; font-size: 1rem; color: #374151;">Total Deductions</h4>
                <div style="background: #fef2f2; padding: 0.75rem; border-radius: 8px;">
                    <div style="display: flex; justify-content: space-between; padding: 0.5rem 0; font-weight: 600; font-size: 1.1rem;">
                        <span>Total Deductions</span>
                        <span style="color: #dc2626;">-${auth.formatCurrency(p.totalDeductions ?? p.totalDeduction ?? 0)}</span>
                    </div>
                </div>
            </div>

            <div style="background: #eff6ff; padding: 1rem; border-radius: 8px; text-align: center; margin-bottom: 1rem;">
                <p style="margin: 0; color: #6b7280; font-size: 0.875rem;">Net Salary</p>
                <p style="margin: 0.25rem 0; font-size: 1.5rem; font-weight: 700; color: #1e40af;">${auth.formatCurrency(p.netSalary || 0)}</p>
            </div>
            
            <div style="display: flex; gap: 0.5rem;">
                <button onclick="closeModal()" class="btn-small" style="flex: 1; background: #f3f4f6; color: #374151;">Close</button>
            </div>
    `;

        modal.style.display = 'flex';
    } catch (e) {
        console.error('Error in viewPayslipDetails:', e);
        alert('Error loading payroll details: ' + e.message);
    }
}

function showPayrollDataInModal(payroll) {
    try {
        console.log('Showing payroll data in modal:', payroll);
        const modal = document.getElementById('payslip-modal');
        const content = document.getElementById('modal-content-body');
        const statusColors = {
            'PENDING_APPROVAL': '#3b82f6',
            'APPROVED': '#8b5cf6',
            'PAID': '#16a34a'
        };
        const statusColor = statusColors[payroll.status] || '#6b7280';

        content.innerHTML = `
        < div style = "text-align: center; margin-bottom: 1.5rem; padding-bottom: 1rem; border-bottom: 2px solid #e5e7eb;" >
                <h3 style="margin: 0; font-size: 1.5rem;">PAYROLL DETAILS</h3>
                <p style="margin: 0.25rem 0; color: #6b7280;">${payroll.monthYear || 'N/A'}</p>
                <p style="margin: 0; font-weight: 600;">${payroll.employeeName || 'Employee'}</p>
                <span style="display: inline-block; margin-top: 0.5rem; background: ${statusColor}20; color: ${statusColor}; padding: 0.25rem 0.75rem; border-radius: 9999px; font-size: 0.875rem; font-weight: 600;">${payroll.status || 'Pending'}</span>
            </div >
            
            <div style="background: #fefce8; border: 1px solid #fde047; border-radius: 8px; padding: 1rem; margin-bottom: 1rem;">
                <p style="margin: 0; color: #854d0e; font-size: 0.875rem;">ℹ️ Payslip not yet generated. Showing payroll data instead.</p>
            </div>
    
    <!--Leave Details Section-- >
    <div style="margin-bottom: 1rem;">
        <h4 style="margin: 0 0 0.5rem 0; font-size: 1rem; color: #374151;">Leave Details</h4>
        <div style="background: #f0f9ff; padding: 0.75rem; border-radius: 8px; border: 1px solid #bae6fd;">
            <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 0.5rem;">
                <div style="text-align: center; padding: 0.5rem; background: white; border-radius: 6px;">
                    <p style="margin: 0; font-size: 0.75rem; color: #6b7280;">Total Days</p>
                    <!-- ✅ FIXED: Use backend values only - no frontend calculation -->
                    <p style="margin: 0; font-size: 1.25rem; font-weight: 600; color: #0284c7;">${payroll.leaveDays || 0}</p>
                </div>
                <div style="text-align: center; padding: 0.5rem; background: white; border-radius: 6px;">
                    <!-- ✅ FIXED: Use backend values only - no frontend calculation -->
                    <p style="margin: 0; font-size: 0.75rem; color: #6b7280;">Paid Days</p>
                    <p style="margin: 0; font-size: 1.25rem; font-weight: 600; color: #16a34a;">${payroll.paidLeaveDays || 0}</p>
                </div>
                <div style="text-align: center; padding: 0.5rem; background: white; border-radius: 6px;">
                    <p style="margin: 0; font-size: 0.75rem; color: #6b7280;">Unpaid Days</p>
                    <p style="margin: 0; font-size: 1.25rem; font-weight: 600; color: #16a34a;">${payroll.unpaidLeaveDays || 0}</p>
                </div>
                <div style="text-align: center; padding: 0.5rem; background: white; border-radius: 6px;">
                    <p style="margin: 0; font-size: 0.75rem; color: #6b7280;">Half Days</p>
                    <p style="margin: 0; font-size: 1.25rem; font-weight: 600; color: #f59e0b;">${payroll.halfDays || 0}</p>
                </div>
            </div>
            ${payroll.absentDays > 0 ? `<div style="margin-top: 0.5rem; padding: 0.5rem; background: #fef2f2; border-radius: 6px; text-align: center;">
                <p style="margin: 0; font-size: 0.875rem; color: #dc2626;">Absent Days: <strong>${payroll.absentDays}</strong></p>
            </div>` : ''}
        </div>
    </div>
    
    <div style="margin-bottom: 1rem;">
        <h4 style="margin: 0 0 0.5rem 0; font-size: 1rem; color: #374151;">Earnings</h4>
        <div style="background: #f0fdf4; padding: 0.75rem; border-radius: 8px;">
            <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Basic Salary</span><span>${auth.formatCurrency(payroll.basicSalary || 0)}</span></div>
            <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>HRA</span><span>${auth.formatCurrency(payroll.hra || 0)}</span></div>

            <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Other Allowances</span><span>${auth.formatCurrency(payroll.otherAllowances || 0)}</span></div>
            <div style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-top: 1px solid #86efac; margin-top: 0.5rem; font-weight: 600;">
                <span>Gross Salary</span><span style="color: #16a34a;">${auth.formatCurrency(payroll.grossSalary || 0)}</span>
            </div>
        </div>
    </div>
    
    <div style="margin-bottom: 1rem;">
        <h4 style="margin: 0 0 0.5rem 0; font-size: 1rem; color: #374151;">Deductions</h4>
        <div style="background: #fef2f2; padding: 0.75rem; border-radius: 8px;">
            <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Provident Fund</span><span style="color: #dc2626;">-${auth.formatCurrency(payroll.providentFund || 0)}</span></div>
            <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Tax</span><span style="color: #dc2626;">-${auth.formatCurrency(payroll.tax || 0)}</span></div>
            <div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Other Deductions</span><span style="color: #dc2626;">-${auth.formatCurrency(payroll.otherDeductions || 0)}</span></div>
            ${payroll.absentLeaveDeduction > 0 ? `<div style="display: flex; justify-content: space-between; padding: 0.25rem 0;"><span>Absent/Leave Deduction</span><span style="color: #dc2626;">-${auth.formatCurrency(payroll.absentLeaveDeduction || 0)}</span></div>` : ''}
            <div style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-top: 1px solid #fca5a5; margin-top: 0.5rem; font-weight: 600;">
                <span>Total Deductions</span><span style="color: #dc2626;">-${auth.formatCurrency(payroll.totalDeductions || 0)}</span>
            </div>
        </div>
    </div>
    
    <div style="background: #eff6ff; padding: 1rem; border-radius: 8px; text-align: center; margin-bottom: 1rem;">
        <p style="margin: 0; color: #6b7280; font-size: 0.875rem;">Net Salary</p>
        <p style="margin: 0.25rem 0; font-size: 1.5rem; font-weight: 700; color: #1e40af;">${auth.formatCurrency(payroll.netSalary || 0)}</p>
    </div>

    `;
        modal.style.display = 'flex';
    } catch (e) {
        console.error('Error viewing payroll:', e);
        alert('Error loading payroll details');
    }
}

async function editPayroll(id) {
    try {
        const res = await auth.apiCall('/api/payroll/' + id);
        if (!res.ok) throw new Error('Failed to fetch payroll');
        const payroll = await res.json();
        currentPayrollData = payroll;

        const modal = document.getElementById('payslip-modal');
        const content = document.getElementById('modal-content-body');

        content.innerHTML = `
        < div style = "text-align: center; margin-bottom: 1.5rem;" >
            <h3 style="margin: 0; font-size: 1.5rem;">Edit Payroll</h3>
            <p style="margin: 0.25rem 0; color: #6b7280;">${payroll.monthYear || 'N/A'}</p>
            <p style="margin: 0; font-weight: 600;">${payroll.employeeName || 'Employee'}</p>
        </div >

        <form id="editPayrollForm">
            <div style="margin-bottom: 1rem;">
                <h4 style="margin: 0 0 0.5rem 0; font-size: 1rem; color: #374151;">Earnings</h4>
                <div style="background: #f0fdf4; padding: 0.75rem; border-radius: 8px;">
                    <div style="margin-bottom: 0.5rem;">
                        <label style="display: block; font-size: 0.875rem; margin-bottom: 0.25rem;">Basic Salary</label>
                        <input type="number" name="basicSalary" value="${payroll.basicSalary || 0}" style="width: 100%; padding: 0.5rem; border: 1px solid #d1d5db; border-radius: 4px;">
                    </div>
                    <div style="margin-bottom: 0.5rem;">
                        <label style="display: block; font-size: 0.875rem; margin-bottom: 0.25rem;">HRA</label>
                        <input type="number" name="hra" value="${payroll.hra || 0}" style="width: 100%; padding: 0.5rem; border: 1px solid #d1d5db; border-radius: 4px;">
                    </div>

                    <div style="margin-bottom: 0.5rem;">
                        <label style="display: block; font-size: 0.875rem; margin-bottom: 0.25rem;">Other Allowances</label>
                        <input type="number" name="otherAllowances" value="${payroll.otherAllowances || 0}" style="width: 100%; padding: 0.5rem; border: 1px solid #d1d5db; border-radius: 4px;">
                    </div>
                </div>
            </div>

            <div style="margin-bottom: 1rem;">
                <h4 style="margin: 0 0 0.5rem 0; font-size: 1rem; color: #374151;">Deductions</h4>
                <div style="background: #fef2f2; padding: 0.75rem; border-radius: 8px;">
                    <div style="margin-bottom: 0.5rem;">
                        <label style="display: block; font-size: 0.875rem; margin-bottom: 0.25rem;">Provident Fund (PF)</label>
                        <input type="number" name="providentFund" value="${payroll.providentFund || 0}" style="width: 100%; padding: 0.5rem; border: 1px solid #d1d5db; border-radius: 4px;">
                    </div>
                    <div style="margin-bottom: 0.5rem;">
                        <label style="display: block; font-size: 0.875rem; margin-bottom: 0.25rem;">Tax</label>
                        <input type="number" name="tax" value="${payroll.tax || 0}" style="width: 100%; padding: 0.5rem; border: 1px solid #d1d5db; border-radius: 4px;">
                    </div>
                    <div style="margin-bottom: 0.5rem;">
                        <label style="display: block; font-size: 0.875rem; margin-bottom: 0.25rem;">Other Deductions</label>
                        <input type="number" name="otherDeductions" value="${payroll.otherDeductions || 0}" style="width: 100%; padding: 0.5rem; border: 1px solid #d1d5db; border-radius: 4px;">
                    </div>
                </div>
            </div>

            <div style="display: flex; gap: 0.5rem;">
                <button type="button" onclick="savePayrollEdit(${id})" class="btn-small" style="flex: 1; background: #16a34a; color: white;">💾 Save Changes</button>
                <button type="button" onclick="closeModal()" class="btn-small" style="flex: 1; background: #f3f4f6; color: #374151;">Cancel</button>
            </div>
        </form>
    `;

        modal.style.display = 'flex';
    } catch (e) {
        console.error('Error loading payroll for edit:', e);
        alert('Error loading payroll details for editing');
    }
}

async function savePayrollEdit(id) {
    const form = document.getElementById('editPayrollForm');
    const formData = new FormData(form);

    const data = {
        basicSalary: parseFloat(formData.get('basicSalary')) || 0,
        hra: parseFloat(formData.get('hra')) || 0,

        otherAllowances: parseFloat(formData.get('otherAllowances')) || 0,
        providentFund: parseFloat(formData.get('providentFund')) || 0,
        tax: parseFloat(formData.get('tax')) || 0,
        insurance: 0, // Removed from UI, default to 0
        otherDeductions: parseFloat(formData.get('otherDeductions')) || 0
    };

    // ✅ FIXED: Remove duplicate calculations - use backend values only
    // grossSalary, totalDeductions, netSalary should come from AttendanceEngine

    try {
        const res = await auth.apiCall('/api/payroll/' + id, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });

        if (res.ok) {
            alert('Payroll updated successfully!');
            closeModal();
            loadPayroll();
        } else {
            const error = await res.text();
            alert('Error updating payroll: ' + error);
        }
    } catch (err) {
        alert('Error saving payroll: ' + err.message);
    }
}

async function updateAssociatedPayslip(payrollId, payrollData) {
    try {
        // Find payslip associated with this payroll
        const payslipRes = await auth.apiCall('/api/payslips');
        if (!payslipRes.ok) return;

        const payslips = await payslipRes.json();
        const associatedPayslip = payslips.find(p => p.payrollId === payrollId || (p.employeeId === currentPayrollData?.employeeId && p.monthYear === currentPayrollData?.monthYear));

        if (associatedPayslip) {
            // Update payslip with new payroll data
            const updateData = {
                basicSalary: payrollData.basicSalary,
                hra: payrollData.hra,

                otherAllowance: payrollData.otherAllowances,
                grossSalary: payrollData.grossSalary,
                providentFund: payrollData.providentFund,
                tax: payrollData.tax,
                insurance: 0,
                otherDeductions: payrollData.otherDeductions,
                totalDeductions: payrollData.totalDeductions,
                netSalary: payrollData.netSalary
            };

            await auth.apiCall('/api/payslips/' + associatedPayslip.id, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(updateData)
            });
            console.log('Associated payslip updated');
        }
    } catch (e) {
        console.error('Error updating associated payslip:', e);
    }
}

function closeModal() {
    document.getElementById('payslip-modal').style.display = 'none';
    currentPayrollData = null;
}

function downloadPayslip(id) {
    // If id is provided use it, otherwise get from current payslip data
    const payslipId = id || (currentPayrollData && currentPayrollData.payslipId);
    if (!payslipId) {
        auth.showNotification('Payslip ID not available', 'error');
        return;
    }
    const token = auth.getAuth().token;
    window.open(`/api/payslips/${payslipId}/download-pdf?token=${token}`, '_blank');
}

function logout() { auth.logout(); }

// Close modal when clicking outside
window.onclick = function (event) {
    const modal = document.getElementById('payslip-modal');
    if (event.target === modal) {
        closeModal();
    }
};

// Initialize on page load
document.addEventListener('DOMContentLoaded', function () {
    const now = new Date();
    document.getElementById('month-filter').value = now.getMonth() + 1;
    document.getElementById('year-filter').value = now.getFullYear();
    loadPayroll();
});

function refreshToCurrentMonth() {
    const now = new Date();
    document.getElementById('month-filter').value = now.getMonth() + 1;
    document.getElementById('year-filter').value = now.getFullYear();
    loadPayroll();
}



