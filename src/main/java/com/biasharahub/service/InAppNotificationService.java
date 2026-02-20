package com.biasharahub.service;

import com.biasharahub.entity.Notification;
import com.biasharahub.entity.Order;
import com.biasharahub.entity.Payment;
import com.biasharahub.entity.Product;
import com.biasharahub.entity.ServiceAppointment;
import com.biasharahub.entity.Shipment;
import com.biasharahub.entity.User;
import com.biasharahub.entity.OrderItem;
import com.biasharahub.repository.NotificationRepository;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
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

        List<User> owners = userRepository.findByRoleAndBusinessId("owner", businessId);
        List<User> staff = userRepository.findByRoleAndBusinessId("staff", businessId);
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

        List<User> ownerList = owners != null ? owners : Collections.emptyList();
        List<User> staffList = staff != null ? staff : Collections.emptyList();
        for (User u : ownerList) {
            saveNotification(u, "order", title, message, actionUrl);
        }
        for (User u : staffList) {
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

    /** Notify seller (owner + staff) when an order is paid so they can fulfill. */
    public void notifySellerPaymentCompleted(Order order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) return;
        OrderItem firstItem = order.getItems().get(0);
        if (firstItem == null || firstItem.getProduct() == null || firstItem.getProduct().getBusinessId() == null) return;
        UUID businessId = firstItem.getProduct().getBusinessId();
        List<User> owners = userRepository.findByRoleAndBusinessId("owner", businessId);
        List<User> staff = userRepository.findByRoleAndBusinessId("staff", businessId);
        String title = "Order paid";
        String message = "Order " + order.getOrderNumber() + " has been paid. You can now prepare and dispatch.";
        String actionUrl = "/dashboard/orders";
        List<User> ownerList = owners != null ? owners : Collections.emptyList();
        List<User> staffList = staff != null ? staff : Collections.emptyList();
        for (User u : ownerList) saveNotification(u, "payment", title, message, actionUrl);
        for (User u : staffList) saveNotification(u, "payment", title, message, actionUrl);
    }

    /** Notify seller when product stock is running low (e.g. <= 10). */
    public void notifySellerLowStock(Product product) {
        if (product == null || product.getBusinessId() == null) return;
        int qty = product.getQuantity() != null ? product.getQuantity() : 0;
        List<User> owners = userRepository.findByRoleAndBusinessId("owner", product.getBusinessId());
        List<User> staff = userRepository.findByRoleAndBusinessId("staff", product.getBusinessId());
        String title = "Low stock alert";
        String message = "Product \"" + (product.getName() != null ? product.getName() : "Unknown") + "\" is running low (" + qty + " left). Consider restocking.";
        String actionUrl = "/dashboard/products";
        List<User> ownerList = owners != null ? owners : Collections.emptyList();
        List<User> staffList = staff != null ? staff : Collections.emptyList();
        for (User u : ownerList) saveNotification(u, "stock", title, message, actionUrl);
        for (User u : staffList) saveNotification(u, "stock", title, message, actionUrl);
    }

    /** Notify seller when a customer opens a dispute on an order. */
    public void notifySellerDisputeCreated(Order order, String disputeType) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) return;
        OrderItem firstItem = order.getItems().get(0);
        if (firstItem == null || firstItem.getProduct() == null || firstItem.getProduct().getBusinessId() == null) return;
        UUID businessId = firstItem.getProduct().getBusinessId();
        List<User> owners = userRepository.findByRoleAndBusinessId("owner", businessId);
        List<User> staff = userRepository.findByRoleAndBusinessId("staff", businessId);
        String title = "Dispute opened";
        String message = "A customer opened a dispute for order " + order.getOrderNumber() + (disputeType != null && !disputeType.isBlank() ? " (" + disputeType + ")." : ".");
        String actionUrl = "/dashboard/admin/disputes";
        List<User> ownerList = owners != null ? owners : Collections.emptyList();
        List<User> staffList = staff != null ? staff : Collections.emptyList();
        for (User u : ownerList) saveNotification(u, "dispute", title, message, actionUrl);
        for (User u : staffList) saveNotification(u, "dispute", title, message, actionUrl);
    }

    /** Notify seller when an order is cancelled. */
    public void notifySellerOrderCancelled(Order order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) return;
        OrderItem firstItem = order.getItems().get(0);
        if (firstItem == null || firstItem.getProduct() == null || firstItem.getProduct().getBusinessId() == null) return;
        UUID businessId = firstItem.getProduct().getBusinessId();
        List<User> owners = userRepository.findByRoleAndBusinessId("owner", businessId);
        List<User> staff = userRepository.findByRoleAndBusinessId("staff", businessId);
        String title = "Order cancelled";
        String message = "Order " + order.getOrderNumber() + " was cancelled. Inventory has been restored.";
        String actionUrl = "/dashboard/orders";
        List<User> ownerList = owners != null ? owners : Collections.emptyList();
        List<User> staffList = staff != null ? staff : Collections.emptyList();
        for (User u : ownerList) saveNotification(u, "order", title, message, actionUrl);
        for (User u : staffList) saveNotification(u, "order", title, message, actionUrl);
    }

    // ---------- Service bookings (BiasharaHub Services) ----------

    /** Notify customer that their service appointment was booked. */
    public void notifyServiceBookingCreated(ServiceAppointment appointment) {
        if (appointment == null || appointment.getUser() == null) return;
        String serviceName = appointment.getService() != null ? appointment.getService().getName() : "your service";
        String dateTime = appointment.getRequestedDate() != null ? appointment.getRequestedDate().toString() : "";
        if (appointment.getRequestedTime() != null) dateTime += " at " + appointment.getRequestedTime();
        saveNotification(appointment.getUser(), "service_booking",
                "Appointment booked",
                "Your appointment for \"" + serviceName + "\" on " + dateTime + " has been booked. Pay to confirm.",
                "/dashboard/services");
    }

    /** Notify provider (owner + staff) when a new service appointment is booked. */
    public void notifyProviderServiceBookingCreated(ServiceAppointment appointment) {
        if (appointment == null || appointment.getService() == null || appointment.getService().getBusinessId() == null) return;
        UUID businessId = appointment.getService().getBusinessId();
        List<User> owners = userRepository.findByRoleAndBusinessId("owner", businessId);
        List<User> staff = userRepository.findByRoleAndBusinessId("staff", businessId);
        String customerName = appointment.getUser() != null && appointment.getUser().getName() != null
                ? appointment.getUser().getName()
                : (appointment.getUser() != null ? appointment.getUser().getEmail() : "a customer");
        String serviceName = appointment.getService().getName() != null ? appointment.getService().getName() : "your service";
        String dateTime = appointment.getRequestedDate() != null ? appointment.getRequestedDate().toString() : "";
        if (appointment.getRequestedTime() != null) dateTime += " at " + appointment.getRequestedTime();
        String title = "New service booking";
        String message = String.format("%s booked \"%s\" for %s. Confirm in the dashboard.", customerName, serviceName, dateTime);
        String actionUrl = "/dashboard/services";
        List<User> ownerList = owners != null ? owners : Collections.emptyList();
        List<User> staffList = staff != null ? staff : Collections.emptyList();
        for (User u : ownerList) saveNotification(u, "service_booking", title, message, actionUrl);
        for (User u : staffList) saveNotification(u, "service_booking", title, message, actionUrl);
    }

    /** Notify customer and provider that meeting link was sent (virtual service, booking confirmed). */
    public void notifyServiceMeetingLinkSent(ServiceAppointment appointment, String meetingLink, String meetingDetails) {
        if (appointment == null || meetingLink == null || meetingLink.isBlank()) return;
        String serviceName = appointment.getService() != null ? appointment.getService().getName() : "your service";
        String msg = "Your meeting link for \"" + serviceName + "\": " + meetingLink;
        if (meetingDetails != null && !meetingDetails.isBlank()) msg += " " + meetingDetails;
        if (appointment.getUser() != null) {
            saveNotification(appointment.getUser(), "service_booking", "Meeting link", msg, "/dashboard/services");
        }
        if (appointment.getService() != null && appointment.getService().getBusinessId() != null) {
            UUID businessId = appointment.getService().getBusinessId();
            List<User> owners = userRepository.findByRoleAndBusinessId("owner", businessId);
            List<User> staff = userRepository.findByRoleAndBusinessId("staff", businessId);
            String title = "Meeting link sent";
            String providerMsg = "Meeting link for \"" + serviceName + "\" with " + (appointment.getUser() != null && appointment.getUser().getName() != null ? appointment.getUser().getName() : "customer") + ": " + meetingLink;
            List<User> ownerList = owners != null ? owners : Collections.emptyList();
            List<User> staffList = staff != null ? staff : Collections.emptyList();
            for (User u : ownerList) saveNotification(u, "service_booking", title, providerMsg, "/dashboard/services");
            for (User u : staffList) saveNotification(u, "service_booking", title, providerMsg, "/dashboard/services");
        }
    }

    /** Notify customer when appointment status changes (confirmed, completed, cancelled). */
    public void notifyServiceBookingStatusUpdated(ServiceAppointment appointment) {
        if (appointment == null || appointment.getUser() == null) return;
        String serviceName = appointment.getService() != null ? appointment.getService().getName() : "your service";
        String status = appointment.getStatus() != null ? appointment.getStatus() : "";
        String title = "Appointment update";
        String message = "Your appointment for \"" + serviceName + "\" is now " + status + ".";
        saveNotification(appointment.getUser(), "service_booking", title, message, "/dashboard/services");
    }

    /** Notify customer that their booking payment was received. */
    public void notifyServiceBookingPaymentCompletedCustomer(ServiceAppointment appointment) {
        if (appointment == null || appointment.getUser() == null) return;
        String serviceName = appointment.getService() != null ? appointment.getService().getName() : "your service";
        saveNotification(appointment.getUser(), "payment",
                "Payment received",
                "We received your payment for \"" + serviceName + "\". Your booking is confirmed.",
                "/dashboard/services");
    }

    /** Notify provider (owner + staff) when a service booking is paid. */
    public void notifyServiceBookingPaymentCompletedProvider(ServiceAppointment appointment) {
        if (appointment == null || appointment.getService() == null || appointment.getService().getBusinessId() == null) return;
        UUID businessId = appointment.getService().getBusinessId();
        List<User> owners = userRepository.findByRoleAndBusinessId("owner", businessId);
        List<User> staff = userRepository.findByRoleAndBusinessId("staff", businessId);
        String serviceName = appointment.getService().getName() != null ? appointment.getService().getName() : "service";
        String customerName = appointment.getUser() != null && appointment.getUser().getName() != null
                ? appointment.getUser().getName()
                : "a customer";
        String title = "Booking payment received";
        String message = String.format("Payment received for \"%s\" from %s. See appointment in dashboard.", serviceName, customerName);
        String actionUrl = "/dashboard/services";
        List<User> ownerList = owners != null ? owners : Collections.emptyList();
        List<User> staffList = staff != null ? staff : Collections.emptyList();
        for (User u : ownerList) saveNotification(u, "payment", title, message, actionUrl);
        for (User u : staffList) saveNotification(u, "payment", title, message, actionUrl);
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

