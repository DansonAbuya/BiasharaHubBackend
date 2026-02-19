package com.biasharahub.service;

import com.biasharahub.config.SmsProperties;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sends SMS via Twilio (outbound only).
 * Recipient numbers in E.164 or national format (e.g. 07xx -> +2547xx for Kenya).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmsClient {

    private final SmsProperties properties;

    /**
     * Send an SMS to the given recipient.
     *
     * @param toPhone Recipient phone in E.164 or national format (e.g. 0712345678 or +254712345678)
     * @param body    Message body (plain text)
     */
    public void send(String toPhone, String body) {
        if (!properties.isEnabled()) {
            log.debug("SMS disabled; skipping message to {}", maskPhone(toPhone));
            return;
        }
        if (toPhone == null || toPhone.isBlank()) {
            log.debug("No recipient phone; skipping SMS");
            return;
        }
        String sid = properties.getTwilioAccountSid();
        String token = properties.getTwilioAuthToken();
        String from = properties.getFromNumber();
        if (sid == null || sid.isBlank() || token == null || token.isBlank() || from == null || from.isBlank()) {
            log.warn("SMS enabled but Twilio credentials or from-number not set; skipping message");
            return;
        }
        try {
            String toE164 = normalizeE164(toPhone);
            String fromE164 = normalizeE164(from);
            com.twilio.Twilio.init(sid, token);
            Message message = Message.creator(
                            new PhoneNumber(toE164),
                            new PhoneNumber(fromE164),
                            body)
                    .create();
            log.debug("SMS sent to {} sid={}", maskPhone(toPhone), message.getSid());
        } catch (Exception e) {
            log.warn("Failed to send SMS to {}: {}", maskPhone(toPhone), e.getMessage());
        }
    }

    private static String normalizeE164(String phone) {
        if (phone == null || phone.isBlank()) return phone;
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("254") && digits.length() >= 12) {
            return "+" + digits;
        }
        if (digits.startsWith("0") && digits.length() >= 9) {
            return "+254" + digits.substring(1);
        }
        if (digits.length() >= 9 && !digits.startsWith("0")) {
            return "+254" + digits;
        }
        return "+" + digits;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }
}
