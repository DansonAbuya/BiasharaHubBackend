package com.biasharahub.courier;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateShipmentResult {
    private String trackingNumber;
    private String labelUrl;
    private String carrierReference;
}
