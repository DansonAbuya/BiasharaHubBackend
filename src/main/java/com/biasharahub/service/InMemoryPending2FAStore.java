package com.biasharahub.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryPending2FAStore implements Pending2FAStore {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void put(String stateToken, UUID userId, String email, Instant expiresAt) {
        store.put(stateToken, new Entry(userId, email, expiresAt));
    }

    @Override
    public Optional<Pending2FA> peek(String stateToken) {
        Entry entry = store.get(stateToken);
        if (entry == null || Instant.now().isAfter(entry.expiresAt)) {
            return Optional.empty();
        }
        return Optional.of(new Pending2FA(entry.userId, entry.email));
    }

    @Override
    public Optional<Pending2FA> consume(String stateToken) {
        Entry entry = store.remove(stateToken);
        if (entry == null || Instant.now().isAfter(entry.expiresAt)) {
            return Optional.empty();
        }
        return Optional.of(new Pending2FA(entry.userId, entry.email));
    }

    @Scheduled(fixedRate = 300_000) // every 5 min
    public void evictExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt));
    }

    private record Entry(UUID userId, String email, Instant expiresAt) {}
}
