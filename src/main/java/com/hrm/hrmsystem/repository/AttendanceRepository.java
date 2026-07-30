package com.hrm.hrmsystem.repository;

import com.hrm.hrmsystem.model.Attendance;
import com.hrm.hrmsystem.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByEmployee(Employee employee);

    List<Attendance> findByEmployeeId(Long employeeId);

    @Query("SELECT a FROM Attendance a JOIN FETCH a.employee e LEFT JOIN FETCH e.department WHERE a.date = :date")
    List<Attendance> findByDate(LocalDate date);

    Optional<Attendance> findByEmployeeIdAndDate(Long employeeId, LocalDate date);

    // Find all attendance records by employee and date (to handle duplicates)
    List<Attendance> findAllByEmployeeIdAndDate(Long employeeId, LocalDate date);

    List<Attendance> findByEmployeeIdAndDateBetween(Long employeeId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT a FROM Attendance a JOIN FETCH a.employee e LEFT JOIN FETCH e.department WHERE a.date BETWEEN :startDate AND :endDate")
    List<Attendance> findByDateBetween(LocalDate startDate, LocalDate endDate);

    // Find attendance by employee and month/year
    @Query("SELECT a FROM Attendance a WHERE a.employee = :employee AND MONTH(a.date) = :month AND YEAR(a.date) = :year")
    List<Attendance> findByEmployeeAndMonthAndYear(@Param("employee") Employee employee, @Param("month") int month, @Param("year") int year);

    void deleteByEmployeeId(Long employeeId);
}
