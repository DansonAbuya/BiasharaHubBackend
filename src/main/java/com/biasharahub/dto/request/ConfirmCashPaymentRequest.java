package com.biasharahub.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional body for PATCH .../payments/{paymentId}/confirm when confirming cash payment.
 * Seller confirms delivery mode and address before the shipment is created.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmCashPaymentRequest {

    /** Delivery mode: SELLER_SELF, COURIER, RIDER_MARKETPLACE, CUSTOMER_PICKUP. */
    private String deliveryMode;

    /** Shipping/delivery address (for SELLER_SELF, COURIER, RIDER_MARKETPLACE). */
    private String shippingAddress;

    /** Pickup location (for CUSTOMER_PICKUP). */
    private String pickupLocation;
}
