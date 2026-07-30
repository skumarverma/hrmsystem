// Sidebar navigation component with RBAC

// Employee Sidebar (Limited access)
const employeeSidebar = `
  <div class="sidebar-header">
    <div class="brand">
      <div class="brand-icon">H</div>
      <div>
        <div class="brand-text">HRM System</div>
        <div class="brand-tagline">Employee Portal</div>
      </div>
    </div>
  </div>

  <div class="user-profile">
    <div class="user-avatar" id="user-avatar">U</div>
    <div class="user-info-sidebar">
      <div class="user-name" id="sidebar-user-name">Loading...</div>
      <div class="user-role">
        <span class="role-badge" id="sidebar-user-role">EMPLOYEE</span>
      </div>
    </div>
  </div>

  <div class="nav-section">
    <div class="nav-title">Main Menu</div>
    <ul class="nav-list">
      <li class="nav-item">
        <a href="dashboard.html" id="nav-dashboard">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"/>
          </svg>
          Dashboard
        </a>
      </li>
      <li class="nav-item">
        <a href="leave-request.html" id="nav-leave-request">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4"/>
          </svg>
          Apply Leave
        </a>
      </li>
      <li class="nav-item">
        <a href="my-leaves.html" id="nav-my-leaves">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/>
          </svg>
          My Leaves
        </a>
      </li>
      <li class="nav-item">
        <a href="my-payslips.html" id="nav-my-payslips">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
          </svg>
          My Payslips
        </a>
      </li>
      <li class="nav-item">
        <a href="my-attendance.html" id="nav-my-attendance">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/>
          </svg>
          My Attendance
        </a>
      </li>
      <li class="nav-item">
        <a href="profile.html" id="nav-profile">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
          </svg>
          My Profile
        </a>
      </li>
      <li class="nav-item">
        <a href="documents.html" id="nav-documents">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
          </svg>
          My Documents
        </a>
      </li>
    </ul>

    <div class="nav-title" style="margin-top: 1.5rem;">Preferences</div>
    <ul class="nav-list">
      <li class="nav-item">
        <a href="settings.html" id="nav-settings">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
          </svg>
          Settings
        </a>
      </li>
    </ul>
  </div>

  <div class="sidebar-footer">
    <button class="logout-btn" onclick="auth.logout()">
      <svg width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"/>
      </svg>
      Logout
    </button>
  </div>
`;

