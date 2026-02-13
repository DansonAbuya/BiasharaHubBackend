package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {

    @Id
    @Column(name = "shipment_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID shipmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "delivery_mode", nullable = false)
    @Builder.Default
    private String deliveryMode = "SELLER_SELF"; // SELLER_SELF, COURIER, RIDER_MARKETPLACE, CUSTOMER_PICKUP

    // Courier delivery fields
    @Column(name = "courier_service")
    private String courierService;

    @Column(name = "tracking_number")
    private String trackingNumber;

    // Rider marketplace fields
    @Column(name = "rider_name")
    private String riderName;

    @Column(name = "rider_phone")
    private String riderPhone;

    @Column(name = "rider_vehicle")
    private String riderVehicle;

    @Column(name = "rider_job_id")
    private String riderJobId;

    // Customer pickup fields
    @Column(name = "pickup_location")
    private String pickupLocation;

    /**
     * Common lifecycle:
     * CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY / READY_FOR_PICKUP ->
     * DELIVERED / COLLECTED -> ESCROW_RELEASED
     */
    @Column(nullable = false)
    @Builder.Default
    private String status = "CREATED";

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "otp_verified_at")
    private Instant otpVerifiedAt;

    @Column(name = "escrow_released_at")
    private Instant escrowReleasedAt;

    /** Trust & Safety: delivery proof (signature image URL, photo URL, GPS). */
    @Column(name = "delivery_signature_url", length = 1024)
    private String deliverySignatureUrl;

    @Column(name = "delivery_photo_url", length = 1024)
    private String deliveryPhotoUrl;

    @Column(name = "delivery_gps_lat", precision = 12, scale = 8)
    private java.math.BigDecimal deliveryGpsLat;

    @Column(name = "delivery_gps_lng", precision = 12, scale = 8)
    private java.math.BigDecimal deliveryGpsLng;

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
