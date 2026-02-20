package com.biasharahub.repository;

import com.biasharahub.entity.ServiceAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceAppointmentRepository extends JpaRepository<ServiceAppointment, UUID> {

    @Query("SELECT a FROM ServiceAppointment a JOIN FETCH a.service JOIN FETCH a.user WHERE a.user.userId = :userId ORDER BY a.requestedDate DESC, a.requestedTime DESC NULLS LAST")
    List<ServiceAppointment> findByUserIdOrderByRequestedDateDesc(@Param("userId") UUID userId);

    @Query("SELECT a FROM ServiceAppointment a JOIN FETCH a.service s JOIN FETCH a.user WHERE s.businessId = :businessId ORDER BY a.requestedDate DESC, a.requestedTime DESC NULLS LAST")
    List<ServiceAppointment> findByService_BusinessIdOrderByRequestedDateDesc(@Param("businessId") UUID businessId);

    @Query("SELECT a FROM ServiceAppointment a JOIN FETCH a.service s JOIN FETCH a.user WHERE s.serviceId = :serviceId ORDER BY a.requestedDate DESC, a.requestedTime DESC NULLS LAST")
    List<ServiceAppointment> findByService_ServiceIdOrderByRequestedDateDesc(@Param("serviceId") UUID serviceId);

    @Query("SELECT a FROM ServiceAppointment a JOIN FETCH a.service JOIN FETCH a.user WHERE a.appointmentId = :id")
    Optional<ServiceAppointment> findByAppointmentIdWithDetails(@Param("id") UUID id);

    boolean existsByAppointmentIdAndUser_UserId(UUID appointmentId, UUID userId);
}
