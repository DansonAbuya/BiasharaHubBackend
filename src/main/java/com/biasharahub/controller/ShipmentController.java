package com.biasharahub.controller;

import com.biasharahub.dto.response.ShipmentDto;
import com.biasharahub.entity.Order;
import com.biasharahub.entity.Shipment;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.ShipmentRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;

    @GetMapping
    public ResponseEntity<List<ShipmentDto>> listShipments(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        List<Shipment> shipments = shipmentRepository.findAll();
        return ResponseEntity.ok(shipments.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<ShipmentDto>> getShipmentsByOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID orderId) {
        if (user == null) return ResponseEntity.status(401).build();
        return orderRepository.findById(orderId)
                .map(order -> ResponseEntity.ok(
                        shipmentRepository.findByOrder(order).stream().map(this::toDto).toList()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ShipmentDto> createShipment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody ShipmentDto dto) {
        if (user == null) return ResponseEntity.status(401).build();
        return orderRepository.findById(dto.getOrderId())
                .map(order -> {
                    Shipment s = Shipment.builder()
                            .order(order)
                            .courierService(dto.getCarrier())
                            .trackingNumber(dto.getTrackingNumber())
                            .status("pending")
                            .build();
                    s = shipmentRepository.save(s);
                    return ResponseEntity.ok(toDto(s));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ShipmentDto> updateShipment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ShipmentDto dto) {
        if (user == null) return ResponseEntity.status(401).build();
        return shipmentRepository.findById(id)
                .map(s -> {
                    if (dto.getStatus() != null) s.setStatus(dto.getStatus());
                    if (dto.getCarrier() != null) s.setCourierService(dto.getCarrier());
                    if (dto.getTrackingNumber() != null) s.setTrackingNumber(dto.getTrackingNumber());
                    if ("delivered".equals(dto.getStatus())) s.setDeliveredAt(java.time.Instant.now());
                    if ("shipped".equals(dto.getStatus()) && s.getShippedAt() == null)
                        s.setShippedAt(java.time.Instant.now());
                    s = shipmentRepository.save(s);
                    return ResponseEntity.ok(toDto(s));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private ShipmentDto toDto(Shipment s) {
        return ShipmentDto.builder()
                .id(s.getShipmentId())
                .orderId(s.getOrder().getOrderId())
                .status(s.getStatus())
                .carrier(s.getCourierService())
                .trackingNumber(s.getTrackingNumber())
                .shippedAt(s.getShippedAt())
                .actualDelivery(s.getDeliveredAt())
                .shippedAt(s.getShippedAt())
                .build();
    }
}
