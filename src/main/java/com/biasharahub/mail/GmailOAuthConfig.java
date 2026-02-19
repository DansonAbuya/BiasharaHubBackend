package com.biasharahub.mail;

import lombok.Builder;
import lombok.Value;

/**
 * Gmail OAuth2 credentials. Per-tenant or default. Refresh token is long-lived.
 */
@Value
@Builder
public class GmailOAuthConfig {

    String clientId;
    String clientSecret;
    String refreshToken;
    /** Sender email (e.g. no-reply@yourdomain.com) - must match the Gmail account that authorized the app */
    String fromEmail;

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && refreshToken != null && !refreshToken.isBlank();
    }
}
