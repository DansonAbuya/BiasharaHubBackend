package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Shipment with order summary for courier portal. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierShipmentDto {
    private ShipmentDto shipment;
    private String orderId;
    private String orderNumber;
    private String customerName;
    private String shippingAddress;
}
