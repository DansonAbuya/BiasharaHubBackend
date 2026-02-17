package com.biasharahub.service;

import com.biasharahub.entity.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes order/payment events in-process using @Async handlers (no Kafka or RabbitMQ).
 * Active when Kafka is disabled and in-process messaging is enabled:
 *   app.kafka.enabled=false (or missing) AND app.messaging.in-process.enabled=true.
 */
@Component
@ConditionalOnExpression(
        "#{environment.getProperty('app.messaging.in-process.enabled', 'true').equals('true') && !environment.getProperty('app.kafka.enabled', 'false').equals('true')}"
)
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
