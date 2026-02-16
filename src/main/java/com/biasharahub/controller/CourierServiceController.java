package com.biasharahub.controller;

import com.biasharahub.dto.response.CourierServiceDto;
import com.biasharahub.service.CourierServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public API for courier services (e.g. dropdown when creating shipments with COURIER delivery mode).
 */
@RestController
@RequestMapping("/courier-services")
@RequiredArgsConstructor
public class CourierServiceController {

    private final CourierServiceService courierServiceService;

    @GetMapping
    public ResponseEntity<List<CourierServiceDto>> listActive() {
        List<CourierServiceDto> list = courierServiceService.listActive();
        return ResponseEntity.ok(list);
    }
}
