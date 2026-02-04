package com.biasharahub.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores pending OAuth2 2FA state (stateToken -> user) until the user completes the OAuth flow.
 */
public interface Pending2FAStore {

    /**
     * Store pending 2FA for a user. The stateToken is passed to the OAuth provider and returned in the callback.
     */
    void put(String stateToken, UUID userId, String email, Instant expiresAt);

    /**
     * Check if state token exists and is not expired (does not remove).
     */
    Optional<Pending2FA> peek(String stateToken);

    /**
     * Retrieve and remove pending 2FA by state token. Returns empty if not found or expired.
     */
    Optional<Pending2FA> consume(String stateToken);
}
