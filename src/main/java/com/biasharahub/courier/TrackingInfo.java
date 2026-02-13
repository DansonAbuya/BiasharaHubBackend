package com.biasharahub.courier;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class TrackingInfo {
    private String trackingNumber;
    private String status;
    private String statusDescription;
    private Instant estimatedDelivery;
    private List<TrackingEvent> events;
}
