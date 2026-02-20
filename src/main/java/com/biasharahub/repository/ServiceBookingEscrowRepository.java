package com.biasharahub.repository;

import com.biasharahub.entity.ServiceBookingEscrow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ServiceBookingEscrowRepository extends JpaRepository<ServiceBookingEscrow, UUID> {

    Optional<ServiceBookingEscrow> findByAppointment_AppointmentId(UUID appointmentId);

    Optional<ServiceBookingEscrow> findByAppointment_AppointmentIdAndStatus(UUID appointmentId, String status);
}
