// employee-form.js – extracted from inline script in employee-form.html
if (!auth.requireAuth()) throw new Error('Auth required');

// Prevent read-only roles from accessing employee add/edit form
const userRole = auth.getUserRole();
if (userRole === 'ROLE_ACCOUNTANT' || userRole === 'ROLE_DIRECTOR' || userRole === 'ROLE_LEAVES') {
    auth.showNotification('Access denied: You do not have permission to add or edit employees', 'error');
    window.location.href = 'employees.html';
    throw new Error('Access denied');
}

// Load dropdowns
async function loadDropdowns() {
    try {
        const [deptRes, shiftRes] = await Promise.all([
            auth.apiCall('/api/departments'),
            auth.apiCall('/api/shifts')
        ]);

        if (deptRes && deptRes.ok) {
            const depts = await deptRes.json();
            const deptSelect = document.getElementById('departmentId');
            depts.forEach(d => {
                deptSelect.innerHTML += `<option value="${d.id}">${d.name}</option>`;
            });
        }

        if (shiftRes && shiftRes.ok) {
            const shifts = await shiftRes.json();
            const shiftSelect = document.getElementById('shiftId');
            shifts.forEach(s => {
                shiftSelect.innerHTML += `<option value="${s.id}">${s.name} (${s.startTime}-${s.endTime})</option>`;
            });
        }

        // Mark dropdowns as loaded
        dropdownsLoaded = true;
        console.log('Dropdowns loaded');

        // If employee data was already loaded, set form values now
        if (employeeData) {
            console.log('Setting employee values after dropdowns loaded');
            setEmployeeFormValues(employeeData);
        }
    } catch (e) { console.error('Error loading dropdowns:', e); }
}

// Calculate dynamic totals
function calculateTotal() {
    const basic = parseFloat(document.getElementById('basicSalary').value) || 0;
    const hra = parseFloat(document.getElementById('hra').value) || 0;
    const specialAllowance = parseFloat(document.getElementById('specialAllowance').value) || 0;
    const bonus = parseFloat(document.getElementById('bonus').value) || 0;
    const incentive = parseFloat(document.getElementById('incentive').value) || 0;
    const otherAllowance = parseFloat(document.getElementById('otherAllowance').value) || 0;

    const pf = parseFloat(document.getElementById('pf').value) || 0;
    const esic = parseFloat(document.getElementById('esic').value) || 0;
    const pt = parseFloat(document.getElementById('professionalTax').value) || 0;
    const tds = parseFloat(document.getElementById('tds').value) || 0;
    const loan = parseFloat(document.getElementById('loanDeduction').value) || 0;
    const lwf = parseFloat(document.getElementById('lwf').value) || 0;
    // Gross salary
    const gross = basic + hra + specialAllowance + bonus + incentive + otherAllowance;
    document.getElementById('salary').value = gross.toFixed(2);

    // Total Deductions
    const totalDeductions = pf + esic + pt + tds + loan + lwf;
    document.getElementById('totalDeductions').value = totalDeductions.toFixed(2);

    // Net Pay
    const netPay = gross - totalDeductions;
    document.getElementById('netPay').value = netPay.toFixed(2);
}

// Check if editing
const urlParams = new URLSearchParams(window.location.search);
const editId = urlParams.get('edit');

// Store employee data globally to set after dropdowns load
let employeeData = null;
let dropdownsLoaded = false;

if (editId) {
    document.getElementById('page-title').textContent = 'Edit Employee';
    document.getElementById('submit-btn').textContent = '💾 Update Employee';
    // Show delete button for HR/Admin only
    const isHR = auth.hasRole('ROLE_HR') || auth.hasRole('ROLE_ADMIN');
    if (isHR) {
        const deleteBtn = document.getElementById('delete-btn');
        deleteBtn.style.display = 'inline-flex';
    }
    loadEmployee(editId);
}

async function loadEmployee(id) {
    try {
        const res = await auth.apiCall(`/api/employees/${id}`);
        if (res && res.ok) {
            employeeData = await res.json();
            console.log('Loaded employee:', employeeData);

            // If dropdowns already loaded, set values now
            if (dropdownsLoaded) {
                setEmployeeFormValues(employeeData);
            }
        }
    } catch (e) { console.error('Error loading employee:', e); }
}

