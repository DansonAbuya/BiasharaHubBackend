package com.biasharahub.service;

import com.biasharahub.dto.response.LoginResponse;
import com.biasharahub.dto.response.UserDto;
import com.biasharahub.entity.User;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * OAuth 2.0 as second factor: build Google auth URL and handle callback (exchange code, verify email, issue JWTs).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2TwoFactorService {

    private static final String REGISTRATION_ID = "google";
    /** Placeholder when GOOGLE_CLIENT_ID is not set; app starts but OAuth2 2FA is disabled. */
    private static final String CLIENT_ID_DISABLED = "disabled";

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final Pending2FAStore pending2FAStore;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.oauth2.backend-base-url:http://localhost:5050/api}")
    private String backendBaseUrl;

    @Value("${app.oauth2.frontend-redirect-uri:http://localhost:3000/auth/callback}")
    private String frontendRedirectUri;

    /**
     * Build the Google authorization URL for 2FA. Empty when Google OAuth2 is not configured.
     */
    public Optional<String> buildAuthorizationUrl(String stateToken) {
        ClientRegistration reg = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
        if (reg == null || reg.getClientId() == null || reg.getClientId().isBlank() || CLIENT_ID_DISABLED.equals(reg.getClientId())) {
            return Optional.empty();
        }
        String redirectUri = backendBaseUrl + "/auth/oauth2/callback";
        String scope = String.join(" ", reg.getScopes());
        String url = reg.getProviderDetails().getAuthorizationUri() +
                "?response_type=code" +
                "&client_id=" + URLEncoder.encode(reg.getClientId(), StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8) +
                "&state=" + URLEncoder.encode(stateToken, StandardCharsets.UTF_8) +
                "&access_type=online";
        return Optional.of(url);
    }

    /**
     * Handle OAuth2 callback: exchange code for token, get user email, validate against pending 2FA, issue our JWTs.
     */
    public Optional<LoginResponse> handleCallback(String code, String state) {
        Optional<Pending2FA> pending = pending2FAStore.consume(state);
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        Pending2FA p = pending.get();

        ClientRegistration reg = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
        if (reg == null || reg.getClientId() == null || CLIENT_ID_DISABLED.equals(reg.getClientId())) {
            return Optional.empty();
        }

        String redirectUri = backendBaseUrl + "/auth/oauth2/callback";
        String accessToken = exchangeCodeForToken(code, redirectUri, reg);
        if (accessToken == null) {
            return Optional.empty();
        }

        String oauthEmail = fetchUserEmail(accessToken, reg);
        if (oauthEmail == null || !oauthEmail.equalsIgnoreCase(p.email())) {
            log.warn("OAuth2 2FA email mismatch: expected {} got {}", p.email(), oauthEmail);
            return Optional.empty();
        }

        return userRepository.findById(p.userId())
                .map(user -> {
                    String token = jwtService.generateToken(user.getUserId(), user.getEmail(), user.getRole());
                    String refreshToken = jwtService.generateRefreshToken(user.getUserId(), user.getEmail(), user.getRole());
                    return LoginResponse.builder()
                            .token(token)
                            .refreshToken(refreshToken)
                            .user(toUserDto(user))
                            .requiresTwoFactor(false)
                            .build();
                });
    }

    public String getFrontendRedirectUri() {
        return frontendRedirectUri;
    }

    private String exchangeCodeForToken(String code, String redirectUri, ClientRegistration reg) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", reg.getClientId());
        body.add("client_secret", reg.getClientSecret());
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                reg.getProviderDetails().getTokenUri(),
                new HttpEntity<>(body, headers),
                Map.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().containsKey("access_token")) {
            return (String) response.getBody().get("access_token");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String fetchUserEmail(String accessToken, ClientRegistration reg) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    reg.getProviderDetails().getUserInfoEndpoint().getUri(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("email");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch OAuth2 userinfo: {}", e.getMessage());
        }
        return null;
    }

    private static UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .businessId(user.getBusinessId() != null ? user.getBusinessId().toString() : null)
                .businessName(user.getBusinessName())
                .verificationStatus(user.getVerificationStatus())
                .sellerTier(user.getSellerTier())
                .applyingForTier(user.getApplyingForTier())
                // Service provider fields
                .serviceProviderStatus(user.getServiceProviderStatus())
                .serviceDeliveryType(user.getServiceDeliveryType())
                .build();
    }
}
