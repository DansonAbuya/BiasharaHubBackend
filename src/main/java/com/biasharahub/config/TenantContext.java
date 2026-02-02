package com.biasharahub.config;

/**
 * Thread-local holder for the current tenant's schema name.
 * Used by multi-tenant configuration to route queries to the correct schema.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantSchema(String schemaName) {
        CURRENT_TENANT.set(schemaName);
    }

    public static String getTenantSchema() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
