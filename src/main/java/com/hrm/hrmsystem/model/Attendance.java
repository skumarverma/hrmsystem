package com.hrm.hrmsystem.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "attendance", indexes = {
    @Index(name = "idx_attendance_employee_date", columnList = "employee_id, date"),
    @Index(name = "idx_attendance_date", columnList = "date")
})
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate date;

    private LocalTime checkInTime;

    private LocalTime checkOutTime;

    private Double workingHours;

    private Double overtimeHours;

    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    private HalfType halfType;

    private String remarks;

    public enum AttendanceStatus {
        PRESENT, ABSENT, HALF_DAY, LATE, ON_LEAVE, PENDING
    }

    public enum HalfType {
        FIRST_HALF, SECOND_HALF
    }

    // Default Constructor
    public Attendance() {}

    // All Args Constructor
    public Attendance(Long id, Employee employee, LocalDate date, LocalTime checkInTime,
                      LocalTime checkOutTime, Double workingHours, Double overtimeHours,
                      AttendanceStatus status, String remarks) {
        this.id = id;
        this.employee = employee;
        this.date = date;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.workingHours = workingHours;
        this.overtimeHours = overtimeHours;
        this.status = status;
        this.remarks = remarks;
    }

    // Getters
    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public LocalDate getDate() { return date; }
    public LocalTime getCheckInTime() { return checkInTime; }
    public LocalTime getCheckOutTime() { return checkOutTime; }
    public Double getWorkingHours() { return workingHours; }
    public Double getOvertimeHours() { return overtimeHours; }
    public AttendanceStatus getStatus() { return status; }
    public HalfType getHalfType() { return halfType; }
    public String getRemarks() { return remarks; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public void setDate(LocalDate date) { this.date = date; }
    public void setCheckInTime(LocalTime checkInTime) { this.checkInTime = checkInTime; }
    public void setCheckOutTime(LocalTime checkOutTime) { this.checkOutTime = checkOutTime; }
    public void setWorkingHours(Double workingHours) { this.workingHours = workingHours; }
    public void setOvertimeHours(Double overtimeHours) { this.overtimeHours = overtimeHours; }
    public void setStatus(AttendanceStatus status) { this.status = status; }
    public void setHalfType(HalfType halfType) { this.halfType = halfType; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private Employee employee;
        private LocalDate date;
        private LocalTime checkInTime;
        private LocalTime checkOutTime;
        private Double workingHours;
        private Double overtimeHours;
        private AttendanceStatus status;
        private HalfType halfType;
        private String remarks;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder employee(Employee employee) { this.employee = employee; return this; }
        public Builder date(LocalDate date) { this.date = date; return this; }
        public Builder checkInTime(LocalTime checkInTime) { this.checkInTime = checkInTime; return this; }
        public Builder checkOutTime(LocalTime checkOutTime) { this.checkOutTime = checkOutTime; return this; }
        public Builder workingHours(Double workingHours) { this.workingHours = workingHours; return this; }
        public Builder overtimeHours(Double overtimeHours) { this.overtimeHours = overtimeHours; return this; }
        public Builder status(AttendanceStatus status) { this.status = status; return this; }
        public Builder halfType(HalfType halfType) { this.halfType = halfType; return this; }
        public Builder remarks(String remarks) { this.remarks = remarks; return this; }

        public Attendance build() {
            Attendance attendance = new Attendance(id, employee, date, checkInTime, checkOutTime, workingHours, overtimeHours, status, remarks);
            attendance.setHalfType(halfType);
            return attendance;
        }
    }
}
