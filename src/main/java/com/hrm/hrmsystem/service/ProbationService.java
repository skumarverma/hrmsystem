package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.model.*;
import com.hrm.hrmsystem.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProbationService {
    
    private final EmployeeRepository employeeRepo;
    
    public ProbationService(EmployeeRepository employeeRepo) {
        this.employeeRepo = employeeRepo;
    }
    
    /**
     * Create/Initialize probation for employee
     */
    @Transactional
    public Employee initializeProbation(Long employeeId, Integer durationMonths) {
        Employee employee = employeeRepo.findById(employeeId)
            .orElseThrow(() -> new RuntimeException("Employee not found"));
        
        employee.setProbationPeriodMonths(durationMonths);
        employee.setProbationStatus(Employee.ProbationStatus.PROBATION);
        employee.setPfPercentage(12.0);
        employee.setAnnualTax(0.0);
        
        return employeeRepo.save(employee);
    }
    
    /**
     * Confirm employee (complete probation)
     */
    @Transactional
    public Employee confirmProbation(Long employeeId, String confirmedBy) {
        Employee employee = employeeRepo.findById(employeeId)
            .orElseThrow(() -> new RuntimeException("Employee not found"));
        
        employee.setProbationStatus(Employee.ProbationStatus.CONFIRMED);
        employee.setProbationConfirmedBy(confirmedBy);
        employee.setProbationConfirmedDate(LocalDate.now());
        
        return employeeRepo.save(employee);
    }
    
    /**
     * Extend probation period
     */
    @Transactional
    public Employee extendProbation(Long employeeId, Integer additionalMonths, String notes) {
        Employee employee = employeeRepo.findById(employeeId)
            .orElseThrow(() -> new RuntimeException("Employee not found"));
        
        int currentMonths = employee.getProbationPeriodMonths() != null ? employee.getProbationPeriodMonths() : 3;
        employee.setProbationPeriodMonths(currentMonths + additionalMonths);
        employee.setProbationStatus(Employee.ProbationStatus.EXTENDED);
        employee.setProbationNotes(notes);
        
        return employeeRepo.save(employee);
    }
    
    /**
     * Get probation details for employee (returns employee with probation fields)
     */
    public Employee getProbationDetails(Long employeeId) {
        return employeeRepo.findById(employeeId).orElse(null);
    }
    
    /**
     * Auto-check and update probation status
     * ✅ USES AttendanceEngine logic for consistency
     */
    @Scheduled(cron = "0 0 0 * * ?") // Run daily at midnight
    @Transactional
    public void autoCheckProbationStatus() {
        List<Employee> employeesToCheck = employeeRepo.findAll().stream()
            .filter(emp -> emp.getProbationStatus() == Employee.ProbationStatus.PROBATION || 
                           emp.getProbationStatus() == Employee.ProbationStatus.EXTENDED)
            .collect(Collectors.toList());
        LocalDate today = LocalDate.now();
        
        for (Employee employee : employeesToCheck) {
            if (employee.getProbationStatus() == Employee.ProbationStatus.PROBATION || 
                employee.getProbationStatus() == Employee.ProbationStatus.EXTENDED) {
                
                int probationMonths = employee.getProbationPeriodMonths() != null ? employee.getProbationPeriodMonths() : 3;
                LocalDate probationEnd = employee.getJoiningDate().plusMonths(probationMonths);
                
                if (!today.isBefore(probationEnd)) {
                    // Auto-confirm
                    employee.setProbationStatus(Employee.ProbationStatus.CONFIRMED);
                    employee.setProbationConfirmedBy("SYSTEM");
                    employee.setProbationConfirmedDate(today);
                    System.out.println("✅ Auto-confirmed probation for employee: " + employee.getId());
                }
            }
        }
        
        employeeRepo.saveAll(employeesToCheck);
    }
}
