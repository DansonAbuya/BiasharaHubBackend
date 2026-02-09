package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {
    private UUID id;
    private String orderId;
    private UUID customerId;
    private String customerName;
    private String customerEmail;
    private String businessId;
    private List<OrderItemDto> items;
    private BigDecimal total;
    private String status;
    private String paymentStatus;
    private String paymentMethod;
    private UUID paymentId; // first pending payment, for client to confirm
    private Instant createdAt;
    private Instant updatedAt;
    private String shippingAddress;
    private Instant deliveredAt;
    private Instant payoutReleasedAt;
}
