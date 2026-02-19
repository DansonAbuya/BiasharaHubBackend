package com.biasharahub.repository;

import com.biasharahub.entity.Notification;
import com.biasharahub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    List<Notification> findByUserAndReadIsFalseOrderByCreatedAtDesc(User user);
}