// Management Sidebar (Full access)
const managementSidebar = `
  <div class="sidebar-header">
    <div class="brand">
      <div class="brand-icon">H</div>
      <div>
        <div class="brand-text">HRM System</div>
        <div class="brand-tagline">HR Portal</div>
      </div>
    </div>
  </div>

  <div class="user-profile">
    <div class="user-avatar" id="user-avatar">U</div>
    <div class="user-info-sidebar">
      <div class="user-name" id="sidebar-user-name">Loading...</div>
      <div class="user-role">
        <span class="role-badge" id="sidebar-user-role">HR</span>
      </div>
    </div>
  </div>

  <div class="nav-section">
    <div class="nav-title">Main Menu</div>
    <ul class="nav-list">
      <li class="nav-item">
        <a href="dashboard.html" id="nav-dashboard">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"/>
          </svg>
          Dashboard
        </a>
      </li>
      <li class="nav-item">
        <a href="employees.html" id="nav-employees">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>
          </svg>
          Employees
        </a>
      </li>
      <li class="nav-item">
        <a href="users.html" id="nav-users">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"/>
          </svg>
          System Users
        </a>
      </li>
      <li class="nav-item">
        <a href="departments.html" id="nav-departments">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M3 21h18M5 21V7l8-4 8 4v14M9 21v-6h6v6"/>
          </svg>
          Departments
        </a>
      </li>
      <li class="nav-item">
        <a href="documents.html" id="nav-documents">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
          </svg>
          Documents
        </a>
      </li>
      <li class="nav-item">
        <a href="shift.html" id="nav-shift">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <circle cx="12" cy="12" r="10"></circle>
            <polyline points="12 6 12 12 16 14"></polyline>
          </svg>
          Shifts
        </a>
      </li>
      <li class="nav-item">
        <a href="attendance.html" id="nav-attendance">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <rect x="3" y="4" width="18" height="18" rx="2"/>
            <line x1="16" y1="2" x2="16" y2="6"/>
            <line x1="8" y1="2" x2="8" y2="6"/>
            <line x1="3" y1="10" x2="21" y2="10"/>
          </svg>
          Attendance
        </a>
      </li>
      <li class="nav-item">
        <a href="leave.html" id="nav-leave">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
          Leave Management
        </a>
      </li>
      <li class="nav-item">
        <a href="payroll.html" id="nav-payroll">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <line x1="12" y1="2" x2="12" y2="22"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/>
          </svg>
          Payroll
        </a>
      </li>
      <li class="nav-item">
        <a href="payslips.html" id="nav-payslips">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
          Payslips
        </a>
      </li>
      <li class="nav-item">
        <a href="profile.html" id="nav-profile">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          My Profile
        </a>
      </li>
    </ul>

    <div class="nav-title" style="margin-top: 1.5rem;">System</div>
    <ul class="nav-list">
      <li class="nav-item">
        <a href="settings.html" id="nav-settings">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <circle cx="12" cy="12" r="3"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"/>
          </svg>
          Settings
        </a>
      </li>
    </ul>
  </div>

  <div class="sidebar-footer">
    <button class="logout-btn" onclick="auth.logout()">
      <svg width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"/>
      </svg>
      Logout
    </button>
  </div>
`;

// Accountant Sidebar
const accountantSidebar = `
  <div class="sidebar-header">
    <div class="brand">
      <div class="brand-icon">H</div>
      <div>
        <div class="brand-text">HRM System</div>
        <div class="brand-tagline">Accountant Portal</div>
      </div>
    </div>
  </div>

  <div class="user-profile">
    <div class="user-avatar" id="user-avatar">U</div>
    <div class="user-info-sidebar">
      <div class="user-name" id="sidebar-user-name">Loading...</div>
      <div class="user-role">
        <span class="role-badge" id="sidebar-user-role">ACCOUNTANT</span>
      </div>
    </div>
  </div>

  <div class="nav-section">
    <div class="nav-title">Main Menu</div>
    <ul class="nav-list">
      <li class="nav-item">
        <a href="dashboard.html" id="nav-dashboard">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"/>
          </svg>
          Dashboard
        </a>
      </li>
      <li class="nav-item">
        <a href="employees.html" id="nav-employees">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>
          </svg>
          Employees
        </a>
      </li>
      <li class="nav-item">
        <a href="payroll.html" id="nav-payroll">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <line x1="12" y1="2" x2="12" y2="22"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/>
          </svg>
          Payroll
        </a>
      </li>
      <li class="nav-item">
        <a href="accountant-approvals.html" id="nav-approvals">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 15l2 2 4-4"/>
          </svg>
          My Approvals
        </a>
      </li>
      <li class="nav-item">
        <a href="leave.html" id="nav-leave">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
          Leave Management
        </a>
      </li>
      <li class="nav-item">
        <a href="payslips.html" id="nav-payslips">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
          </svg>
          Payslips
        </a>
      </li>
      <li class="nav-item">
        <a href="profile.html" id="nav-profile">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          My Profile
        </a>
      </li>
    </ul>

    <div class="nav-title" style="margin-top: 1.5rem;">Preferences</div>
    <ul class="nav-list">
      <li class="nav-item">
        <a href="settings.html" id="nav-settings">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
          </svg>
          Settings
        </a>
      </li>
    </ul>
  </div>

  <div class="sidebar-footer">
    <button class="logout-btn" onclick="auth.logout()">
      <svg width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"/>
      </svg>
      Logout
    </button>
  </div>
`;

