package com.biasharahub.controller;

import com.biasharahub.dto.response.NotificationDto;
import com.biasharahub.entity.Notification;
import com.biasharahub.entity.User;
import com.biasharahub.repository.NotificationRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<NotificationDto>> listNotifications(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(name = "unreadOnly", required = false, defaultValue = "false") boolean unreadOnly) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userRepository.findById(currentUser.userId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findByUserAndReadIsFalseOrderByCreatedAtDesc(user)
                : notificationRepository.findByUserOrderByCreatedAtDesc(user);
        List<NotificationDto> dtoList = notifications.stream().map(NotificationController::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID id) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userRepository.findById(currentUser.userId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification == null || !notification.getUser().getUserId().equals(user.getUserId())) {
            return ResponseEntity.status(404).build();
        }
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userRepository.findById(currentUser.userId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        List<Notification> unread = notificationRepository.findByUserAndReadIsFalseOrderByCreatedAtDesc(user);
        if (!unread.isEmpty()) {
            Instant now = Instant.now();
            for (Notification n : unread) {
                n.setRead(true);
                n.setReadAt(now);
            }
            notificationRepository.saveAll(unread);
        }
        return ResponseEntity.ok().build();
    }

    private static NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getNotificationId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .actionUrl(n.getActionUrl())
                .data(n.getData())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}

