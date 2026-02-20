package com.biasharahub.service;

import com.biasharahub.dto.response.OwnerVerificationDocumentDto;
import com.biasharahub.dto.response.UserDto;
import com.biasharahub.dto.response.VerificationChecklistDto;
import com.biasharahub.entity.OwnerVerificationDocument;
import com.biasharahub.entity.User;
import com.biasharahub.repository.OwnerVerificationDocumentRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerVerificationService {

    private final UserRepository userRepository;
    private final OwnerVerificationDocumentRepository documentRepository;

    /** Document types per BiasharaHub Trust & Verification Process. */
    public static final String DOC_TYPE_ID = "national_id";
    public static final String DOC_TYPE_PASSPORT = "passport";
    public static final String DOC_TYPE_BUSINESS_REG = "business_registration";
    public static final String DOC_TYPE_LOCATION = "location_details";
    public static final String DOC_TYPE_KRA_PIN = "kra_pin_certificate";
    public static final String DOC_TYPE_COMPLIANCE = "compliance_documentation";
    public static final String DOC_TYPE_OTHER = "other";

    /** Tier 1: Informal Sellers — ID, phone, email. Tier 2: Registered SMEs — business reg + location. Tier 3: Corporate — KRA PIN + compliance. */
    public static final String TIER1 = "tier1";
    public static final String TIER2 = "tier2";
    public static final String TIER3 = "tier3";

    /** Owner uploads a verification document. */
    @Transactional
    public OwnerVerificationDocumentDto uploadDocument(AuthenticatedUser currentUser, String documentType, String fileUrl) {
        User owner = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!"owner".equalsIgnoreCase(owner.getRole())) {
            throw new IllegalArgumentException("Only owners can upload verification documents");
        }
        OwnerVerificationDocument doc = OwnerVerificationDocument.builder()
                .user(owner)
                .documentType(documentType != null ? documentType : DOC_TYPE_OTHER)
                .fileUrl(fileUrl)
                .build();
        doc = documentRepository.save(doc);
        return toDto(doc);
    }

    /** Owner lists their verification documents. */
    public List<OwnerVerificationDocumentDto> getMyDocuments(AuthenticatedUser currentUser) {
        return documentRepository.findByUserUserIdOrderByUploadedAtDesc(currentUser.userId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Owner gets their verification status. */
    public UserDto getMyVerificationStatus(AuthenticatedUser currentUser) {
        User user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return UserDto.builder()
                .id(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .businessId(user.getBusinessId() != null ? user.getBusinessId().toString() : null)
                .businessName(user.getBusinessName())
                .verificationStatus(user.getVerificationStatus())
                .verifiedAt(user.getVerifiedAt())
                .verifiedByUserId(user.getVerifiedByUserId())
                .verificationNotes(user.getVerificationNotes())
                .sellerTier(user.getSellerTier())
                .applyingForTier(user.getApplyingForTier())
                .strikeCount(user.getStrikeCount())
                .accountStatus(user.getAccountStatus())
                .build();
    }

    /** Owner: get verification checklist (phone, M-Pesa, location, terms). */
    public VerificationChecklistDto getMyChecklist(AuthenticatedUser currentUser) {
        User user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return VerificationChecklistDto.builder()
                .phoneVerified(user.getPhoneVerifiedAt() != null)
                .mpesaValidated(user.getMpesaValidatedAt() != null)
                .businessLocationVerified(user.getBusinessLocationVerifiedAt() != null)
                .termsAccepted(user.getTermsAcceptedAt() != null)
                .build();
    }

    /** Admin: set verification checklist flags for an owner. */
    @Transactional
    public VerificationChecklistDto setOwnerChecklist(UUID ownerUserId, Boolean phoneVerified, Boolean mpesaValidated,
                                                      Boolean businessLocationVerified, Boolean termsAccepted) {
        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
        if (!"owner".equalsIgnoreCase(owner.getRole())) {
            throw new IllegalArgumentException("User is not an owner");
        }
        Instant now = Instant.now();
        if (Boolean.TRUE.equals(phoneVerified)) owner.setPhoneVerifiedAt(now);
        if (Boolean.TRUE.equals(mpesaValidated)) owner.setMpesaValidatedAt(now);
        if (Boolean.TRUE.equals(businessLocationVerified)) owner.setBusinessLocationVerifiedAt(now);
        if (Boolean.TRUE.equals(termsAccepted)) owner.setTermsAcceptedAt(now);
        userRepository.save(owner);
        return VerificationChecklistDto.builder()
                .phoneVerified(owner.getPhoneVerifiedAt() != null)
                .mpesaValidated(owner.getMpesaValidatedAt() != null)
                .businessLocationVerified(owner.getBusinessLocationVerifiedAt() != null)
                .termsAccepted(owner.getTermsAcceptedAt() != null)
                .build();
    }

    /** Admin: get verification documents for a specific owner (to review before verify/reject). */
    public List<OwnerVerificationDocumentDto> getDocumentsForOwner(UUID ownerUserId) {
        return documentRepository.findByUserUserIdOrderByUploadedAtDesc(ownerUserId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Admin/super_admin: list owners pending verification. */
    public List<UserDto> listPendingOwners() {
        return userRepository.findByRoleIgnoreCaseAndVerificationStatusOrderByCreatedAtAsc("owner", "pending")
                .stream()
                .map(this::toUserDto)
                .collect(Collectors.toList());
    }

    /** Admin/super_admin: approve or reject an owner. */
    @Transactional
    public UserDto setVerificationStatus(UUID ownerUserId, String status, String notes, AuthenticatedUser reviewer, String tier) {
        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
        if (!"owner".equalsIgnoreCase(owner.getRole())) {
            throw new IllegalArgumentException("User is not an owner");
        }
        if (!"verified".equalsIgnoreCase(status) && !"rejected".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Status must be verified or rejected");
        }
        owner.setVerificationStatus(status.toLowerCase());
        owner.setVerifiedAt(Instant.now());
        owner.setVerifiedByUserId(reviewer.userId());
        owner.setVerificationNotes(notes);
        if (tier != null && !tier.isBlank()) {
            String t = tier.toLowerCase();
            if (!TIER1.equals(t) && !TIER2.equals(t) && !TIER3.equals(t)) {
                throw new IllegalArgumentException("sellerTier must be tier1 (Informal), tier2 (Registered SME), or tier3 (Corporate)");
            }
            owner.setSellerTier(t);
        }
        owner = userRepository.save(owner);
        return toUserDto(owner);
    }

    private OwnerVerificationDocumentDto toDto(OwnerVerificationDocument d) {
        return OwnerVerificationDocumentDto.builder()
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
