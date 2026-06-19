package com.hrm.hrmsystem;

import com.hrm.hrmsystem.model.Leave;
import com.hrm.hrmsystem.repository.LeaveRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.io.FileWriter;
import java.util.List;

@Component
public class DataExporter implements CommandLineRunner {
    private final LeaveRepository leaveRepository;
    public DataExporter(LeaveRepository leaveRepository) { this.leaveRepository = leaveRepository; }
    @Override
    public void run(String... args) throws Exception {
        try (FileWriter writer = new FileWriter("anubhav_debug.txt")) {
            writer.write("--- ANUBHAV LEAVE DATA ---\n");
            leaveRepository.findAll().forEach(l -> {
                if (l.getEmployee() != null && l.getEmployee().getFirstName().contains("Anubhav")) {
                    try {
                        writer.write(String.format("ID: %d | Dates: %s to %s | Type: %s | Status: %s | Paid: %.1f | Unpaid: %.1f | Total: %.1f\n",
                            l.getId(), l.getStartDate(), l.getEndDate(), l.getLeaveType(), l.getStatus(), 
                            l.getPaidDays(), l.getUnpaidDays(), l.getTotalDays()));
                    } catch (Exception e) {}
                }
            });
        }
    }
}
