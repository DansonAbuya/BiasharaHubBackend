package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReviewDto {
    private UUID reviewId;
    private UUID orderId;
    private UUID reviewerUserId;
    private String reviewerName;
    private Integer rating;
    private String comment;
    private Instant createdAt;
}
