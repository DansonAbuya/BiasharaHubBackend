package com.biasharahub.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.biasharahub.entity.Payment;
import com.biasharahub.repository.PaymentRepository;
import com.biasharahub.service.OrderEventPublisher;
import com.biasharahub.service.TenantWalletService;
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
    private final TenantWalletService tenantWalletService;
    private final OrderEventPublisher orderEventPublisher;

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

        if (optPayment.isEmpty()) {
            log.warn("M-Pesa callback for unknown checkoutRequestId={}", callback.getCheckoutRequestId());
            return ResponseEntity.ok().build();
        }

        Payment payment = optPayment.get();

        if (callback.getResultCode() != 0) {
            payment.setPaymentStatus("failed");
            paymentRepository.save(payment);
            return ResponseEntity.ok().build();
        }

        BigDecimal amount = extractAmount(callback.getCallbackMetadata());
        if (amount == null) {
            amount = payment.getAmount();
        }

        payment.setPaymentStatus("completed");
        // Optionally overwrite transactionId with receipt number for easier reconciliation
        String receipt = extractReceipt(callback.getCallbackMetadata());
        if (receipt != null) {
            payment.setTransactionId(receipt);
        }
        paymentRepository.save(payment);

        // Credit current tenant wallet and publish event
        tenantWalletService.recordIncomingPaymentForCurrentTenant(
                amount, payment.getOrder().getOrderId().toString(), payment.getPaymentId().toString());
        orderEventPublisher.paymentCompleted(payment.getOrder().getOrderId(), payment.getPaymentId());

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
}

