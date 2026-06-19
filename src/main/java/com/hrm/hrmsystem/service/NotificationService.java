package com.hrm.hrmsystem.service;

import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.Leave;
import com.hrm.hrmsystem.model.Payroll;
import com.hrm.hrmsystem.model.NotificationPreference;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.repository.NotificationPreferenceRepository;
import com.hrm.hrmsystem.repository.UserRepository;
import com.hrm.hrmsystem.repository.AppNotificationRepository;
import com.hrm.hrmsystem.model.AppNotification;
import com.hrm.hrmsystem.model.User;
import com.hrm.hrmsystem.util.EmailUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
public class NotificationService {

    @Autowired
    private EmailUtil emailUtil;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private AppNotificationRepository appNotificationRepository;

    /**
     * Check if email notification is enabled for the employee for a specific category
     */
    private boolean shouldSendEmail(Employee employee, String category) {
        if (employee == null || employee.getId() == null) return false;
        
        return userRepository.findByEmployeeId(employee.getId())
            .flatMap(user -> preferenceRepository.findByUser(user))
            .map(pref -> {
                boolean globalEmail = pref.getEmailNotifications() != null && pref.getEmailNotifications();
                boolean categoryEnabled = true;
                
                if ("PAYROLL".equals(category)) {
                    categoryEnabled = pref.getPayrollUpdates() != null && pref.getPayrollUpdates();
                } else if ("LEAVE".equals(category)) {
                    categoryEnabled = pref.getLeaveUpdates() != null && pref.getLeaveUpdates();
                } else if ("ATTENDANCE".equals(category)) {
                    categoryEnabled = pref.getAttendanceUpdates() != null && pref.getAttendanceUpdates();
                }
                
                return globalEmail && categoryEnabled;
            })
            .orElse(true); // Default to true if no preference record exists
    }

    /**
     * Send payroll payment notification to employee
     */
    public void sendPayrollPaymentNotification(Payroll payroll) {
        try {
            Employee employee = payroll.getEmployee();
            if (employee == null || employee.getEmail() == null) {
                return;
            }

            if (!shouldSendEmail(employee, "PAYROLL")) {
                System.out.println("⏩ Skipping payroll email for " + employee.getEmail() + " (preference disabled)");
                return;
            }

            String toEmail = employee.getEmail();
            String subject = "Salary Payment Notification - " + getMonthYear(payroll.getMonth(), payroll.getYear());
            
            // Safe null handling for BigDecimal values
            double basicSalary = payroll.getBasicSalary() != null ? payroll.getBasicSalary().doubleValue() : 0.0;
            double hra = payroll.getHra() != null ? payroll.getHra().doubleValue() : 0.0;
            double ta = payroll.getTa() != null ? payroll.getTa().doubleValue() : 0.0;
            double allowances = hra + ta;
            double deductions = payroll.getTotalDeductions() != null ? payroll.getTotalDeductions().doubleValue() : 0.0;
            double netSalary = payroll.getNetSalary() != null ? payroll.getNetSalary().doubleValue() : 0.0;
            
            String htmlBody = String.format(
                "<html><body>" +
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                "<h2 style='color: #4f46e5;'>💰 Salary Payment Notification</h2>" +
                "<p>Dear %s,</p>" +
                "<p>Your salary for <strong>%s %d</strong> has been processed and paid.</p>" +
                "<div style='background: #f3f4f6; padding: 20px; border-radius: 8px; margin: 20px 0;'>" +
                "<h3>Payment Details:</h3>" +
                "<table style='width: 100%%; border-collapse: collapse;'>" +
                "<tr><td style='padding: 8px 0;'><strong>Basic Salary:</strong></td><td style='text-align: right;'>₹%,.2f</td></tr>" +
                "<tr><td style='padding: 8px 0;'><strong>Allowances:</strong></td><td style='text-align: right;'>₹%,.2f</td></tr>" +
                "<tr><td style='padding: 8px 0;'><strong>Deductions:</strong></td><td style='text-align: right;'>₹%,.2f</td></tr>" +
                "<tr style='border-top: 2px solid #4f46e5;'><td style='padding: 12px 0; font-weight: bold;'><strong>Net Salary:</strong></td><td style='text-align: right; color: #10b981; font-weight: bold;'>₹%,.2f</td></tr>" +
                "</table>" +
                "</div>" +
                "<p>The amount has been credited to your account.</p>" +
                "<p style='color: #6b7280; font-size: 14px;'>This is an automated notification. Please contact HR for any queries.</p>" +
                "</div>" +
                "</body></html>",
                employee.getFirstName(),
                getMonthName(payroll.getMonth()),
                payroll.getYear(),
                basicSalary,
                allowances,
                deductions,
                netSalary
            );

            emailUtil.sendHtmlEmail(toEmail, subject, htmlBody);
        } catch (Exception e) {
            System.err.println("Error sending payroll notification: " + e.getMessage());
        }
    }

