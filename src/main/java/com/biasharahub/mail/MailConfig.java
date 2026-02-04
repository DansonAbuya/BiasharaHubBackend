package com.biasharahub.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mail beans: Gmail OAuth config and primary EmailSender (Gmail OAuth when configured, else fallback).
 */
@Configuration
public class MailConfig {

    @Value("${app.mail.postmark.server-token:${POSTMARK_SERVER_TOKEN:}}")
    private String postmarkServerToken;

    @Value("${app.mail.postmark.from:${MAIL_FROM:no-reply@biasharahub.local}}")
    private String postmarkFrom;

    @Value("${app.mail.postmark.message-stream:${POSTMARK_MESSAGE_STREAM:}}")
    private String postmarkMessageStream;

    @Value("${app.mail.gmail.client-id:${GMAIL_CLIENT_ID:}}")
    private String gmailClientId;

    @Value("${app.mail.gmail.client-secret:${GMAIL_CLIENT_SECRET:}}")
    private String gmailClientSecret;

    @Value("${app.mail.gmail.refresh-token:${GMAIL_REFRESH_TOKEN:}}")
    private String gmailRefreshToken;

    @Value("${app.mail.gmail.from:${MAIL_FROM:no-reply@biasharahub.local}}")
    private String gmailFrom;

    @Bean
    public GmailOAuthConfig gmailOAuthConfig() {
        return GmailOAuthConfig.builder()
                .clientId(blankToNull(gmailClientId))
                .clientSecret(blankToNull(gmailClientSecret))
                .refreshToken(blankToNull(gmailRefreshToken))
                .fromEmail(gmailFrom != null && !gmailFrom.isBlank() ? gmailFrom : "no-reply@biasharahub.local")
                .build();
    }

    /**
     * Default sender:
     * - Postmark when configured (recommended)
     * - else Gmail OAuth when configured
     * - else no-op
     *
     * Wired into MultiTenantEmailSender.
     */
    @Bean("defaultEmailSender")
    public EmailSender defaultEmailSender(GmailOAuthEmailSender gmailOAuthEmailSender, ObjectMapper objectMapper) {
        PostmarkEmailSender postmark = new PostmarkEmailSender(
                blankToNull(postmarkServerToken),
                postmarkFrom,
                blankToNull(postmarkMessageStream),
                objectMapper
        );
        if (postmark.isAvailable()) {
            return postmark;
        }
        if (gmailOAuthEmailSender.isAvailable()) {
            return gmailOAuthEmailSender;
        }
        return new NoOpEmailSender();
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }
}
