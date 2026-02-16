package com.biasharahub.courier;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TrackingEvent {
    private Instant timestamp;
    private String status;
    private String description;
    private String location;
}
