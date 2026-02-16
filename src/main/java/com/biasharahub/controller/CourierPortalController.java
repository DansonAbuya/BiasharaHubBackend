package com.biasharahub.controller;

import com.biasharahub.dto.response.CourierShipmentDto;
import com.biasharahub.dto.response.ShipmentDto;
import com.biasharahub.entity.Shipment;
import com.biasharahub.entity.User;
import com.biasharahub.repository.ShipmentRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.InAppNotificationService;
import com.biasharahub.service.WhatsAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Courier portal: couriers can list shipments assigned to them and update status.
 * Shipments are matched by assigned_courier_id or rider_phone = courier's phone.
 */
@RestController
@RequestMapping("/courier")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('COURIER')")
public class CourierPortalController {

    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final InAppNotificationService inAppNotificationService;

    /**
     * List shipments assigned to this courier (by user id or rider_phone).
     * Optionally filter to only active (exclude DELIVERED/COLLECTED).
     * Includes order summary (customer name, shipping address) for courier display.
     */
    @GetMapping("/shipments")
    public ResponseEntity<List<CourierShipmentDto>> listMyShipments(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        if (user == null) return ResponseEntity.status(401).build();
        String[] phones = getCourierPhoneVariants(user.userId());
        List<Shipment> shipments = activeOnly
                ? shipmentRepository.findByAssignedCourierOrRiderPhone(user.userId(), phones[0], phones[1])
                : shipmentRepository.findAllByAssignedCourierOrRiderPhone(user.userId(), phones[0], phones[1]);
        return ResponseEntity.ok(shipments.stream().map(this::toCourierDto).collect(Collectors.toList()));
    }

    /**
     * Update shipment status. Only allowed if shipment is assigned to this courier.
     * Valid transitions: CREATED/SHIPPED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED
     */
    @PatchMapping("/shipments/{id}/status")
    public ResponseEntity<?> updateStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        if (user == null) return ResponseEntity.status(401).build();
        String newStatus = body != null ? body.get("status") : null;
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }
        var opt = shipmentRepository.findById(id).filter(s -> isAssignedToCourier(s, user.userId()));
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Shipment not found or not assigned to you"));
        }
        Shipment s = opt.get();
        String prevStatus = s.getStatus();
        s.setStatus(newStatus.trim().toUpperCase());
        if (("SHIPPED".equalsIgnoreCase(newStatus) || "IN_TRANSIT".equalsIgnoreCase(newStatus) || "PICKED_UP".equalsIgnoreCase(newStatus)) && s.getShippedAt() == null) {
            s.setShippedAt(Instant.now());
        }
        if ("DELIVERED".equalsIgnoreCase(newStatus) || "COLLECTED".equalsIgnoreCase(newStatus)) {
            if (s.getDeliveredAt() == null) s.setDeliveredAt(Instant.now());
        }
        s = shipmentRepository.save(s);

        boolean justDispatched = "CREATED".equalsIgnoreCase(prevStatus) &&
                ("IN_TRANSIT".equalsIgnoreCase(newStatus) || "SHIPPED".equalsIgnoreCase(newStatus) || "PICKED_UP".equalsIgnoreCase(newStatus));
        if (justDispatched) {
            try { whatsAppNotificationService.notifyShipmentUpdated(s); } catch (Exception e) { log.warn("WhatsApp notify failed: {}", e.getMessage()); }
            try { inAppNotificationService.notifyShipmentUpdated(s); } catch (Exception e) { log.warn("In-app notify failed: {}", e.getMessage()); }
        }
        return ResponseEntity.ok(toDto(s));
    }

    private boolean isAssignedToCourier(Shipment s, UUID courierUserId) {
        if (s.getAssignedCourier() != null && s.getAssignedCourier().getUserId().equals(courierUserId)) {
            return true;
        }
        String[] phones = getCourierPhoneVariants(courierUserId);
        String riderPhone = normalizePhone(s.getRiderPhone());
        if (riderPhone.isEmpty()) return false;
        return riderPhone.equals(phones[0]) || riderPhone.equals(phones[1]);
    }

    /** Returns [normalized, alternate] e.g. [+254712345678, 0712345678]. Uses empty string for null. */
    private String[] getCourierPhoneVariants(UUID userId) {
        String raw = userRepository.findById(userId)
                .map(User::getPhone)
                .orElse(null);
        if (raw == null || raw.isBlank()) return new String[]{"", ""};
        String n = normalizePhone(raw);
        String alt = toAlternateFormat(n);
        return new String[]{n, alt != null ? alt : ""};
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return "";
        return phone.replaceAll("\\s+", "").trim();
    }

    /** Kenya: +254712345678 <-> 0712345678 */
    private static String toAlternateFormat(String normalized) {
        if (normalized == null || normalized.isEmpty()) return "";
        if (normalized.startsWith("+254")) {
            return "0" + normalized.substring(4);
        }
        if (normalized.startsWith("0") && normalized.length() == 10) {
            return "+254" + normalized.substring(1);
        }
        return "";
    }

    private CourierShipmentDto toCourierDto(Shipment s) {
        var order = s.getOrder();
        String customerName = order.getUser() != null ? order.getUser().getName() : null;
        return CourierShipmentDto.builder()
                .shipment(toDto(s))
                .orderId(order.getOrderId().toString())
                .orderNumber(order.getOrderNumber())
                .customerName(customerName)
                .shippingAddress(order.getShippingAddress())
                .build();
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
}
