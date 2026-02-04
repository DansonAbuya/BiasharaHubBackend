package com.biasharahub.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes Gmail OAuth access token before expiry (~1h). Runs every 50 minutes so sends never block on expired token.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GmailOAuthTokenRefreshScheduler {

    private final GmailOAuthEmailSender gmailSender;

    @Scheduled(fixedDelayString = "${app.mail.gmail.refresh-interval-ms:3000000}") // 50 min default
    public void refreshToken() {
        if (!gmailSender.isAvailable()) {
            return;
        }
        try {
            gmailSender.refreshAccessToken();
            log.trace("Gmail OAuth token refresh completed");
        } catch (Exception e) {
            log.warn("Gmail OAuth scheduled refresh failed: {}", e.getMessage());
        }
    }
}
