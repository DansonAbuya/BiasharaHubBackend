package com.biasharahub.courier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RateQuote {
    private String serviceName;
    private String serviceCode;
    private BigDecimal amount;
    private String currency;
    private String estimatedDays;
}