// Director Sidebar
const directorSidebar = `
  <div class="sidebar-header">
    <div class="brand">
      <div class="brand-icon">H</div>
      <div>
        <div class="brand-text">HRM System</div>
        <div class="brand-tagline">Director Portal</div>
      </div>
    </div>
  </div>

  <div class="user-profile">
    <div class="user-avatar" id="user-avatar">U</div>
    <div class="user-info-sidebar">
      <div class="user-name" id="sidebar-user-name">Loading...</div>
      <div class="user-role">
        <span class="role-badge" id="sidebar-user-role">DIRECTOR</span>
      </div>
    </div>
  </div>

  <div class="nav-section">
    <div class="nav-title">Main Menu</div>
    <ul class="nav-list">
      <li class="nav-item">
        <a href="dashboard.html" id="nav-dashboard">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"/>
          </svg>
          Dashboard
        </a>
      </li>
      <li class="nav-item">
        <a href="employees.html" id="nav-employees">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>
          </svg>
          Employees
        </a>
      </li>

      <li class="nav-item">
        <a href="payroll.html" id="nav-payroll">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <line x1="12" y1="2" x2="12" y2="22"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/>
          </svg>
          Payroll
        </a>
      </li>
      <li class="nav-item">
        <a href="director-approvals.html" id="nav-approvals">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
          </svg>
          Final Approvals
        </a>
      </li>
      <li class="nav-item">
        <a href="payslips.html" id="nav-payslips">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
          </svg>
          Payslips
        </a>
      </li>
      <li class="nav-item">
        <a href="profile.html" id="nav-profile">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          My Profile
        </a>
      </li>
    </ul>

    <div class="nav-title" style="margin-top: 1.5rem;">Preferences</div>
    <ul class="nav-list">
      <li class="nav-item">
        <a href="settings.html" id="nav-settings">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
          </svg>
          Settings
        </a>
      </li>
    </ul>
  </div>

  <div class="sidebar-footer">
    <button class="logout-btn" onclick="auth.logout()">
      <svg width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"/>
      </svg>
      Logout
    </button>
  </div>
`;

// Leaves Department Sidebar
const leavesSidebar = `
  <div class="sidebar-header">
    <div class="brand">
      <div class="brand-icon">H</div>
      <div>
        <div class="brand-text">HRM System</div>
        <div class="brand-tagline">Leaves Portal</div>
      </div>
    </div>
  </div>

  <div class="user-profile">
    <div class="user-avatar" id="user-avatar">U</div>
    <div class="user-info-sidebar">
      <div class="user-name" id="sidebar-user-name">Loading...</div>
      <div class="user-role">
        <span class="role-badge" id="sidebar-user-role">LEAVES</span>
      </div>
    </div>
  </div>

  <div class="nav-section">
    <div class="nav-title">Main Menu</div>
    <ul class="nav-list">
      <li class="nav-item">
        <a href="dashboard.html" id="nav-dashboard">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"/>
          </svg>
          Dashboard
        </a>
      </li>
      <li class="nav-item">
        <a href="leave.html" id="nav-leave">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
          Leave Management
        </a>
      </li>
      <li class="nav-item">
        <a href="employees.html" id="nav-employees">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>
          </svg>
          Employees <span style="font-size:0.65rem;color:#9ca3af;">(View)</span>
        </a>
      </li>
      <li class="nav-item">
        <a href="payroll.html" id="nav-payroll">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <line x1="12" y1="2" x2="12" y2="22"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/>
          </svg>
          Payroll <span style="font-size:0.65rem;color:#9ca3af;">(View)</span>
        </a>
      </li>
      <li class="nav-item">
        <a href="profile.html" id="nav-profile">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          My Profile
        </a>
      </li>
    </ul>

    <div class="nav-title" style="margin-top: 1.5rem;">Preferences</div>
    <ul class="nav-list">
      <li class="nav-item">
        <a href="settings.html" id="nav-settings">
          <svg class="nav-icon" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
          </svg>
          Settings
        </a>
      </li>
    </ul>
  </div>

  <div class="sidebar-footer">
    <button class="logout-btn" onclick="auth.logout()">
      <svg width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"/>
      </svg>
      Logout
    </button>
  </div>
`;

