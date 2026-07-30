package com.hrm.hrmsystem.repository;

import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);

    List<Employee> findByDepartment(Department department);

    List<Employee> findByDepartmentId(Long departmentId);

    List<Employee> findByStatus(Employee.EmployeeStatus status);

    boolean existsByEmail(String email);

    // Eagerly load department and shift to avoid N+1 and lazy loading issues
    @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.department LEFT JOIN FETCH e.shift")
    List<Employee> findAllWithDepartment();

    // Eagerly load department and shift for single employee
    @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.department LEFT JOIN FETCH e.shift WHERE e.id = :id")
    Optional<Employee> findByIdWithDepartment(Long id);

    boolean existsByShiftId(Long shiftId);

    List<Employee> findByPhone(String phone);

    boolean existsByPhone(String phone);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    default Optional<Employee> findByIdentifier(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<Employee> emp = findByEmployeeCode(String.valueOf(id));
        return emp.isPresent() ? emp : findById(id);
    }

    default Optional<Employee> findByIdentifierWithDepartment(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<Employee> emp = findByEmployeeCode(String.valueOf(id));
        if (emp.isPresent()) {
            return findByIdWithDepartment(emp.get().getId());
        }
        return findByIdWithDepartment(id);
    }
}
