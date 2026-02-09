package com.biasharahub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.mpesa")
@Data
public class MpesaProperties {
    private boolean enabled;
    private String environment;
    private String baseUrl;
    private String shortcode;
    private String passkey;
    private String consumerKey;
    private String consumerSecret;
    private String stkCallbackUrl;
    private String b2cCallbackUrl;
    private String payoutProductName;
    private int timeout;
}

