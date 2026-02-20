package com.biasharahub.repository;

import com.biasharahub.entity.ServiceContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceContractRepository extends JpaRepository<ServiceContract, UUID> {

    List<ServiceContract> findByAppointment_AppointmentId(UUID appointmentId);

    List<ServiceContract> findByService_ServiceId(UUID serviceId);

    List<ServiceContract> findByCustomer_UserId(UUID customerId);

    List<ServiceContract> findByBusinessId(UUID businessId);
}
