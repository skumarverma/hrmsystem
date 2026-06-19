package com.hrm.hrmsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_documents")
public class EmployeeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private String documentType; // Marksheets, Offer Letter, Other Documents, Images

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, length = 1000)
    private String filePath;

    /** Cloudinary public_id — used for delete and signed download URLs */
    @Column(name = "cloudinary_public_id")
    private String cloudinaryPublicId;

    /** Cloudinary resource_type: image, raw, video, or auto */
    @Column(name = "resource_type")
    private String resourceType;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    public EmployeeDocument() {}

    public EmployeeDocument(Long id, Employee employee, String documentType, String fileName, String filePath,
                            String cloudinaryPublicId, String resourceType, LocalDateTime uploadedAt) {
        this.id = id;
        this.employee = employee;
        this.documentType = documentType;
        this.fileName = fileName;
        this.filePath = filePath;
        this.cloudinaryPublicId = cloudinaryPublicId;
        this.resourceType = resourceType;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getCloudinaryPublicId() { return cloudinaryPublicId; }
    public void setCloudinaryPublicId(String cloudinaryPublicId) { this.cloudinaryPublicId = cloudinaryPublicId; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private Employee employee;
        private String documentType;
        private String fileName;
        private String filePath;
        private String cloudinaryPublicId;
        private String resourceType;
        private LocalDateTime uploadedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder employee(Employee employee) { this.employee = employee; return this; }
        public Builder documentType(String documentType) { this.documentType = documentType; return this; }
        public Builder fileName(String fileName) { this.fileName = fileName; return this; }
        public Builder filePath(String filePath) { this.filePath = filePath; return this; }
        public Builder cloudinaryPublicId(String cloudinaryPublicId) { this.cloudinaryPublicId = cloudinaryPublicId; return this; }
        public Builder resourceType(String resourceType) { this.resourceType = resourceType; return this; }
        public Builder uploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; return this; }

        public EmployeeDocument build() {
            return new EmployeeDocument(id, employee, documentType, fileName, filePath,
                    cloudinaryPublicId, resourceType, uploadedAt);
        }
    }
}
