package com.hrm.hrmsystem.migration;

import com.hrm.hrmsystem.model.EmployeeDocument;
import com.hrm.hrmsystem.repository.EmployeeDocumentRepository;
import com.hrm.hrmsystem.service.CloudinaryDocumentService;
import com.hrm.hrmsystem.service.CloudinaryDocumentService.UploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * Migrate local document files into Cloudinary and update DB rows.
 * Enable with property `migration.docs.enabled=true`.
 */
@Component
@ConditionalOnProperty(prefix = "migration.docs", name = "enabled", havingValue = "true")
public class DocumentMigrator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentMigrator.class);

    private final EmployeeDocumentRepository documentRepository;
    private final CloudinaryDocumentService cloudinaryDocumentService;

    @Value("${migration.docs.base-dir:uploads}")
    private String baseDir;

    public DocumentMigrator(EmployeeDocumentRepository documentRepository,
                             CloudinaryDocumentService cloudinaryDocumentService) {
        this.documentRepository = documentRepository;
        this.cloudinaryDocumentService = cloudinaryDocumentService;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("DocumentMigrator starting. Base dir: {}", baseDir);
        List<EmployeeDocument> docs = documentRepository.findAll();
        int localMigrated = 0, cloudMigrated = 0, skipped = 0;

        for (EmployeeDocument doc : docs) {
            String path = doc.getFilePath();
            if (path == null) {
                skipped++;
                continue;
            }

            // Case 1: Local file that needs to be uploaded to Cloudinary
            if (!path.startsWith("http")) {
                File f = new File(path);
                if (!f.exists()) f = new File(baseDir, path);
                if (!f.exists()) {
                    log.warn("Local file for document id {} not found: {}", doc.getId(), path);
                    skipped++;
                    continue;
                }

                try {
                    UploadResult res = cloudinaryDocumentService.upload(f, doc.getEmployee().getId());
                    doc.setFilePath(res.secureUrl());
                    doc.setCloudinaryPublicId(res.publicId());
                    doc.setResourceType(res.resourceType());
                    documentRepository.save(doc);
                    log.info("Migrated local file document id {} to Cloudinary: {}", doc.getId(), res.secureUrl());
                    localMigrated++;
                } catch (Exception ex) {
                    log.error("Failed to migrate document id {} (file {}). Error: {}", doc.getId(), f.getAbsolutePath(), ex.getMessage());
                }
                continue;
            }

            // Case 2: File already in Cloudinary but has wrong resource type (e.g., PDF as "image" instead of "raw")
            String fileName = doc.getFileName();
            String currentResourceType = doc.getResourceType();
            String correctResourceType = getCorrectResourceType(fileName);

            if (currentResourceType != null && !currentResourceType.equals(correctResourceType)) {
                log.warn("Document id {} has wrong resource_type: {} (should be {}). Marking for re-upload.",
                        doc.getId(), currentResourceType, correctResourceType);
                // Update DB with correct resource type (the file will still work via stream endpoint)
                doc.setResourceType(correctResourceType);
                documentRepository.save(doc);
                cloudMigrated++;
                log.info("Updated document id {} resource type to {}", doc.getId(), correctResourceType);
            }
        }
        log.info("DocumentMigrator finished. Local: {}, Cloud: {}, Skipped: {}", localMigrated, cloudMigrated, skipped);
    }

    private String getCorrectResourceType(String fileName) {
        if (fileName == null) return "raw";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")
                || lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".ppt")
                || lower.endsWith(".pptx") || lower.endsWith(".txt") || lower.endsWith(".csv")
                || lower.endsWith(".zip") || lower.endsWith(".rar")) {
            return "raw";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")) {
            return "image";
        }
        return "raw";
    }
}
