package com.biasharahub.service;

import com.biasharahub.config.TenantContext;
import com.biasharahub.entity.Order;
import com.biasharahub.messaging.OrderCreatedEvent;
import com.biasharahub.messaging.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.order-created:orders.created}")
    private String orderCreatedTopic;

    @Value("${app.kafka.topics.payment-completed:payments.completed}")
    private String paymentCompletedTopic;

    @Override
    public void orderCreated(Order order) {
        try {
            String tenantSchema = TenantContext.getTenantSchema();
            if (tenantSchema == null) tenantSchema = "tenant_default";
            OrderCreatedEvent event = OrderCreatedEvent.builder()
                    .orderId(order.getOrderId())
                    .orderNumber(order.getOrderNumber())
                    .tenantSchema(tenantSchema)
                    .customerId(order.getUser().getUserId())
                    .total(order.getTotalAmount())
                    .status(order.getOrderStatus())
                    .build();
            kafkaTemplate.send(orderCreatedTopic, order.getOrderId().toString(), event);
            log.debug("Published order.created for order {}", order.getOrderId());
        } catch (Exception e) {
            log.warn("Failed to publish order.created for order {}: {}", order.getOrderId(), e.getMessage());
        }
    }

    @Override
    public void paymentCompleted(UUID orderId, UUID paymentId) {
        try {
            String tenantSchema = TenantContext.getTenantSchema();
            if (tenantSchema == null) tenantSchema = "tenant_default";
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                    .orderId(orderId)
                    .paymentId(paymentId)
                    .tenantSchema(tenantSchema)
                    .build();
            kafkaTemplate.send(paymentCompletedTopic, orderId.toString(), event);
            log.debug("Published payment.completed for order {}", orderId);
        } catch (Exception e) {
            log.warn("Failed to publish payment.completed for order {}: {}", orderId, e.getMessage());
        }
    }
}
