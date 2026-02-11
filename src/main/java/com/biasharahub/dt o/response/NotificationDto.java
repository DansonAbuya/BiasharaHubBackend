package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID id;
    private String type;
    private String title;
    private String message;
    private String actionUrl;
    private String data;
    private boolean read;
    private Instant createdAt;
}

