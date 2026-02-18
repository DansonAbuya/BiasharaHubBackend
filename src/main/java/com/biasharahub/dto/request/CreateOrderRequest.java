package com.biasharahub.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<OrderItemRequest> items;

    private String shippingAddress;

    /**
     * Preferred delivery mode selected by the customer.
     * SELLER_SELF, COURIER, RIDER_MARKETPLACE, CUSTOMER_PICKUP
     */
    private String deliveryMode;

    /**
     * Shipping fee to charge the customer (optional; defaults to 0 on backend).
     */
    private BigDecimal shippingFee;

    /**
     * Payment method: "M-Pesa" or "Cash". Default "M-Pesa".
     * Cash: customer pays in cash; seller confirms payment in the system.
     */
    private String paymentMethod;

    /** When staff/owner place an order on behalf of a customer, set this to the customer's user ID. */
    private java.util.UUID orderForCustomerId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        @NotNull
        private java.util.UUID productId;
        @NotNull
        private Integer quantity;
    }
}
