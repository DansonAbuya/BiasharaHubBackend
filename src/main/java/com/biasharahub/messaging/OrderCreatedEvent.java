package com.biasharahub.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** Event payload for orders.created topic. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private UUID orderId;
    private String orderNumber;
    private String tenantSchema;
    private UUID customerId;
    private BigDecimal total;
    private String status;
}
