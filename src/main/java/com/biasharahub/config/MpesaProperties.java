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
    /** B2C initiator name (API user with B2C role in M-Pesa portal). */
    private String b2cInitiatorName;
    /** B2C security credential (encrypted initiator password from M-Pesa portal). */
    private String b2cSecurityCredential;
    /** B2C shortcode (organization paybill/till); often same as shortcode. */
    private String b2cShortcode;
    private int timeout;
}

