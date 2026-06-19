package com.hrm.hrmsystem.controller;

import com.hrm.hrmsystem.model.Department;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.User;
import com.hrm.hrmsystem.repository.DepartmentRepository;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
@Transactional
public class UserController {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, 
                          EmployeeRepository employeeRepository,
                          DepartmentRepository departmentRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
    }
    @GetMapping("/role/{role}")
    public ResponseEntity<?> getUsersByRole(@PathVariable String role) {
        try {
            System.out.println("Fetching users with role: " + role);
            User.Role roleEnum = User.Role.valueOf(role);
            List<User> users = userRepository.findByRole(roleEnum);
            System.out.println("Found " + users.size() + " users.");
            return ResponseEntity.ok(users);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid role: " + role);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: " + role));
        } catch (Exception e) {
            System.err.println("Error fetching users by role: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Error fetching users: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/admin-update")
    public ResponseEntity<?> adminUpdateUser(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            System.out.println("Admin update for user ID: " + id);
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String oldEmail = user.getEmail();
            String newEmail = request.get("email");
            String newPassword = request.get("password");
            final String finalEmail = (newEmail != null && !newEmail.trim().isEmpty()) ? newEmail.trim() : null;

            // 1. Update User Credentials
            if (finalEmail != null) {
                System.out.println("Updating email to: " + finalEmail);
                // Check if email taken
                Optional<User> existing = userRepository.findByEmail(finalEmail);
                if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Email already in use by another user"));
                }
                user.setEmail(finalEmail);
                user.setUsername(finalEmail);
            }

            if (newPassword != null && !newPassword.trim().isEmpty()) {
                System.out.println("Updating password...");
                user.setPassword(passwordEncoder.encode(newPassword.trim()));
            }

            // 2. Find, Link, or CREATE Employee
            Employee emp = user.getEmployee();
            if (emp == null) {
                System.out.println("User not linked to employee. Searching by email...");
                // Try to find by current email or old email
                emp = employeeRepository.findByEmail(user.getEmail()).orElse(null);
                if (emp == null && oldEmail != null) {
                    emp = employeeRepository.findByEmail(oldEmail).orElse(null);
                }
                
                if (emp == null) {
                    System.out.println("No existing employee record found. Creating a new one for this account...");
                    emp = new Employee();
                    emp.setEmail(user.getEmail());
                    emp.setFirstName(request.getOrDefault("firstName", "Accountant"));
                    emp.setLastName(request.getOrDefault("lastName", "User"));
                    emp.setStatus(Employee.EmployeeStatus.ACTIVE);
                    emp = employeeRepository.save(emp);
                }
                
                System.out.println("Linking user to employee ID: " + emp.getId());
                user.setEmployee(emp); // Link them!
                userRepository.save(user); // FORCE SAVE THE LINK IMMEDIATELY
            }

            // 3. Update Employee Profile Details
            if (emp != null) {
                if (request.get("firstName") != null) emp.setFirstName(request.get("firstName"));
                if (request.get("lastName") != null) emp.setLastName(request.get("lastName"));
                if (request.get("phone") != null) emp.setPhone(request.get("phone"));
                if (request.get("designation") != null) emp.setDesignation(request.get("designation"));
                if (finalEmail != null) emp.setEmail(finalEmail);
                
                // Handle Department
                if (request.get("departmentId") != null && !request.get("departmentId").isEmpty()) {
                    Long deptId = Long.parseLong(request.get("departmentId"));
                    departmentRepository.findById(deptId).ifPresent(emp::setDepartment);
                }

                employeeRepository.save(emp);
                System.out.println("Employee profile record updated.");
            }

            userRepository.save(user);
            System.out.println("User account updated successfully.");
            return ResponseEntity.ok(Map.of("message", "User and Profile updated successfully"));
        } catch (Exception e) {
            System.err.println("Error in adminUpdateUser: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Update failed: " + e.getMessage()));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication auth) {
        String username = auth.getName();
        System.out.println("Profile request for username: " + username);
        
        Optional<User> userOpt = userRepository.findByUsername(username);
        
        if (userOpt.isEmpty()) {
            // Fallback: Try searching by email if username search fails (common after admin update)
            userOpt = userRepository.findByEmail(username);
        }

        if (userOpt.isEmpty()) {
            System.err.println("CRITICAL: Profile requested for non-existent user identity: " + username);
            return ResponseEntity.status(401).body(Map.of("error", "Session invalid. Please log out and log in again."));
        }
        
        User user = userOpt.get();
        System.out.println("Found user: " + user.getEmail() + " (ID: " + user.getId() + ")");
        
        // 1. Get current linked employee
        Employee emp = user.getEmployee();
        
        // 2. Robust lookup by email case-insensitively and trim-wise to sync or find a better record
        if (emp == null || emp.getFirstName() == null || emp.getFirstName().equals("Accountant") || emp.getFirstName().equals("Admin")) {
            System.out.println("Current profile is empty, placeholder, or unlinked. Searching case-insensitively for a matching record by email: " + user.getEmail());
            String targetEmail = user.getEmail().trim().toLowerCase();
            List<Employee> allEmps = employeeRepository.findAll();
            for (Employee e : allEmps) {
                if (e.getEmail() != null && e.getEmail().trim().toLowerCase().equals(targetEmail)) {
                    if (emp == null || !e.getId().equals(emp.getId())) {
                        System.out.println("Syncing user with existing employee record ID: " + e.getId());
                        user.setEmployee(e);
                        userRepository.save(user);
                        emp = e;
                    }
                    break;
                }
            }
        }

        // 3. Self-healing placeholder creation if no employee record exists at all
        if (emp == null) {
            System.out.println("No employee record found case-insensitively for email: " + user.getEmail() + ". Creating placeholder.");
            emp = new Employee();
            emp.setFirstName(user.getUsername());
            emp.setLastName("Placeholder");
            emp.setEmail(user.getEmail().trim().toLowerCase());
            emp.setJoiningDate(java.time.LocalDate.now());
            emp.setBasicSalary(new java.math.BigDecimal("30000.0"));
            emp.setStatus(com.hrm.hrmsystem.model.Employee.EmployeeStatus.ACTIVE);
            
            // Try to assign a department if any exists
            List<Department> depts = departmentRepository.findAll();
            if (!depts.isEmpty()) {
                emp.setDepartment(depts.get(0));
            }
            
            emp = employeeRepository.save(emp);
            user.setEmployee(emp);
            userRepository.save(user);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("username", user.getUsername());
        res.put("email", user.getEmail());
        
        // Determine displayed role dynamically based on department
        String finalRole = user.getRole().name();
        if (user.getRole() == User.Role.ROLE_ADMIN || user.getRole() == User.Role.ROLE_HR) {
            finalRole = "ROLE_HR";
        }
        if (user.getRole() != User.Role.ROLE_ADMIN && user.getRole() != User.Role.ROLE_HR) {
            if (emp.getDepartment() != null) {
                String deptName = emp.getDepartment().getName().toLowerCase();
                if (deptName.contains("accountant")) finalRole = "ROLE_ACCOUNTANT";
                else if (deptName.contains("director")) finalRole = "ROLE_DIRECTOR";
                else if (deptName.contains("leave") || deptName.equals("leaves")) finalRole = "ROLE_LEAVES";
                else if (deptName.contains("hr") || deptName.equals("human resources")) finalRole = "ROLE_HR";
                else finalRole = "ROLE_EMPLOYEE";
            }
        }
        res.put("role", finalRole);
        res.put("firstName", emp.getFirstName() != null ? emp.getFirstName() : "Not Set");
        res.put("lastName", emp.getLastName() != null ? emp.getLastName() : "Not Set");
        res.put("phone", emp.getPhone() != null ? emp.getPhone() : "Not Set");
        res.put("designation", emp.getDesignation() != null ? emp.getDesignation() : "Not Set");
        if (user.getRole() == User.Role.ROLE_ADMIN || user.getRole() == User.Role.ROLE_HR) {
            res.put("department", "HR");
        } else {
            res.put("department", emp.getDepartment() != null ? emp.getDepartment().getName() : "General");
        }
        res.put("employeeId", emp.getId());

        System.out.println("Profile data prepared successfully for " + user.getEmail());
        return ResponseEntity.ok(res);
    }

    @PutMapping("/update-email")
    public ResponseEntity<?> updateEmail(Authentication auth, @RequestBody Map<String, String> request) {
        String newEmail = request.get("newEmail");
        if (newEmail == null || newEmail.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "New email is required"));
        }
        final String finalEmail = newEmail.trim();

        try {
            String currentUsername = auth.getName();
            System.out.println("Updating email for user: " + currentUsername + " to " + finalEmail);
            
            User user = userRepository.findByUsername(currentUsername)
                    .orElseThrow(() -> new RuntimeException("User not found: " + currentUsername));

            // 1. Check if new email is already taken in User table
            Optional<User> existingUser = userRepository.findByEmail(finalEmail);
            if (existingUser.isPresent() && !existingUser.get().getId().equals(user.getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "This email is already registered to another user account."));
            }

            // 2. Check if new email is already taken in Employee table
            Optional<Employee> existingEmployee = employeeRepository.findByEmail(finalEmail);
            if (existingEmployee.isPresent()) {
                // Check if the found employee is the one linked to THIS user
                if (user.getEmployee() == null || !existingEmployee.get().getId().equals(user.getEmployee().getId())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "This email is already assigned to another employee record."));
                }
            }

            String oldEmail = user.getEmail();
            System.out.println("Old email: " + oldEmail);

            // 3. Update User
            user.setEmail(finalEmail);
            user.setUsername(finalEmail); // Keep username in sync with email for login
            userRepository.save(user);
            System.out.println("User record updated successfully.");

            // 4. Update associated employee record
            if (user.getEmployee() != null) {
                Employee emp = user.getEmployee();
                emp.setEmail(finalEmail);
                employeeRepository.save(emp);
                System.out.println("Linked Employee record updated successfully.");
            } else {
                // Fallback: search by old email if not directly linked
                employeeRepository.findByEmail(oldEmail).ifPresent(emp -> {
                    emp.setEmail(finalEmail);
                    employeeRepository.save(emp);
                    System.out.println("Employee record found by email and updated successfully.");
                });
            }

            return ResponseEntity.ok(Map.of("message", "Email updated successfully. Please log in again with your new email."));
        } catch (Exception e) {
            System.err.println("Error updating email: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "An internal error occurred: " + e.getMessage()));
        }
    }
}
