package com.biasharahub.service;

import com.biasharahub.config.R2Config.R2Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

/**
 * Uploads product images to Cloudflare R2 and returns public URLs.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(S3Client.class)
@Slf4j
public class R2StorageService {

    private final S3Client s3Client;
    private final R2Properties r2Properties;

    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB
    private static final long MAX_VERIFICATION_FILE_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB
    private static final String ALLOWED_CONTENT_PREFIX = "image/";
    private static final String APPLICATION_PDF = "application/pdf";

    /**
     * Uploads an image to R2 and returns the public URL.
     *
     * @param file the image file (e.g. image/jpeg, image/png)
     * @return public URL of the uploaded image, or null if R2 is not configured or upload fails
     */
    public String uploadProductImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be null or empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith(ALLOWED_CONTENT_PREFIX)) {
            throw new IllegalArgumentException("File must be an image (e.g. image/jpeg, image/png)");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size must not exceed 20 MB");
        }

        String key = "products/" + UUID.randomUUID() + "-" + sanitizeFilename(file.getOriginalFilename());

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(r2Properties.getBucket())
                .key(key)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        String publicUrl = buildPublicUrl(key);
        log.debug("Uploaded product image to R2: {}", publicUrl);
        return publicUrl;
    }

    /**
     * Uploads a verification document (image or PDF) to R2 and returns the public URL.
     * Used for owner verification documents (ID, business reg, etc.).
     */
    public String uploadVerificationDocument(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be null or empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = APPLICATION_PDF;
        }
        String ct = contentType.toLowerCase();
        boolean allowed = ct.startsWith(ALLOWED_CONTENT_PREFIX) || APPLICATION_PDF.equals(ct);
        if (!allowed) {
            throw new IllegalArgumentException("File must be an image (e.g. image/jpeg, image/png) or PDF");
        }
        if (file.getSize() > MAX_VERIFICATION_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size must not exceed 20 MB");
        }
        String ext = ct.contains("pdf") ? "pdf" : "img";
        String key = "verification/" + UUID.randomUUID() + "-" + sanitizeFilename(file.getOriginalFilename(), ext);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(r2Properties.getBucket())
                .key(key)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        String publicUrl = buildPublicUrl(key);
        log.debug("Uploaded verification document to R2: {}", publicUrl);
        return publicUrl;
    }

    private String sanitizeFilename(String name, String fallbackExt) {
        if (name == null || name.isBlank()) return "doc." + fallbackExt;
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return (safe.length() > 100 ? safe.substring(0, 100) : safe);
    }

    private String buildPublicUrl(String key) {
        String base = r2Properties.getPublicUrl();
        if (base == null || base.isBlank()) {
            return key;
        }
        return base.replaceFirst("/+$", "") + "/" + key;
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "image";
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe.length() > 100 ? safe.substring(0, 100) : safe;
    }
}
