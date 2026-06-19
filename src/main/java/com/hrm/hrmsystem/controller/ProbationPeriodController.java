package com.hrm.hrmsystem.controller;

import com.hrm.hrmsystem.engine.AttendanceEngine;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Optional;

@RestController
@RequestMapping("/api/probation")
@CrossOrigin(origins = "*")
public class ProbationPeriodController {

    private final EmployeeRepository employeeRepository;
    private final AttendanceEngine attendanceEngine;

    public ProbationPeriodController(EmployeeRepository employeeRepository, AttendanceEngine attendanceEngine) {
        this.employeeRepository = employeeRepository;
        this.attendanceEngine = attendanceEngine;
    }

    /**
     * Get probation period by employee ID
     */
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<?> getProbationByEmployee(@PathVariable Long employeeId) {
        return employeeRepository.findByIdentifier(employeeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Check if employee is on probation
     */
    @GetMapping("/employee/{employeeId}/status")
    public ResponseEntity<?> checkProbationStatus(@PathVariable Long employeeId) {
        Optional<Employee> employeeOpt = employeeRepository.findByIdentifier(employeeId);
        
        if (employeeOpt.isPresent()) {
            Employee employee = employeeOpt.get();
            boolean isCompleted = attendanceEngine.isProbationCompleted(employee);
            
            int probationMonths = employee.getProbationPeriodMonths() != null ? employee.getProbationPeriodMonths() : 3;
            LocalDate probationEnd = employee.getJoiningDate() != null ? employee.getJoiningDate().plusMonths(probationMonths) : null;
            
            long remainingDays = 0;
            if (!isCompleted && probationEnd != null) {
                remainingDays = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), probationEnd);
                remainingDays = Math.max(0, remainingDays);
            }

            return ResponseEntity.ok(new ProbationStatusResponse(
                !isCompleted,
                probationEnd,
                isCompleted, // Paid leave eligible if probation completed
                remainingDays
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Response DTO
    public static class ProbationStatusResponse {
        private boolean isOnProbation;
        private Object endDate;
        private boolean paidLeaveEligible;
        private long remainingDays;

        public ProbationStatusResponse(boolean isOnProbation, Object endDate, 
                                      boolean paidLeaveEligible, long remainingDays) {
            this.isOnProbation = isOnProbation;
            this.endDate = endDate;
            this.paidLeaveEligible = paidLeaveEligible;
            this.remainingDays = remainingDays;
        }

        public boolean isOnProbation() { return isOnProbation; }
        public void setOnProbation(boolean isOnProbation) { this.isOnProbation = isOnProbation; }
        public Object getEndDate() { return endDate; }
        public void setEndDate(Object endDate) { this.endDate = endDate; }
        public boolean isPaidLeaveEligible() { return paidLeaveEligible; }
        public void setPaidLeaveEligible(boolean paidLeaveEligible) { this.paidLeaveEligible = paidLeaveEligible; }
        public long getRemainingDays() { return remainingDays; }
        public void setRemainingDays(long remainingDays) { this.remainingDays = remainingDays; }
    }
}
