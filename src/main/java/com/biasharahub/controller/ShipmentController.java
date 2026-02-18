package com.biasharahub.controller;

import com.biasharahub.courier.CourierIntegrationException;
import com.biasharahub.courier.CourierIntegrationService;
import com.biasharahub.courier.CreateShipmentResult;
import com.biasharahub.courier.TrackingEvent;
import com.biasharahub.courier.TrackingInfo;
import com.biasharahub.dto.response.ShipmentDto;
import com.biasharahub.dto.response.TrackingInfoDto;
import com.biasharahub.entity.Order;
import com.biasharahub.entity.Shipment;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.ShipmentRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.InAppNotificationService;
import com.biasharahub.service.PayoutService;
import com.biasharahub.service.WhatsAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/shipments")
@RequiredArgsConstructor
@Slf4j
public class ShipmentController {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final InAppNotificationService inAppNotificationService;
    private final PayoutService payoutService;
    private final CourierIntegrationService courierIntegrationService;

    @GetMapping
    public ResponseEntity<List<ShipmentDto>> listShipments(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return ResponseEntity.status(401).<List<ShipmentDto>>build();
        List<Shipment> shipments = shipmentRepository.findAll();
        return ResponseEntity.ok(shipments.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/order/{orderId}")
    @Transactional(readOnly = false)
    public ResponseEntity<List<ShipmentDto>> getShipmentsByOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID orderId) {
        if (user == null) return ResponseEntity.status(401).<List<ShipmentDto>>build();
        return orderRepository.findById(orderId)
                .map(order -> {
                    List<Shipment> list = shipmentRepository.findByOrder(order);
                    if (list.isEmpty() && "confirmed".equalsIgnoreCase(order.getOrderStatus())) {
                        ensureShipmentForConfirmedOrder(order);
                        list = shipmentRepository.findByOrder(order);
                    }
                    return ResponseEntity.ok(list.stream().map(this::toDto).toList());
                })
                .orElse(ResponseEntity.status(404).<List<ShipmentDto>>build());
    }

    /** Create a shipment for a confirmed order that has none (e.g. cash-confirmed orders where async handler failed). */
    private void ensureShipmentForConfirmedOrder(Order order) {
        if (order == null) return;
        String deliveryMode = order.getDeliveryMode() != null ? order.getDeliveryMode() : "SELLER_SELF";
        String otp = null;
        if ("SELLER_SELF".equalsIgnoreCase(deliveryMode) || "CUSTOMER_PICKUP".equalsIgnoreCase(deliveryMode)) {
            otp = generateOtp();
        }
        Shipment shipment = Shipment.builder()
                .order(order)
                .deliveryMode(deliveryMode)
                .status("CREATED")
                .otpCode(otp)
                .build();
        shipmentRepository.save(shipment);
        log.info("Created missing shipment for confirmed order {} (e.g. cash-confirmed)", order.getOrderId());
    }

    @PostMapping
    public ResponseEntity<ShipmentDto> createShipment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody ShipmentDto dto) {
        if (user == null) return ResponseEntity.status(401).<ShipmentDto>build();
        return orderRepository.findById(dto.getOrderId())
                .map(order -> {
                    String deliveryMode = dto.getDeliveryMode() != null ? dto.getDeliveryMode() : "SELLER_SELF";
                    Shipment.ShipmentBuilder builder = Shipment.builder()
                            .order(order)
                            .deliveryMode(deliveryMode)
                            .courierService(dto.getCarrier())
                            .trackingNumber(dto.getTrackingNumber())
                            .riderName(dto.getRiderName())
                            .riderPhone(dto.getRiderPhone())
                            .riderVehicle(dto.getRiderVehicle())
                            .riderJobId(dto.getRiderJobId())
                            .pickupLocation(dto.getPickupLocation())
                            .status("CREATED");

                    // Phase 1: OTP verification for seller self-delivery and customer pickup
                    if ("SELLER_SELF".equalsIgnoreCase(deliveryMode) ||
                            "CUSTOMER_PICKUP".equalsIgnoreCase(deliveryMode)) {
                        builder.otpCode(generateOtp());
                    }

                    Shipment s = builder.build();
                    s = shipmentRepository.save(s);
                    return ResponseEntity.ok(toDto(s));
                })
                .orElse(ResponseEntity.status(404).<ShipmentDto>build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ShipmentDto> updateShipment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ShipmentDto dto) {
        if (user == null) return ResponseEntity.status(401).<ShipmentDto>build();
        return shipmentRepository.findById(id)
                .map(s -> {
                    String prevStatus = s.getStatus();
                    if (dto.getStatus() != null) {
                        String newStatus = dto.getStatus();
                        s.setStatus(newStatus);
                        if (("SHIPPED".equalsIgnoreCase(newStatus) || "IN_TRANSIT".equalsIgnoreCase(newStatus)) && s.getShippedAt() == null) {
                            s.setShippedAt(Instant.now());
                        }
                        if ("DELIVERED".equalsIgnoreCase(newStatus) && s.getDeliveredAt() == null) {
                            s.setDeliveredAt(Instant.now());
                        }
                    }
                    if (dto.getCarrier() != null) s.setCourierService(dto.getCarrier());
                    if (dto.getTrackingNumber() != null) s.setTrackingNumber(dto.getTrackingNumber());
                    if (dto.getRiderName() != null) s.setRiderName(dto.getRiderName());
                    if (dto.getRiderPhone() != null) s.setRiderPhone(dto.getRiderPhone());
                    if (dto.getRiderVehicle() != null) s.setRiderVehicle(dto.getRiderVehicle());
                    if (dto.getRiderJobId() != null) s.setRiderJobId(dto.getRiderJobId());
                    if (dto.getPickupLocation() != null) s.setPickupLocation(dto.getPickupLocation());
                    if (dto.getAssignedCourierId() != null) {
                        s.setAssignedCourier(userRepository.findById(dto.getAssignedCourierId()).orElse(null));
                    }
                    s = shipmentRepository.save(s);

                    boolean justDispatched = !"CREATED".equalsIgnoreCase(s.getStatus()) && "CREATED".equalsIgnoreCase(prevStatus);
                    if (justDispatched && ("IN_TRANSIT".equalsIgnoreCase(s.getStatus()) || "SHIPPED".equalsIgnoreCase(s.getStatus()))) {
                        try {
                            whatsAppNotificationService.notifyShipmentUpdated(s);
                        } catch (Exception e) {
                            log.warn("Failed to send WhatsApp dispatch notification: {}", e.getMessage());
                        }
                        try {
                            inAppNotificationService.notifyShipmentUpdated(s);
                        } catch (Exception e) {
                            log.warn("Failed to create in-app dispatch notification: {}", e.getMessage());
                        }
                    }
                    return ResponseEntity.ok(toDto(s));
                })
                .orElse(ResponseEntity.status(404).<ShipmentDto>build());
    }

    /**
     * Verify shipment using OTP (seller self-delivery or pickup).
     * When OTP is correct, mark shipment as DELIVERED/COLLECTED and release escrow.
     */
    @PostMapping("/{id}/verify-otp")
    public ResponseEntity<ShipmentDto> verifyOtp(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        if (user == null) return ResponseEntity.status(401).<ShipmentDto>build();
        String code = body.getOrDefault("code", "").trim();
        if (code.isEmpty()) {
            return ResponseEntity.badRequest().<ShipmentDto>build();
        }
        return shipmentRepository.findById(id)
                .map(shipment -> {
                    if (shipment.getOtpCode() == null || !shipment.getOtpCode().equals(code)) {
                        return ResponseEntity.status(400).<ShipmentDto>build();
                    }
                    Instant now = Instant.now();
                    shipment.setOtpVerifiedAt(now);
                    // Update status based on delivery mode
                    if ("CUSTOMER_PICKUP".equalsIgnoreCase(shipment.getDeliveryMode())) {
                        shipment.setStatus("COLLECTED");
                    } else {
                        shipment.setStatus("DELIVERED");
                    }
                    if (shipment.getDeliveredAt() == null) {
                        shipment.setDeliveredAt(now);
                    }
                    // Trust & Safety: optional delivery proof (signature, photo, GPS)
                    if (body != null) {
                        if (body.get("deliverySignatureUrl") != null && !body.get("deliverySignatureUrl").isBlank()) {
                            shipment.setDeliverySignatureUrl(body.get("deliverySignatureUrl").trim());
                        }
                        if (body.get("deliveryPhotoUrl") != null && !body.get("deliveryPhotoUrl").isBlank()) {
                            shipment.setDeliveryPhotoUrl(body.get("deliveryPhotoUrl").trim());
                        }
                        try {
                            if (body.get("deliveryGpsLat") != null && !body.get("deliveryGpsLat").isBlank()) {
                                shipment.setDeliveryGpsLat(new java.math.BigDecimal(body.get("deliveryGpsLat").trim()));
                            }
                            if (body.get("deliveryGpsLng") != null && !body.get("deliveryGpsLng").isBlank()) {
                                shipment.setDeliveryGpsLng(new java.math.BigDecimal(body.get("deliveryGpsLng").trim()));
                            }
                        } catch (NumberFormatException ignored) { }
                    }

                    // Simple escrow release: mark payout released on the order
                    Order order = shipment.getOrder();
                    if (order.getPayoutReleasedAt() == null) {
                        order.setPayoutReleasedAt(now);
                        orderRepository.save(order);
                        // Auto-transfer owner's payment to their default payout destination (e.g. M-Pesa)
                        try {
                            payoutService.triggerAutoPayoutForOrder(order);
                        } catch (Exception e) {
                            log.warn("Auto-payout for order {} failed (escrow still released): {}", order.getOrderId(), e.getMessage());
                        }
                    }
                    shipment.setEscrowReleasedAt(now);

                    shipment = shipmentRepository.save(shipment);

                    // Notify WhatsApp chatbot about final delivery/collection
                    try {
                        whatsAppNotificationService.notifyShipmentUpdated(shipment);
                    } catch (Exception e) {
                        // ignore failures, do not affect core flow
                    }

                    // In-app notification for final delivery/collection
                    try {
                        inAppNotificationService.notifyShipmentUpdated(shipment);
                    } catch (Exception e) {
                        // ignore failures
                    }

                    return ResponseEntity.ok(toDto(shipment));
                })
                .orElse(ResponseEntity.status(404).<ShipmentDto>build());
    }

    /**
     * Create shipment with integrated courier provider (DHL, FedEx, Sendy, REST).
     * Body: { "courierServiceCode": "dhl" }. Updates shipment with tracking number and carrier.
     */
    @PostMapping("/{id}/create-with-provider")
    public ResponseEntity<?> createWithProvider(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        if (user == null) return ResponseEntity.status(401).build();
        String code = body != null ? body.get("courierServiceCode") : null;
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "courierServiceCode is required"));
        }
        try {
            CreateShipmentResult result = courierIntegrationService.createShipmentWithProvider(id, code.trim());
            if (result == null) {
                return ResponseEntity.ok(Map.of("message", "Manual courier: enter tracking number in shipment details."));
            }
            Shipment s = shipmentRepository.findById(id).orElseThrow();
            return ResponseEntity.ok(Map.of(
                    "shipment", toDto(s),
                    "trackingNumber", result.getTrackingNumber(),
                    "labelUrl", result.getLabelUrl() != null ? result.getLabelUrl() : ""));
        } catch (CourierIntegrationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get tracking info from courier provider (if integrated). Returns null fields for MANUAL.
     */
    @GetMapping("/{id}/tracking")
    public ResponseEntity<TrackingInfoDto> getTracking(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        if (user == null) return ResponseEntity.status(401).build();
        try {
            TrackingInfo info = courierIntegrationService.getTracking(id);
            if (info == null) {
                return ResponseEntity.ok(TrackingInfoDto.builder().build());
            }
            TrackingInfoDto dto = TrackingInfoDto.builder()
                    .trackingNumber(info.getTrackingNumber())
                    .status(info.getStatus())
                    .statusDescription(info.getStatusDescription())
                    .estimatedDelivery(info.getEstimatedDelivery())
                    .events(info.getEvents() != null ? info.getEvents().stream()
                            .map(e -> TrackingInfoDto.TrackingEventDto.builder()
                                    .timestamp(e.getTimestamp())
                                    .status(e.getStatus())
                                    .description(e.getDescription())
                                    .location(e.getLocation())
                                    .build())
                            .toList() : null)
                    .build();
            return ResponseEntity.ok(dto);
        } catch (CourierIntegrationException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private ShipmentDto toDto(Shipment s) {
        return ShipmentDto.builder()
                .id(s.getShipmentId())
                .orderId(s.getOrder().getOrderId())
                .assignedCourierId(s.getAssignedCourier() != null ? s.getAssignedCourier().getUserId() : null)
                .deliveryMode(s.getDeliveryMode())
                .status(s.getStatus())
                .carrier(s.getCourierService())
                .trackingNumber(s.getTrackingNumber())
                .riderName(s.getRiderName())
                .riderPhone(s.getRiderPhone())
                .riderVehicle(s.getRiderVehicle())
                .riderJobId(s.getRiderJobId())
                .pickupLocation(s.getPickupLocation())
                .shippedAt(s.getShippedAt())
                .estimatedDelivery(null)
                .actualDelivery(s.getDeliveredAt())
                .escrowReleasedAt(s.getEscrowReleasedAt())
                .otpCode(s.getOtpCode())
                .deliverySignatureUrl(s.getDeliverySignatureUrl())
                .deliveryPhotoUrl(s.getDeliveryPhotoUrl())
                .deliveryGpsLat(s.getDeliveryGpsLat())
                .deliveryGpsLng(s.getDeliveryGpsLng())
                .build();
    }

    private static String generateOtp() {
        int code = (int) (Math.random() * 1_000_000);
        return String.format("%06d", code);
    }
}
