package com.biasharahub.controller;

import com.biasharahub.dto.response.ShipmentDto;
import com.biasharahub.entity.Order;
import com.biasharahub.entity.Shipment;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.ShipmentRepository;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.InAppNotificationService;
import com.biasharahub.service.WhatsAppNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final InAppNotificationService inAppNotificationService;

    @GetMapping
    public ResponseEntity<List<ShipmentDto>> listShipments(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return ResponseEntity.status(401).<List<ShipmentDto>>build();
        List<Shipment> shipments = shipmentRepository.findAll();
        return ResponseEntity.ok(shipments.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<ShipmentDto>> getShipmentsByOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID orderId) {
        if (user == null) return ResponseEntity.status(401).<List<ShipmentDto>>build();
        return orderRepository.findById(orderId)
                .map(order -> ResponseEntity.ok(
                        shipmentRepository.findByOrder(order).stream().map(this::toDto).toList()))
                .orElse(ResponseEntity.status(404).<List<ShipmentDto>>build());
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
                    if (dto.getStatus() != null) {
                        String newStatus = dto.getStatus();
                        s.setStatus(newStatus);
                        if ("SHIPPED".equalsIgnoreCase(newStatus) && s.getShippedAt() == null) {
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
                    s = shipmentRepository.save(s);
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

                    // Simple escrow release: mark payout released on the order
                    Order order = shipment.getOrder();
                    if (order.getPayoutReleasedAt() == null) {
                        order.setPayoutReleasedAt(now);
                        orderRepository.save(order);
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

    private ShipmentDto toDto(Shipment s) {
        return ShipmentDto.builder()
                .id(s.getShipmentId())
                .orderId(s.getOrder().getOrderId())
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
                .build();
    }

    private static String generateOtp() {
        int code = (int) (Math.random() * 1_000_000);
        return String.format("%06d", code);
    }
}
