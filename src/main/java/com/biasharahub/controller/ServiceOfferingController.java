package com.biasharahub.controller;

import com.biasharahub.dto.request.CreateServiceAppointmentRequest;
import com.biasharahub.dto.request.CreateServiceOfferingRequest;
import com.biasharahub.dto.request.PaymentInitiateRequest;
import com.biasharahub.dto.request.UpdateServiceAppointmentRequest;
import com.biasharahub.dto.request.UpdateServiceOfferingRequest;
import com.biasharahub.dto.response.PaymentInitiateResponse;
import com.biasharahub.dto.response.ServiceAppointmentDto;
import com.biasharahub.dto.response.ServiceCategoryDto;
import com.biasharahub.dto.response.ServiceOfferingDto;
import com.biasharahub.dto.response.ServiceProviderLocationDto;
import com.biasharahub.entity.ServiceAppointment;
import com.biasharahub.entity.ServiceBookingPayment;
import com.biasharahub.entity.ServiceCategory;
import com.biasharahub.entity.ServiceOffering;
import com.biasharahub.entity.User;
import com.biasharahub.repository.ServiceAppointmentRepository;
import com.biasharahub.repository.ServiceBookingPaymentRepository;
import com.biasharahub.repository.ServiceCategoryRepository;
import com.biasharahub.repository.ServiceOfferingRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.service.GoogleCalendarMeetService;
import com.biasharahub.service.InAppNotificationService;
import com.biasharahub.service.MpesaClient;
import com.biasharahub.service.R2StorageService;
import com.biasharahub.service.ServiceBookingEscrowService;
import com.biasharahub.service.SmsNotificationService;
import com.biasharahub.service.WhatsAppNotificationService;
import com.biasharahub.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BiasharaHub Services: provider picks a service category and delivery (online or physical).
 * Virtual = online meeting or other remote means; Physical = customer books appointment then attends.
 */
@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
@Slf4j
public class ServiceOfferingController {

    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final ServiceAppointmentRepository serviceAppointmentRepository;
    private final ServiceBookingPaymentRepository serviceBookingPaymentRepository;
    private final UserRepository userRepository;
    private final MpesaClient mpesaClient;
    private final InAppNotificationService inAppNotificationService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final SmsNotificationService smsNotificationService;
    private final ServiceBookingEscrowService serviceBookingEscrowService;
    private final GoogleCalendarMeetService googleCalendarMeetService;

    @Autowired(required = false)
    private R2StorageService r2StorageService;

