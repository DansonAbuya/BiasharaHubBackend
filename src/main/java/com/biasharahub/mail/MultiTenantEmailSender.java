package com.biasharahub.mail;

import com.biasharahub.config.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Multi-tenant email facade: resolves tenant from TenantContext and delegates to the default sender.
 * Extend with per-tenant config (e.g. DB) if you need different senders per tenant.
 */
@Component
@Primary
@Slf4j
public class MultiTenantEmailSender implements EmailSender {

    private final EmailSender defaultSender;

    public MultiTenantEmailSender(@Qualifier("defaultEmailSender") EmailSender defaultSender) {
        this.defaultSender = defaultSender;
    }

    @Override
    public void send(EmailMessage message) {
        String tenantSchema = TenantContext.getTenantSchema();
        if (message.getTenantId() == null && tenantSchema != null) {
            message.setTenantId(tenantSchema);
        }
        // Per-tenant sender resolution: could look up sender by message.getTenantId() here
        defaultSender.send(message);
    }

    @Override
    public boolean isAvailable() {
        return defaultSender.isAvailable();
    }
}