function setEmployeeFormValues(emp) {
    if (!emp) return;

    document.getElementById('firstName').value = emp.firstName || '';
    document.getElementById('lastName').value = emp.lastName || '';
    document.getElementById('email').value = emp.email || '';
    document.getElementById('phone').value = emp.phone || '';
    const genderValue = emp.gender ? emp.gender.charAt(0) + emp.gender.slice(1).toLowerCase() : '';
    document.getElementById('gender').value = genderValue;
    document.getElementById('joiningDate').value = emp.joiningDate || '';
    document.getElementById('address').value = emp.address || '';
    document.getElementById('employeeId').value = emp.employeeId || '';
    document.getElementById('designation').value = emp.designation || '';
    document.getElementById('status').value = emp.status || 'ACTIVE';
    document.getElementById('probationPeriodMonths').value = (emp.probationPeriodMonths !== null && emp.probationPeriodMonths !== undefined) ? emp.probationPeriodMonths : 3;
    document.getElementById('basicSalary').value = emp.basicSalary || 0;
    document.getElementById('hra').value = emp.hra || 0;
    document.getElementById('specialAllowance').value = emp.specialAllowance || 0;
    document.getElementById('bonus').value = emp.bonus || 0;
    document.getElementById('incentive').value = emp.incentive || 0;
    document.getElementById('otherAllowance').value = emp.otherAllowance || 0;
    document.getElementById('pf').value = emp.pf || 0;
    document.getElementById('uanNo').value = emp.uanNo || '';
    document.getElementById('esic').value = emp.esic || 0;
    document.getElementById('professionalTax').value = emp.professionalTax || 0;
    document.getElementById('tds').value = emp.tds || emp.tax || 0;
    document.getElementById('loanDeduction').value = emp.loanDeduction || 0;
    document.getElementById('lwf').value = emp.lwf || 0;

    // Set dropdown values
    if (emp.departmentId) {
        const deptSelect = document.getElementById('departmentId');
        if (deptSelect.querySelector(`option[value="${emp.departmentId}"]`)) {
            deptSelect.value = emp.departmentId;
        }
    }

    if (emp.shiftId) {
        const shiftSelect = document.getElementById('shiftId');
        if (shiftSelect.querySelector(`option[value="${emp.shiftId}"]`)) {
            shiftSelect.value = emp.shiftId;
        }
    }

    calculateTotal();
}

// Form submit
document.getElementById('employee-form').addEventListener('submit', async (e) => {
    e.preventDefault();

    const data = {
        firstName: document.getElementById('firstName').value,
        lastName: document.getElementById('lastName').value,
        email: document.getElementById('email').value,
        phone: document.getElementById('phone').value,
        gender: document.getElementById('gender').value,
        joiningDate: document.getElementById('joiningDate').value,
        address: document.getElementById('address').value,
        departmentId: parseInt(document.getElementById('departmentId').value),
        designation: document.getElementById('designation').value,
        shiftId: document.getElementById('shiftId').value ? parseInt(document.getElementById('shiftId').value) : null,
        status: document.getElementById('status').value,
        probationPeriodMonths: parseInt(document.getElementById('probationPeriodMonths').value) || 0,
        employeeId: document.getElementById('employeeId').value,
        basicSalary: parseFloat(document.getElementById('basicSalary').value) || 0,
        hra: parseFloat(document.getElementById('hra').value) || 0,
        specialAllowance: parseFloat(document.getElementById('specialAllowance').value) || 0,
        bonus: parseFloat(document.getElementById('bonus').value) || 0,
        incentive: parseFloat(document.getElementById('incentive').value) || 0,
        otherAllowance: parseFloat(document.getElementById('otherAllowance').value) || 0,
        pf: parseFloat(document.getElementById('pf').value) || 0,
        uanNo: document.getElementById('uanNo').value,
        esic: parseFloat(document.getElementById('esic').value) || 0,
        professionalTax: parseFloat(document.getElementById('professionalTax').value) || 0,
        tds: parseFloat(document.getElementById('tds').value) || 0,
        loanDeduction: parseFloat(document.getElementById('loanDeduction').value) || 0,
        lwf: parseFloat(document.getElementById('lwf').value) || 0,
        salary: parseFloat(document.getElementById('salary').value) || 0
    };

    try {
        const url = editId ? `/api/employees/${editId}` : '/api/employees';
        const method = editId ? 'PUT' : 'POST';
        const res = await auth.apiCall(url, { method, body: JSON.stringify(data) });

        if (res && res.ok) {
            auth.showNotification(editId ? 'Employee updated!' : 'Employee added!', 'success');
            window.location.href = 'employees.html';
        } else {
            const err = await res.text();
            auth.showNotification('Error: ' + err, 'error');
        }
    } catch (e) {
        auth.showNotification('Error: ' + e.message, 'error');
    }
});

function resetForm() {
    document.getElementById('employee-form').reset();
    calculateTotal();
}

async function deleteEmployee() {
    if (!editId) return;
    const name = (document.getElementById('firstName').value + ' ' + document.getElementById('lastName').value).trim();
    if (!confirm(`⚠️ PERMANENT DELETE\n\nAre you sure you want to permanently delete:\n"${name}"\n\nThis will remove ALL their data including attendance, payslips, and leave records. This action CANNOT be undone!`)) return;
    try {
        const res = await auth.apiCall(`/api/employees/${editId}`, { method: 'DELETE' });
        if (res && res.ok) {
            auth.showNotification(`Employee "${name}" deleted permanently.`, 'success');
            setTimeout(() => { window.location.href = 'employees.html'; }, 1200);
        } else {
            const err = await res.text();
            auth.showNotification('Delete failed: ' + err, 'error');
        }
    } catch (e) {
        auth.showNotification('Error: ' + e.message, 'error');
    }
}

function logout() { auth.logout(); }

loadDropdowns();
