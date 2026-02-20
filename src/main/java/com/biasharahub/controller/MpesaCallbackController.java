package com.biasharahub.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.biasharahub.entity.Payment;
import com.biasharahub.entity.ServiceBookingEscrow;
import com.biasharahub.entity.ServiceBookingPayment;
import com.biasharahub.repository.PaymentRepository;
import com.biasharahub.repository.ServiceAppointmentRepository;
import com.biasharahub.repository.ServiceBookingEscrowRepository;
import com.biasharahub.repository.ServiceBookingPaymentRepository;
import com.biasharahub.service.InAppNotificationService;
import com.biasharahub.service.OrderEventPublisher;
import com.biasharahub.service.PayoutService;
import com.biasharahub.service.SmsNotificationService;
import com.biasharahub.service.TenantWalletService;
import com.biasharahub.service.WhatsAppNotificationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Receives M-Pesa STK Push callbacks and updates payments, orders, and tenant wallet ledger.
 */
@RestController
@RequestMapping("/payments/mpesa")
@RequiredArgsConstructor
@Slf4j
public class MpesaCallbackController {

    private final PaymentRepository paymentRepository;
    private final ServiceBookingPaymentRepository serviceBookingPaymentRepository;
    private final ServiceBookingEscrowRepository serviceBookingEscrowRepository;
    private final ServiceAppointmentRepository serviceAppointmentRepository;
    private final TenantWalletService tenantWalletService;
    private final OrderEventPublisher orderEventPublisher;
    private final PayoutService payoutService;
    private final InAppNotificationService inAppNotificationService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final SmsNotificationService smsNotificationService;

