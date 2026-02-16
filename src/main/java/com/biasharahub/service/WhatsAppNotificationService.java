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
                    } else if ("IN_TRANSIT".equalsIgnoreCase(status) || "OUT_FOR_DELIVERY".equalsIgnoreCase(status) || "SHIPPED".equalsIgnoreCase(status)) {
                        String details = buildDispatchDetails(shipment);
                        String otpInfo = (shipment.getOtpCode() != null && !shipment.getOtpCode().isBlank())
                                ? " Delivery OTP: " + shipment.getOtpCode() + " (for confirmation on receipt)."
                                : "";
                        body = String.format(
                                "BiasharaHub: Your order #%s has been dispatched.%s%s",
                                order.getOrderNumber(),
                                details.isEmpty() ? "" : " " + details,
                                otpInfo);
                    } else if ("CREATED".equalsIgnoreCase(status)) {
                        return;
                    } else {
                        body = String.format(
                                "BiasharaHub: Order #%s – %s.",
                                order.getOrderNumber(),
                                status);
                    }
                    client.sendMessage(phone, body);
                }));
    }

    private String buildDispatchDetails(Shipment s) {
        StringBuilder sb = new StringBuilder();
        String mode = s.getDeliveryMode() != null ? s.getDeliveryMode() : "SELLER_SELF";
        if ("COURIER".equalsIgnoreCase(mode) && s.getCourierService() != null && !s.getCourierService().isBlank()) {
            sb.append("Delivery by: ").append(s.getCourierService());
            if (s.getTrackingNumber() != null && !s.getTrackingNumber().isBlank()) {
                sb.append(". Tracking: ").append(s.getTrackingNumber());
            }
        } else if (("SELLER_SELF".equalsIgnoreCase(mode) || "RIDER_MARKETPLACE".equalsIgnoreCase(mode)) && (s.getRiderName() != null || s.getRiderVehicle() != null)) {
            if (s.getRiderName() != null && !s.getRiderName().isBlank()) sb.append("Driver: ").append(s.getRiderName());
            if (s.getRiderVehicle() != null && !s.getRiderVehicle().isBlank()) {
                if (sb.length() > 0) sb.append(". ");
                sb.append("Vehicle/Bike reg: ").append(s.getRiderVehicle());
            }
        } else if (s.getRiderVehicle() != null && !s.getRiderVehicle().isBlank()) {
            sb.append("Vehicle/Bike reg: ").append(s.getRiderVehicle());
        } else if (s.getCourierService() != null && !s.getCourierService().isBlank()) {
            sb.append("Delivery by: ").append(s.getCourierService());
        } else if ("CUSTOMER_PICKUP".equalsIgnoreCase(mode)) {
            sb.append("Ready for pickup");
            if (s.getPickupLocation() != null && !s.getPickupLocation().isBlank()) {
                sb.append(" at ").append(s.getPickupLocation());
            }
        }
        return sb.toString();
    }
}
