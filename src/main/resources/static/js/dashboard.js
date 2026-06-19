// Dashboard functionality

// Check auth first
if (!auth.requireAuth()) {
    throw new Error('Authentication required');
}

// Initialize dashboard
document.addEventListener('DOMContentLoaded', () => {
    initializeDashboard();
});

async function initializeDashboard() {
    // Set user name
    const user = auth.getCurrentUser();
    if (user) {
        document.getElementById('user-name').textContent = user.username || user.employeeName || 'User';
    }

    // Load dashboard data
    await loadDashboardData();
}

async function loadDashboardData() {
    const loadingEl = document.getElementById('loading');
    const contentEl = document.getElementById('dashboard-content');

    try {
        // Fetch all data in parallel
        const [employeesRes, attendanceRes, leaveRes, payrollRes] = await Promise.all([
            auth.apiCall('/api/employees'),
            auth.apiCall('/api/dashboard/my/attendance'), // ✅ STANDARDIZED: Use same API as other pages
            auth.apiCall('/api/leaves'),
            auth.apiCall('/api/payroll/list')
        ]);

        const stats = {
            totalEmployees: 0,
            presentToday: 0,
            absentToday: 0,        // ✅ STANDARDIZED: Added absent days
            paidUsedLeaves: 0,    // ✅ STANDARDIZED: Added Paid Used Leaves
            unpaidUsedLeaves: 0,  // ✅ STANDARDIZED: Added Unpaid Used Leaves
            monthlyPayroll: 0,
            leaveRequests: 0
        };

        let employees = [];

        // Process employees
        if (employeesRes && employeesRes.ok) {
            employees = await employeesRes.json();
            stats.totalEmployees = employees.length || 0;
        }

        // Process attendance - ✅ STANDARDIZED: Use same field names as API
        if (attendanceRes && attendanceRes.ok) {
            const attendance = await attendanceRes.json();
            // API returns: { presentDays, absentDays, paidUsedLeaves, unpaidUsedLeaves, effectiveDays, totalHours }
            stats.presentToday = attendance.presentDays || 0;
            stats.absentToday = attendance.absentDays || 0;
            stats.paidUsedLeaves = attendance.paidUsedLeaves || 0;
            stats.unpaidUsedLeaves = attendance.unpaidUsedLeaves || 0;
        }

        // Process leave requests
        if (leaveRes && leaveRes.ok) {
            const leaves = await leaveRes.json();
            stats.leaveRequests = leaves.filter(l => l.status === 'PENDING' || l.status === 'Pending').length;
        }

        // Process payroll
        if (payrollRes && payrollRes.ok) {
            const payroll = await payrollRes.json();
            const currentMonth = new Date().getMonth() + 1;
            const currentYear = new Date().getFullYear();
            const currentMonthPayroll = payroll.filter(p => p.month === currentMonth && p.year === currentYear);
            stats.monthlyPayroll = currentMonthPayroll.reduce((sum, p) => sum + (p.netSalary || 0), 0);
        }

        // Update stats
        document.getElementById('stat-employees').textContent = stats.totalEmployees;
        document.getElementById('stat-present').textContent = stats.presentToday;
        document.getElementById('stat-payroll').textContent = auth.formatCurrency(stats.monthlyPayroll);
        document.getElementById('stat-leave').textContent = stats.leaveRequests;

        // Update recent employees table
        updateEmployeesTable(employees.slice(0, 5));

        // Show content, hide loading
        loadingEl.style.display = 'none';
        contentEl.style.display = 'block';

    } catch (error) {
        console.error('Error loading dashboard:', error);
        loadingEl.innerHTML = `
            <div style="text-align: center; color: #dc2626;">
                <p>Error loading dashboard data.</p>
                <button onclick="location.reload()" style="
                    margin-top: 1rem;
                    padding: 0.5rem 1rem;
                    background: #4f46e5;
                    color: white;
                    border: none;
                    border-radius: 6px;
                    cursor: pointer;
                ">Retry</button>
            </div>
        `;
    }
}

function updateEmployeesTable(employees) {
    const tbody = document.getElementById('employees-table');

    if (!employees || employees.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="3" style="text-align: center; color: #6b7280; padding: 2rem;">
                    No employees found
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = employees.map(emp => `
        <tr>
            <td>
                <div style="display: flex; align-items: center; gap: 0.75rem;">
                    <div style="
                        width: 32px;
                        height: 32px;
                        background: #4f46e5;
                        border-radius: 50%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        color: white;
                        font-size: 0.875rem;
                        font-weight: 600;
                    ">
                        ${(emp.firstName || '')[0]}${(emp.lastName || '')[0]}
                    </div>
                    <div>
                        <div style="font-weight: 500;">${emp.firstName || ''} ${emp.lastName || ''}</div>
                        <div style="font-size: 0.75rem; color: #6b7280;">${emp.designation || 'Employee'}</div>
                    </div>
                </div>
            </td>
            <td>${emp.department?.name || emp.department || 'N/A'}</td>
            <td>
                <span class="status-badge ${(emp.status || 'Active').toLowerCase()}">
                    ${emp.status || 'Active'}
                </span>
            </td>
        </tr>
    `).join('');
}

// Navigation helper
function navigateTo(page) {
    window.location.href = `/html/${page}.html`;
}

// Logout function (already in auth.js but here for button onclick)
function logout() {
    auth.logout();
}
