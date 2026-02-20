package com.biasharahub.service;

import com.biasharahub.entity.ServiceAppointment;
import com.biasharahub.entity.ServiceBookingEscrow;
import com.biasharahub.repository.ServiceAppointmentRepository;
import com.biasharahub.repository.ServiceBookingEscrowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Virtual service escrow: release funds to provider when customer confirms, or refund to customer when disputed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceBookingEscrowService {

    private final ServiceBookingEscrowRepository escrowRepository;
    private final ServiceAppointmentRepository appointmentRepository;
    private final TenantWalletService tenantWalletService;
    private final MpesaClient mpesaClient;

    /**
     * Customer confirmed service was provided: release held funds to provider wallet.
     */
    @Transactional
    public boolean releaseToProvider(UUID appointmentId) {
        return escrowRepository.findByAppointment_AppointmentIdAndStatus(appointmentId, "HELD")
                .map(escrow -> {
                    escrow.setStatus("RELEASED");
                    escrow.setReleasedAt(Instant.now());
                    escrowRepository.save(escrow);
                    ServiceAppointment a = escrow.getAppointment();
                    a.setEscrowStatus("RELEASED");
                    a.setStatus("CUSTOMER_CONFIRMED");
                    a.setCustomerConfirmedAt(Instant.now());
                    appointmentRepository.save(a);
                    tenantWalletService.recordIncomingPaymentForCurrentTenant(
                            escrow.getAmount(),
                            "appointment:" + appointmentId,
                            "escrow:" + escrow.getEscrowId());
                    log.info("Released escrow for appointment {} to provider", appointmentId);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Customer disputed: refund to customer via M-Pesa B2C (or mark for manual refund if B2C not configured).
     */
    @Transactional
    public boolean refundToCustomer(UUID appointmentId) {
        return escrowRepository.findByAppointment_AppointmentIdAndStatus(appointmentId, "HELD")
                .map(escrow -> {
                    ServiceAppointment a = escrow.getAppointment();
                    a.setEscrowStatus("REFUNDED");
                    a.setStatus("CUSTOMER_DISPUTED");
                    a.setCustomerDisputedAt(Instant.now());
                    appointmentRepository.save(a);
                    escrow.setStatus("REFUNDED");
                    escrow.setRefundedAt(Instant.now());
                    escrowRepository.save(escrow);

                    String phone = a.getUser() != null ? a.getUser().getPhone() : null;
                    if (phone != null && !phone.isBlank()) {
                        String ref = "REFUND-SVC-" + appointmentId;
                        String conversationId = mpesaClient.initiateB2C(phone, escrow.getAmount(), ref, "BiasharaHub service booking refund");
                        if (conversationId != null) {
                            log.info("Initiated refund B2C for appointment {} to {}", appointmentId, phone);
                        } else {
                            log.warn("B2C not configured or failed for refund appointment {} – consider manual refund", appointmentId);
                        }
                    } else {
                        log.warn("No customer phone for refund appointment {} – manual refund required", appointmentId);
                    }
                    return true;
                })
                .orElse(false);
    }
}
