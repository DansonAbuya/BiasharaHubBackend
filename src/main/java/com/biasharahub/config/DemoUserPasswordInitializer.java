package com.biasharahub.config;

import com.biasharahub.entity.User;
import com.biasharahub.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Ensures demo users in tenant_default can log in with password "password123"
 * by setting their password hash using the application's PasswordEncoder at startup.
 * This fixes "Invalid email or password" regardless of what hash was in migrations.
 */
@Component
public class DemoUserPasswordInitializer implements ApplicationRunner, Ordered {

    private static final String DEMO_PASSWORD = "password123";
    private static final String DEMO_TENANT_SCHEMA = "tenant_default";

    private static final List<UUID> DEMO_USER_IDS = List.of(
            UUID.fromString("11111111-1111-1111-1111-111111111100"),
            UUID.fromString("11111111-1111-1111-1111-111111111101"),
            UUID.fromString("11111111-1111-1111-1111-111111111102"),
            UUID.fromString("11111111-1111-1111-1111-111111111103")
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoUserPasswordInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String previous = TenantContext.getTenantSchema();
        try {
            TenantContext.setTenantSchema(DEMO_TENANT_SCHEMA);
            String encoded = passwordEncoder.encode(DEMO_PASSWORD);
            for (UUID id : DEMO_USER_IDS) {
                userRepository.findById(id).ifPresent(user -> {
                    user.setPasswordHash(encoded);
                    userRepository.save(user);
                });
            }
        } finally {
            if (previous != null) {
                TenantContext.setTenantSchema(previous);
            } else {
                TenantContext.clear();
            }
        }
    }
}