// Render sidebar based on role
function renderSidebar() {
  const sidebar = document.querySelector('.sidebar');
  if (!sidebar) return;

  const role = auth.getUserRole();
  console.log('Current user role:', role);

  // Show sidebar based on exact role
  if (role === 'ROLE_ACCOUNTANT') {
    sidebar.innerHTML = accountantSidebar;
  } else if (role === 'ROLE_DIRECTOR') {
    sidebar.innerHTML = directorSidebar;
  } else if (role === 'ROLE_LEAVES') {
    sidebar.innerHTML = leavesSidebar;
  } else if (role === 'ROLE_ADMIN' || role === 'ROLE_HR') {
    sidebar.innerHTML = managementSidebar;
  } else {
    sidebar.innerHTML = employeeSidebar;
  }

  // Update user info in sidebar
  const user = auth.getCurrentUser();
  if (user) {
    const sidebarUserName = document.getElementById('sidebar-user-name');
    const userAvatar = document.getElementById('user-avatar');
    const sidebarUserRole = document.getElementById('sidebar-user-role');

    if (sidebarUserName) {
      sidebarUserName.textContent = user.name || user.username || 'User';
    }
    if (userAvatar) {
      userAvatar.textContent = (user.name || user.username || 'U').charAt(0).toUpperCase();
    }
    if (sidebarUserRole) {
      sidebarUserRole.textContent = auth.getRoleDisplayName() || 'EMPLOYEE';
    }
  }

  // Highlight current page
  const pathParts = window.location.pathname.split('/');
  const currentPage = (pathParts[pathParts.length - 1] || 'dashboard.html').replace('.html', '');
  const navItem = document.getElementById(`nav-${currentPage}`);
  if (navItem) {
    navItem.classList.add('active');
  }

  // Restore scroll position to prevent jumping to top
  const savedScrollPosition = sessionStorage.getItem('sidebarScrollPos');
  if (savedScrollPosition) {
    sidebar.scrollTop = parseInt(savedScrollPosition, 10);
  }

  // Save scroll position when user scrolls the sidebar
  sidebar.addEventListener('scroll', () => {
    sessionStorage.setItem('sidebarScrollPos', sidebar.scrollTop);
  });

  // Add mobile toggle if header exists
  const header = document.querySelector('.header');
  if (header && !document.querySelector('.mobile-toggle')) {
    const toggleBtn = document.createElement('button');
    toggleBtn.className = 'mobile-toggle';
    toggleBtn.innerHTML = `
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="3" y1="12" x2="21" y2="12"></line>
                <line x1="3" y1="6" x2="21" y2="6"></line>
                <line x1="3" y1="18" x2="21" y2="18"></line>
            </svg>
        `;
    toggleBtn.onclick = toggleSidebar;
    header.prepend(toggleBtn);
  }

  // Add Notification Bell (DISABLED globally per user request)
  if (false && header && currentPage === 'dashboard' && !document.querySelector('.notification-container')) {
    const actionContainer = header.querySelector('.header-actions, .flex.items-center, .flex.gap-2') || header;
    
    const notifContainer = document.createElement('div');
    notifContainer.className = 'notification-container';
    
    notifContainer.innerHTML = `
      <button id="notif-bell-btn">
        <svg width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/>
        </svg>
        <span id="notif-badge" style="display: none;">0</span>
      </button>
      
      <div id="notif-dropdown" style="display: none;">
        <div class="notif-header">
          <h3>Notifications</h3>
          <button id="notif-read-all">Mark all as read</button>
        </div>
        <div id="notif-list" style="max-height: 350px; overflow-y: auto; padding: 0;">
          <div style="padding: 20px; text-align: center; color: #6b7280; font-size: 13px;">Loading...</div>
        </div>
      </div>
    `;
    
    // Insert properly (before logout/last button)
    if (actionContainer !== header) {
      if (actionContainer.children.length > 0) {
        actionContainer.insertBefore(notifContainer, actionContainer.lastElementChild);
      } else {
        actionContainer.appendChild(notifContainer);
      }
    } else {
      header.appendChild(notifContainer);
    }

    // Bind events
    const btn = document.getElementById('notif-bell-btn');
    const dropdown = document.getElementById('notif-dropdown');
    const readAllBtn = document.getElementById('notif-read-all');

    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const isVisible = dropdown.style.display === 'block';
      dropdown.style.display = isVisible ? 'none' : 'block';
      if (!isVisible) {
        fetchNotifications();
      }
    });

    readAllBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
      try {
        await auth.apiCall('/api/notifications/read-all', { method: 'POST' });
        fetchNotifications();
      } catch(e) {
        console.error(e);
      }
    });

    document.addEventListener('click', (e) => {
      if (!notifContainer.contains(e.target)) {
        dropdown.style.display = 'none';
      }
    });

    // Initial fetch
    fetchNotifications();
  }
}

