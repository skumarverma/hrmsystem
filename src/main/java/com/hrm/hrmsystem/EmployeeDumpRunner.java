
package com.hrm.hrmsystem;

import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class EmployeeDumpRunner implements CommandLineRunner {
    private final EmployeeRepository repository;
    public EmployeeDumpRunner(EmployeeRepository repository) { this.repository = repository; }
    @Override
    public void run(String... args) {
        System.out.println("--- EMPLOYEE DUMP START ---");
        repository.findAll().forEach(e -> {
            if (e.getFirstName().contains("Anubhav") || e.getLastName().contains("Anubhav")) {
                System.out.println("ID: " + e.getId());
                System.out.println("Name: " + e.getFirstName() + " " + e.getLastName());
                System.out.println("Joining Date: " + e.getJoiningDate());
                System.out.println("Probation Months: " + e.getProbationPeriodMonths());
                System.out.println("Probation Status: " + e.getProbationStatus());
            }
        });
        System.out.println("--- EMPLOYEE DUMP END ---");
    }
}
