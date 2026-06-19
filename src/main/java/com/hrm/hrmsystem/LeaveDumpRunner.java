package com.hrm.hrmsystem;

import com.hrm.hrmsystem.model.Leave;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.repository.LeaveRepository;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class LeaveDumpRunner implements CommandLineRunner {
    private final LeaveRepository repository;
    private final EmployeeRepository employeeRepository;
    
    public LeaveDumpRunner(LeaveRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }
    
    @Override
    public void run(String... args) {
        System.out.println("--- LEAVE DUMP START ---");
        
        employeeRepository.findById(8L).ifPresentOrElse(emp -> {
            System.out.println("EMPLOYEE ID 8: " + emp.getFirstName() + " " + emp.getLastName());
            System.out.println("Joining Date: " + emp.getJoiningDate());
            System.out.println("Probation Status: " + emp.getProbationStatus());
            System.out.println("Probation Period Months: " + emp.getProbationPeriodMonths());
        }, () -> {
            System.out.println("EMPLOYEE ID 8 NOT FOUND!");
        });
        
        repository.findAll().forEach(l -> {
            if (l.getEmployee() != null && l.getEmployee().getId() == 8L) {
                System.out.println("ID: " + l.getId());
                System.out.println("Type: " + l.getLeaveType());
                System.out.println("Dates: " + l.getStartDate() + " to " + l.getEndDate());
                System.out.println("Total: " + l.getTotalDays());
                System.out.println("Paid: " + l.getPaidDays());
                System.out.println("Unpaid: " + l.getUnpaidDays());
                System.out.println("FinalPaid: " + l.getFinalPaidDays());
                System.out.println("FinalUnpaid: " + l.getFinalUnpaidDays());
                System.out.println("Status: " + l.getStatus());
                System.out.println("---");
            }
        });
        System.out.println("--- LEAVE DUMP END ---");
    }
}