    /**
     * Whether the current user can offer services. Uses service provider verification (separate from product seller verification).
     * Only owners with service_provider_status = verified can list services.
     */
    @GetMapping("/can-offer")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<Map<String, Object>> canOfferServices(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser == null) {
            return ResponseEntity.ok(Map.of("canOffer", false, "reason", "not_authenticated"));
        }
        UUID businessId = getBusinessId(currentUser);
        if (businessId == null) {
            return ResponseEntity.ok(Map.of("canOffer", false, "reason", "no_business"));
        }
        User owner = userRepository.findById(businessId).orElse(null);
        if (owner == null) {
            return ResponseEntity.ok(Map.of("canOffer", false, "reason", "business_not_found"));
        }
        boolean verified = "verified".equalsIgnoreCase(owner.getServiceProviderStatus());
        return ResponseEntity.ok(Map.of(
                "canOffer", verified,
                "reason", verified ? "verified" : "service_provider_not_verified",
                "serviceProviderStatus", owner.getServiceProviderStatus() != null ? owner.getServiceProviderStatus() : "pending"));
    }

    /** List service categories for provider dropdown (e.g. Consulting, Repair, Training). Public. */
    @GetMapping("/categories")
    public List<ServiceCategoryDto> listCategories() {
        return serviceCategoryRepository.findAllByOrderByDisplayOrderAscNameAsc()
                .stream()
                .map(c -> ServiceCategoryDto.builder()
                        .id(c.getCategoryId())
                        .name(c.getName())
                        .displayOrder(c.getDisplayOrder())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * List verified service providers with their location info.
     * Public endpoint for browsing and map-based search. Shows all verified providers.
     * For PHYSICAL or BOTH delivery types, location must be set.
     * ONLINE-only providers are included without location requirement.
     * Optionally filter by category, delivery type, or search query.
     */
    @GetMapping("/providers")
    public List<ServiceProviderLocationDto> listServiceProviders(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String search) {
        List<User> verifiedProviders = userRepository
                .findByRoleIgnoreCaseAndServiceProviderStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc("owner", "verified");

        return verifiedProviders.stream()
                .filter(u -> {
                    // Include all verified providers with valid delivery type
                    String dt = u.getServiceDeliveryType();
                    if (dt == null) return false;
                    // For PHYSICAL or BOTH, require location to be set
                    if ("PHYSICAL".equalsIgnoreCase(dt) || "BOTH".equalsIgnoreCase(dt)) {
                        return u.getServiceLocationLat() != null && u.getServiceLocationLng() != null;
                    }
                    // ONLINE providers don't need location
                    return "ONLINE".equalsIgnoreCase(dt);
                })
                .filter(u -> {
                    // Filter by delivery type if specified
                    if (deliveryType != null && !deliveryType.isBlank()) {
                        return deliveryType.equalsIgnoreCase(u.getServiceDeliveryType());
                    }
                    return true;
                })
                .filter(u -> {
                    // Filter by category if specified
                    if (categoryId != null) {
                        return categoryId.equals(u.getServiceProviderCategoryId());
                    }
                    return true;
                })
                .filter(u -> {
                    // Filter by search query (name, business name, location description)
                    if (search != null && !search.isBlank()) {
                        String q = search.toLowerCase();
                        String name = u.getName() != null ? u.getName().toLowerCase() : "";
                        String businessName = u.getBusinessName() != null ? u.getBusinessName().toLowerCase() : "";
                        String locDesc = u.getServiceLocationDescription() != null ? u.getServiceLocationDescription().toLowerCase() : "";
                        return name.contains(q) || businessName.contains(q) || locDesc.contains(q);
                    }
                    return true;
                })
                .map(u -> {
                    String categoryName = null;
                    if (u.getServiceProviderCategoryId() != null) {
                        categoryName = serviceCategoryRepository.findById(u.getServiceProviderCategoryId())
                                .map(ServiceCategory::getName)
                                .orElse(null);
                    }
                    int serviceCount = serviceOfferingRepository.countByBusinessIdAndIsActive(u.getBusinessId(), true);
                    return ServiceProviderLocationDto.builder()
                            .ownerId(u.getUserId())
                            .businessId(u.getBusinessId())
                            .businessName(u.getBusinessName())
                            .name(u.getName())
                            .email(u.getEmail())
                            .phone(u.getPhone())
                            .serviceDeliveryType(u.getServiceDeliveryType())
                            .locationLat(u.getServiceLocationLat())
                            .locationLng(u.getServiceLocationLng())
                            .locationDescription(u.getServiceLocationDescription())
                            .serviceCategoryId(u.getServiceProviderCategoryId())
                            .serviceCategoryName(categoryName)
                            .serviceCount(serviceCount)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * List service offerings.
     * - Owner/staff: only their business's services (all, including inactive).
     * - Customer/anonymous: only active services from verified businesses.
     * Filter by categoryId (predefined category) or category (string) or deliveryType.
     */
    @GetMapping
    public List<ServiceOfferingDto> listServices(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) UUID businessId,
            @RequestParam(required = false) UUID ownerId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (isOwnerOrStaff(currentUser)) {
            UUID myBusinessId = getBusinessId(currentUser);
            if (myBusinessId == null) return List.of();
            List<ServiceOffering> list;
            if (categoryId != null) {
                list = serviceOfferingRepository.findByBusinessIdInAndCategoryId(Set.of(myBusinessId), categoryId, true);
            } else if (category != null && !category.isBlank()) {
                list = serviceOfferingRepository.findByBusinessIdInAndCategory(Set.of(myBusinessId), category, true);
            } else if (deliveryType != null && !deliveryType.isBlank()) {
                list = serviceOfferingRepository.findByBusinessIdInAndDeliveryType(Set.of(myBusinessId), deliveryType, true);
            } else {
                list = serviceOfferingRepository.findByBusinessIdWithCategory(myBusinessId);
            }
            return list.stream().map(this::toDto).collect(Collectors.toList());
        }
        // Only businesses whose owner is verified as a service provider (separate from product seller verification)
        Set<UUID> verifiedBusinessIds = userRepository
                .findByRoleIgnoreCaseAndServiceProviderStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc("owner", "verified")
                .stream()
                .map(User::getBusinessId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (verifiedBusinessIds.isEmpty()) return List.of();
        if (ownerId != null) {
            final Set<UUID> verifiedSet = verifiedBusinessIds;
            verifiedBusinessIds = userRepository.findById(ownerId)
                    .filter(u -> "owner".equalsIgnoreCase(u.getRole()) && u.getBusinessId() != null && "verified".equalsIgnoreCase(u.getServiceProviderStatus()) && verifiedSet.contains(u.getBusinessId()))
                    .map(u -> Set.of(u.getBusinessId()))
                    .orElse(Set.of());
        }
        if (businessId != null) {
            if (!verifiedBusinessIds.contains(businessId)) return List.of();
            verifiedBusinessIds = Set.of(businessId);
        }
        List<ServiceOffering> list;
        if (categoryId != null) {
            list = serviceOfferingRepository.findByBusinessIdInAndCategoryId(verifiedBusinessIds, categoryId, false);
        } else if (category != null && !category.isBlank()) {
            list = serviceOfferingRepository.findByBusinessIdInAndCategory(verifiedBusinessIds, category, false);
        } else if (deliveryType != null && !deliveryType.isBlank()) {
            list = serviceOfferingRepository.findByBusinessIdInAndDeliveryType(verifiedBusinessIds, deliveryType, false);
        } else {
            list = serviceOfferingRepository.findByBusinessIdIn(verifiedBusinessIds, false);
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceOfferingDto> getService(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return serviceOfferingRepository.findByServiceIdWithCategory(id)
                .filter(s -> canAccess(s, currentUser))
                .map(s -> ResponseEntity.ok(toDto(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<?> createService(
            @Valid @RequestBody CreateServiceOfferingRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UUID businessId = getBusinessId(currentUser);
        if (businessId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        User owner = userRepository.findById(businessId).orElse(null);
        if (owner == null || !"verified".equalsIgnoreCase(owner.getVerificationStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Business must be verified before adding services."));
        }
        ServiceCategory sc = request.getCategoryId() != null
                ? serviceCategoryRepository.findById(request.getCategoryId()).orElse(null)
                : null;
        if (sc == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid service category."));
        }
        String categoryName = request.getCategory() != null && !request.getCategory().isBlank()
                ? request.getCategory() : sc.getName();
        ServiceOffering s = ServiceOffering.builder()
                .name(request.getName())
                .serviceCategory(sc)
                .category(categoryName)
                .description(request.getDescription())
                .price(request.getPrice())
                .businessId(businessId)
                .deliveryType(request.getDeliveryType() != null ? request.getDeliveryType() : "PHYSICAL")
                .meetingLink(request.getMeetingLink())
                .meetingDetails(request.getMeetingDetails())
                .onlineDeliveryMethods(request.getOnlineDeliveryMethods())
                .paymentTiming(request.getPaymentTiming() != null && !request.getPaymentTiming().isBlank() ? request.getPaymentTiming() : "BEFORE_BOOKING")
                .durationMinutes(request.getDurationMinutes())
                .imageUrl(request.getImageUrl())
                .videoUrl(request.getVideoUrl())
                .galleryUrls(request.getGalleryUrls())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
        s = serviceOfferingRepository.save(s);
        return ResponseEntity.ok(toDto(s));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ServiceOfferingDto> updateService(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceOfferingRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UUID businessId = getBusinessId(currentUser);
        if (businessId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return serviceOfferingRepository.findByServiceIdWithCategory(id)
                .filter(s -> s.getBusinessId().equals(businessId))
                .map(s -> {
                    if (request.getName() != null) s.setName(request.getName());
                    if (request.getCategoryId() != null) {
                        serviceCategoryRepository.findById(request.getCategoryId()).ifPresent(sc -> {
                            s.setServiceCategory(sc);
                            s.setCategory(request.getCategory() != null && !request.getCategory().isBlank() ? request.getCategory() : sc.getName());
                        });
                    } else if (request.getCategory() != null) s.setCategory(request.getCategory());
                    if (request.getDescription() != null) s.setDescription(request.getDescription());
                    if (request.getPrice() != null) s.setPrice(request.getPrice());
                    if (request.getDeliveryType() != null) s.setDeliveryType(request.getDeliveryType());
                    if (request.getMeetingLink() != null) s.setMeetingLink(request.getMeetingLink());
                    if (request.getMeetingDetails() != null) s.setMeetingDetails(request.getMeetingDetails());
                    if (request.getOnlineDeliveryMethods() != null) s.setOnlineDeliveryMethods(request.getOnlineDeliveryMethods());
                    if (request.getPaymentTiming() != null) s.setPaymentTiming(request.getPaymentTiming());
                    if (request.getDurationMinutes() != null) s.setDurationMinutes(request.getDurationMinutes());
                    if (request.getImageUrl() != null) s.setImageUrl(request.getImageUrl());
                    if (request.getVideoUrl() != null) s.setVideoUrl(request.getVideoUrl());
                    if (request.getGalleryUrls() != null) s.setGalleryUrls(request.getGalleryUrls());
                    if (request.getIsActive() != null) s.setIsActive(request.getIsActive());
                    ServiceOffering saved = serviceOfferingRepository.save(s);
                    return ResponseEntity.ok(toDto(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<Void> deleteService(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UUID businessId = getBusinessId(currentUser);
        if (businessId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (serviceOfferingRepository.existsByServiceIdAndBusinessId(id, businessId)) {
            serviceOfferingRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Upload service media (image or video) for showcasing a service.
     * Supports images (JPEG, PNG, GIF, WebP) and videos (MP4, WebM, MOV, AVI).
     * Max file size: 20 MB.
     * 
     * @return JSON with the public URL of the uploaded file
     */
    @PostMapping(value = "/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<?> uploadServiceMedia(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (r2StorageService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "File upload is not configured (R2 disabled). Please use a URL instead."));
        }
        UUID businessId = getBusinessId(currentUser);
        if (businessId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No business associated with user"));
        }
        try {
            String url = r2StorageService.uploadServiceMedia(file);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to upload service media", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload file: " + e.getMessage()));
        }
    }

    // ---------- Appointments (physical: book then attend; virtual: book, pay, meeting link, confirm/dispute for escrow) ----------

    /** Book an appointment. Physical: in-person; Virtual: pay to book, meeting link sent on confirm, funds held until customer confirms or disputes. */
    @PostMapping("/{serviceId}/appointments")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'CUSTOMER')")
    public ResponseEntity<?> createAppointment(
            @PathVariable UUID serviceId,
            @Valid @RequestBody CreateServiceAppointmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return serviceOfferingRepository.findByServiceIdWithCategory(serviceId)
                .filter(s -> s.getIsActive() != null && s.getIsActive())
                .map(service -> {
                    ServiceAppointment a = ServiceAppointment.builder()
                            .service(service)
                            .user(userRepository.getReferenceById(currentUser.userId()))
                            .requestedDate(request.getRequestedDate())
                            .requestedTime(request.getRequestedTime())
                            .status("PENDING")
                            .notes(request.getNotes())
                            .build();
                    ServiceAppointment saved = serviceAppointmentRepository.save(a);
                    ServiceBookingPayment payment = ServiceBookingPayment.builder()
                            .appointment(saved)
                            .user(userRepository.getReferenceById(currentUser.userId()))
                            .amount(service.getPrice())
                            .paymentStatus("pending")
                            .paymentMethod("M-Pesa")
                            .build();
                    serviceBookingPaymentRepository.save(payment);
                    try {
                        inAppNotificationService.notifyServiceBookingCreated(saved);
                        inAppNotificationService.notifyProviderServiceBookingCreated(saved);
                        whatsAppNotificationService.notifyServiceBookingCreated(saved);
                        whatsAppNotificationService.notifyProviderServiceBookingCreated(saved);
                        smsNotificationService.notifyProviderServiceBookingCreated(saved);
                    } catch (Exception ex) {
                        log.warn("Failed to send service booking notifications for appointment {}: {}", saved.getAppointmentId(), ex.getMessage());
                    }
                    return ResponseEntity.ok(toAppointmentDto(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** List appointments: as customer = my bookings; as owner/staff = bookings for their services. */
    @GetMapping("/appointments")
    public List<ServiceAppointmentDto> listAppointments(
            @RequestParam(required = false) UUID serviceId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser == null) return List.of();
        if (isOwnerOrStaff(currentUser)) {
            UUID businessId = getBusinessId(currentUser);
            if (businessId == null) return List.of();
            List<ServiceAppointment> list = serviceId != null
                    ? serviceAppointmentRepository.findByService_ServiceIdOrderByRequestedDateDesc(serviceId)
                    : serviceAppointmentRepository.findByService_BusinessIdOrderByRequestedDateDesc(businessId);
            return list.stream().map(this::toAppointmentDto).collect(Collectors.toList());
        }
        List<ServiceAppointment> list = serviceAppointmentRepository.findByUserIdOrderByRequestedDateDesc(currentUser.userId());
        return list.stream().map(this::toAppointmentDto).collect(Collectors.toList());
    }

    @GetMapping("/appointments/{id}")
    public ResponseEntity<ServiceAppointmentDto> getAppointment(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return serviceAppointmentRepository.findByAppointmentIdWithDetails(id)
                .filter(a -> canAccessAppointment(a, currentUser))
                .map(a -> ResponseEntity.ok(toAppointmentDto(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Initiate M-Pesa payment for a service booking. Customer only, for their own appointment. */
    @PostMapping("/appointments/{id}/payments/initiate")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<PaymentInitiateResponse> initiateBookingPayment(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentInitiateRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return serviceAppointmentRepository.findByAppointmentIdWithDetails(id)
                .filter(a -> a.getUser().getUserId().equals(currentUser.userId()))
                .flatMap(a -> serviceBookingPaymentRepository.findByAppointmentAndPaymentStatus(a, "pending"))
                .map(payment -> {
                    String checkoutRequestId = mpesaClient.initiateStkPush(
                            request.getPhoneNumber(),
                            payment.getAmount(),
                            "SVC-" + payment.getAppointment().getAppointmentId(),
                            "BiasharaHub service booking");
                    payment.setTransactionId(checkoutRequestId);
                    serviceBookingPaymentRepository.save(payment);
                    return ResponseEntity.ok(PaymentInitiateResponse.builder()
                            .paymentId(payment.getPaymentId())
                            .checkoutRequestId(checkoutRequestId)
                            .status("pending")
                            .message("M-PESA STK push initiated. Complete the payment on your phone.")
                            .build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Update appointment status. Provider: CONFIRMED (sends meeting link for virtual), SERVICE_PROVIDED (with evidence), COMPLETED, CANCELLED. Customer: CANCELLED, or use confirm-service/dispute for virtual escrow. */
    @PatchMapping("/appointments/{id}")
    public ResponseEntity<ServiceAppointmentDto> updateAppointment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceAppointmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser == null || request.getStatus() == null) return ResponseEntity.badRequest().build();
        return serviceAppointmentRepository.findByAppointmentIdWithDetails(id)
                .filter(a -> canAccessAppointment(a, currentUser))
                .map(a -> {
                    a.setStatus(request.getStatus());
                    if ("SERVICE_PROVIDED".equals(request.getStatus())) {
                        if (request.getEvidenceUrl() != null) a.setEvidenceUrl(request.getEvidenceUrl());
                        if (request.getEvidenceNotes() != null) a.setEvidenceNotes(request.getEvidenceNotes());
                        a.setProviderMarkedProvidedAt(java.time.Instant.now());
                    }
                    ServiceAppointment saved = serviceAppointmentRepository.save(a);
                    if ("CONFIRMED".equals(request.getStatus()) && saved.getService() != null
                            && "VIRTUAL".equalsIgnoreCase(saved.getService().getDeliveryType())) {
                        String effectiveLink = getEffectiveMeetingLink(saved);
                        if (effectiveLink != null && !effectiveLink.isBlank()) {
                            saved.setMeetingLinkSentAt(java.time.Instant.now());
                            saved = serviceAppointmentRepository.save(saved);
                            try {
                                String details = saved.getService().getMeetingDetails();
                                inAppNotificationService.notifyServiceMeetingLinkSent(saved, effectiveLink, details);
                                whatsAppNotificationService.notifyServiceMeetingLinkSent(saved, effectiveLink);
                            } catch (Exception ex) {
                                log.warn("Failed to send meeting link notifications for appointment {}: {}", saved.getAppointmentId(), ex.getMessage());
                            }
                        }
                    }
                    try {
                        inAppNotificationService.notifyServiceBookingStatusUpdated(saved);
                        whatsAppNotificationService.notifyServiceBookingStatusUpdated(saved);
                    } catch (Exception ex) {
                        log.warn("Failed to send service booking status notification for appointment {}: {}", saved.getAppointmentId(), ex.getMessage());
                    }
                    return ResponseEntity.ok(toAppointmentDto(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create a Google Calendar event with Google Meet link for this virtual appointment; sets meeting link and sends to both parties. Provider only. */
    @PostMapping("/appointments/{id}/create-google-meet")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<ServiceAppointmentDto> createGoogleMeet(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!googleCalendarMeetService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(null);
        }
        return serviceAppointmentRepository.findByAppointmentIdWithDetails(id)
                .filter(a -> canAccessAppointment(a, currentUser))
                .filter(a -> a.getService() != null && "VIRTUAL".equalsIgnoreCase(a.getService().getDeliveryType()))
                .map(a -> {
                    java.util.List<String> emails = new java.util.ArrayList<>();
                    if (a.getUser() != null && a.getUser().getEmail() != null && !a.getUser().getEmail().isBlank()) {
                        emails.add(a.getUser().getEmail());
                    }
                    userRepository.findByRoleAndBusinessId("owner", a.getService().getBusinessId()).stream()
                            .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                            .findFirst()
                            .ifPresent(u -> emails.add(u.getEmail()));
                    userRepository.findByRoleAndBusinessId("staff", a.getService().getBusinessId()).stream()
                            .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                            .findFirst()
                            .ifPresent(u -> emails.add(u.getEmail()));

                    return googleCalendarMeetService.createEventWithMeet(
                            a.getService().getName(),
                            a.getService().getDescription(),
                            a.getRequestedDate(),
                            a.getRequestedTime(),
                            a.getService().getDurationMinutes(),
                            emails)
                            .map(result -> {
                                a.setMeetingLink(result.getMeetLink());
                                a.setGoogleEventId(result.getEventId());
                                a.setMeetingLinkSentAt(java.time.Instant.now());
                                ServiceAppointment saved = serviceAppointmentRepository.save(a);
                                try {
                                    inAppNotificationService.notifyServiceMeetingLinkSent(saved, result.getMeetLink(), saved.getService().getMeetingDetails());
                                    whatsAppNotificationService.notifyServiceMeetingLinkSent(saved, result.getMeetLink());
                                } catch (Exception ex) {
                                    log.warn("Failed to send meeting link notifications: {}", ex.getMessage());
                                }
                                return ResponseEntity.ok(toAppointmentDto(saved));
                            })
                            .orElse(ResponseEntity.<ServiceAppointmentDto>status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Customer confirms virtual service was provided: releases escrow to provider. */
    @PostMapping("/appointments/{id}/confirm-service")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'STAFF')")
    public ResponseEntity<ServiceAppointmentDto> confirmServiceProvided(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return serviceAppointmentRepository.findByAppointmentIdWithDetails(id)
                .filter(a -> a.getUser().getUserId().equals(currentUser.userId()))
                .filter(a -> "SERVICE_PROVIDED".equals(a.getStatus()) || "CONFIRMED".equals(a.getStatus()))
                .map(a -> {
                    boolean released = serviceBookingEscrowService.releaseToProvider(id);
                    ServiceAppointment updated = serviceAppointmentRepository.findByAppointmentIdWithDetails(id).orElse(a);
                    return released ? ResponseEntity.ok(toAppointmentDto(updated)) : ResponseEntity.status(HttpStatus.CONFLICT).body(toAppointmentDto(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Customer disputes: did not receive service; refunds via M-Pesa B2C and marks escrow REFUNDED. */
    @PostMapping("/appointments/{id}/dispute")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'STAFF')")
    public ResponseEntity<ServiceAppointmentDto> disputeServiceProvided(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return serviceAppointmentRepository.findByAppointmentIdWithDetails(id)
                .filter(a -> a.getUser().getUserId().equals(currentUser.userId()))
                .map(a -> {
                    boolean refunded = serviceBookingEscrowService.refundToCustomer(id);
                    ServiceAppointment updated = serviceAppointmentRepository.findByAppointmentIdWithDetails(id).orElse(a);
                    return refunded ? ResponseEntity.ok(toAppointmentDto(updated)) : ResponseEntity.status(HttpStatus.CONFLICT).body(toAppointmentDto(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Per-appointment meeting link overrides service default when set (e.g. from Google Meet). */
    private String getEffectiveMeetingLink(ServiceAppointment a) {
        if (a == null) return null;
        if (a.getMeetingLink() != null && !a.getMeetingLink().isBlank()) return a.getMeetingLink();
        return a.getService() != null ? a.getService().getMeetingLink() : null;
    }

    private boolean canAccessAppointment(ServiceAppointment a, AuthenticatedUser user) {
        if (a.getUser().getUserId().equals(user.userId())) return true;
        if (isOwnerOrStaff(user)) {
            UUID businessId = getBusinessId(user);
            return businessId != null && businessId.equals(a.getService().getBusinessId());
        }
        return false;
    }

    private ServiceAppointmentDto toAppointmentDto(ServiceAppointment a) {
        ServiceAppointmentDto dto = ServiceAppointmentDto.builder()
                .id(a.getAppointmentId())
                .serviceId(a.getService().getServiceId())
                .serviceName(a.getService().getName())
                .userId(a.getUser().getUserId())
                .userName(a.getUser().getName() != null ? a.getUser().getName() : a.getUser().getEmail())
                .requestedDate(a.getRequestedDate())
                .requestedTime(a.getRequestedTime())
                .status(a.getStatus())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .businessId(a.getService().getBusinessId() != null ? a.getService().getBusinessId().toString() : null)
                .amount(a.getService().getPrice())
                .escrowStatus(a.getEscrowStatus())
                .meetingLink(getEffectiveMeetingLink(a))
                .meetingLinkSentAt(a.getMeetingLinkSentAt())
                .evidenceUrl(a.getEvidenceUrl())
                .evidenceNotes(a.getEvidenceNotes())
                .build();
        serviceBookingPaymentRepository.findByAppointment_AppointmentId(a.getAppointmentId())
                .ifPresent(p -> dto.setPaymentStatus(p.getPaymentStatus()));
        return dto;
    }

    private boolean isOwnerOrStaff(AuthenticatedUser user) {
        if (user == null) return false;
        String r = user.role();
        return "owner".equalsIgnoreCase(r) || "staff".equalsIgnoreCase(r);
    }

    private UUID getBusinessId(AuthenticatedUser user) {
        if (user == null) return null;
        return userRepository.findById(user.userId()).map(User::getBusinessId).orElse(null);
    }

    private boolean canAccess(ServiceOffering s, AuthenticatedUser currentUser) {
        if (currentUser == null) {
            return s.getIsActive() != null && s.getIsActive()
                    && userRepository.findByRoleIgnoreCaseAndVerificationStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc("owner", "verified")
                    .stream().anyMatch(u -> s.getBusinessId().equals(u.getBusinessId()));
        }
        if (isOwnerOrStaff(currentUser)) {
            UUID businessId = getBusinessId(currentUser);
            return businessId != null && businessId.equals(s.getBusinessId());
        }
        return s.getIsActive() != null && s.getIsActive()
                && userRepository.findByRoleIgnoreCaseAndVerificationStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc("owner", "verified")
                .stream().anyMatch(u -> s.getBusinessId().equals(u.getBusinessId()));
    }

    private ServiceOfferingDto toDto(ServiceOffering s) {
        return ServiceOfferingDto.builder()
                .id(s.getServiceId())
                .name(s.getName())
                .categoryId(s.getServiceCategory() != null ? s.getServiceCategory().getCategoryId() : null)
                .category(s.getCategory())
                .description(s.getDescription())
                .price(s.getPrice())
                .businessId(s.getBusinessId() != null ? s.getBusinessId().toString() : null)
                .deliveryType(s.getDeliveryType())
                .meetingLink(s.getMeetingLink())
                .meetingDetails(s.getMeetingDetails())
                .onlineDeliveryMethods(s.getOnlineDeliveryMethods())
                .paymentTiming(s.getPaymentTiming() != null ? s.getPaymentTiming() : "BEFORE_BOOKING")
                .durationMinutes(s.getDurationMinutes())
                .isActive(s.getIsActive())
                .imageUrl(s.getImageUrl())
                .videoUrl(s.getVideoUrl())
                .galleryUrls(s.getGalleryUrls())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