    /**
     * Send an in-app notification to a specific user
     */
    public void sendInAppNotification(User recipient, String title, String message, String type, String actionLink) {
        AppNotification notification = new AppNotification();
        notification.setRecipient(recipient);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setActionLink(actionLink);
        notification.setRead(false);
        notification.setCreatedAt(java.time.LocalDateTime.now());
        appNotificationRepository.save(notification);
    }

    /**
     * Send an in-app notification to all users with a specific role
     */
    public void sendInAppNotificationToRole(User.Role role, String title, String message, String type, String actionLink) {
        userRepository.findByRole(role).forEach(user -> 
            sendInAppNotification(user, title, message, type, actionLink)
        );
    }

    /**
     * Send an in-app notification to an employee
     */
    public void sendInAppNotificationToEmployee(Employee employee, String title, String message, String type, String actionLink) {
        if (employee == null || employee.getId() == null) return;
        userRepository.findByEmployeeId(employee.getId()).ifPresent(user -> 
            sendInAppNotification(user, title, message, type, actionLink)
        );
    }

    /**
     * Send leave approval/rejection notification
     */
    public void sendLeaveStatusNotification(Leave leave, boolean approved) {
        try {
            Employee employee = leave.getEmployee();
            if (employee == null || employee.getEmail() == null) {
                return;
            }

            if (!shouldSendEmail(employee, "LEAVE")) {
                System.out.println("⏩ Skipping leave status email for " + employee.getEmail() + " (preference disabled)");
                return;
            }

            String toEmail = employee.getEmail();
            String status = approved ? "Approved" : "Rejected";
            String statusEmoji = approved ? "✅" : "❌";
            String subject = "Leave Application " + status + " - " + leave.getLeaveType();
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
            
            String htmlBody = String.format(
                "<html><body>" +
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                "<h2 style='color: %s;'>%s Leave Application %s</h2>" +
                "<p>Dear %s,</p>" +
                "<p>Your leave application has been <strong>%s</strong>.</p>" +
                "<div style='background: #f3f4f6; padding: 20px; border-radius: 8px; margin: 20px 0;'>" +
                "<h3>Leave Details:</h3>" +
                "<p><strong>Leave Type:</strong> %s</p>" +
                "<p><strong>From Date:</strong> %s</p>" +
                "<p><strong>To Date:</strong> %s</p>" +
                "<p><strong>Total Days:</strong> %s</p>" +
                "<p><strong>Reason:</strong> %s</p>" +
                "</div>" +
                "%s" +
                "<p style='color: #6b7280; font-size: 14px;'>This is an automated notification. Please contact HR for any queries.</p>" +
                "</div>" +
                "</body></html>",
                approved ? "#10b981" : "#ef4444",
                statusEmoji,
                status,
                employee.getFirstName(),
                status,
                leave.getLeaveType(),
                leave.getStartDate().format(formatter),
                leave.getEndDate().format(formatter),
                String.valueOf(leave.getTotalDays()),
                leave.getReason() != null ? leave.getReason() : "N/A",
                approved ? "<p style='color: #10b981;'>Enjoy your time off!</p>" : ""
            );

            emailUtil.sendHtmlEmail(toEmail, subject, htmlBody);
        } catch (Exception e) {
            System.err.println("Error sending leave notification: " + e.getMessage());
        }
    }

