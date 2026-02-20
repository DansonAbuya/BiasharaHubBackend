package com.biasharahub.controller;

import com.biasharahub.dto.request.ServiceProviderApplyRequest;
import com.biasharahub.dto.response.OwnerVerificationDocumentDto;
import com.biasharahub.dto.response.ServiceProviderDocumentDto;
import com.biasharahub.dto.response.UserDto;
import com.biasharahub.dto.response.VerificationChecklistDto;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.OwnerVerificationService;
import com.biasharahub.service.R2StorageService;
import com.biasharahub.service.ServiceProviderVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner verification: document upload and status.
 * Admin: list pending owners, approve/reject.
 */
@RestController
@RequestMapping("/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final OwnerVerificationService verificationService;
    private final ServiceProviderVerificationService serviceProviderVerificationService;
    private final Optional<R2StorageService> r2StorageService;

    @PostMapping("/documents")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> uploadDocument(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody Map<String, String> body) {
        String documentType = body.get("documentType");
        String fileUrl = body.get("fileUrl");
        if (fileUrl == null || fileUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "fileUrl is required. Upload a file or paste a document URL."));
        }
        OwnerVerificationDocumentDto doc = verificationService.uploadDocument(user, documentType, fileUrl);
        return ResponseEntity.ok(doc);
    }

    /**
     * Upload a verification document (image or PDF) using the same R2 storage as product photos.
     * Images use the same uploadProductImage path; PDFs use uploadVerificationDocument.
     */
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> uploadDocumentFile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {
        if (r2StorageService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "File upload is not configured (R2 disabled). Use the same R2 settings as product photos, or paste a document URL instead."));
        }
        try {
            String contentType = file.getContentType();
            String ct = contentType != null ? contentType.toLowerCase() : "";
            boolean isImage = ct.startsWith("image/");
            String url;
            if (isImage) {
                url = r2StorageService.get().uploadProductImage(file);
            } else {
                url = r2StorageService.get().uploadVerificationDocument(file);
            }
            OwnerVerificationDocumentDto doc = verificationService.uploadDocument(user, documentType, url);
            return ResponseEntity.ok(doc);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/documents")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<OwnerVerificationDocumentDto>> getMyDocuments(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(verificationService.getMyDocuments(user));
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<UserDto> getMyStatus(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(verificationService.getMyVerificationStatus(user));
    }

    @GetMapping("/checklist")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<VerificationChecklistDto> getMyChecklist(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(verificationService.getMyChecklist(user));
    }

    @GetMapping("/admin/pending-owners")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<List<UserDto>> listPendingOwners() {
        return ResponseEntity.ok(verificationService.listPendingOwners());
    }

    @GetMapping("/admin/owners/{ownerId}/documents")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<List<OwnerVerificationDocumentDto>> getOwnerDocuments(@PathVariable UUID ownerId) {
        return ResponseEntity.ok(verificationService.getDocumentsForOwner(ownerId));
    }

    @PatchMapping("/admin/owners/{ownerId}/checklist")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<VerificationChecklistDto> setOwnerChecklist(
            @PathVariable UUID ownerId,
            @RequestBody Map<String, Boolean> body) {
        VerificationChecklistDto dto = verificationService.setOwnerChecklist(ownerId,
                body.get("phoneVerified"),
                body.get("mpesaValidated"),
                body.get("businessLocationVerified"),
                body.get("termsAccepted"));
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/admin/owners/{ownerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<UserDto> setOwnerVerification(
            @PathVariable UUID ownerId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        String status = body.get("status");
        String notes = body.get("notes");
        String tier = body.getOrDefault("sellerTier", body.getOrDefault("tier", null));
        UserDto updated = verificationService.setVerificationStatus(ownerId, status, notes, admin, tier);
        return ResponseEntity.ok(updated);
    }

    // ---------- Service provider verification (separate journey from product seller) ----------

    /**
     * Upload a service provider verification document (image or PDF) using R2 storage.
     * This is for service provider verification documents (ID, qualifications), not product seller documents.
     */
    @PostMapping(value = "/service-provider/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> uploadServiceProviderDocumentFile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {
        if (r2StorageService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "File upload is not configured (R2 disabled). Please paste a document URL instead."));
        }
        try {
            String contentType = file.getContentType();
            String ct = contentType != null ? contentType.toLowerCase() : "";
            boolean isImage = ct.startsWith("image/");
            String url;
            if (isImage) {
                url = r2StorageService.get().uploadProductImage(file);
            } else {
                url = r2StorageService.get().uploadVerificationDocument(file);
            }
            ServiceProviderDocumentDto doc = serviceProviderVerificationService.uploadDocument(user, documentType, url);
            return ResponseEntity.ok(doc);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/service-provider/apply")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<UserDto> applyServiceProvider(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody ServiceProviderApplyRequest request) {
        UserDto dto = serviceProviderVerificationService.apply(user, request);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/service-provider/status")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<UserDto> getServiceProviderStatus(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(serviceProviderVerificationService.getMyStatus(user));
    }

    @GetMapping("/service-provider/documents")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<ServiceProviderDocumentDto>> getServiceProviderDocuments(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(serviceProviderVerificationService.getMyDocuments(user));
    }

    @GetMapping("/admin/pending-service-providers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<List<UserDto>> listPendingServiceProviders() {
        return ResponseEntity.ok(serviceProviderVerificationService.listPendingServiceProviders());
    }

    @GetMapping("/admin/service-providers/{ownerId}/documents")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<List<ServiceProviderDocumentDto>> getServiceProviderDocumentsForOwner(
            @PathVariable UUID ownerId) {
        return ResponseEntity.ok(serviceProviderVerificationService.getDocumentsForOwner(ownerId));
    }

    @PatchMapping("/admin/service-providers/{ownerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<UserDto> setServiceProviderVerification(
            @PathVariable UUID ownerId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        String status = body.get("status");
        String notes = body.get("notes");
        UserDto updated = serviceProviderVerificationService.setServiceProviderStatus(ownerId, status, notes, admin);
        return ResponseEntity.ok(updated);
    }
}
