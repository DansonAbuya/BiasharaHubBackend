package com.biasharahub.mail;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production-ready Gmail sender using OAuth2 (refresh token). Thread-safe; token is refreshed on a schedule.
 */
@Slf4j
@Component
public class GmailOAuthEmailSender implements EmailSender {

    private final GmailOAuthConfig config;
    private final AtomicReference<Gmail> gmailRef = new AtomicReference<>();
    private volatile boolean available;

    public GmailOAuthEmailSender(GmailOAuthConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        if (!config.isConfigured()) {
            log.info("Gmail OAuth not configured (GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET, GMAIL_REFRESH_TOKEN). Gmail sender disabled.");
            available = false;
            return;
        }
        try {
            refreshClient();
            available = true;
            log.info("Gmail OAuth email sender initialized (from: {})", config.getFromEmail());
        } catch (Exception e) {
            log.warn("Gmail OAuth email sender failed to initialize: {}", e.getMessage());
            available = false;
        }
    }

    @PreDestroy
    public void destroy() {
        gmailRef.set(null);
        available = false;
    }

    /**
     * Refresh the Gmail client with a new access token (call from scheduler or before send).
     */
    public void refreshAccessToken() {
        if (!config.isConfigured()) return;
        try {
            refreshClient();
        } catch (Exception e) {
            log.warn("Gmail OAuth token refresh failed: {}", e.getMessage());
        }
    }

    private void refreshClient() throws Exception {
        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(config.getClientId())
                .setClientSecret(config.getClientSecret())
                .setRefreshToken(config.getRefreshToken())
                .build();
        credentials.refreshIfExpired();

        Gmail gmail = new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("BiasharaHub")
                .build();
        gmailRef.set(gmail);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void send(EmailMessage message) {
        if (!available) {
            throw new EmailException("Gmail OAuth sender not available (check configuration and token refresh)");
        }
        Gmail gmail = gmailRef.get();
        if (gmail == null) {
            refreshAccessToken();
            gmail = gmailRef.get();
        }
        if (gmail == null) {
            throw new EmailException("Gmail client not initialized");
        }
        try {
            String raw = buildRawMessage(message);
            Message gmailMessage = new Message();
            gmailMessage.setRaw(raw);
            gmail.users().messages().send("me", gmailMessage).execute();
            log.debug("Email sent via Gmail OAuth to {}", message.getTo());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("401") || msg.contains("invalid_grant")) {
                log.warn("Gmail OAuth token may be expired, refreshing");
                refreshAccessToken();
                try {
                    String raw = buildRawMessage(message);
                    Message gmailMessage = new Message();
                    gmailMessage.setRaw(raw);
                    gmailRef.get().users().messages().send("me", gmailMessage).execute();
                    return;
                } catch (Exception e2) {
                    throw new EmailException("Failed to send after token refresh", e2);
                }
            }
            throw new EmailException("Gmail send failed: " + e.getMessage(), e);
        }
    }

    private String buildRawMessage(EmailMessage message) throws Exception {
        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage mime = new MimeMessage(session);
        String from = message.getFrom() != null ? message.getFrom() : config.getFromEmail();
        mime.setFrom(new InternetAddress(from));
        if (message.getTo() != null && !message.getTo().isEmpty()) {
            mime.setRecipients(jakarta.mail.Message.RecipientType.TO,
                    InternetAddress.parse(String.join(",", message.getTo())));
        }
        if (message.getSubject() != null) {
            mime.setSubject(message.getSubject(), StandardCharsets.UTF_8.name());
        }
        if (message.getHtmlBody() != null && !message.getHtmlBody().isBlank()) {
            mime.setContent(message.getHtmlBody(), "text/html; charset=UTF-8");
        } else {
            mime.setText(message.getTextBody() != null ? message.getTextBody() : "", StandardCharsets.UTF_8.name());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mime.writeTo(out);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }
}
