package com.biasharahub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Provides an empty ClientRegistrationRepository when Spring Boot does not create one
 * (e.g. when no OAuth2 registration is configured). Allows the app to start with email-only 2FA;
 * OAuth2TwoFactorService will see no "google" registration and return empty/503 where appropriate.
 */
@Configuration
public class OptionalOAuth2ClientConfig {

    @Bean
    @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new EmptyClientRegistrationRepository();
    }
}
