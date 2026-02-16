package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingInfoDto {
    private String trackingNumber;
    private String status;
    private String statusDescription;
    private Instant estimatedDelivery;
    private List<TrackingEventDto> events;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingEventDto {
        private Instant timestamp;
        private String status;
        private String description;
        private String location;
    }
}
