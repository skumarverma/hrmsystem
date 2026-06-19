package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.dto.DepartmentDTO;
import com.hrm.hrmsystem.model.Department;
import com.hrm.hrmsystem.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentDTO> getAllDepartments() {
        List<Department> departments = departmentRepository.findAll();
        // Initialize lazy collections within transaction
        departments.forEach(dept -> {
            if (dept.getEmployees() != null) {
                dept.getEmployees().size(); // Force initialization
            }
        });
        return departments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DepartmentDTO getDepartmentById(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        // Initialize lazy collection
        if (department.getEmployees() != null) {
            department.getEmployees().size();
        }
        return convertToDTO(department);
    }

    @Transactional
    public DepartmentDTO createDepartment(DepartmentDTO dto) {
        if (departmentRepository.existsByName(dto.getName())) {
            throw new RuntimeException("Department already exists");
        }
        Department department = convertToEntity(dto);
        department.setCreatedDate(LocalDate.now());
        department = departmentRepository.save(department);
        return convertToDTO(department);
    }

    @Transactional
    public DepartmentDTO updateDepartment(Long id, DepartmentDTO dto) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        department.setName(dto.getName());
        department.setCode(dto.getCode());
        department.setDescription(dto.getDescription());
        department.setHeadOfDepartment(dto.getHeadOfDepartment());

        department = departmentRepository.save(department);
        return convertToDTO(department);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        departmentRepository.deleteById(id);
    }

    private DepartmentDTO convertToDTO(Department department) {
        int empCount = 0;
        if (department.getEmployees() != null) {
            String deptName = department.getName() != null ? department.getName().toLowerCase() : "";
            boolean isSystemDept = deptName.contains("hr") || deptName.contains("director") || 
                                   deptName.contains("leave") || deptName.contains("accountant");
            
            if (!isSystemDept) {
                empCount = (int) department.getEmployees().stream()
                        .filter(emp -> emp.getStatus() == com.hrm.hrmsystem.model.Employee.EmployeeStatus.ACTIVE)
                        .count();
            }
        }

        return DepartmentDTO.builder()
                .id(department.getId())
                .name(department.getName())
                .code(department.getCode())
                .description(department.getDescription())
                .headOfDepartment(department.getHeadOfDepartment())
                .createdDate(department.getCreatedDate())
                .employeeCount(empCount)
                .build();
    }

    private Department convertToEntity(DepartmentDTO dto) {
        return Department.builder()
                .name(dto.getName())
                .code(dto.getCode())
                .description(dto.getDescription())
                .headOfDepartment(dto.getHeadOfDepartment())
                .build();
    }
}
