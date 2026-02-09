package com.biasharahub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Enables Kafka auto-configuration only when app.kafka.enabled=true.
 * When disabled, KafkaAutoConfiguration is excluded at application level so no broker connection is attempted.
 */
@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Import(KafkaAutoConfiguration.class)
@EnableKafka
public class KafkaConfig {
}
