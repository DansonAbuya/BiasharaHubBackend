package com.biasharahub.service;

import com.biasharahub.entity.Order;
import com.biasharahub.entity.Payment;
import com.biasharahub.entity.Shipment;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Sends WhatsApp notifications for order, payment, and shipment events via Twilio.
 * Recipient is the customer (order user). If the user has no phone number, the notification is skipped.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppNotificationService {

    private final WhatsAppClient client;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;

    public void notifyOrderCreated(Order order) {
        orderRepository.findCustomerPhoneByOrderId(order.getOrderId()).filter(p -> p != null && !p.isBlank())
                .ifPresent(phone -> {
                    String body = String.format(
                            "BiasharaHub: Your order #%s has been placed. Total: %s. Complete payment to confirm.",
                            order.getOrderNumber(),
                            order.getTotalAmount());
                    client.sendMessage(phone, body);
                });
    }

    public void notifyPaymentRequested(Order order, Payment payment) {
        orderRepository.findCustomerPhoneByOrderId(order.getOrderId()).filter(p -> p != null && !p.isBlank())
                .ifPresent(phone -> {
                    String body = String.format(
                            "BiasharaHub: Pay KES %s for order #%s via M-Pesa to confirm. Check your phone for the prompt.",
                            payment.getAmount(),
                            order.getOrderNumber());
                    client.sendMessage(phone, body);
                });
    }

    public void notifyPaymentCompleted(UUID orderId, UUID paymentId) {
        orderRepository.findById(orderId).flatMap(o -> orderRepository.findCustomerPhoneByOrderId(o.getOrderId()))
                .filter(p -> p != null && !p.isBlank())
                .ifPresent(phone -> orderRepository.findById(orderId).ifPresent(order -> {
                    String body = String.format(
                            "BiasharaHub: Payment received for order #%s. Your order is confirmed. We'll notify you when it ships.",
                            order.getOrderNumber());
                    client.sendMessage(phone, body);
                }));
    }

    public void notifyShipmentUpdated(Shipment shipment) {
        UUID orderId = shipmentRepository.findOrderIdByShipmentId(shipment.getShipmentId()).orElse(null);
        if (orderId == null) return;
        orderRepository.findCustomerPhoneByOrderId(orderId).filter(p -> p != null && !p.isBlank())
                .ifPresent(phone -> orderRepository.findById(orderId).ifPresent(order -> {
                    String status = shipment.getStatus() != null ? shipment.getStatus() : "";
                    String body;
                    if ("DELIVERED".equalsIgnoreCase(status) || "COLLECTED".equalsIgnoreCase(status)) {
                        body = String.format(
                                "BiasharaHub: Delivered! Your order #%s has been delivered. Thank you for shopping with us!",
                                order.getOrderNumber());
                    } else if ("IN_TRANSIT".equalsIgnoreCase(status) || "OUT_FOR_DELIVERY".equalsIgnoreCase(status)) {
                        body = String.format(
                                "BiasharaHub: Out for Delivery – Order #%s is on the way.",
                                order.getOrderNumber());
                    } else if ("CREATED".equalsIgnoreCase(status)) {
                        String otpInfo = shipment.getOtpCode() != null
                                ? String.format(" Your delivery OTP: %s.", shipment.getOtpCode())
                                : "";
                        body = String.format(
                                "BiasharaHub: Order Shipped – Order #%s has been dispatched.%s",
                                order.getOrderNumber(),
                                otpInfo);
                    } else {
                        body = String.format(
                                "BiasharaHub: Order #%s – %s.",
                                order.getOrderNumber(),
                                status);
                    }
                    client.sendMessage(phone, body);
                }));
    }
}
