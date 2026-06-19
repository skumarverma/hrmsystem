package com.hrm.hrmsystem.config;

import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.service.LeaveRecalculationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class StartupRunner implements CommandLineRunner {

    private final LeaveRecalculationService leaveRecalculationService;
    private final EmployeeRepository employeeRepository;

    public StartupRunner(LeaveRecalculationService leaveRecalculationService, EmployeeRepository employeeRepository) {
        this.leaveRecalculationService = leaveRecalculationService;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Starting Leave Ledger Recalculation for ALL employees...");
        List<Employee> employees = employeeRepository.findAll();
        for (Employee emp : employees) {
            try {
                leaveRecalculationService.recalculateFromDate(emp.getId(), LocalDate.now());
                System.out.println("Successfully recalculated ledger for employee: " + emp.getEmployeeCode());
            } catch (Exception e) {
                System.err.println("Error recalculating for employee: " + emp.getEmployeeCode());
                e.printStackTrace();
            }
        }
        System.out.println("Leave Ledger Recalculation Complete.");
    }
}
