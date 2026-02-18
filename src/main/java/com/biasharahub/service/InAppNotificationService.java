package com.biasharahub.service;

import com.biasharahub.entity.Notification;
import com.biasharahub.entity.Order;
import com.biasharahub.entity.Payment;
import com.biasharahub.entity.Shipment;
import com.biasharahub.entity.User;
import com.biasharahub.entity.OrderItem;
import com.biasharahub.repository.NotificationRepository;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * In-app/platform notifications persisted to the tenant database so users can view notifications
 * inside the BiasharaHub UI.
 *
 * These mirror key WhatsApp notifications (order, payment, shipment), but are always available
 * even when WhatsApp is disabled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public void notifyOrderCreated(Order order) {
        User customer = order.getUser();
        if (customer == null) return;
        saveNotification(customer, "order",
                "Order placed",
                "Your order " + order.getOrderNumber() + " has been placed. Complete payment to confirm.",
                "/dashboard/orders");
    }

    /**
     * Notify the seller (owner + staff) when a new order is placed for their shop.
     * This mirrors the customer "order placed" notification but is targeted at the seller side.
     */
    public void notifySellerOrderCreated(Order order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }
        OrderItem firstItem = order.getItems().get(0);
        if (firstItem == null || firstItem.getProduct() == null || firstItem.getProduct().getBusinessId() == null) {
            return;
        }
        java.util.UUID businessId = firstItem.getProduct().getBusinessId();

        java.util.List<User> owners = userRepository.findByRoleAndBusinessId("owner", businessId);
        java.util.List<User> staff = userRepository.findByRoleAndBusinessId("staff", businessId);
        if ((owners == null || owners.isEmpty()) && (staff == null || staff.isEmpty())) {
            return;
        }

        String customerName = order.getUser() != null && order.getUser().getName() != null
                ? order.getUser().getName()
                : "a customer";
        String title = "New order received";
        String message = "You have received a new order " + order.getOrderNumber()
                + " from " + customerName + ".";
        String actionUrl = "/dashboard/orders";

        for (User u : owners) {
            saveNotification(u, "order", title, message, actionUrl);
        }
        for (User u : staff) {
            saveNotification(u, "order", title, message, actionUrl);
        }
    }

    public void notifyPaymentRequested(Order order, Payment payment) {
        User customer = order.getUser();
        if (customer == null) return;
        saveNotification(customer, "payment",
                "Payment requested",
                "We sent an M-Pesa prompt for order " + order.getOrderNumber() + ". Complete the payment on your phone.",
                "/dashboard/orders");
    }

    public void notifyPaymentCompleted(UUID orderId, UUID paymentId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            User customer = order.getUser();
            if (customer == null) return;
            saveNotification(customer, "payment",
                    "Payment received",
                    "We received your payment for order " + order.getOrderNumber() + ". Your order is now confirmed.",
                    "/dashboard/orders");
        });
    }

    public void notifyShipmentUpdated(Shipment shipment) {
        Order order = shipment.getOrder();
        if (order == null || order.getUser() == null) return;
        User customer = order.getUser();
        String status = shipment.getStatus() != null ? shipment.getStatus() : "";
        String title;
        String message;
        if ("DELIVERED".equalsIgnoreCase(status) || "COLLECTED".equalsIgnoreCase(status)) {
            title = "Delivery completed";
            message = "Your order " + order.getOrderNumber() + " has been delivered. Thank you for shopping with us.";
        } else if ("IN_TRANSIT".equalsIgnoreCase(status) || "SHIPPED".equalsIgnoreCase(status) || "OUT_FOR_DELIVERY".equalsIgnoreCase(status)) {
            title = "Order dispatched";
            message = buildDispatchMessage(order.getOrderNumber(), shipment);
        } else if ("CREATED".equalsIgnoreCase(status)) {
            return;
        } else {
            title = "Shipment update";
            message = "Your order " + order.getOrderNumber() + " shipment status is now: " + status + ".";
        }
        saveNotification(customer, "shipment", title, message, "/dashboard/orders");
    }

    private String buildDispatchMessage(String orderNumber, Shipment s) {
        StringBuilder sb = new StringBuilder("Your order " + orderNumber + " has been dispatched.");
        String mode = s.getDeliveryMode() != null ? s.getDeliveryMode() : "SELLER_SELF";
        if ("COURIER".equalsIgnoreCase(mode) && s.getCourierService() != null && !s.getCourierService().isBlank()) {
            sb.append(" Delivery by: ").append(s.getCourierService());
            if (s.getTrackingNumber() != null && !s.getTrackingNumber().isBlank()) {
                sb.append(". Tracking: ").append(s.getTrackingNumber());
            }
        } else if ((s.getRiderVehicle() != null && !s.getRiderVehicle().isBlank()) || (s.getRiderName() != null && !s.getRiderName().isBlank())) {
            if (s.getRiderName() != null && !s.getRiderName().isBlank()) sb.append(" Driver: ").append(s.getRiderName());
            if (s.getRiderVehicle() != null && !s.getRiderVehicle().isBlank()) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') sb.append(". ");
                sb.append("Vehicle/Bike reg: ").append(s.getRiderVehicle());
            }
        } else if (s.getCourierService() != null && !s.getCourierService().isBlank()) {
            sb.append(" Delivery by: ").append(s.getCourierService());
        } else if ("CUSTOMER_PICKUP".equalsIgnoreCase(mode)) {
            sb.append(" Ready for pickup.");
            if (s.getPickupLocation() != null && !s.getPickupLocation().isBlank()) {
                sb.append(" Location: ").append(s.getPickupLocation());
            }
        }
        sb.append(".");
        return sb.toString();
    }

    private void saveNotification(User user, String type, String title, String message, String actionUrl) {
        try {
            Notification notif = Notification.builder()
                    .user(user)
                    .type(type)
                    .title(title)
                    .message(message)
                    .actionUrl(actionUrl)
                    .read(false)
                    .build();
            notificationRepository.save(notif);
        } catch (Exception e) {
            log.warn("Failed to save in-app notification for user {}: {}", user.getUserId(), e.getMessage());
        }
    }
}

