package com.hrm.hrmsystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    private Boolean emailNotifications = true;

    @Column(nullable = false)
    private Boolean pushNotifications = true;

    @Column(nullable = false)
    private Boolean smsNotifications = false;

    @Column(nullable = false)
    private Boolean leaveUpdates = true;

    @Column(nullable = false)
    private Boolean payrollUpdates = true;

    @Column(nullable = false)
    private Boolean attendanceUpdates = true;

    @Column(nullable = false)
    private Boolean monthlyReports = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // Pre-persist lifecycle callback
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Default constructor
    public NotificationPreference() {}

    // Constructor with fields
    public NotificationPreference(User user, Boolean emailNotifications, Boolean pushNotifications, 
                                  Boolean smsNotifications, Boolean leaveUpdates, Boolean payrollUpdates,
                                  Boolean attendanceUpdates) {
        this.user = user;
        this.emailNotifications = emailNotifications != null ? emailNotifications : true;
        this.pushNotifications = pushNotifications != null ? pushNotifications : true;
        this.smsNotifications = smsNotifications != null ? smsNotifications : false;
        this.leaveUpdates = leaveUpdates != null ? leaveUpdates : true;
        this.payrollUpdates = payrollUpdates != null ? payrollUpdates : true;
        this.attendanceUpdates = attendanceUpdates != null ? attendanceUpdates : true;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    
    public Boolean getEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(Boolean emailNotifications) { this.emailNotifications = emailNotifications; }
    
    public Boolean getPushNotifications() { return pushNotifications; }
    public void setPushNotifications(Boolean pushNotifications) { this.pushNotifications = pushNotifications; }
    
    public Boolean getSmsNotifications() { return smsNotifications; }
    public void setSmsNotifications(Boolean smsNotifications) { this.smsNotifications = smsNotifications; }
    
    public Boolean getLeaveUpdates() { return leaveUpdates; }
    public void setLeaveUpdates(Boolean leaveUpdates) { this.leaveUpdates = leaveUpdates; }
    
    public Boolean getPayrollUpdates() { return payrollUpdates; }
    public void setPayrollUpdates(Boolean payrollUpdates) { this.payrollUpdates = payrollUpdates; }
    
    public Boolean getAttendanceUpdates() { return attendanceUpdates; }
    public void setAttendanceUpdates(Boolean attendanceUpdates) { this.attendanceUpdates = attendanceUpdates; }
    
    public Boolean getMonthlyReports() { return monthlyReports; }
    public void setMonthlyReports(Boolean monthlyReports) { this.monthlyReports = monthlyReports; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
