package com.biasharahub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for WhatsApp integration via Twilio.
 *
 * Set app.whatsapp.enabled=true and provide Twilio credentials plus the
 * WhatsApp sender number (e.g. Sandbox "whatsapp:+14155238886" or your
 * Twilio WhatsApp Business number in E.164).
 */
@Component
@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsAppProperties {

    /**
     * Whether WhatsApp notifications are enabled. When false, sending is a no-op.
     */
    private boolean enabled = false;

    /**
     * Twilio Account SID (from Twilio Console).
     */
    private String twilioAccountSid;

    /**
     * Twilio Auth Token (from Twilio Console).
     */
    private String twilioAuthToken;

    /**
     * WhatsApp sender number in E.164 with whatsapp: prefix, e.g. whatsapp:+14155238886
     * (Sandbox) or your WhatsApp Business-enabled Twilio number.
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
