package com.hrm.hrmsystem.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user who receives this notification.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User recipient;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    // e.g., "PAYSLIP_APPROVAL", "INFO", "WARNING"
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
private boolean isRead = false;

@Column(nullable = false)
private LocalDateTime createdAt = LocalDateTime.now();
    
    // Optional link for the notification to redirect the user
    @Column(length = 255)
    private String actionLink;
}