async function fetchNotifications() {
  try {
    const res = await auth.apiCall('/api/notifications');
    if (!res || !res.ok) return;
    const data = await res.json();
    
    const badge = document.getElementById('notif-badge');
    if (data.unreadCount > 0) {
      badge.style.display = 'flex';
      badge.textContent = data.unreadCount > 99 ? '99+' : data.unreadCount;
    } else {
      badge.style.display = 'none';
    }

    const list = document.getElementById('notif-list');
    if (!list) return;

    if (!data.notifications || data.notifications.length === 0) {
      list.innerHTML = '<div style="padding: 24px; text-align: center; color: #9ca3af; font-size: 13px;">No notifications yet</div>';
      return;
    }

    list.innerHTML = data.notifications.map(n => {
      const isUnread = !n.read;
      const date = new Date(n.createdAt);
      const timeStr = date.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
      const dateStr = date.toLocaleDateString([], {month: 'short', day: 'numeric'});
      
      let icon = '<svg width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>';
      let bg = '#eff6ff';
      let color = '#3b82f6';

      if (n.type === 'PAYSLIP_APPROVAL') {
        icon = '<svg width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/></svg>';
        bg = '#fef3c7'; color = '#d97706';
      }

      return `
        <div class="notif-item ${isUnread ? 'unread' : ''}" onclick="handleNotificationClick(${n.id}, '${n.actionLink || ''}')">
          <div class="notif-icon-wrapper" style="background: ${bg}; color: ${color};">
            ${icon}
          </div>
          <div class="notif-content">
            <div class="notif-title-row">
              <h4 class="notif-title" style="font-weight: ${isUnread ? '700' : '600'};">${n.title}</h4>
              <span class="notif-date">${dateStr}</span>
            </div>
            <p class="notif-message">${n.message}</p>
            <div class="notif-time">${timeStr}</div>
          </div>
          ${isUnread ? `<div class="notif-dot"></div>` : ''}
        </div>
      `;
    }).join('');

  } catch(e) {
    console.error('Error fetching notifications:', e);
  }
}

window.handleNotificationClick = async function(id, actionLink) {
  try {
    // Mark as read
    await auth.apiCall(`/api/notifications/${id}/read`, { method: 'POST' });
    // Redirect if there's a link
    if (actionLink && actionLink !== 'null') {
      window.location.href = actionLink;
    } else {
      fetchNotifications();
    }
  } catch(e) {
    console.error(e);
  }
}

function toggleSidebar() {
  const sidebar = document.querySelector('.sidebar');
  if (sidebar) {
    sidebar.classList.toggle('open');
  }
}

