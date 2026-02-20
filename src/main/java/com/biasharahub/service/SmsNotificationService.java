package com.biasharahub.service;

import com.biasharahub.entity.Order;
import com.biasharahub.entity.OrderItem;
import com.biasharahub.entity.Product;
import com.biasharahub.entity.ServiceAppointment;
import com.biasharahub.entity.User;
import com.biasharahub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sends SMS notifications for seller events (e.g. new order) via Twilio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsNotificationService {

    private final SmsClient smsClient;
    private final UserRepository userRepository;

    /**
     * Notify seller (owner + staff) via SMS when a new order is placed for their business.
     */
    public void notifySellerOrderCreated(Order order) {
        UUID businessId = getBusinessIdFromOrder(order);
        if (businessId == null) return;
        String customerName = order.getUser() != null && order.getUser().getName() != null
                ? order.getUser().getName()
                : "a customer";
        String body = String.format(
                "BiasharaHub: New order #%s from %s. Total: %s. Log in to process the order.",
                order.getOrderNumber(),
                customerName,
                order.getTotalAmount());
        for (User u : getSellerUsers(businessId)) {
            if (u.getPhone() != null && !u.getPhone().isBlank()) {
                smsClient.send(u.getPhone(), body);
            }
        }
    }

    private UUID getBusinessIdFromOrder(Order order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) return null;
        OrderItem first = order.getItems().get(0);
        if (first == null || first.getProduct() == null || first.getProduct().getBusinessId() == null) return null;
        return first.getProduct().getBusinessId();
    }

    private List<User> getSellerUsers(UUID businessId) {
        List<User> out = new ArrayList<>();
        List<User> owners = userRepository.findByRoleAndBusinessId("owner", businessId);
        List<User> staff = userRepository.findByRoleAndBusinessId("staff", businessId);
        if (owners != null) out.addAll(owners);
        if (staff != null) out.addAll(staff);
        return out;
    }

    /** Notify seller (owner + staff) via SMS when an order is paid. */
    public void notifySellerPaymentCompleted(Order order) {
        UUID businessId = getBusinessIdFromOrder(order);
        if (businessId == null) return;
        String body = String.format(
                "BiasharaHub: Order #%s has been paid. You can now prepare and dispatch.",
                order.getOrderNumber());
        for (User u : getSellerUsers(businessId)) {
            if (u.getPhone() != null && !u.getPhone().isBlank()) {
                smsClient.send(u.getPhone(), body);
            }
        }
    }

    /** Notify seller when product stock is running low. */
    public void notifySellerLowStock(Product product) {
        if (product == null || product.getBusinessId() == null) return;
        int qty = product.getQuantity() != null ? product.getQuantity() : 0;
        String body = String.format(
                "BiasharaHub: Low stock – \"%s\" has %d left. Consider restocking.",
                product.getName() != null ? product.getName() : "Unknown",
                qty);
        for (User u : getSellerUsers(product.getBusinessId())) {
            if (u.getPhone() != null && !u.getPhone().isBlank()) {
                smsClient.send(u.getPhone(), body);
            }
        }
    }

    /** Notify seller when a customer opens a dispute. */
    public void notifySellerDisputeCreated(Order order, String disputeType) {
        UUID businessId = getBusinessIdFromOrder(order);
        if (businessId == null) return;
        String body = String.format(
                "BiasharaHub: A customer opened a dispute for order #%s%s. Please respond in the dashboard.",
                order.getOrderNumber(),
                disputeType != null && !disputeType.isBlank() ? " (" + disputeType + ")" : "");
        for (User u : getSellerUsers(businessId)) {
            if (u.getPhone() != null && !u.getPhone().isBlank()) {
                smsClient.send(u.getPhone(), body);
            }
        }
    }

    /** Notify seller when an order is cancelled. */
    public void notifySellerOrderCancelled(Order order) {
        UUID businessId = getBusinessIdFromOrder(order);
        if (businessId == null) return;
        String body = String.format(
                "BiasharaHub: Order #%s was cancelled. Inventory has been restored.",
                order.getOrderNumber());
        for (User u : getSellerUsers(businessId)) {
            if (u.getPhone() != null && !u.getPhone().isBlank()) {
                smsClient.send(u.getPhone(), body);
            }
        }
    }

    // ---------- Service bookings (BiasharaHub Services) ----------

    /** Notify provider (owner + staff) when a new service appointment is booked. */
    public void notifyProviderServiceBookingCreated(ServiceAppointment appointment) {
        if (appointment == null || appointment.getService() == null || appointment.getService().getBusinessId() == null) return;
        UUID businessId = appointment.getService().getBusinessId();
        String customerName = appointment.getUser() != null && appointment.getUser().getName() != null
                ? appointment.getUser().getName()
                : (appointment.getUser() != null ? appointment.getUser().getEmail() : "a customer");
        String serviceName = appointment.getService().getName() != null ? appointment.getService().getName() : "your service";
        String dateTime = appointment.getRequestedDate() != null ? appointment.getRequestedDate().toString() : "";
        if (appointment.getRequestedTime() != null) dateTime += " at " + appointment.getRequestedTime();
        String body = String.format(
                "BiasharaHub: New booking for \"%s\" from %s on %s. Log in to confirm.",
                serviceName, customerName, dateTime);
        for (User u : getSellerUsers(businessId)) {
            if (u.getPhone() != null && !u.getPhone().isBlank()) smsClient.send(u.getPhone(), body);
        }
    }

    /** Notify provider when a service booking is paid. */
    public void notifyProviderServiceBookingPaymentCompleted(ServiceAppointment appointment) {
        if (appointment == null || appointment.getService() == null || appointment.getService().getBusinessId() == null) return;
        UUID businessId = appointment.getService().getBusinessId();
        String serviceName = appointment.getService().getName() != null ? appointment.getService().getName() : "service";
        String customerName = appointment.getUser() != null && appointment.getUser().getName() != null
                ? appointment.getUser().getName() : "a customer";
        String body = String.format(
                "BiasharaHub: Payment received for \"%s\" from %s. See dashboard.",
                serviceName, customerName);
        for (User u : getSellerUsers(businessId)) {
            if (u.getPhone() != null && !u.getPhone().isBlank()) smsClient.send(u.getPhone(), body);
        }
    }
}
