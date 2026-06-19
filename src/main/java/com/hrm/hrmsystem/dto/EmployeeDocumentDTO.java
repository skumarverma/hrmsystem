package com.hrm.hrmsystem.dto;

import java.time.LocalDateTime;

public class EmployeeDocumentDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String documentType;
    private String fileName;
    private String fileUrl;
    private LocalDateTime uploadedAt;

    public EmployeeDocumentDTO() {}

    public EmployeeDocumentDTO(Long id, Long employeeId, String employeeName, String documentType,
                               String fileName, String fileUrl, LocalDateTime uploadedAt) {
        this.id = id;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.documentType = documentType;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