// Check page access
function checkPageAccess() {
  const role = auth.getUserRole();
  const pathParts = window.location.pathname.split('/');
  const currentPage = pathParts[pathParts.length - 1] || pathParts[pathParts.length - 2] || 'dashboard.html';

  // Pages restricted by role
  const hrAdminPages = ['employees.html', 'users.html', 'departments.html', 'leave.html'];
  const hrAdminManagerPages = ['attendance.html'];
  const payrollPages = ['payroll.html', 'payslips.html'];
  const adminOnlyPages = [];

  // Pages restricted to specific roles
  const accountantPages = ['accountant-approvals.html'];
  const directorPages = ['director-approvals.html'];

  // Check HR/Admin pages
  if (hrAdminPages.includes(currentPage) && !['ROLE_ADMIN', 'ROLE_HR'].includes(role)) {
    if ((currentPage === 'employees.html' || currentPage === 'users.html') && (role === 'ROLE_DIRECTOR' || role === 'ROLE_LEAVES' || role === 'ROLE_ACCOUNTANT')) {
      // Explicitly allow Director, Leaves, and Accountant to view the read-only Employees and Users list
    } else if (currentPage === 'leave.html' && (role === 'ROLE_LEAVES' || role === 'ROLE_ACCOUNTANT')) {
      // Explicitly allow Leaves and Accountant roles to manage leave.html
    } else {
      auth.showNotification('Access denied: HR/Admin role required', 'error');
      window.location.href = '/html/dashboard.html';
      return false;
    }
  }

  // Check Attendance pages
  if (hrAdminManagerPages.includes(currentPage) && !['ROLE_ADMIN', 'ROLE_HR', 'ROLE_MANAGER'].includes(role)) {
    auth.showNotification('Access denied: Manager role required', 'error');
    window.location.href = '/html/dashboard.html';
    return false;
  }

  // Check Payroll pages
  if (payrollPages.includes(currentPage) && !['ROLE_ADMIN', 'ROLE_HR', 'ROLE_ACCOUNTANT', 'ROLE_DIRECTOR', 'ROLE_LEAVES'].includes(role)) {
    auth.showNotification('Access denied: Payroll access required', 'error');
    window.location.href = '/html/dashboard.html';
    return false;
  }

  // Check accountant pages
  if (accountantPages.includes(currentPage) && role !== 'ROLE_ACCOUNTANT') {
    auth.showNotification('Access denied: Accountant role required', 'error');
    window.location.href = '/html/dashboard.html';
    return false;
  }

  // Check director pages
  if (directorPages.includes(currentPage) && role !== 'ROLE_DIRECTOR') {
    auth.showNotification('Access denied: Director role required', 'error');
    window.location.href = '/html/dashboard.html';
    return false;
  }

  return true;
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', async () => {
  if (auth.isAuthenticated()) {
    renderSidebar();
    checkPageAccess();

    // Global Dynamic Role Sync on Page Load
    try {
      const res = await auth.apiCall('/api/users/profile');
      if (res && res.ok) {
        const data = await res.json();
        const currentUser = auth.getCurrentUser();
        if (currentUser && currentUser.role !== data.role) {
          console.log(`[Role Sync] Local '${currentUser.role}' -> Server '${data.role}' - Reloading...`);
          currentUser.role = data.role;
          auth.setAuth(auth.getAuth().token, currentUser);
          
          // Force a full page reload so that all widgets, cards, and state re-initialize correctly
          window.location.reload();
        }
      }
    } catch (e) {
      console.error('[Role Sync] Failed to sync user role:', e);
    }
  }
});

// Close sidebar when clicking outside on mobile
document.addEventListener('mousedown', (e) => {
  const sidebar = document.querySelector('.sidebar');
  if (sidebar && sidebar.classList.contains('open') && !sidebar.contains(e.target) && !e.target.closest('.mobile-toggle')) {
    sidebar.classList.remove('open');
  }
});

window.sidebar = {
  renderSidebar,
  checkPageAccess,
  toggleSidebar
};
