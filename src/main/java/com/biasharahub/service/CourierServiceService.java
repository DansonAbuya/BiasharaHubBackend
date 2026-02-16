package com.biasharahub.service;

import com.biasharahub.dto.request.CreateCourierServiceRequest;
import com.biasharahub.dto.request.UpdateCourierServiceRequest;
import com.biasharahub.dto.response.CourierServiceDto;
import com.biasharahub.entity.CourierService;
import com.biasharahub.repository.CourierServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourierServiceService {

    private final CourierServiceRepository courierServiceRepository;

    public List<CourierServiceDto> listActive() {
        return courierServiceRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<CourierServiceDto> listAll() {
        return courierServiceRepository.findAllByOrderByNameAsc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public CourierServiceDto getById(UUID courierId) {
        return courierServiceRepository.findById(courierId).map(this::toDto).orElse(null);
    }

    @Transactional
    public CourierServiceDto create(CreateCourierServiceRequest request) {
        String code = request.getCode() != null ? request.getCode().trim().toLowerCase() : "";
        if (courierServiceRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("Courier service with code '" + code + "' already exists");
        }
        CourierService entity = CourierService.builder()
                .name(request.getName().trim())
                .code(code)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .trackingUrlTemplate(blankToNull(request.getTrackingUrlTemplate()))
                .providerType(blankToDefault(request.getProviderType(), "MANUAL"))
                .apiBaseUrl(blankToNull(request.getApiBaseUrl()))
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .baseRate(request.getBaseRate() != null ? request.getBaseRate() : java.math.BigDecimal.ZERO)
                .ratePerKg(request.getRatePerKg() != null ? request.getRatePerKg() : java.math.BigDecimal.ZERO)
                .build();
        entity = courierServiceRepository.save(entity);
        return toDto(entity);
    }

    @Transactional
    public CourierServiceDto update(UUID courierId, UpdateCourierServiceRequest request) {
        CourierService entity = courierServiceRepository.findById(courierId)
                .orElseThrow(() -> new IllegalArgumentException("Courier service not found: " + courierId));
        if (request.getName() != null) entity.setName(request.getName().trim());
        if (request.getDescription() != null) entity.setDescription(request.getDescription().trim());
        if (request.getTrackingUrlTemplate() != null) entity.setTrackingUrlTemplate(blankToNull(request.getTrackingUrlTemplate()));
        if (request.getProviderType() != null) entity.setProviderType(blankToDefault(request.getProviderType(), "MANUAL"));
        if (request.getApiBaseUrl() != null) entity.setApiBaseUrl(blankToNull(request.getApiBaseUrl()));
        if (request.getIsActive() != null) entity.setIsActive(request.getIsActive());
        if (request.getBaseRate() != null) entity.setBaseRate(request.getBaseRate());
        if (request.getRatePerKg() != null) entity.setRatePerKg(request.getRatePerKg());
        entity = courierServiceRepository.save(entity);
        return toDto(entity);
    }

    @Transactional
    public void delete(UUID courierId) {
        if (!courierServiceRepository.existsById(courierId)) {
            throw new IllegalArgumentException("Courier service not found: " + courierId);
        }
        courierServiceRepository.deleteById(courierId);
    }

    public CourierServiceDto toDto(CourierService e) {
        return CourierServiceDto.builder()
                .courierId(e.getCourierId())
                .name(e.getName())
                .code(e.getCode())
                .description(e.getDescription())
                .trackingUrlTemplate(e.getTrackingUrlTemplate())
                .providerType(e.getProviderType())
                .apiBaseUrl(e.getApiBaseUrl())
                .isActive(e.getIsActive())
                .baseRate(e.getBaseRate())
                .ratePerKg(e.getRatePerKg())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private static String blankToDefault(String s, String defaultVal) {
        return s != null && !s.isBlank() ? s.trim() : defaultVal;
    }
}
