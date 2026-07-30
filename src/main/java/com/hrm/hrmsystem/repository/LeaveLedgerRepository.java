package com.hrm.hrmsystem.repository;

import com.hrm.hrmsystem.model.LeaveLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveLedgerRepository extends JpaRepository<LeaveLedger, Long> {

    /** All ledger entries for an employee, sorted by event date ascending */
    List<LeaveLedger> findByEmployeeIdOrderByEventDateAsc(Long employeeId);

    /** Entries from a specific date onwards (used for partial recalculation) */
    List<LeaveLedger> findByEmployeeIdAndEventDateGreaterThanEqualOrderByEventDateAsc(
            Long employeeId, LocalDate fromDate);

    /** Entries in a date range (used for cycle-based queries) */
    List<LeaveLedger> findByEmployeeIdAndEventDateBetweenOrderByEventDateAsc(
            Long employeeId, LocalDate startDate, LocalDate endDate);

    /** Entries in a date range for all employees */
    List<LeaveLedger> findByEventDateBetweenOrderByEventDateAsc(LocalDate startDate, LocalDate endDate);

    /** Latest ledger entry for an employee before a given date (for opening balance) */
    @Query("SELECT l FROM LeaveLedger l WHERE l.employeeId = :empId AND l.eventDate < :date ORDER BY l.eventDate DESC")
    List<LeaveLedger> findLatestBeforeDate(@Param("empId") Long empId, @Param("date") LocalDate date);

    /** Find ledger entry for a specific leave reference */
    Optional<LeaveLedger> findByReferenceIdAndEventType(Long referenceId, LeaveLedger.EventType eventType);

    /** Delete all entries from a date onwards (for re-computation) */
    @Modifying
    @Query("DELETE FROM LeaveLedger l WHERE l.employeeId = :empId AND l.eventDate >= :fromDate")
    void deleteFromDate(@Param("empId") Long empId, @Param("fromDate") LocalDate fromDate);

    /** Delete all entries for a specific employee in a cycle */
    @Modifying
    @Query("DELETE FROM LeaveLedger l WHERE l.employeeId = :empId AND l.eventDate BETWEEN :start AND :end")
    void deleteBetween(@Param("empId") Long empId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
