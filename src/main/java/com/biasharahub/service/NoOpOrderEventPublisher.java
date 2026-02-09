package com.biasharahub.service;

import com.biasharahub.entity.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnMissingBean(OrderEventPublisher.class)
public class NoOpOrderEventPublisher implements OrderEventPublisher {

    @Override
    public void orderCreated(Order order) {
        // No-op; wire RabbitMQ/Kafka producer to publish order.created
    }

    @Override
    public void paymentCompleted(UUID orderId, UUID paymentId) {
        // No-op; wire RabbitMQ/Kafka producer to publish payment.completed
    }
}
