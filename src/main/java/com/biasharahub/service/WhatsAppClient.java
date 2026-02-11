package com.biasharahub.service;

import com.biasharahub.config.WhatsAppProperties;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sends WhatsApp messages via Twilio.
 *
 * Recipient numbers should be in E.164 format (e.g. +254712345678).
 * When not in E.164, a best-effort normalization is applied (e.g. 07xx -> +2547xx for Kenya).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppClient {

    private final WhatsAppProperties properties;

    /**
     * Send a WhatsApp text message to the given recipient.
     *
     * @param toPhone Recipient phone in E.164 or national format (e.g. 0712345678 or +254712345678)
     * @param body    Message body (plain text)
     */
    public void sendMessage(String toPhone, String body) {
        if (!properties.isEnabled()) {
            log.debug("WhatsApp disabled; skipping message to {}", maskPhone(toPhone));
            return;
        }
        if (toPhone == null || toPhone.isBlank()) {
            log.debug("No recipient phone; skipping WhatsApp message");
            return;
        }
        String sid = properties.getTwilioAccountSid();
        String token = properties.getTwilioAuthToken();
        String from = properties.getFromNumber();
        if (sid == null || sid.isBlank() || token == null || token.isBlank() || from == null || from.isBlank()) {
            log.warn("WhatsApp enabled but Twilio credentials or from-number not set; skipping message");
            return;
        }
        try {
            String toWhatsApp = toE164WhatsApp(toPhone);
            com.twilio.Twilio.init(sid, token);
            Message message = Message.creator(
                            new PhoneNumber(toWhatsApp),
                            new PhoneNumber(from),
                            body)
                    .create();
            log.debug("WhatsApp message sent to {} sid={}", maskPhone(toPhone), message.getSid());
        } catch (Exception e) {
            log.warn("Failed to send WhatsApp message to {}: {}", maskPhone(toPhone), e.getMessage());
        }
    }

    /**
     * Normalize phone to whatsapp:+E164. Handles Kenyan 07xx -> +2547xx.
     */
    static String toE164WhatsApp(String phone) {
        if (phone == null || phone.isBlank()) return phone;
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("254") && digits.length() >= 12) {
            return "whatsapp:+" + digits;
        }
        if (digits.startsWith("0") && digits.length() >= 9) {
            return "whatsapp:+254" + digits.substring(1);
        }
        if (digits.length() >= 9 && !digits.startsWith("0")) {
            return "whatsapp:+254" + digits;
        }
        return "whatsapp:+" + digits;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }
}
