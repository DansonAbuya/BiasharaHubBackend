package com.biasharahub.config;

import com.biasharahub.entity.Order;
import com.biasharahub.service.OrderEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Ensures an OrderEventPublisher bean always exists. When neither InProcessOrderEventPublisher
 * nor KafkaOrderEventPublisher is created (e.g. condition evaluation order or Kafka excluded),
 * this fallback provides a no-op publisher so ReconciliationService and others can start.
 */
@Configuration
public class OrderEventPublisherConfig {

    @Bean
    @ConditionalOnMissingBean(OrderEventPublisher.class)
    public OrderEventPublisher noOpOrderEventPublisher() {
        return new OrderEventPublisher() {
            @Override
            public void orderCreated(Order order) {}

            @Override
            public void paymentCompleted(UUID orderId, UUID paymentId) {}
        };
    }
}
