package com.hrm.hrmsystem.repository;

import com.hrm.hrmsystem.model.Leave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Repository
public interface LeaveRepository extends JpaRepository<Leave, Long> {

    List<Leave> findByEmployeeId(Long employeeId);

    List<Leave> findByStatus(Leave.LeaveStatus status);

    List<Leave> findByEmployeeIdAndStatus(Long employeeId, Leave.LeaveStatus status);

    List<Leave> findByEmployeeIdAndStatusAndStartDateBetween(
            Long employeeId, Leave.LeaveStatus status, LocalDate startDate, LocalDate endDate);

    /** Find all leaves assigned to a specific approver (by employee ID) */
    List<Leave> findByApproverId(Long approverId);
    
    // REMOVED: countWorkingDays() - Use AttendanceEngine.calculateWorkingDays() only

    void deleteByEmployeeId(Long employeeId);
}

