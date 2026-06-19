// HRM System - Core Application Module
// Corresponds to: HrmsystemApplication.java, config/, exception/, util/

// Global state
let currentUser = null;
let currentEditingEmployee = null;

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
});

function initializeApp() {
    console.log('=== HRM SYSTEM INITIALIZED ===');
    checkAuthStatus();
    setupGlobalEventListeners();
}

function checkAuthStatus() {
    const token = localStorage.getItem('token');
    const user = localStorage.getItem('user');
    
    if (token && user) {
        currentUser = JSON.parse(user);
        showDashboard();
        updateUserInfo();
    } else {
        showLogin();
    }
}

function setupGlobalEventListeners() {
    // Login form
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', handleLogin);
    }
    
    // Salary input listeners for PF/Tax auto-calculation
    const salaryInput = document.getElementById('salary');
    if (salaryInput) {
        salaryInput.addEventListener('input', function() {
            if (document.getElementById('calculatePf')?.checked) {
                togglePfCalculation();
            }
            if (document.getElementById('calculateTax')?.checked) {
                toggleTaxCalculation();
            }
        });
    }
    
    // Status toggle
    const statusToggle = document.getElementById('employeeStatus');
    const statusText = document.querySelector('.status-text');
    if (statusToggle && statusText) {
        statusToggle.addEventListener('change', function() {
            statusText.textContent = this.checked ? 'Active' : 'Inactive';
            statusText.style.color = this.checked ? '#10b981' : '#ef4444';
        });
    }
}

// Page navigation
function showLogin() {
    document.getElementById('loginPage')?.classList.add('active');
    document.getElementById('dashboardPage')?.classList.remove('active');
}

function showDashboard() {
    document.getElementById('loginPage')?.classList.remove('active');
    document.getElementById('dashboardPage')?.classList.add('active');
}

function showSection(sectionId) {
    // Hide all sections
    const sections = document.querySelectorAll('.content-section');
    sections.forEach(section => section.classList.remove('active'));
    
    // Show target section
    const targetSection = document.getElementById(sectionId + 'Section');
    if (targetSection) {
        targetSection.classList.add('active');
    }
    
    // Update active nav link
    updateActiveNavLink(sectionId);
    
    // Load data for specific sections
    loadSectionData(sectionId);
}

function loadSectionData(sectionId) {
    switch(sectionId) {
        case 'leave': loadLeaveRequests(); break;
        case 'employees': loadEmployees(); break;
        case 'documents': loadDocuments(); break;
        case 'attendance': loadAttendanceData(); break;
        case 'departments': loadDepartments(); break;
        case 'payroll': loadPayrollData(); break;
        case 'payslips': 
        case 'adminPayslips': loadPayslipsData(); break;
    }
}

function updateActiveNavLink(sectionId) {
    const links = document.querySelectorAll('.nav-menu a');
    links.forEach(link => link.classList.remove('active'));
    
    const activeLink = document.querySelector(`[onclick="showSection('${sectionId}')"]`);
    if (activeLink) {
        activeLink.classList.add('active');
    }
}

function updateUserInfo() {
    if (!currentUser) return;
    
    const dashboardUserName = document.getElementById('dashboardUserName');
    if (dashboardUserName) {
        dashboardUserName.textContent = currentUser.name;
    }
    
    const userName = document.getElementById('userName');
    const userRole = document.getElementById('userRole');
    const userAvatar = document.getElementById('userAvatar');
    
    if (userName) userName.textContent = currentUser.name;
    if (userRole) userRole.textContent = currentUser.role;
    if (userAvatar) userAvatar.textContent = currentUser.name?.charAt(0) || 'U';
    
    updateMenuBasedOnRole();
}

function updateMenuBasedOnRole() {
    const employeeMenu = document.getElementById('employeeMenu');
    const adminMenu = document.getElementById('adminMenu');
    
    if (!employeeMenu || !adminMenu) return;
    
    if (currentUser.role === 'EMPLOYEE') {
        employeeMenu.style.display = 'block';
        adminMenu.style.display = 'none';
    } else {
        employeeMenu.style.display = 'none';
        adminMenu.style.display = 'block';
        
        const adminMenuHeader = adminMenu.previousElementSibling;
        if (adminMenuHeader && adminMenuHeader.tagName === 'H3') {
            adminMenuHeader.textContent = currentUser.role === 'HR' ? 'HR Menu' : 'Admin Menu';
        }
    }
}

function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    currentUser = null;
    showLogin();
}

// Utility functions
function showError(message) {
    const errorDiv = document.getElementById('error');
    if (errorDiv) {
        errorDiv.textContent = message;
        errorDiv.style.display = 'block';
    }
}

function showSuccess(message) {
    const successDiv = document.createElement('div');
    successDiv.className = 'success-message';
    successDiv.textContent = message;
    
    const mainContent = document.querySelector('.main-content');
    const loginCard = document.querySelector('.login-card');
    
    if (mainContent) {
        mainContent.insertBefore(successDiv, mainContent.firstChild);
        setTimeout(() => successDiv.remove(), 3000);
    } else if (loginCard) {
        loginCard.appendChild(successDiv);
        setTimeout(() => successDiv.remove(), 3000);
    }
}

function formatDate(dateString) {
    if (!dateString) return 'N/A';
    const date = new Date(dateString);
    return date.toLocaleDateString('en-IN');
}

function getStatusClass(status) {
    switch (status?.toUpperCase()) {
        case 'APPROVED': return 'success';
        case 'PENDING': return 'warning';
        case 'REJECTED': return 'danger';
        default: return 'secondary';
    }
}

function formatCurrency(amount) {
    const formatted = new Intl.NumberFormat('en-IN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(amount || 0);
    return `Rs. ${formatted}`;
}

// API helper
async function apiCall(url, options = {}) {
    const method = options.method || 'GET';
    const config = {
        method: method,
        headers: {}
    };

    // Only add Content-Type for methods with body
    if (method !== 'GET' && method !== 'HEAD') {
        config.headers['Content-Type'] = 'application/json';
    }

    // Only add body if exists and not GET/HEAD
    if (options.body && method !== 'GET' && method !== 'HEAD') {
        config.body = options.body;
    }

    return fetch(`http://localhost:8080${url}`, config);
}

// Global exports
window.showSection = showSection;
window.logout = logout;
window.showError = showError;
window.showSuccess = showSuccess;
window.formatDate = formatDate;
window.getStatusClass = getStatusClass;
window.formatCurrency = formatCurrency;
window.apiCall = apiCall;
window.currentUser = currentUser;
window.currentEditingEmployee = currentEditingEmployee;
