package com.biasharahub.service;

import com.biasharahub.entity.Order;
import com.biasharahub.entity.Shipment;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Handlers for order/payment events when using in-process async (no Kafka/RabbitMQ).
 * Runs in a separate thread so the API returns immediately. Extend here to send email, create shipment, etc.
 * Accepts IDs and primitives only so the async thread does not touch detached entities.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InProcessOrderEventHandlers {

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final InAppNotificationService inAppNotificationService;

    @Async
    @Transactional
    public void onOrderCreated(UUID orderId, String orderNumber, UUID customerId, BigDecimal total) {
        try {
            log.info("In-process: order created orderId={}, orderNumber={}, customerId={}, total={}",
                    orderId, orderNumber, customerId, total);
            // Send WhatsApp event for order confirmation (if integration enabled)
            orderRepository.findById(orderId).ifPresent(order -> {
                try {
                    whatsAppNotificationService.notifyOrderCreated(order);
                } catch (Exception e) {
                    log.warn("Failed to send WhatsApp order-created notification for {}: {}", orderId, e.getMessage());
                }
                try {
                    inAppNotificationService.notifyOrderCreated(order);
                } catch (Exception e) {
                    log.warn("Failed to create in-app order-created notification for {}: {}", orderId, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("In-process order.created handler failed for order {}: {}", orderId, e.getMessage());
        }
    }

    @Async
    @Transactional
    public void onPaymentCompleted(UUID orderId, UUID paymentId) {
        try {
            log.info("In-process: payment completed orderId={}, paymentId={}", orderId, paymentId);

            orderRepository.findById(orderId).ifPresent(order -> {
                // 1) Ensure order is marked as confirmed once payment succeeds
                if (!"confirmed".equalsIgnoreCase(order.getOrderStatus())) {
                    order.setOrderStatus("confirmed");
                }

                // 2) Automatically create a shipment record if one does not already exist
                boolean hasExistingShipment = order.getShipments() != null && !order.getShipments().isEmpty();
                if (!hasExistingShipment) {
                    String deliveryMode = order.getDeliveryMode() != null ? order.getDeliveryMode() : "SELLER_SELF";
                    // Generate OTP for delivery confirmation when applicable
                    String otp = null;
                    if ("SELLER_SELF".equalsIgnoreCase(deliveryMode) ||
                            "CUSTOMER_PICKUP".equalsIgnoreCase(deliveryMode)) {
                        int code = (int) (Math.random() * 1_000_000);
                        otp = String.format("%06d", code);
                    }
                    Shipment shipment = Shipment.builder()
                            .order(order)
                            .deliveryMode(deliveryMode)
                            .status("CREATED")
                            .otpCode(otp)
                            .build();
                    shipment = shipmentRepository.save(shipment);
                    log.info("Created shipment {} for paid order {} – seller will add dispatch details and notify customer", shipment.getShipmentId(), orderId);
                    // Do NOT notify here – customer learns about dispatch when seller marks as dispatched with courier/vehicle details
                } else {
                    log.debug("Order {} already has a shipment, skipping auto-create", orderId);
                }

                orderRepository.save(order);
            });

            try {
                whatsAppNotificationService.notifyPaymentCompleted(orderId, paymentId);
            } catch (Exception e) {
                log.warn("Failed to send WhatsApp payment-completed notification for order {}: {}", orderId, e.getMessage());
            }
            try {
                inAppNotificationService.notifyPaymentCompleted(orderId, paymentId);
            } catch (Exception e) {
                log.warn("Failed to create in-app payment-completed notification for order {}: {}", orderId, e.getMessage());
            }
        } catch (Exception e) {
            log.warn("In-process payment.completed handler failed for order {}: {}", orderId, e.getMessage());
        }
    }
}