    /**
     * Send new leave application notification to HR/Manager
     */
    public void sendNewLeaveApplicationNotification(Leave leave, String hrEmail) {
        try {
            if (hrEmail == null || hrEmail.isEmpty()) {
                return;
            }

            // Check recipient's email notification preferences
            boolean shouldSend = userRepository.findByEmail(hrEmail)
                .flatMap(user -> preferenceRepository.findByUser(user))
                .map(pref -> {
                    boolean globalEmail = pref.getEmailNotifications() != null && pref.getEmailNotifications();
                    boolean categoryEnabled = pref.getLeaveUpdates() != null && pref.getLeaveUpdates();
                    return globalEmail && categoryEnabled;
                })
                .orElse(true);

            if (!shouldSend) {
                System.out.println("⏩ Skipping new leave notification email for " + hrEmail + " (preference disabled)");
                return;
            }

            Employee employee = leave.getEmployee();
            String employeeName = employee != null ? 
                employee.getFirstName() + " " + employee.getLastName() : "Unknown";

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
            
            String subject = "New Leave Application - " + employeeName;
            
            String htmlBody = String.format(
                "<html><body>" +
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                "<h2 style='color: #f59e0b;'>📝 New Leave Application</h2>" +
                "<p>A new leave application has been submitted.</p>" +
                "<div style='background: #f3f4f6; padding: 20px; border-radius: 8px; margin: 20px 0;'>" +
                "<h3>Employee Details:</h3>" +
                "<p><strong>Name:</strong> %s</p>" +
                "<p><strong>Email:</strong> %s</p>" +
                "<h3>Leave Details:</h3>" +
                "<p><strong>Leave Type:</strong> %s</p>" +
                "<p><strong>From Date:</strong> %s</p>" +
                "<p><strong>To Date:</strong> %s</p>" +
                "<p><strong>Total Days:</strong> %s</p>" +
                "<p><strong>Reason:</strong> %s</p>" +
                "</div>" +
                "<p>Please review and take necessary action.</p>" +
                "</div>" +
                "</body></html>",
                employeeName,
                employee != null ? employee.getEmail() : "N/A",
                leave.getLeaveType(),
                leave.getStartDate().format(formatter),
                leave.getEndDate().format(formatter),
                String.valueOf(leave.getTotalDays()),
                leave.getReason() != null ? leave.getReason() : "N/A"
            );

            emailUtil.sendHtmlEmail(hrEmail, subject, htmlBody);
        } catch (Exception e) {
            System.err.println("Error sending new leave notification: " + e.getMessage());
        }
    }

    /**
     * Send attendance regularization notification
     */
    public void sendAttendanceRegularizationNotification(Employee employee, String date, String status) {
        try {
            if (employee == null || employee.getEmail() == null) {
                return;
            }

            if (!shouldSendEmail(employee, "ATTENDANCE")) {
                System.out.println("⏩ Skipping attendance email for " + employee.getEmail() + " (preference disabled)");
                return;
            }

            String toEmail = employee.getEmail();
            String subject = "Attendance Marked - " + date;
            
            String htmlBody = String.format(
                "<html><body>" +
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                "<h2 style='color: #4f46e5;'>✅ Attendance Marked</h2>" +
                "<p>Dear %s,</p>" +
                "<p>Your attendance for <strong>%s</strong> has been marked as <strong>%s</strong>.</p>" +
                "<p style='color: #6b7280; font-size: 14px;'>This is an automated notification.</p>" +
                "</div>" +
                "</body></html>",
                employee.getFirstName(),
                date,
                status
            );

            emailUtil.sendHtmlEmail(toEmail, subject, htmlBody);
        } catch (Exception e) {
            System.err.println("Error sending attendance notification: " + e.getMessage());
        }
    }

    /**
     * Send leave modification notification to employee
     */
    public void sendLeaveModificationNotification(Leave leave, String reason) {
        try {
            Employee employee = leave.getEmployee();
            if (employee == null || employee.getEmail() == null) {
                return;
            }

            if (!shouldSendEmail(employee, "LEAVE")) {
                System.out.println("⏩ Skipping leave status email for " + employee.getEmail() + " (preference disabled)");
                return;
            }

            String toEmail = employee.getEmail();
            String subject = "Leave Application Modified - " + leave.getLeaveType();
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
            
            String htmlBody = String.format(
                "<html><body>" +
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                "<h2 style='color: #4f46e5;'>📝 Leave Application Modified</h2>" +
                "<p>Dear %s,</p>" +
                "<p>Your leave application has been <strong>modified</strong> by the administrator.</p>" +
                "<div style='background: #f3f4f6; padding: 20px; border-radius: 8px; margin: 20px 0;'>" +
                "<h3>Updated Leave Details:</h3>" +
                "<p><strong>Leave Type:</strong> %s</p>" +
                "<p><strong>From Date:</strong> %s</p>" +
                "<p><strong>To Date:</strong> %s</p>" +
                "<p><strong>Total Days:</strong> %s</p>" +
                "<p><strong>Reason/Remarks:</strong> %s</p>" +
                "</div>" +
                "<p style='color: #6b7280; font-size: 14px;'>This is an automated notification. Please contact HR for any queries.</p>" +
                "</div>" +
                "</body></html>",
                employee.getFirstName(),
                leave.getLeaveType(),
                leave.getStartDate().format(formatter),
                leave.getEndDate().format(formatter),
                String.valueOf(leave.getTotalDays()),
                reason != null ? reason : "Leave details modified"
            );

            emailUtil.sendHtmlEmail(toEmail, subject, htmlBody);
        } catch (Exception e) {
            System.err.println("Error sending leave modification notification: " + e.getMessage());
        }
    }

    // Helper methods
    private String getMonthName(int month) {
        String[] months = {"January", "February", "March", "April", "May", "June",
                          "July", "August", "September", "October", "November", "December"};
        return months[month - 1];
    }

    private String getMonthYear(Integer month, Integer year) {
        return getMonthName(month) + " " + year;
    }
}