    @PostMapping("/stk-callback")
    @Transactional
    public ResponseEntity<Void> handleStkCallback(@RequestBody StkCallbackEnvelope envelope) {
        if (envelope == null || envelope.getBody() == null || envelope.getBody().getStkCallback() == null) {
            return ResponseEntity.ok().build();
        }
        StkCallback callback = envelope.getBody().getStkCallback();
        log.info("Received M-Pesa STK callback: merchantRequestId={}, checkoutRequestId={}, resultCode={}, resultDesc={}",
                callback.getMerchantRequestId(), callback.getCheckoutRequestId(),
                callback.getResultCode(), callback.getResultDesc());

        Optional<Payment> optPayment =
                paymentRepository.findAll().stream()
                        .filter(p -> callback.getCheckoutRequestId().equals(p.getTransactionId()))
                        .findFirst();

        if (optPayment.isPresent()) {
            Payment payment = optPayment.get();
            if (callback.getResultCode() != 0) {
                payment.setPaymentStatus("failed");
                paymentRepository.save(payment);
                return ResponseEntity.ok().build();
            }
            BigDecimal amount = extractAmount(callback.getCallbackMetadata());
            if (amount == null) amount = payment.getAmount();
            payment.setPaymentStatus("completed");
            String receipt = extractReceipt(callback.getCallbackMetadata());
            if (receipt != null) payment.setTransactionId(receipt);
            paymentRepository.save(payment);
            tenantWalletService.recordIncomingPaymentForCurrentTenant(
                    amount, payment.getOrder().getOrderId().toString(), payment.getPaymentId().toString());
            orderEventPublisher.paymentCompleted(payment.getOrder().getOrderId(), payment.getPaymentId());
            return ResponseEntity.ok().build();
        }

        // Not an order payment: try service booking payment
        Optional<ServiceBookingPayment> optBookingPayment =
                serviceBookingPaymentRepository.findByTransactionId(callback.getCheckoutRequestId());
        if (optBookingPayment.isEmpty()) {
            log.warn("M-Pesa callback for unknown checkoutRequestId={}", callback.getCheckoutRequestId());
            return ResponseEntity.ok().build();
        }

        ServiceBookingPayment bookingPayment = optBookingPayment.get();
        if (callback.getResultCode() != 0) {
            bookingPayment.setPaymentStatus("failed");
            serviceBookingPaymentRepository.save(bookingPayment);
            return ResponseEntity.ok().build();
        }
        BigDecimal amount = extractAmount(callback.getCallbackMetadata());
        if (amount == null) amount = bookingPayment.getAmount();
        bookingPayment.setPaymentStatus("completed");
        String receipt = extractReceipt(callback.getCallbackMetadata());
        if (receipt != null) bookingPayment.setTransactionId(receipt);
        serviceBookingPaymentRepository.save(bookingPayment);
        var appointment = bookingPayment.getAppointment();
        boolean isVirtual = appointment.getService() != null && "VIRTUAL".equalsIgnoreCase(appointment.getService().getDeliveryType());
        if (isVirtual) {
            // Virtual: hold in escrow until customer confirms or disputes
            ServiceBookingEscrow escrow = ServiceBookingEscrow.builder()
                    .appointment(appointment)
                    .bookingPayment(bookingPayment)
                    .amount(amount)
                    .status("HELD")
                    .build();
            serviceBookingEscrowRepository.save(escrow);
            appointment.setEscrowStatus("HELD");
            serviceAppointmentRepository.save(appointment);
        } else {
            // Physical (pay before): credit provider wallet immediately
            tenantWalletService.recordIncomingPaymentForCurrentTenant(
                    amount, "appointment:" + appointment.getAppointmentId(), bookingPayment.getPaymentId().toString());
        }
        try {
            inAppNotificationService.notifyServiceBookingPaymentCompletedCustomer(appointment);
            inAppNotificationService.notifyServiceBookingPaymentCompletedProvider(appointment);
            whatsAppNotificationService.notifyServiceBookingPaymentCompletedCustomer(appointment);
            whatsAppNotificationService.notifyServiceBookingPaymentCompletedProvider(appointment);
            smsNotificationService.notifyProviderServiceBookingPaymentCompleted(appointment);
        } catch (Exception ex) {
            log.warn("Failed to send service booking payment notifications: {}", ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    private BigDecimal extractAmount(CallbackMetadata metadata) {
        if (metadata == null || metadata.getItem() == null) return null;
        return metadata.getItem().stream()
                .filter(i -> "Amount".equalsIgnoreCase(i.getName()))
                .map(Item::getValue)
                .filter(v -> v instanceof Number)
                .map(v -> new BigDecimal(((Number) v).toString()))
                .findFirst()
                .orElse(null);
    }

    private String extractReceipt(CallbackMetadata metadata) {
        if (metadata == null || metadata.getItem() == null) return null;
        return metadata.getItem().stream()
                .filter(i -> "MpesaReceiptNumber".equalsIgnoreCase(i.getName()))
                .map(Item::getValue)
                .filter(v -> v instanceof String)
                .map(v -> (String) v)
                .findFirst()
                .orElse(null);
    }

    /**
     * Receives M-Pesa B2C result callback. Updates payout status by ConversationID (external_reference).
     */
    @PostMapping("/b2c-callback")
    @Transactional
    public ResponseEntity<B2CResultResponse> handleB2CCallback(@RequestBody B2CResultEnvelope envelope) {
        if (envelope == null || envelope.getResult() == null) {
            return ResponseEntity.ok(new B2CResultResponse(0, "Accepted"));
        }
        B2CResult result = envelope.getResult();
        String conversationId = result.getConversationID() != null ? result.getConversationID() : result.getOriginatorConversationID();
        int resultCode = result.getResultCode() != null ? result.getResultCode() : -1;
        String resultDesc = result.getResultDesc();
        log.info("Received M-Pesa B2C callback: conversationId={}, resultCode={}, resultDesc={}",
                conversationId, resultCode, resultDesc);

        payoutService.handleB2CResult(conversationId, resultCode, resultDesc != null ? resultDesc : "");

        return ResponseEntity.ok(new B2CResultResponse(0, "Accepted"));
    }

    // --- Payload classes matching M-Pesa STK callback JSON ---

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StkCallbackEnvelope {
        @JsonProperty("Body")
        private Body body;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        @JsonProperty("stkCallback")
        private StkCallback stkCallback;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StkCallback {
        @JsonProperty("MerchantRequestID")
        private String merchantRequestId;

        @JsonProperty("CheckoutRequestID")
        private String checkoutRequestId;

        @JsonProperty("ResultCode")
        private int resultCode;

        @JsonProperty("ResultDesc")
        private String resultDesc;

        @JsonProperty("CallbackMetadata")
        private CallbackMetadata callbackMetadata;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CallbackMetadata {
        @JsonProperty("Item")
        private List<Item> item;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        @JsonProperty("Name")
        private String name;

        @JsonProperty("Value")
        private Object value;
    }

    // --- B2C result callback (Daraja sends Result with ResultCode, ConversationID, etc.) ---

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class B2CResultEnvelope {
        @JsonProperty("Result")
        private B2CResult result;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class B2CResult {
        @JsonProperty("ResultType")
        private Integer resultType;
        @JsonProperty("ResultCode")
        private Integer resultCode;
        @JsonProperty("ResultDesc")
        private String resultDesc;
        @JsonProperty("OriginatorConversationID")
        private String originatorConversationID;
        @JsonProperty("ConversationID")
        private String conversationID;
        @JsonProperty("TransactionID")
        private String transactionID;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class B2CResultResponse {
        @JsonProperty("ResultCode")
        private final int resultCode;
        @JsonProperty("ResultDesc")
        private final String resultDesc;

        public B2CResultResponse(int resultCode, String resultDesc) {
            this.resultCode = resultCode;
            this.resultDesc = resultDesc;
        }
    }
}

