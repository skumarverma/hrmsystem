
package com.hrm.hrmsystem;

import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.io.FileWriter;

@Component
public class EmpDataExporter implements CommandLineRunner {
    private final EmployeeRepository repository;
    public EmpDataExporter(EmployeeRepository repository) { this.repository = repository; }
    @Override
    public void run(String... args) throws Exception {
        try (FileWriter writer = new FileWriter("emp_debug.txt")) {
            repository.findAll().forEach(e -> {
                if (e.getFirstName().contains("Anubhav") || e.getId() == 16 || e.getId() == 51) {
                    try {
                        writer.write(String.format("ID: %d | Name: %s %s | Joining: %s | Probation: %s | Status: %s\n",
                            e.getId(), e.getFirstName(), e.getLastName(), e.getJoiningDate(),
                            e.getProbationPeriodMonths(), e.getProbationStatus()));
                    } catch (Exception ex) {}
                }
            });
        }
    }
}
