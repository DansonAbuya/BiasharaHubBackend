package com.biasharahub.mail;

/**
 * Abstraction for sending email. Implementations use REST APIs only (no SMTP):
 * - Postmark (recommended): POST https://api.postmarkapp.com/email
 * - Gmail API: OAuth2 + users.messages.send
 * Enables multi-tenant and provider swap without changing callers.
 */
public interface EmailSender {

    /**
     * Send an email. Implementations may use tenantId for tenant-specific config.
     *
     * @param message the email to send
     * @throws EmailException if send fails (after retries if applicable)
     */
    void send(EmailMessage message);

    /**
     * Whether this sender is available (e.g. credentials configured). If false, a fallback or no-op may be used.
     */
    boolean isAvailable();
}
