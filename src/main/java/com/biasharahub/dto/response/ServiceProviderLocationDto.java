package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for verified service providers with location information.
 * Used for map-based search on the public services page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceProviderLocationDto {
    private UUID ownerId;
    private UUID businessId;
    private String businessName;
    private String name;
    private String email;
    private String phone;
    private String serviceDeliveryType;
    private Double locationLat;
    private Double locationLng;
    private String locationDescription;
    private UUID serviceCategoryId;
    private String serviceCategoryName;
    private int serviceCount;
}
