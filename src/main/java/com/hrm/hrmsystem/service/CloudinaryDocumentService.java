package com.hrm.hrmsystem.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.hrm.hrmsystem.config.CloudinaryProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CloudinaryDocumentService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CloudinaryDocumentService.class);

    private final Cloudinary cloudinary;
    private final CloudinaryProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public CloudinaryDocumentService(Cloudinary cloudinary, CloudinaryProperties properties) {
        this.cloudinary = cloudinary;
        this.properties = properties;
        validateCloudinaryConfig();
    }

    private void validateCloudinaryConfig() {
        if (cloudinary.config.apiKey == null || cloudinary.config.apiKey.isBlank()) {
            throw new IllegalStateException("❌ CLOUDINARY ERROR: API Key is missing. Check your credentials.");
        }
        if (cloudinary.config.cloudName == null || cloudinary.config.cloudName.isBlank()) {
            throw new IllegalStateException("❌ CLOUDINARY ERROR: Cloud Name is missing.");
        }
    }

    public UploadResult upload(MultipartFile file, Long employeeId) throws Exception {
        String folder = properties.getFolder() + "/" + employeeId;
        String resourceType = resolveUploadResourceType(file.getOriginalFilename(), file.getContentType());
        String format = extractExtension(file.getOriginalFilename());

        Map<String, Object> options = new HashMap<>();
        options.put("resource_type", resourceType);
        options.put("folder", folder);
        options.put("use_filename", true);
        options.put("unique_filename", true);
        options.put("type", "upload"); // Explicitly set to 'upload' for public CDN delivery
        if (format != null && !"raw".equals(resourceType)) {
            options.put("format", format);
        }

        log.info("Uploading file '{}' to Cloudinary folder: {}", file.getOriginalFilename(), folder);

        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), options);

        String uploadedResourceType = (String) uploadResult.get("resource_type");
        String secureUrl = normalizeDeliveryUrl(
                (String) uploadResult.get("secure_url"),
                (String) uploadResult.get("public_id"),
                uploadedResourceType,
                file.getOriginalFilename()
        );

        return new UploadResult(
                secureUrl,
                (String) uploadResult.get("public_id"),
                uploadedResourceType
        );
    }

    /** Upload from a File (used for migrating local files into Cloudinary). */
    public UploadResult upload(java.io.File file, Long employeeId) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("File not found: " + (file != null ? file.getAbsolutePath() : "null"));
        }

        String folder = properties.getFolder() + "/" + employeeId;
        String resourceType = resolveUploadResourceType(file.getName(), null);
        String format = extractExtension(file.getName());

        Map<String, Object> options = new HashMap<>();
        options.put("resource_type", resourceType);
        options.put("folder", folder);
        options.put("use_filename", true);
        options.put("unique_filename", true);
        options.put("type", "upload");
        if (format != null && !"raw".equals(resourceType)) {
            options.put("format", format);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file, options);

            String uploadedResourceType = (String) uploadResult.get("resource_type");
            String secureUrl = normalizeDeliveryUrl(
                    (String) uploadResult.get("secure_url"),
                    (String) uploadResult.get("public_id"),
                    uploadedResourceType,
                    file.getName()
            );

            UploadResult result = new UploadResult(
                    secureUrl,
                    (String) uploadResult.get("public_id"),
                    uploadedResourceType
            );
            log.info("Uploaded local file to Cloudinary: {} -> {}", file.getAbsolutePath(), result.secureUrl());
            return result;
        } catch (Exception ex) {
            throw new IOException("Cloudinary upload failed: " + ex.getMessage(), ex);
        }
    }

    public String buildViewUrl(String secureUrl, String fileName) {
        if (secureUrl == null) {
            return null;
        }

        String url = stripDeliveryFlags(secureUrl);
        String ext = extractExtension(fileName);

        // Google viewer for office docs
        if (ext != null && List.of("doc", "docx", "xls", "xlsx", "ppt", "pptx").contains(ext)) {
            try {
                return "https://docs.google.com/viewer?url="
                        + java.net.URLEncoder.encode(url, "UTF-8")
                        + "&embedded=true";
            } catch (Exception e) {
                return url;
            }
        }

        // ✅ Do NOT add fl_inline
        return url;
    }

    public String buildDownloadUrl(String publicId, String resourceType, String secureUrl, String fileName) {
        if (secureUrl == null) return null;
        String url = stripDeliveryFlags(secureUrl);
        // fl_attachment only works for image/video resource types.
        // Raw resources (docx, xlsx) download by default.
        if (url.contains("/image/upload/") || url.contains("/video/upload/")) {
            url = addFlagToUrl(url, "fl_attachment");
        }
        return ensureExtensionInUrl(url, fileName);
    }

    /** Fetch file stream from Cloudinary, trying alternate URLs if the stored link returns 400. */
    public InputStream fetchDocumentStream(
            String secureUrl,
            String publicId,
            String resourceType,
            String fileName) throws IOException {

        List<String> candidates = buildFetchUrlCandidates(secureUrl, publicId, resourceType, fileName);
        log.info("Cloudinary fetch candidates for publicId='{}', resourceType='{}', fileName='{}': {}",
                publicId, resourceType, fileName, candidates);
        IOException lastError = null;
        boolean allMissing = true;

        for (String url : candidates) {
            try {
                return httpGetStream(url);
            } catch (FileNotFoundException ex) {
                lastError = ex;
            } catch (IOException ex) {
                allMissing = false;
                lastError = ex;
            }
        }

        if (allMissing && lastError != null) {
            throw new FileNotFoundException(lastError.getMessage());
        }

        throw lastError != null ? lastError : new IOException("Could not stream document from Cloudinary");
    }

    public MediaType resolveMediaType(String fileName) {
        String ext = extractExtension(fileName);
        if (ext == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return switch (ext) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "doc" -> MediaType.parseMediaType("application/msword");
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "xls" -> MediaType.parseMediaType("application/vnd.ms-excel");
            case "xlsx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    public void delete(String publicId, String resourceType) throws Exception {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        cloudinary.uploader().destroy(
                publicId,
                ObjectUtils.asMap("resource_type", resourceType != null ? resourceType : "auto")
        );
    }

    public String resolvePublicId(String storedPublicId, String secureUrl) {
        if (storedPublicId != null && !storedPublicId.isBlank()) {
            return storedPublicId;
        }
        return extractPublicIdFromUrl(secureUrl);
    }

    public String resolveResourceType(String storedResourceType, String secureUrl) {
        if (storedResourceType != null && !storedResourceType.isBlank()
                && !"auto".equalsIgnoreCase(storedResourceType)) {
            return storedResourceType;
        }
        return extractResourceTypeFromUrl(secureUrl);
    }

    private List<String> buildFetchUrlCandidates(
            String secureUrl,
            String publicId,
            String resourceType,
            String fileName) {

        Set<String> urls = new LinkedHashSet<>();
        String cleaned = normalizeDeliveryUrl(stripDeliveryFlags(secureUrl), publicId, resourceType, fileName);
        if (cleaned != null && !cleaned.isBlank()) {
            urls.add(cleaned);
        }

        String rebuilt = rebuildDeliveryUrl(secureUrl, publicId, resourceType, fileName);
        if (rebuilt != null && !rebuilt.isBlank()) {
            urls.add(rebuilt);
        }

        if (secureUrl != null && !secureUrl.isBlank()) {
            urls.add(normalizeDeliveryUrl(stripDeliveryFlags(secureUrl), publicId, resourceType, fileName));
            urls.add(stripDeliveryFlags(secureUrl));
        }

        return new ArrayList<>(urls);
    }

    private String rebuildDeliveryUrl(
            String storedUrl,
            String publicId,
            String resourceType,
            String fileName) {

        if (publicId == null || publicId.isBlank()) {
            return null;
        }

        String type = resourceType != null && !"auto".equalsIgnoreCase(resourceType)
                ? resourceType
                : extractResourceTypeFromUrl(storedUrl);
        if ("auto".equals(type)) {
            type = "raw";
        }

        String version = extractVersionSegment(storedUrl);
        String ext = extractExtension(fileName);
        String path = publicId;
        if (ext != null && !path.toLowerCase().endsWith("." + ext)) {
            path = path + "." + ext;
        }

        String versionPart = version != null ? version + "/" : "";
        return String.format(
                "https://res.cloudinary.com/%s/%s/upload/%s%s",
                properties.getCloudName(),
                type,
                versionPart,
                path
        );
    }

    private String normalizeDeliveryUrl(String url, String publicId, String resourceType, String fileName) {
        if (url == null || url.isBlank()) {
            return url;
        }

        String normalizedResourceType = resourceType != null && !resourceType.isBlank()
                ? resourceType
                : extractResourceTypeFromUrl(url);

        String cleaned = stripDeliveryFlags(url);

        // PDFs uploaded as Cloudinary image resources should use the original delivery URL/public_id
        // without forcing an extra extension.
        if ("image".equalsIgnoreCase(normalizedResourceType) && "pdf".equals(extractExtension(fileName))) {
            String rebuilt = rebuildDeliveryUrl(cleaned, publicId, normalizedResourceType, fileName);
            return rebuilt != null ? rebuilt : cleaned;
        }

        return ensureExtensionInUrl(cleaned, fileName);
    }

    private String addFlagToUrl(String url, String flag) {
        if (url == null || !url.contains("/upload/") || url.contains("/" + flag + "/")) return url;
        return url.replace("/upload/", "/upload/" + flag + "/");
    }

    private InputStream httpGetStream(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching document", ex);
        }

        if (response.statusCode() >= 400) {
            String msg = "Cloudinary returned HTTP " + response.statusCode() + " for URL: " + url;
            log.warn(msg);
            if (response.statusCode() == 404) {
                throw new FileNotFoundException(msg);
            }
            throw new IOException(msg);
        }
        return response.body();
    }

    private String ensureExtensionInUrl(String url, String fileName) {
        if (url == null || url.isBlank() || fileName == null) {
            return url;
        }
        String ext = extractExtension(fileName);
        if (ext == null) {
            return url;
        }

        String lowerUrl = url.toLowerCase();
        if (lowerUrl.matches(".*\\.(png|jpg|jpeg|pdf|doc|docx|xls|xlsx|gif|webp)$")) {
            return url;
        }

        String suffix = "." + ext;
        if (lowerUrl.contains(suffix.toLowerCase())) {
            return url;
        }
        int query = url.indexOf('?');
        if (query > 0) {
            return url.substring(0, query) + suffix + url.substring(query);
        }
        return url + suffix;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private String extractVersionSegment(String url) {
        if (url == null || !url.contains("/upload/")) {
            return null;
        }
        String after = url.substring(url.indexOf("/upload/") + "/upload/".length());
        after = after.replaceFirst("^(fl_inline|fl_attachment)/", "");
        if (after.matches("^v\\d+/.*")) {
            return after.substring(0, after.indexOf('/'));
        }
        return null;
    }

    private String resolveUploadResourceType(String fileName, String contentType) {
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".pdf")) {
                return "image"; // ✅ PDFs must be 'image' type for high-quality inline preview
            }
            if (lower.endsWith(".doc") || lower.endsWith(".docx")
                    || lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".ppt")
                    || lower.endsWith(".pptx") || lower.endsWith(".txt") || lower.endsWith(".csv")
                    || lower.endsWith(".zip") || lower.endsWith(".rar")) {
                return "raw";
            }
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                    || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")) {
                return "image";
            }
        }
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("pdf")) {
                return "image"; // Ensure PDFs are 'image' type for preview support
            }
            if (ct.contains("word") || ct.contains("excel")
                    || ct.contains("sheet") || ct.contains("document") || ct.contains("text")) {
                return "raw";
            }
            if (ct.startsWith("image/")) {
                return "image";
            }
        }
        return "raw";
    }

    private String stripDeliveryFlags(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String cleaned = url;
        for (String flag : new String[] { "fl_inline", "fl_attachment" }) {
            cleaned = cleaned.replace("/upload/" + flag + "/", "/upload/");
        }
        return cleaned;
    }

    private String extractPublicIdFromUrl(String url) {
        if (url == null || !url.contains("/upload/")) {
            return null;
        }
        String afterUpload = url.substring(url.indexOf("/upload/") + "/upload/".length());
        if (afterUpload.startsWith("fl_attachment/")) {
            afterUpload = afterUpload.substring("fl_attachment/".length());
        }
        if (afterUpload.startsWith("fl_inline/")) {
            afterUpload = afterUpload.substring("fl_inline/".length());
        }
        if (afterUpload.matches("^v\\d+/.*")) {
            afterUpload = afterUpload.replaceFirst("^v\\d+/", "");
        }
        int dot = afterUpload.lastIndexOf('.');
        if (dot > 0) {
            afterUpload = afterUpload.substring(0, dot);
        }
        return afterUpload;
    }

    private String extractResourceTypeFromUrl(String url) {
        if (url == null) {
            return "auto";
        }
        if (url.contains("/raw/upload/")) {
            return "raw";
        }
        if (url.contains("/video/upload/")) {
            return "video";
        }
        if (url.contains("/image/upload/")) {
            return "image";
        }
        return "auto";
    }

    public record UploadResult(String secureUrl, String publicId, String resourceType) {}
}
