package com.biasharahub.service;

import com.biasharahub.entity.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes order/payment events in-process using @Async handlers (no Kafka or RabbitMQ).
 * Active when Kafka is disabled (default). When app.kafka.enabled=true, KafkaOrderEventPublisher is used instead.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class InProcessOrderEventPublisher implements OrderEventPublisher {

    private final InProcessOrderEventHandlers handlers;

    public InProcessOrderEventPublisher(InProcessOrderEventHandlers handlers) {
        this.handlers = handlers;
    }

    @Override
    public void orderCreated(Order order) {
        UUID customerId = order.getUser() != null ? order.getUser().getUserId() : null;
        handlers.onOrderCreated(order.getOrderId(), order.getOrderNumber(), customerId, order.getTotalAmount());
    }

    @Override
    public void paymentCompleted(UUID orderId, UUID paymentId) {
        handlers.onPaymentCompleted(orderId, paymentId);
    }
}
