package com.biasharahub.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Example Kafka consumers for order/payment events. Only active when app.kafka.enabled=true.
 * Replace or extend with real logic (e.g. send email, create shipment, update analytics).
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Slf4j
public class OrderEventKafkaListener {

    @KafkaListener(topics = "${app.kafka.topics.order-created:orders.created}", groupId = "${spring.kafka.consumer.group-id:biasharahub}")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Kafka received order.created: orderId={}, orderNumber={}, tenant={}, customerId={}, total={}",
                event.getOrderId(), event.getOrderNumber(), event.getTenantSchema(), event.getCustomerId(), event.getTotal());
        // TODO: e.g. send order confirmation email, notify warehouse, update cache
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-completed:payments.completed}", groupId = "${spring.kafka.consumer.group-id:biasharahub}")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Kafka received payment.completed: orderId={}, paymentId={}, tenant={}",
                event.getOrderId(), event.getPaymentId(), event.getTenantSchema());
        // TODO: e.g. update order status to confirmed, create shipment, send receipt email
    }
}
