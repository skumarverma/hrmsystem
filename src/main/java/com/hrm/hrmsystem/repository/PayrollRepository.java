package com.hrm.hrmsystem.repository;

import com.hrm.hrmsystem.model.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    List<Payroll> findByEmployeeId(Long employeeId);

    Optional<Payroll> findByEmployeeIdAndMonthAndYear(Long employeeId, Integer month, Integer year);

    // Use this when there might be duplicates - returns the most recent one
    Optional<Payroll> findFirstByEmployeeIdAndMonthAndYearOrderByIdDesc(Long employeeId, Integer month, Integer year);

    @Query("SELECT p FROM Payroll p JOIN FETCH p.employee e LEFT JOIN FETCH e.department WHERE p.month = :month AND p.year = :year")
    List<Payroll> findByMonthAndYear(Integer month, Integer year);

    List<Payroll> findByStatus(Payroll.PayrollStatus status);

    void deleteByEmployeeId(Long employeeId);
}
