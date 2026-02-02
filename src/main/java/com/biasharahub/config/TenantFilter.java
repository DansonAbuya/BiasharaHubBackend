package com.biasharahub.config;

import com.biasharahub.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that resolves the tenant from X-Tenant-ID header and sets TenantContext.
 * For public endpoints (auth, static), uses default tenant.
 */
@Component
@Order(-200)  // Run before Security (order -100)
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String DEFAULT_TENANT_SCHEMA = "tenant_default";  // Used when no X-Tenant-ID header

    private final TenantRepository tenantRepository;

    public TenantFilter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantId = request.getHeader(TENANT_HEADER);
            String schema = DEFAULT_TENANT_SCHEMA;

            if (tenantId != null && !tenantId.isBlank()) {
                try {
                    UUID uuid = UUID.fromString(tenantId);
                    schema = tenantRepository.findSchemaByTenantId(uuid).orElse(DEFAULT_TENANT_SCHEMA);
                } catch (IllegalArgumentException ignored) {
                    // Invalid UUID, use default
                }
            }
            TenantContext.setTenantSchema(schema);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
