// Shared Authentication Utilities

const AUTH_KEY = 'token';
const USER_KEY = 'user';

// Store authentication data
function setAuth(token, userData) {
    localStorage.setItem(AUTH_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(userData));
}

// Get authentication data
function getAuth() {
    const token = localStorage.getItem(AUTH_KEY);
    const user = localStorage.getItem(USER_KEY);
    return {
        token,
        user: user ? JSON.parse(user) : null
    };
}

// Check if user is authenticated
function isAuthenticated() {
    const { token } = getAuth();
    return !!token;
}

// Get auth headers for API calls
function getAuthHeaders() {
    const { token } = getAuth();
    return {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
    };
}

// Get current user
function getCurrentUser() {
    const { user } = getAuth();
    return user;
}

// Logout
function logout() {
    localStorage.removeItem(AUTH_KEY);
    localStorage.removeItem(USER_KEY);
    window.location.href = '/html/login.html';
}

// Protect page - redirect if not authenticated
function requireAuth() {
    if (!isAuthenticated()) {
        window.location.href = '/html/login.html';
        return false;
    }
    return true;
}

// Redirect if already authenticated (for login/register pages)
function redirectIfAuth() {
    if (isAuthenticated()) {
        window.location.href = '/html/dashboard.html';
        return true;
    }
    return false;
}

// Format currency
function formatCurrency(amount) {
    const formatted = new Intl.NumberFormat('en-IN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(amount || 0);
    return `Rs. ${formatted}`;
}

// Format date
function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-IN', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
    });
}

// Show notification
function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `notification notification-${type}`;
    notification.textContent = message;
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 1rem 1.5rem;
        border-radius: 8px;
        color: white;
        font-weight: 500;
        z-index: 1000;
        animation: slideIn 0.3s ease;
        background: ${type === 'success' ? '#16a34a' : type === 'error' ? '#dc2626' : '#4f46e5'};
    `;
    
    document.body.appendChild(notification);
    
    setTimeout(() => {
        notification.style.animation = 'slideOut 0.3s ease';
        setTimeout(() => notification.remove(), 300);
    }, 3000);
}

// API call helper with auth
async function apiCall(url, options = {}) {
    const headers = getAuthHeaders();
    
    // Debug logging
    console.log('API Call:', url);
    console.log('Headers:', headers);
    
    const response = await fetch(url, {
        ...options,
        headers: {
            ...headers,
            ...options.headers
        }
    });
    
    // Log response status for debugging
    console.log('Response Status:', response.status, response.statusText);
    
    if (response.status === 401) {
        // Token expired or invalid
        console.log('Token expired or invalid, logging out...');
        logout();
        return null;
    }

    if (response.status === 403 && url.includes('/api/users/profile')) {
        // Active user was deleted or credentials invalidated
        console.log('Active user session invalid or deleted, logging out...');
        logout();
        return null;
    }
    
    return response;
}

// Role-based access control
const ROLES = {
    ADMIN: 'ROLE_ADMIN',
    HR: 'ROLE_HR',
    MANAGER: 'ROLE_MANAGER',
    EMPLOYEE: 'ROLE_EMPLOYEE',
    ACCOUNTANT: 'ROLE_ACCOUNTANT',
    DIRECTOR: 'ROLE_DIRECTOR',
    LEAVES: 'ROLE_LEAVES'
};

// Get user role
function getUserRole() {
    const user = getCurrentUser();
    return user?.role || null;
}

// Check if user has specific role
function hasRole(role) {
    const userRole = getUserRole();
    return userRole === role;
}

// Check if user is HR/Admin/Accountant/Director (Management roles) - matches sidebar logic
function isManagement() {
    const role = getUserRole();
    return role === ROLES.ADMIN || role === ROLES.HR || role === ROLES.ACCOUNTANT || role === ROLES.DIRECTOR || role === ROLES.LEAVES;
}

// Check if user is Employee only
function isEmployee() {
    const role = getUserRole();
    return role === ROLES.EMPLOYEE;
}

// Check if user is Accountant
function isAccountant() {
    const role = getUserRole();
    return role === ROLES.ACCOUNTANT;
}

// Check if user is Director
function isDirector() {
    const role = getUserRole();
    return role === ROLES.DIRECTOR;
}

// Require management role
function requireManagement() {
    if (!requireAuth()) return false;
    if (!isManagement()) {
        auth.showNotification('Access denied: Management role required', 'error');
        window.location.href = '/html/dashboard.html';
        return false;
    }
    return true;
}

// Get role display name
function getRoleDisplayName() {
    const role = getUserRole();
    if (!role) return 'User';
    return role.replace('ROLE_', '').replace('_', ' ');
}

// Export functions
window.auth = {
    setAuth,
    getAuth,
    isAuthenticated,
    getAuthHeaders,
    getCurrentUser,
    logout,
    requireAuth,
    redirectIfAuth,
    formatCurrency,
    formatDate,
    showNotification,
    apiCall,
    // Role functions
    ROLES,
    getUserRole,
    hasRole,
    isManagement,
    isEmployee,
    isAccountant,
    isDirector,
    requireManagement,
    getRoleDisplayName
};
