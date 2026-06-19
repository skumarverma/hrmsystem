package com.hrm.hrmsystem.controller;

import com.hrm.hrmsystem.dto.EmployeeDocumentDTO;
import com.hrm.hrmsystem.exception.ResourceNotFoundException;
import com.hrm.hrmsystem.model.Employee;
import com.hrm.hrmsystem.model.EmployeeDocument;
import com.hrm.hrmsystem.model.User;
import com.hrm.hrmsystem.repository.EmployeeDocumentRepository;
import com.hrm.hrmsystem.repository.EmployeeRepository;
import com.hrm.hrmsystem.repository.UserRepository;
import com.hrm.hrmsystem.service.CloudinaryDocumentService;
import com.hrm.hrmsystem.service.CloudinaryDocumentService.UploadResult;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class EmployeeDocumentController {
    private static final Logger log = LoggerFactory.getLogger(EmployeeDocumentController.class);
    private final EmployeeDocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;
    private final CloudinaryDocumentService cloudinaryDocumentService;
    private final UserRepository userRepository;

    public EmployeeDocumentController(
            EmployeeDocumentRepository documentRepository,
            EmployeeRepository employeeRepository,
            CloudinaryDocumentService cloudinaryDocumentService,
            UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.employeeRepository = employeeRepository;
        this.cloudinaryDocumentService = cloudinaryDocumentService;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    private boolean isHrOrAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean validateAccess(Long targetEmployeeId) {
        if (isHrOrAdmin()) {
            return true;
        }
        User user = getCurrentUser();
        if (user == null) {
            return false;
        }
        return user.getEmployee() != null && user.getEmployee().getId().equals(targetEmployeeId);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("employeeId") Long employeeId,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {

        Long targetEmployeeId = employeeId;
        if (!isHrOrAdmin()) {
            User user = getCurrentUser();
            if (user == null || user.getEmployee() == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: You do not have permission to upload documents.");
            }
            targetEmployeeId = user.getEmployee().getId();
        } else {
            Employee employee = employeeRepository.findByIdentifier(employeeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
            targetEmployeeId = employee.getId();
        }

        if (file == null || file.isEmpty()) {
            log.error("Upload failed: No file part found for employee {}", targetEmployeeId);
            return ResponseEntity.badRequest().body("File is empty or missing. Check your request format.");
        }

        final Long finalTargetEmployeeId = targetEmployeeId;
        Employee employee = employeeRepository.findById(finalTargetEmployeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + finalTargetEmployeeId));

        try {
            UploadResult upload = cloudinaryDocumentService.upload(file, employee.getId());
            String originalFileName = file.getOriginalFilename();

            EmployeeDocument document = EmployeeDocument.builder()
                    .employee(employee)
                    .documentType(documentType)
                    .fileName(originalFileName != null ? originalFileName : "document")
                    .filePath(upload.secureUrl())
                    .cloudinaryPublicId(upload.publicId())
                    .resourceType(upload.resourceType())
                    .uploadedAt(LocalDateTime.now())
                    .build();

            EmployeeDocument saved = documentRepository.save(document);
            return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));

        } catch (Exception ex) {
            log.error("Cloudinary upload failed for employee {}: {}", employeeId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not upload file to Cloudinary. Error: " + ex.getMessage());
        }
    }

    /**
     * Proxy endpoint for viewing documents inline.
     * Bypasses Cloudinary direct-access restrictions (401) by streaming through backend.
     */
    @GetMapping("/view/{id}")
    public ResponseEntity<?> viewDocument(@PathVariable Long id) {
        return streamDocument(id, "inline");
    }

    /**
     * Proxy endpoint for downloading documents.
     * Bypasses Cloudinary direct-access restrictions (401) by streaming through backend.
     */
    @GetMapping("/download/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> downloadDocument(@PathVariable Long id) {
        return streamDocument(id, "attachment");
    }

    /**
     * Stream file through backend so the browser never hits a broken Cloudinary URL (HTTP 400).
     * Use for PDF preview iframe and reliable downloads.
     */
    @GetMapping("/stream/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> streamDocument(
            @PathVariable("id") Long id,
            @RequestParam(value = "disposition", defaultValue = "inline") String disposition) {

        EmployeeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        if (!validateAccess(document.getEmployee().getId())) {
            log.warn("Access denied for streaming document: document ID {}, owner employeeId {}", id, document.getEmployee().getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: You do not have permission to access this document.");
        }

        String secureUrl = document.getFilePath();
        if (secureUrl == null || secureUrl.isBlank() || !secureUrl.startsWith("http")) {
            log.error("Document {} has invalid or relative path: {}", id, secureUrl);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Document has an invalid URL in database. Please re-upload.");
        }

        String publicId = cloudinaryDocumentService.resolvePublicId(
            document.getCloudinaryPublicId(), secureUrl);
        String resourceType = cloudinaryDocumentService.resolveResourceType(
            document.getResourceType(), secureUrl);

        log.info("Streaming document ID {}: Name={}, StoredType={}, ResolvedType={}, PublicId={}, Path={}",
                id,
                document.getFileName(),
                document.getResourceType(),
                resourceType,
                publicId,
                secureUrl);

        try {
            // Proxy the stream through the backend to bypass Cloudinary's direct-access security (401/404)
            InputStream inputStream = cloudinaryDocumentService.fetchDocumentStream(
                    secureUrl, publicId, resourceType, document.getFileName());

            String safeName = document.getFileName() != null
                    ? document.getFileName().replace("\"", "")
                    : "document";

            String contentDisposition = "attachment".equalsIgnoreCase(disposition)
                    ? "attachment; filename=\"" + safeName + "\""
                    : "inline; filename=\"" + safeName + "\"";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .contentType(cloudinaryDocumentService.resolveMediaType(document.getFileName()))
                    .body(new InputStreamResource(inputStream));

        } catch (java.io.FileNotFoundException e) {
            log.warn("Document asset missing in Cloudinary for document {}: {}", id, e.getMessage());
            throw new ResourceNotFoundException("Document file not found in Cloudinary for id: " + id);
        } catch (Exception e) {
            log.error("Failed to stream document {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Failed to stream document: " + e.getMessage());
        }
    }

    @GetMapping("/employee/{employeeId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getEmployeeDocuments(@PathVariable Long employeeId) {
        if (!validateAccess(employeeId)) {
            log.warn("Access denied for listing documents: target employeeId {}", employeeId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: You do not have permission to view this employee's documents.");
        }
        Employee employee = employeeRepository.findByIdentifier(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
        List<EmployeeDocument> docs = documentRepository.findByEmployeeId(employee.getId());
        List<EmployeeDocumentDTO> dtos = docs.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}/debug")
    @Transactional(readOnly = true)
    public ResponseEntity<?> debugDocument(@PathVariable Long id) {
        EmployeeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        if (!validateAccess(document.getEmployee().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: You do not have access to this document.");
        }

        String secureUrl = document.getFilePath();
        String publicId = cloudinaryDocumentService.resolvePublicId(document.getCloudinaryPublicId(), secureUrl);
        String resourceType = cloudinaryDocumentService.resolveResourceType(document.getResourceType(), secureUrl);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", document.getId());
        body.put("fileName", document.getFileName());
        body.put("documentType", document.getDocumentType());
        body.put("filePath", document.getFilePath());
        body.put("cloudinaryPublicId", document.getCloudinaryPublicId());
        body.put("resourceType", document.getResourceType());
        body.put("resolvedPublicId", publicId);
        body.put("resolvedResourceType", resourceType);
        body.put("mediaType", cloudinaryDocumentService.resolveMediaType(document.getFileName()).toString());
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteDocument(@PathVariable("id") Long id) {
        if (!isHrOrAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden: Only HR and Administrators are allowed to delete documents."));
        }
        Optional<EmployeeDocument> documentOpt = documentRepository.findById(id);
        if (documentOpt.isEmpty()) {
            throw new ResourceNotFoundException("Document not found with id: " + id);
        }

        EmployeeDocument document = documentOpt.get();
        try {
            String publicId = cloudinaryDocumentService.resolvePublicId(
                    document.getCloudinaryPublicId(), document.getFilePath());
            String resourceType = cloudinaryDocumentService.resolveResourceType(
                    document.getResourceType(), document.getFilePath());

            if (publicId != null && !publicId.isBlank()) {
                try {
                    cloudinaryDocumentService.delete(publicId, resourceType);
                } catch (Exception e) {
                    log.warn("Could not delete file from Cloudinary for document {}: {}", id, e.getMessage());
                }
            }

            documentRepository.deleteById(id);
            documentRepository.flush();

            Map<String, Object> body = new HashMap<>();
            body.put("message", "Document deleted successfully");
            body.put("id", id);
            return ResponseEntity.ok(body);

        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Could not delete document. Error: " + ex.getMessage(), ex);
        }
    }

    private EmployeeDocumentDTO toDto(EmployeeDocument doc) {
        return new EmployeeDocumentDTO(
                doc.getId(),
                doc.getEmployee().getId(),
                doc.getEmployee().getFirstName() + " " + doc.getEmployee().getLastName(),
                doc.getDocumentType(),
                doc.getFileName(),
                doc.getFilePath(),
                doc.getUploadedAt()
        );
    }
}
