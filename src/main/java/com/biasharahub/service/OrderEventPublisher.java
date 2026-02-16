package com.biasharahub.service;

import com.biasharahub.entity.Order;

import java.util.UUID;

/**
 * Publishes order-related events to a message queue (RabbitMQ/Kafka) for async processing
 * (inventory updates, notifications, shipment initiation). No-op by default.
 */
public interface OrderEventPublisher {

    void orderCreated(Order order);

    void paymentCompleted(UUID orderId, UUID paymentId);
}
