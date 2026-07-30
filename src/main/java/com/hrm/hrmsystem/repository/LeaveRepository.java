package com.hrm.hrmsystem.repository;

import com.hrm.hrmsystem.model.Leave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.repository.query.Param;

@Repository
public interface LeaveRepository extends JpaRepository<Leave, Long> {
    
    @Query("SELECT l FROM Leave l JOIN FETCH l.employee e LEFT JOIN FETCH e.department")
    List<Leave> findAll();

    List<Leave> findByEmployeeId(Long employeeId);

    @Query("SELECT l FROM Leave l JOIN FETCH l.employee e LEFT JOIN FETCH e.department WHERE l.status = :status")
    List<Leave> findByStatusWithEmployee(Leave.LeaveStatus status);

    List<Leave> findByStatus(Leave.LeaveStatus status);

    List<Leave> findByEmployeeIdAndStatus(Long employeeId, Leave.LeaveStatus status);

    @Query("SELECT l FROM Leave l JOIN FETCH l.employee e WHERE l.status = :status AND l.startDate <= :date AND l.endDate >= :date")
    List<Leave> findByStatusAndDateOverlapping(@Param("status") Leave.LeaveStatus status, @Param("date") LocalDate date);

    List<Leave> findByEmployeeIdAndStatusAndStartDateBetween(
            Long employeeId, Leave.LeaveStatus status, LocalDate startDate, LocalDate endDate);

    /** Find all leaves assigned to a specific approver (by employee ID) */
    List<Leave> findByApproverId(Long approverId);
    
    // REMOVED: countWorkingDays() - Use AttendanceEngine.calculateWorkingDays() only

    void deleteByEmployeeId(Long employeeId);
}

