package com.biasharahub.controller;

import com.biasharahub.dto.request.CreateCourierServiceRequest;
import com.biasharahub.dto.request.UpdateCourierServiceRequest;
import com.biasharahub.dto.response.CourierServiceDto;
import com.biasharahub.service.CourierServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin CRUD for platform courier services catalog.
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class CourierServiceAdminController {

    private final CourierServiceService courierServiceService;

    @GetMapping("/admin/courier-services")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<List<CourierServiceDto>> listAll() {
        return ResponseEntity.ok(courierServiceService.listAll());
    }

    @GetMapping("/admin/courier-services/{courierId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<CourierServiceDto> getOne(@PathVariable UUID courierId) {
        CourierServiceDto dto = courierServiceService.getById(courierId);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    @PostMapping("/admin/courier-services")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateCourierServiceRequest request) {
        try {
            CourierServiceDto created = courierServiceService.create(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/admin/courier-services/{courierId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<?> update(
            @PathVariable UUID courierId,
            @RequestBody UpdateCourierServiceRequest request) {
        try {
            CourierServiceDto updated = courierServiceService.update(courierId, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/admin/courier-services/{courierId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable UUID courierId) {
        try {
            courierServiceService.delete(courierId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
