package com.biasharahub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for outbound SMS via Twilio (e.g. seller new-order notifications).
 * Uses the same Twilio account as WhatsApp; set a separate "from" number that supports SMS.
 * The SMS "from" number is read from environment variable TWILIO_SMS_FROM when set (e.g. +14155238886).
 */
@Component
@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {

    private static final String TWILIO_SMS_FROM_ENV = "TWILIO_SMS_FROM";

    /**
     * Whether SMS notifications are enabled. When false, sending is a no-op.
     */
    private boolean enabled = false;

    /**
     * Twilio Account SID (can reuse TWILIO_ACCOUNT_SID from WhatsApp).
     */
    private String twilioAccountSid;

    /**
     * Twilio Auth Token (can reuse TWILIO_AUTH_TOKEN from WhatsApp).
     */
    private String twilioAuthToken;

    /**
     * SMS sender number in E.164 (e.g. +14155238886 for US Twilio). Fallback when TWILIO_SMS_FROM is not set.
     */
    private String fromNumber;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTwilioAccountSid() {
        return twilioAccountSid;
    }

    public void setTwilioAccountSid(String twilioAccountSid) {
        this.twilioAccountSid = twilioAccountSid;
    }

    public String getTwilioAuthToken() {
        return twilioAuthToken;
    }

    public void setTwilioAuthToken(String twilioAuthToken) {
        this.twilioAuthToken = twilioAuthToken;
    }

    /**
     * SMS "from" number. Uses environment variable TWILIO_SMS_FROM when set, otherwise app.sms.from-number.
     */
    public String getFromNumber() {
        String fromEnv = System.getenv(TWILIO_SMS_FROM_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return fromNumber;
    }

    public void setFromNumber(String fromNumber) {
        this.fromNumber = fromNumber;
    }
}
