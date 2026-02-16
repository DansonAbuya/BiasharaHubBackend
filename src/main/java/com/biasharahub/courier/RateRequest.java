package com.biasharahub.courier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RateRequest {
    private String originAddress;
    private String originCity;
    private String originPostalCode;
    private String originCountry;
    private String destinationAddress;
    private String destinationCity;
    private String destinationPostalCode;
    private String destinationCountry;
    private BigDecimal weightKg;
    private BigDecimal lengthCm;
    private BigDecimal widthCm;
    private BigDecimal heightCm;
}
