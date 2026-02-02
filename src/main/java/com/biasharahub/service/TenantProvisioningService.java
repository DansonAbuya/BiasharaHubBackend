package com.biasharahub.service;

import com.biasharahub.entity.Tenant;
import com.biasharahub.repository.TenantRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Provisions new tenants by creating their dedicated schema and tables.
 */
@Service
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;

    public TenantProvisioningService(TenantRepository tenantRepository, JdbcTemplate jdbcTemplate) {
        this.tenantRepository = tenantRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Tenant createTenant(String name, String domain, String schemaName) {
        Tenant tenant = Tenant.builder()
                .tenantId(UUID.randomUUID())
                .schemaName(sanitizeSchemaName(schemaName != null ? schemaName : "tenant_" + UUID.randomUUID().toString().replace("-", "_")))
                .name(name)
                .domain(domain)
                .logoUrl("/api/static/logo.png")
                .primaryColor("#2E7D32")
                .accentColor("#FF8F00")
                .isActive(true)
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();
        tenant = tenantRepository.save(tenant);
        final UUID tenantId = tenant.getTenantId();
        final String tenantSchemaName = tenant.getSchemaName();
        jdbcTemplate.execute((ConnectionCallback<Void>) con -> {
            try (var ps = con.prepareStatement("SELECT public.create_tenant_schema(?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setString(2, tenantSchemaName);
                ps.execute();
            }
            return null;
        });
        return tenant;
    }

    private String sanitizeSchemaName(String name) {
        String s = name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return s.length() > 63 ? s.substring(0, 63) : s;
    }
}
