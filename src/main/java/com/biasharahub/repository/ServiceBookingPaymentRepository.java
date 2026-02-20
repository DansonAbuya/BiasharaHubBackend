package com.biasharahub.repository;

import com.biasharahub.entity.ServiceAppointment;
import com.biasharahub.entity.ServiceBookingPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ServiceBookingPaymentRepository extends JpaRepository<ServiceBookingPayment, UUID> {

    Optional<ServiceBookingPayment> findByAppointment_AppointmentId(UUID appointmentId);

    Optional<ServiceBookingPayment> findByAppointmentAndPaymentStatus(ServiceAppointment appointment, String status);

    Optional<ServiceBookingPayment> findByTransactionId(String transactionId);
}
