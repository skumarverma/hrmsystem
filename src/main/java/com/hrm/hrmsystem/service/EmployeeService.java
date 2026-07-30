package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.dto.EmployeeDTO;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.Department;
import com.hrm.hrmsystem.model.User;
import com.hrm.hrmsystem.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    @PersistenceContext
    private EntityManager entityManager;

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private PayrollApprovalRepository payrollApprovalRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @Autowired
    private SalaryAdjustmentRepository salaryAdjustmentRepository;

    @Autowired
    private EmployeeDocumentRepository employeeDocumentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AppNotificationRepository appNotificationRepository;

    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;


    public EmployeeService(EmployeeRepository employeeRepository, 
                          DepartmentRepository departmentRepository, 
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<EmployeeDTO> getAllEmployees() {
        // Use findAllWithDepartment to eagerly load department and avoid lazy loading issues
        return employeeRepository.findAllWithDepartment()
                .stream()
                .filter(emp -> emp.getStatus() != Employee.EmployeeStatus.TERMINATED)
                .filter(emp -> !isSystemUser(emp))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EmployeeDTO> getSystemUsers() {
        return employeeRepository.findAllWithDepartment()
                .stream()
                .filter(emp -> emp.getStatus() != Employee.EmployeeStatus.TERMINATED)
                .filter(emp -> isSystemUser(emp))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public boolean isSystemUser(Employee emp) {
        if (emp.getDepartment() == null || emp.getDepartment().getName() == null) return false;
        String dept = emp.getDepartment().getName().toLowerCase();
        return dept.contains("hr") || dept.contains("director") || 
               dept.contains("leave") || dept.contains("accountant");
    }

    @Transactional(readOnly = true)
    public EmployeeDTO getEmployeeById(Long id) {
        // Use findByIdentifierWithDepartment to eagerly load department
        Employee employee = employeeRepository.findByIdentifierWithDepartment(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        
        // Return null for terminated employees
        if (employee.getStatus() == Employee.EmployeeStatus.TERMINATED) {
            return null;
        }
        
        return convertToDTO(employee);
    }

    @Transactional(readOnly = true)
    public Employee getEmployeeByEmail(String email) {
        return employeeRepository.findByEmail(email)
                .orElse(null);
    }

    @Transactional
    public EmployeeDTO createEmployee(EmployeeDTO dto) {
        if (employeeRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        Employee employee = convertToEntity(dto);
        employee = employeeRepository.save(employee);

        // AUTO-CREATE USER ACCOUNT
        try {
            User user = new User();
            user.setEmail(employee.getEmail());
            user.setUsername(employee.getEmail());
            user.setPassword(passwordEncoder.encode("Sachin@123")); // Default Password
            user.setEmployee(employee);
            user.setIsActive(true);
            user.setCreatedAt(java.time.LocalDateTime.now());

            // Determine Role based on Department
            String dept = employee.getDepartment() != null ? employee.getDepartment().getName().toLowerCase() : "";
            if (dept.contains("accountant")) {
                user.setRole(User.Role.ROLE_ACCOUNTANT);
            } else if (dept.contains("director")) {
                user.setRole(User.Role.ROLE_DIRECTOR);
            } else if (dept.contains("leave") || dept.equals("leaves")) {
                user.setRole(User.Role.ROLE_LEAVES);
            } else if (dept.contains("hr") || dept.equals("human resources")) {
                user.setRole(User.Role.ROLE_HR);
            } else {
                user.setRole(User.Role.ROLE_EMPLOYEE);
            }

            userRepository.save(user);
            System.out.println("AUTO-ACCOUNT: Created login account for " + employee.getEmail());
        } catch (Exception e) {
            System.err.println("Failed to auto-create user account: " + e.getMessage());
        }

        return convertToDTO(employee);
    }

    @Transactional
    public EmployeeDTO updateEmployee(Long id, EmployeeDTO dto) {
        Employee employee = employeeRepository.findByIdentifier(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        // Validate email uniqueness
        String newEmail = dto.getEmail();
        java.util.Optional<Employee> existing = employeeRepository.findByEmail(newEmail);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new RuntimeException("Email already exists");
        }

        String oldEmail = employee.getEmail(); // Capture old email for sync fallback
        employee.setFirstName(dto.getFirstName());
        employee.setLastName(dto.getLastName());
        employee.setPhone(dto.getPhone());
        employee.setEmail(dto.getEmail());
        employee.setEmployeeCode(dto.getEmployeeId());
        employee.setDesignation(dto.getDesignation());
        employee.setJoiningDate(dto.getJoiningDate());
        employee.setSalary(dto.getSalary());
        employee.setAddress(dto.getAddress());
        employee.setGender(dto.getGender() != null ? Employee.Gender.valueOf(dto.getGender().toUpperCase()) : null);
        employee.setProbationPeriodMonths(dto.getProbationPeriodMonths());
        employee.setBasicSalary(dto.getBasicSalary());
        employee.setHra(dto.getHra());
        employee.setSpecialAllowance(dto.getSpecialAllowance());
        employee.setBonus(dto.getBonus());
        employee.setIncentive(dto.getIncentive());
        employee.setOtherAllowance(dto.getOtherAllowance());
        employee.setPf(dto.getPf());
        employee.setEsic(dto.getEsic());
        employee.setProfessionalTax(dto.getProfessionalTax());
        employee.setTds(dto.getTds());
        employee.setTax(dto.getTds() != null ? dto.getTds() : dto.getTax());
        employee.setLoanDeduction(dto.getLoanDeduction());
        employee.setLwf(dto.getLwf());
        employee.setUanNo(dto.getUanNo());

        if (dto.getDepartmentId() != null) {
            Department department = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            employee.setDepartment(department);
        } else if (dto.getDepartmentName() != null && !dto.getDepartmentName().isEmpty()) {
            Department department = departmentRepository.findByName(dto.getDepartmentName())
                    .orElseThrow(() -> new RuntimeException("Department not found: " + dto.getDepartmentName()));
            employee.setDepartment(department);
        }

        if (dto.getStatus() != null) {
            try {
                employee.setStatus(Employee.EmployeeStatus.valueOf(dto.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Handle legacy status values or log warning
                employee.setStatus(Employee.EmployeeStatus.ACTIVE);
            }
        }

        employee = employeeRepository.save(employee);

        // Also update the User entity's email and username to keep login in sync
        try {
            final String finalNewEmail = dto.getEmail();
            final Employee finalEmployee = employee;
            
            // Try to find by direct link first
            Optional<User> userOpt = userRepository.findByEmployeeId(employee.getId());
            
            // Fallback: If not linked, try to find by OLD email
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findByEmail(oldEmail);
            }
            
            userOpt.ifPresent(user -> {
                boolean changed = false;
                
                if (!user.getEmail().equals(finalNewEmail)) {
                    System.out.println("Syncing User login email to " + finalNewEmail);
                    user.setEmail(finalNewEmail);
                    user.setUsername(finalNewEmail);
                    changed = true;
                }
                
                if (user.getEmployee() == null || !user.getEmployee().getId().equals(finalEmployee.getId())) {
                    user.setEmployee(finalEmployee);
                    changed = true;
                }

                // Dynamic Role Sync: Update the permanent role if department matches
                String dept = finalEmployee.getDepartment() != null ? finalEmployee.getDepartment().getName().toLowerCase() : "";
                if (dept.contains("accountant")) {
                    if (user.getRole() != User.Role.ROLE_ACCOUNTANT) {
                        user.setRole(User.Role.ROLE_ACCOUNTANT);
                        changed = true;
                    }
                } else if (dept.contains("director")) {
                    if (user.getRole() != User.Role.ROLE_DIRECTOR) {
                        user.setRole(User.Role.ROLE_DIRECTOR);
                        changed = true;
                    }
                } else if (dept.contains("leave") || dept.equals("leaves")) {
                    if (user.getRole() != User.Role.ROLE_LEAVES) {
                        user.setRole(User.Role.ROLE_LEAVES);
                        changed = true;
                    }
                } else if (dept.contains("hr") || dept.equals("human resources")) {
                    if (user.getRole() != User.Role.ROLE_HR) {
                        user.setRole(User.Role.ROLE_HR);
                        changed = true;
                    }
                }

                if (changed) {
                    userRepository.save(user);
                }
            });
        } catch (Exception e) {
            System.err.println("Error syncing user login credentials: " + e.getMessage());
        }

        return convertToDTO(employee);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = employeeRepository.findByIdentifier(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Long employeeId = employee.getId();
        String email = employee.getEmail();

        // 1. Delete Attendance records (table is name = "attendance")
        entityManager.createNativeQuery("DELETE FROM attendance WHERE employee_id = :empId")
                .setParameter("empId", employeeId)
                .executeUpdate();

        // 2. Delete Leave records
        entityManager.createNativeQuery("DELETE FROM leaves WHERE employee_id = :empId")
                .setParameter("empId", employeeId)
                .executeUpdate();

        // 3. Delete Salary Adjustments
        entityManager.createNativeQuery("DELETE FROM salary_adjustments WHERE employee_id = :empId")
                .setParameter("empId", employeeId)
                .executeUpdate();

        // 4. Delete Employee Documents
        entityManager.createNativeQuery("DELETE FROM employee_documents WHERE employee_id = :empId")
                .setParameter("empId", employeeId)
                .executeUpdate();

        // 5. Delete Payslip absent dates first to satisfy foreign key constraint
        entityManager.createNativeQuery("DELETE FROM payslip_absent_dates WHERE payslip_id IN (SELECT id FROM payslips WHERE employee_id = :empId)")
                .setParameter("empId", employeeId)
                .executeUpdate();

        // 6. Delete Payslip records
        entityManager.createNativeQuery("DELETE FROM payslips WHERE employee_id = :empId")
                .setParameter("empId", employeeId)
                .executeUpdate();

        // 6. Delete Payroll approvals related to this employee's payrolls
        entityManager.createNativeQuery("DELETE FROM payroll_approvals WHERE payroll_id IN (SELECT id FROM payroll WHERE employee_id = :empId)")
                .setParameter("empId", employeeId)
                .executeUpdate();

        // 7. Delete Payroll records (table is name = "payroll")
        entityManager.createNativeQuery("DELETE FROM payroll WHERE employee_id = :empId")
                .setParameter("empId", employeeId)
                .executeUpdate();

        // 8. Find associated User ID
        Long userId = null;
        try {
            Object uIdObj = entityManager.createNativeQuery("SELECT id FROM users WHERE employee_id = :empId OR LOWER(email) = :email")
                    .setParameter("empId", employeeId)
                    .setParameter("email", email.toLowerCase())
                    .getSingleResult();
            if (uIdObj != null) {
                userId = ((Number) uIdObj).longValue();
            }
        } catch (Exception e) {
            // No user found, which is fine
        }

        if (userId != null) {
            // Sever relationship first to satisfy FK constraint before deleting
            entityManager.createNativeQuery("UPDATE users SET employee_id = NULL WHERE id = :uId")
                    .setParameter("uId", userId)
                    .executeUpdate();

            // 9. Delete Notification Preferences for this User
            entityManager.createNativeQuery("DELETE FROM notification_preferences WHERE user_id = :uId")
                    .setParameter("uId", userId)
                    .executeUpdate();

            // 10. Delete App Notifications for this User
            entityManager.createNativeQuery("DELETE FROM app_notifications WHERE user_id = :uId")
                    .setParameter("uId", userId)
                    .executeUpdate();

            // 11. Delete Payroll Approvals approved by this User
            entityManager.createNativeQuery("DELETE FROM payroll_approvals WHERE approved_by = :uId")
                    .setParameter("uId", userId)
                    .executeUpdate();

            // 12. Delete User
            entityManager.createNativeQuery("DELETE FROM users WHERE id = :uId")
                    .setParameter("uId", userId)
                    .executeUpdate();
        } else {
            // If no user but still has employee link, clear it
            entityManager.createNativeQuery("UPDATE users SET employee_id = NULL WHERE employee_id = :empId")
                    .setParameter("empId", employeeId)
                    .executeUpdate();
        }

        // 13. Delete OTPs associated with the email
        entityManager.createNativeQuery("DELETE FROM password_reset_otps WHERE email = :email")
                .setParameter("email", email)
                .executeUpdate();

        // 14. Delete Audit Logs
        entityManager.createNativeQuery("DELETE FROM audit_logs WHERE employee_id = :empId")
                .setParameter("empId", employeeId)
                .executeUpdate();

        // 15. Delete Employee
        entityManager.createNativeQuery("DELETE FROM employees WHERE id = :empId")
                .setParameter("empId", employeeId)
                .executeUpdate();

        // Clear Persistence Context so Hibernate doesn't try to auto-flush outdated entity states
        entityManager.clear();
    }

    private EmployeeDTO convertToDTO(Employee employee) {
        return EmployeeDTO.builder()
                .id(employee.getId())
                .employeeId(employee.getEmployeeCode())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .departmentId(employee.getDepartment() != null ? employee.getDepartment().getId() : null)
                .departmentName(employee.getDepartment() != null ? employee.getDepartment().getName() : null)
                .designation(employee.getDesignation())
                .joiningDate(employee.getJoiningDate())
                .salary(employee.getSalary())
                .status(employee.getStatus() != null ? employee.getStatus().name() : null)
                .address(employee.getAddress())
                .gender(employee.getGender() != null ? employee.getGender().name() : null)
                .shiftId(employee.getShift() != null ? employee.getShift().getId() : null)
                .probationPeriodMonths(employee.getProbationPeriodMonths())
                .basicSalary(employee.getBasicSalary())
                .hra(employee.getHra())
                .specialAllowance(employee.getSpecialAllowance())
                .bonus(employee.getBonus())
                .incentive(employee.getIncentive())
                .otherAllowance(employee.getOtherAllowance())
                .pf(employee.getPf())
                .esic(employee.getEsic())
                .professionalTax(employee.getProfessionalTax())
                .tds(employee.getTds())
                .tax(employee.getTax())
                .loanDeduction(employee.getLoanDeduction())
                .lwf(employee.getLwf())
                .uanNo(employee.getUanNo())
                .build();
    }

    private Employee convertToEntity(EmployeeDTO dto) {
        Employee employee = Employee.builder()
                .employeeCode(dto.getEmployeeId())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .designation(dto.getDesignation())
                .joiningDate(dto.getJoiningDate())
                .salary(dto.getSalary())
                .status(dto.getStatus() != null ? Employee.EmployeeStatus.valueOf(dto.getStatus()) : Employee.EmployeeStatus.ACTIVE)
                .address(dto.getAddress())
                .gender(dto.getGender() != null ? Employee.Gender.valueOf(dto.getGender().toUpperCase()) : null)
                .probationPeriodMonths(dto.getProbationPeriodMonths())
                .basicSalary(dto.getBasicSalary())
                .hra(dto.getHra())
                .specialAllowance(dto.getSpecialAllowance())
                .bonus(dto.getBonus())
                .incentive(dto.getIncentive())
                .otherAllowance(dto.getOtherAllowance())
                .pf(dto.getPf())
                .esic(dto.getEsic())
                .professionalTax(dto.getProfessionalTax())
                .tds(dto.getTds())
                .tax(dto.getTax() != null ? dto.getTax() : dto.getTds())
                .loanDeduction(dto.getLoanDeduction())
                .lwf(dto.getLwf())
                .uanNo(dto.getUanNo())
                .build();

        if (dto.getDepartmentId() != null) {
            Department department = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            employee.setDepartment(department);
        } else if (dto.getDepartmentName() != null && !dto.getDepartmentName().isEmpty()) {
            Department department = departmentRepository.findByName(dto.getDepartmentName())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            employee.setDepartment(department);
        }

        return employee;
    }
}
