package com.biasharahub.repository;

import com.biasharahub.entity.Dispute;
import com.biasharahub.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    List<Dispute> findByOrderOrderByCreatedAtDesc(Order order);

    List<Dispute> findByStatusOrderByCreatedAtDesc(String status);

    Optional<Dispute> findByDisputeIdAndOrder(UUID disputeId, Order order);
}
