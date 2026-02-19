package com.biasharahub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for outbound SMS via Twilio (e.g. seller new-order notifications).
 * Uses the same Twilio account as WhatsApp; set a separate "from" number that supports SMS.
 */
@Component
@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {

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
     * SMS sender number in E.164 (e.g. +254712345678). Must be a Twilio number that supports SMS.
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

    public String getFromNumber() {
        return fromNumber;
    }

    public void setFromNumber(String fromNumber) {
        this.fromNumber = fromNumber;
    }
}
