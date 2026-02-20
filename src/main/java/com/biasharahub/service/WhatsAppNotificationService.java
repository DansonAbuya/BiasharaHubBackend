package com.biasharahub.service;

import com.biasharahub.entity.Order;
import com.biasharahub.entity.OrderItem;
import com.biasharahub.entity.Payment;
import com.biasharahub.entity.Product;
import com.biasharahub.entity.ServiceAppointment;
import com.biasharahub.entity.Shipment;
import com.biasharahub.entity.User;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.ShipmentRepository;
import com.biasharahub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sends WhatsApp notifications for order, payment, and shipment events via Twilio.
 * Recipient is the customer (order user) or seller (owner/staff). If the user has no phone number, the notification is skipped.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppNotificationService {

    private final WhatsAppClient client;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;

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

    /**
     * Notify seller (owner + staff) via WhatsApp when a new order is placed for their business.
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
                client.sendMessage(u.getPhone(), body);
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

    /** Notify seller (owner + staff) via WhatsApp when an order is paid. */
    public void notifySellerPaymentCompleted(Order order) {
        UUID businessId = getBusinessIdFromOrder(order);
        if (businessId == null) return;
        String body = String.format(
                "BiasharaHub: Order #%s has been paid. You can now prepare and dispatch.",
                order.getOrderNumber());
        for (User u : getSellerUsers(businessId)) {
            if (u.getPhone() != null && !u.getPhone().isBlank()) {
                client.sendMessage(u.getPhone(), body);
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
                client.sendMessage(u.getPhone(), body);
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
                client.sendMessage(u.getPhone(), body);
            }
        }
    }

    // ---------- Service bookings (BiasharaHub Services) ----------

    /** Notify customer that their service appointment was booked. */
    public void notifyServiceBookingCreated(ServiceAppointment appointment) {
        if (appointment == null || appointment.getUser() == null) return;
        String phone = appointment.getUser().getPhone();
        if (phone == null || phone.isBlank()) return;
        String serviceName = appointment.getService() != null ? appointment.getService().getName() : "your service";
        String dateTime = appointment.getRequestedDate() != null ? appointment.getRequestedDate().toString() : "";
        if (appointment.getRequestedTime() != null) dateTime += " at " + appointment.getRequestedTime();
        String body = String.format(
                "BiasharaHub: Your appointment for \"%s\" on %s has been booked. Pay in the app to confirm.",
                serviceName, dateTime);
        client.sendMessage(phone, body);
    }

    /** Notify provider (owner + staff) when a new service appointment is booked. */
    public void notifyProviderServiceBookingCreated(ServiceAppointment appointment) {
        if (appointment == null || appointment.getService() == null || appointment.getService().getBusinessId() == null) return;
        String customerName = appointment.getUser() != null && appointment.getUser().getName() != null
                ? appointment.getUser().getName()
                : (appointment.getUser() != null ? appointment.getUser().getEmail() : "a customer");
        String serviceName = appointment.getService().getName() != null ? appointment.getService().getName() : "your service";
        String dateTime = appointment.getRequestedDate() != null ? appointment.getRequestedDate().toString() : "";
        if (appointment.getRequestedTime() != null) dateTime += " at " + appointment.getRequestedTime();

        StringBuilder body = new StringBuilder();
        body.append("BiasharaHub: New booking for \"").append(serviceName).append("\" from ").append(customerName);
        body.append(" on ").append(dateTime).append(".");

        // Include customer location for physical services
        if (appointment.getCustomerLocationDescription() != null && !appointment.getCustomerLocationDescription().isBlank()) {
            body.append("\n📍 Customer location: ").append(appointment.getCustomerLocationDescription());
        } else if (appointment.getCustomerLocationLat() != null && appointment.getCustomerLocationLng() != null) {
            body.append("\n📍 Customer location: https://www.google.com/maps?q=")
                    .append(appointment.getCustomerLocationLat()).append(",").append(appointment.getCustomerLocationLng());
        }

        body.append("\nLog in to confirm.");

        for (User u : getSellerUsers(appointment.getService().getBusinessId())) {
            if (u.getPhone() != null && !u.getPhone().isBlank()) client.sendMessage(u.getPhone(), body.toString());
        }
    }

    /** Notify customer and provider with meeting link (virtual, booking confirmed). */
    public void notifyServiceMeetingLinkSent(ServiceAppointment appointment, String meetingLink) {
        if (appointment == null || meetingLink == null || meetingLink.isBlank()) return;
        String serviceName = appointment.getService() != null ? appointment.getService().getName() : "your service";
        if (appointment.getUser() != null) {
            String phone = appointment.getUser().getPhone();
            if (phone != null && !phone.isBlank()) {
                client.sendMessage(phone, "BiasharaHub: Meeting link for \"" + serviceName + "\": " + meetingLink);
            }
        }
        if (appointment.getService() != null && appointment.getService().getBusinessId() != null) {
            String body = "BiasharaHub: Meeting link for \"" + serviceName + "\": " + meetingLink;
            for (User u : getSellerUsers(appointment.getService().getBusinessId())) {
                if (u.getPhone() != null && !u.getPhone().isBlank()) client.sendMessage(u.getPhone(), body);
            }
        }
    }

    /** Notify customer when appointment status changes. */
    public void notifyServiceBookingStatusUpdated(ServiceAppointment appointment) {
        if (appointment == null || appointment.getUser() == null) return;
        String phone = appointment.getUser().getPhone();
        if (phone == null || phone.isBlank()) return;
        String serviceName = appointment.getService() != null ? appointment.getService().getName() : "your service";
        String status = appointment.getStatus() != null ? appointment.getStatus() : "";
        String body = String.format("BiasharaHub: Your appointment for \"%s\" is now %s.", serviceName, status);
        client.sendMessage(phone, body);
    }

    /** Notify customer that their booking payment was received. */
    public void notifyServiceBookingPaymentCompletedCustomer(ServiceAppointment appointment) {
        if (appointment == null || appointment.getUser() == null) return;
        String phone = appointment.getUser().getPhone();
        if (phone == null || phone.isBlank()) return;
        String serviceName = appointment.getService() != null ? appointment.getService().getName() : "your service";
        String body = String.format("BiasharaHub: Payment received for \"%s\". Your booking is confirmed.", serviceName);
        client.sendMessage(phone, body);
    }

    /** Notify provider when a service booking is paid. */
    public void notifyServiceBookingPaymentCompletedProvider(ServiceAppointment appointment) {
        if (appointment == null || appointment.getService() == null || appointment.getService().getBusinessId() == null) return;
        String serviceName = appointment.getService().getName() != null ? appointment.getService().getName() : "service";
        String customerName = appointment.getUser() != null && appointment.getUser().getName() != null
                ? appointment.getUser().getName() : "a customer";
        String body = String.format("BiasharaHub: Payment received for \"%s\" from %s. See dashboard.", serviceName, customerName);
        for (User u : getSellerUsers(appointment.getService().getBusinessId())) {
            if (u.getPhone() != null && !u.getPhone().isBlank()) client.sendMessage(u.getPhone(), body);
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
                client.sendMessage(u.getPhone(), body);
            }
        }
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
