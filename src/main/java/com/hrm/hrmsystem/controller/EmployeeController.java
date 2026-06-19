package com.hrm.hrmsystem.controller;

import com.hrm.hrmsystem.dto.EmployeeDTO;
import com.hrm.hrmsystem.repository.UserRepository;
import com.hrm.hrmsystem.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin(origins = "*")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final UserRepository userRepository;

    public EmployeeController(EmployeeService employeeService, UserRepository userRepository) {
        this.employeeService = employeeService;
        this.userRepository = userRepository;
    }

    /** Management only: list all employees */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','ACCOUNTANT','DIRECTOR','LEAVES','MANAGER')")
    public ResponseEntity<List<EmployeeDTO>> getAllEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployees());
    }

    /** Management only: list system users */
    @GetMapping("/system")
    @PreAuthorize("hasAnyRole('ADMIN','HR','ACCOUNTANT','DIRECTOR','LEAVES','MANAGER')")
    public ResponseEntity<List<EmployeeDTO>> getSystemUsers() {
        return ResponseEntity.ok(employeeService.getSystemUsers());
    }

    /**
     * Approver lookup — accessible by ALL authenticated users (including EMPLOYEE role).
     * Returns ONLY the minimal fields needed to populate the leave-form approver dropdown:
     * id, firstName, lastName, designation, departmentName.
     * No sensitive salary/personal data is exposed.
     *
     * IMPORTANT: Only system/management users are returned — regular employees
     * (Admin dept, etc.) are intentionally excluded so they cannot appear as approvers.
     *
     * Optionally filter by department name: GET /api/employees/approvers?department=HR
     */
    @GetMapping("/approvers")
    public ResponseEntity<List<Map<String, Object>>> getApprovers(
            @RequestParam(required = false) String department) {

        // ONLY system users (management roles: HR, Leaves, Accountant, Director, etc.)
        // Regular employees are intentionally excluded — they cannot approve leaves.
        List<EmployeeDTO> systemUsers = employeeService.getSystemUsers();

        List<Map<String, Object>> result = systemUsers.stream()
                .filter(emp -> {
                    if (department == null || department.isBlank()) return true;
                    if (emp.getDepartmentName() == null) return false;
                    return emp.getDepartmentName().trim().equalsIgnoreCase(department.trim());
                })
                .map(emp -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", emp.getId());
                    m.put("firstName", emp.getFirstName());
                    m.put("lastName", emp.getLastName());
                    m.put("designation", emp.getDesignation());
                    m.put("departmentName", emp.getDepartmentName());
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Get employee by ID.
     * - Management roles: can access any employee.
     * - Employee role: can only access their OWN record (linked via user account).
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getEmployeeById(@PathVariable Long id, Authentication authentication) {
        boolean isManagement = authentication.getAuthorities().stream()
                .anyMatch(a -> {
                    String role = a.getAuthority();
                    return role.equals("ROLE_ADMIN") || role.equals("ROLE_HR")
                        || role.equals("ROLE_ACCOUNTANT") || role.equals("ROLE_DIRECTOR")
                        || role.equals("ROLE_LEAVES") || role.equals("ROLE_MANAGER");
                });

        if (!isManagement) {
            // Employee: verify they are only accessing their own record
            String username = authentication.getName();
            var userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent() && userOpt.get().getEmployee() != null) {
                Long linkedId = userOpt.get().getEmployee().getId();
                if (!linkedId.equals(id)) {
                    return ResponseEntity.status(403)
                            .body("Access denied: You can only view your own employee record.");
                }
            }
        }

        return ResponseEntity.ok(employeeService.getEmployeeById(id));
    }

    @PostMapping
    public ResponseEntity<EmployeeDTO> createEmployee(@Valid @RequestBody EmployeeDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.createEmployee(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmployeeDTO> updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeDTO dto) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, dto));
    }
@DeleteMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'HR')")
public ResponseEntity<String> deleteEmployee(@PathVariable Long id) {
    employeeService.deleteEmployee(id);
    return ResponseEntity.ok("Employee deleted successfully");
}
}
