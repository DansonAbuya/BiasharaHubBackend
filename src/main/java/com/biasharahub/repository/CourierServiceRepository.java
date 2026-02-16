package com.biasharahub.repository;

import com.biasharahub.entity.CourierService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierServiceRepository extends JpaRepository<CourierService, UUID> {

    List<CourierService> findByIsActiveTrueOrderByNameAsc();

    List<CourierService> findAllByOrderByNameAsc();

    Optional<CourierService> findByCode(String code);
}
