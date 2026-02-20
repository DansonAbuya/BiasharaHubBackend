package com.biasharahub.service;

import com.biasharahub.dto.request.ServiceProviderApplyRequest;
import com.biasharahub.dto.response.ServiceProviderDocumentDto;
import com.biasharahub.dto.response.UserDto;
import com.biasharahub.entity.ServiceCategory;
import com.biasharahub.entity.ServiceProviderDocument;
import com.biasharahub.entity.User;
import com.biasharahub.repository.ServiceCategoryRepository;
import com.biasharahub.repository.ServiceProviderDocumentRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service provider verification: separate journey from product seller (business) verification.
 * Owners apply with service category, delivery type (online/physical/both), and qualification documents.
 */
@Service
@RequiredArgsConstructor
public class ServiceProviderVerificationService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_VERIFIED = "verified";
    public static final String STATUS_REJECTED = "rejected";

    public static final String DELIVERY_ONLINE = "ONLINE";
    public static final String DELIVERY_PHYSICAL = "PHYSICAL";
    public static final String DELIVERY_BOTH = "BOTH";

    public static final String DOC_TYPE_QUALIFICATION = "qualification_cert";
    public static final String DOC_TYPE_LICENSE = "license";
    public static final String DOC_TYPE_OTHER = "other";

    private final UserRepository userRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final ServiceProviderDocumentRepository documentRepository;

    /** Owner applies to become a service provider. Resets status to pending and stores category, delivery type, and docs. */
    @Transactional
    public UserDto apply(AuthenticatedUser currentUser, ServiceProviderApplyRequest request) {
        User owner = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!"owner".equalsIgnoreCase(owner.getRole())) {
            throw new IllegalArgumentException("Only business owners can apply as service providers");
        }
        if (owner.getBusinessId() == null) {
            throw new IllegalArgumentException("You need a business to offer services. Get onboarded as an owner first.");
        }
        if (request.getServiceCategoryId() == null) {
            throw new IllegalArgumentException("Service category is required");
        }
        ServiceCategory category = serviceCategoryRepository.findById(request.getServiceCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid service category"));
        String deliveryType = request.getServiceDeliveryType();
        if (deliveryType == null || deliveryType.isBlank()) {
            deliveryType = DELIVERY_BOTH;
        } else {
            String d = deliveryType.toUpperCase();
            if (!DELIVERY_ONLINE.equals(d) && !DELIVERY_PHYSICAL.equals(d) && !DELIVERY_BOTH.equals(d)) {
                throw new IllegalArgumentException("serviceDeliveryType must be ONLINE, PHYSICAL, or BOTH");
            }
            deliveryType = d;
        }

        owner.setServiceProviderStatus(STATUS_PENDING);
        owner.setServiceProviderNotes(null);
        owner.setServiceProviderCategoryId(category.getCategoryId());
        owner.setServiceDeliveryType(deliveryType);
        owner.setServiceProviderVerifiedAt(null);
        owner.setServiceProviderVerifiedByUserId(null);
        userRepository.save(owner);

        documentRepository.deleteByUserUserId(owner.getUserId());
        if (request.getDocuments() != null) {
            for (ServiceProviderApplyRequest.DocumentUpload up : request.getDocuments()) {
                if (up.getFileUrl() != null && !up.getFileUrl().isBlank()) {
                    String docType = up.getDocumentType() != null && !up.getDocumentType().isBlank()
                            ? up.getDocumentType() : DOC_TYPE_QUALIFICATION;
                    ServiceProviderDocument doc = ServiceProviderDocument.builder()
                            .user(owner)
                            .documentType(docType)
                            .fileUrl(up.getFileUrl())
                            .build();
                    documentRepository.save(doc);
                }
            }
        }
        return toUserDto(owner);
    }

    public UserDto getMyStatus(AuthenticatedUser currentUser) {
        User user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toUserDto(user);
    }

    public List<ServiceProviderDocumentDto> getMyDocuments(AuthenticatedUser currentUser) {
        return documentRepository.findByUserUserIdOrderByUploadedAtDesc(currentUser.userId())
                .stream()
                .map(this::toDocDto)
                .collect(Collectors.toList());
    }

    /** Admin: list owners pending service provider verification. */
    public List<UserDto> listPendingServiceProviders() {
        return userRepository.findByRoleIgnoreCaseAndServiceProviderStatusOrderByCreatedAtAsc("owner", STATUS_PENDING)
                .stream()
                .map(this::toUserDto)
                .collect(Collectors.toList());
    }

    public List<ServiceProviderDocumentDto> getDocumentsForOwner(UUID ownerId) {
        return documentRepository.findByUserUserIdOrderByUploadedAtDesc(ownerId)
                .stream()
                .map(this::toDocDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDto setServiceProviderStatus(UUID ownerId, String status, String notes, AuthenticatedUser admin) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
        if (!"owner".equalsIgnoreCase(owner.getRole())) {
            throw new IllegalArgumentException("User is not an owner");
        }
        if (!STATUS_VERIFIED.equalsIgnoreCase(status) && !STATUS_REJECTED.equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Status must be verified or rejected");
        }
        owner.setServiceProviderStatus(status.toLowerCase());
        owner.setServiceProviderVerifiedAt(Instant.now());
        owner.setServiceProviderVerifiedByUserId(admin.userId());
        owner.setServiceProviderNotes(notes);
        owner = userRepository.save(owner);
        return toUserDto(owner);
    }

    private ServiceProviderDocumentDto toDocDto(ServiceProviderDocument d) {
        return ServiceProviderDocumentDto.builder()
                .documentId(d.getDocumentId())
                .userId(d.getUser().getUserId())
                .documentType(d.getDocumentType())
                .fileUrl(d.getFileUrl())
                .uploadedAt(d.getUploadedAt())
                .build();
    }

    private UserDto toUserDto(User u) {
        return UserDto.builder()
                .id(u.getUserId())
                .name(u.getName())
                .email(u.getEmail())
                .role(u.getRole())
                .businessId(u.getBusinessId() != null ? u.getBusinessId().toString() : null)
                .businessName(u.getBusinessName())
                .verificationStatus(u.getVerificationStatus())
                .verifiedAt(u.getVerifiedAt())
                .verifiedByUserId(u.getVerifiedByUserId())
                .verificationNotes(u.getVerificationNotes())
                .sellerTier(u.getSellerTier())
                .applyingForTier(u.getApplyingForTier())
                .strikeCount(u.getStrikeCount())
                .accountStatus(u.getAccountStatus())
                .serviceProviderStatus(u.getServiceProviderStatus())
                .serviceProviderNotes(u.getServiceProviderNotes())
                .serviceProviderCategoryId(u.getServiceProviderCategoryId())
                .serviceDeliveryType(u.getServiceDeliveryType())
                .serviceProviderVerifiedAt(u.getServiceProviderVerifiedAt())
                .serviceProviderVerifiedByUserId(u.getServiceProviderVerifiedByUserId())
                .build();
    }
}
