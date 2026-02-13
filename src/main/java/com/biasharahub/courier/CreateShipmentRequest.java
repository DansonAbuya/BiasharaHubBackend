package com.biasharahub.courier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CreateShipmentRequest {
    private UUID orderId;
    private UUID shipmentId;
    private String shipperName;
    private String shipperPhone;
    private String shipperAddress;
    private String shipperCity;
    private String shipperPostalCode;
    private String shipperCountry;
    private String recipientName;
    private String recipientPhone;
    private String recipientAddress;
    private String recipientCity;
    private String recipientPostalCode;
    private String recipientCountry;
    private BigDecimal weightKg;
    private String parcelDescription;
}
