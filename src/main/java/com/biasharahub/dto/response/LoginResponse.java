package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String refreshToken;
    private UserDto user;
    private boolean requiresTwoFactor;
    /** When requiresTwoFactor is true and 2FA is OAuth: state token for the OAuth flow */
    private String stateToken;
    /** When requiresTwoFactor is true and 2FA is OAuth: URL to redirect user to (e.g. Google sign-in) */
    private String authorizationUrl;
}
