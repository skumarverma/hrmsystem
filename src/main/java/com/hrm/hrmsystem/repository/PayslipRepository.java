package com.hrm.hrmsystem.repository;

import com.hrm.hrmsystem.entity.Payslip;
import com.hrm.hrmsystem.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    
    @Query("SELECT p FROM Payslip p JOIN FETCH p.employee e LEFT JOIN FETCH e.department")
    List<Payslip> findAll();
    
    // Find payslip by employee and month
    Optional<Payslip> findByEmployeeIdAndMonthYear(Long employeeId, String monthYear);
    
    // Find all payslips by employee and month (to handle duplicates)
    java.util.List<Payslip> findAllByEmployeeIdAndMonthYear(Long employeeId, String monthYear);
    
    // Find payslip by employee entity and month (for deletion)
    Optional<Payslip> findByEmployeeAndMonthYear(Employee employee, String monthYear);
    
    // Find all payslips by employee entity and month (to handle duplicates)
    java.util.List<Payslip> findAllByEmployeeAndMonthYear(Employee employee, String monthYear);
    
    // Find all payslips for an employee
    List<Payslip> findByEmployeeIdOrderByMonthYearDesc(Long employeeId);
    
    // Find payslips by employee entity
    List<Payslip> findByEmployeeOrderByMonthYearDesc(Employee employee);
    
    // Find payslips by status
    List<Payslip> findByStatusOrderByCreatedAtDesc(Payslip.PayslipStatus status);
    
    // Find payslips for a specific month across all employees
    List<Payslip> findByMonthYear(String monthYear);
    
    // Clear payslips for a month so they can be regenerated with fresh attendance data
    void deleteByMonthYear(String monthYear);
    
    // Custom query to find pending approvals
    @Query("SELECT p FROM Payslip p WHERE p.status = 'GENERATED' ORDER BY p.createdAt DESC")
    List<Payslip> findPendingApprovals();
    
    // Count payslips for a month
    long countByMonthYear(String monthYear);
    
    // Find all approved payslips for a month
    List<Payslip> findByStatusAndMonthYear(Payslip.PayslipStatus status, String monthYear);
    
    // Check if payslip exists for employee in month
    boolean existsByEmployeeIdAndMonthYear(Long employeeId, String monthYear);
    
    // Check if payslip exists for employee entity in month
    boolean existsByEmployeeAndMonthYear(Employee employee, String monthYear);

    void deleteByEmployeeId(Long employeeId);
}
