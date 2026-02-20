package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * BiasharaHub Services module: a service offered by a business.
 * Delivery type: VIRTUAL (online) or PHYSICAL (in-person).
 */
@Entity
@Table(name = "service_offerings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceOffering {

    @Id
    @Column(name = "service_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID serviceId;

    @Column(nullable = false)
    private String name;

    /** Predefined category (provider selects from service categories). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_category_id")
    private ServiceCategory serviceCategory;

    /** Category name (denormalized for display/filter; set from serviceCategory when present). */
    @Column(length = 100)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    /**
     * VIRTUAL = online/service delivered remotely; PHYSICAL = in-person/on-site.
     */
    @Column(name = "delivery_type", nullable = false, length = 20)
    @Builder.Default
    private String deliveryType = "PHYSICAL";

    /** For VIRTUAL: online meeting link (e.g. Zoom/Meet). Sent to customer and provider when booking is confirmed. */
    @Column(name = "meeting_link", columnDefinition = "TEXT")
    private String meetingLink;

    /** For VIRTUAL: extra instructions (e.g. "Join 5 min early"). */
    @Column(name = "meeting_details", columnDefinition = "TEXT")
    private String meetingDetails;

    /**
     * For VIRTUAL/online services: comma-separated delivery methods.
     * Possible values: VIDEO_CALL, PHONE_CALL, WHATSAPP, LIVE_CHAT, EMAIL, 
     * SCREEN_SHARE, FILE_DELIVERY, RECORDED_CONTENT, SOCIAL_MEDIA
     */
    @Column(name = "online_delivery_methods", columnDefinition = "TEXT")
    private String onlineDeliveryMethods;

    /**
     * When payment is taken: BEFORE_BOOKING (pay to book), AFTER_SERVICE (pay after service), AS_PER_CONTRACT (per signed contract).
     */
    @Column(name = "payment_timing", length = 30)
    @Builder.Default
    private String paymentTiming = "BEFORE_BOOKING";

    /** Optional duration in minutes (e.g. for consultations). */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** Main image URL to showcase this service. */
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    /** Optional video URL to demonstrate this service (YouTube, Vimeo, or direct link). */
    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    /** Comma-separated list of additional image URLs for this service. */
    @Column(name = "gallery_urls", columnDefinition = "TEXT")
    private String galleryUrls;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
