package com.biasharahub.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for seller to set or update delivery mode and address for an order.
 * Used for cash orders where the seller decides how and where to ship/deliver.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderDeliveryRequest {

    /**
     * Delivery mode: SELLER_SELF, COURIER, RIDER_MARKETPLACE, CUSTOMER_PICKUP.
     */
    private String deliveryMode;

    /**
     * Shipping/delivery address (for SELLER_SELF, COURIER, RIDER_MARKETPLACE).
     */
    private String shippingAddress;

    /**
     * Pickup location (for CUSTOMER_PICKUP – where the customer collects).
     */
    private String pickupLocation;

    /**
     * Optional shipping fee to record (does not change order total).
     */
    private BigDecimal shippingFee;
}
