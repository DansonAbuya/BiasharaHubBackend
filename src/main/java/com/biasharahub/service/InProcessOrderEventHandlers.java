package com.biasharahub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Handlers for order/payment events when using in-process async (no Kafka/RabbitMQ).
 * Runs in a separate thread so the API returns immediately. Extend here to send email, create shipment, etc.
 * Accepts IDs and primitives only so the async thread does not touch detached entities.
 */
@Component
@Slf4j
public class InProcessOrderEventHandlers {

    @Async
    public void onOrderCreated(UUID orderId, String orderNumber, UUID customerId, BigDecimal total) {
        try {
            log.info("In-process: order created orderId={}, orderNumber={}, customerId={}, total={}",
                    orderId, orderNumber, customerId, total);
            // TODO: e.g. send order confirmation email (load order/customer from DB by id), notify warehouse, update cache
        } catch (Exception e) {
            log.warn("In-process order.created handler failed for order {}: {}", orderId, e.getMessage());
        }
    }

    @Async
    public void onPaymentCompleted(UUID orderId, UUID paymentId) {
        try {
            log.info("In-process: payment completed orderId={}, paymentId={}", orderId, paymentId);
            // TODO: e.g. update order status to confirmed, create shipment, send receipt email
        } catch (Exception e) {
            log.warn("In-process payment.completed handler failed for order {}: {}", orderId, e.getMessage());
        }
    }
}
