package com.biasharahub.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.StringJoiner;

/**
 * Postmark Email API sender (REST only, no SMTP).
 * Sends via POST https://api.postmarkapp.com/email.
 *
 * Config via:
 * - app.mail.postmark.server-token (or POSTMARK_SERVER_TOKEN)
 * - app.mail.postmark.from (or MAIL_FROM)
 * - app.mail.postmark.message-stream (optional)
 */
@Slf4j
public class PostmarkEmailSender implements EmailSender {

    private static final URI DEFAULT_ENDPOINT = URI.create("https://api.postmarkapp.com/email");

    private final String serverToken;
    private final String from;
    private final String messageStream;
    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final URI endpoint;

    public PostmarkEmailSender(String serverToken, String from, String messageStream, ObjectMapper objectMapper) {
        this(serverToken, from, messageStream, objectMapper, DEFAULT_ENDPOINT);
    }

    public PostmarkEmailSender(String serverToken, String from, String messageStream, ObjectMapper objectMapper, URI endpoint) {
        this.serverToken = serverToken != null ? serverToken.trim() : "";
        this.from = from != null ? from.trim() : "";
        this.messageStream = messageStream != null && !messageStream.isBlank() ? messageStream.trim() : null;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.endpoint = endpoint != null ? endpoint : DEFAULT_ENDPOINT;
    }

    @Override
    public boolean isAvailable() {
        return !serverToken.isBlank() && !from.isBlank();
    }

    @Override
    public void send(EmailMessage message) {
        if (!isAvailable()) {
            throw new EmailException("Postmark sender not available (set POSTMARK_SERVER_TOKEN and MAIL_FROM)");
        }
        if (message == null) {
            throw new EmailException("EmailMessage is null");
        }
        if (message.getTo() == null || message.getTo().isEmpty()) {
            throw new EmailException("EmailMessage.to is required");
        }

        try {
            String payload = buildPayload(message);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(endpoint)
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-Postmark-Server-Token", serverToken)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = res.statusCode();
            if (code >= 200 && code < 300) {
                log.debug("Email sent via Postmark to {}", message.getTo());
                return;
            }
            throw new EmailException("Postmark send failed (HTTP " + code + "): " + res.body());
        } catch (EmailException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailException("Postmark send failed: " + e.getMessage(), e);
        }
    }

    private String buildPayload(EmailMessage message) throws Exception {
        String fromValue = (message.getFrom() != null && !message.getFrom().isBlank()) ? message.getFrom() : from;

        StringJoiner toJoiner = new StringJoiner(",");
        for (String t : message.getTo()) {
            if (t != null && !t.isBlank()) toJoiner.add(t.trim());
        }

        var node = objectMapper.createObjectNode();
        node.put("From", fromValue);
        node.put("To", toJoiner.toString());
        if (message.getSubject() != null) node.put("Subject", message.getSubject());
        if (message.getHtmlBody() != null && !message.getHtmlBody().isBlank()) {
            node.put("HtmlBody", message.getHtmlBody());
        }
        node.put("TextBody", message.getTextBody() != null ? message.getTextBody() : "");
        if (messageStream != null) node.put("MessageStream", messageStream);

        // CC/BCC if present
        if (message.getCc() != null && !message.getCc().isEmpty()) {
            StringJoiner ccJoiner = new StringJoiner(",");
            for (String c : message.getCc()) {
                if (c != null && !c.isBlank()) ccJoiner.add(c.trim());
            }
            node.put("Cc", ccJoiner.toString());
        }
        if (message.getBcc() != null && !message.getBcc().isEmpty()) {
            StringJoiner bccJoiner = new StringJoiner(",");
            for (String b : message.getBcc()) {
                if (b != null && !b.isBlank()) bccJoiner.add(b.trim());
            }
            node.put("Bcc", bccJoiner.toString());
        }

        return objectMapper.writeValueAsString(node);
    }
}

