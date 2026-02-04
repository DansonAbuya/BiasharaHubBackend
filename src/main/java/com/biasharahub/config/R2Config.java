package com.biasharahub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Configuration for Cloudflare R2 (S3-compatible) object storage.
 * Beans are only created when r2.enabled=true and endpoint/credentials are set.
 */
@Configuration
public class R2Config {

    @Bean
    @ConfigurationProperties(prefix = "r2")
    public R2Properties r2Properties() {
        return new R2Properties();
    }

    @Bean
    @ConditionalOnProperty(name = "r2.enabled", havingValue = "true")
    public S3Client r2S3Client(R2Properties props) {
        if (props.getEndpoint() == null || props.getEndpoint().isBlank()
                || props.getAccessKeyId() == null || props.getAccessKeyId().isBlank()
                || props.getSecretAccessKey() == null || props.getSecretAccessKey().isBlank()) {
            throw new IllegalStateException("R2 is enabled but r2.endpoint, r2.access-key-id, and r2.secret-access-key must be set.");
        }
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint().replaceFirst("/+$", "")))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKeyId(), props.getSecretAccessKey())))
                .region(Region.of("auto"))
                .serviceConfiguration(s3Config)
                .build();
    }

    @lombok.Getter
    @lombok.Setter
    public static class R2Properties {
        private boolean enabled;
        private String bucket = "biasharahub-products";
        private String endpoint;
        private String accessKeyId;
        private String secretAccessKey;
        private String publicUrl;
    }
}
