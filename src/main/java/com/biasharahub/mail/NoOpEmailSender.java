package com.biasharahub.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * No-op sender when Gmail OAuth (or other provider) is not configured. Logs instead of sending.
 * Enables app to run without email config; 2FA and other features degrade gracefully.
 */
@Slf4j
public class NoOpEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage message) {
        log.debug("Email not sent (no provider configured): to={}, subject={}", message.getTo(), message.getSubject());
    }

    @Override
    public boolean isAvailable() {
        return true; // always "available" so we don't fail; we just no-op
    }
}
