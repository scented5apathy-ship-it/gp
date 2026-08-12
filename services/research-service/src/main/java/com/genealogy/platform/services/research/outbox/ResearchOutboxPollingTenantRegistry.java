package com.genealogy.platform.services.research.outbox;

import java.util.List;
import java.util.Objects;

/**
 * Tenant registry the relay driver uses. The single-tenant
 * default keeps the E6.1d test path configured; the
 * production roll-out (E2.1) plugs in a real registry that
 * walks the {@code tenant-service} membership API.
 */
public class ResearchOutboxPollingTenantRegistry {

    private volatile List<String> tenants = List.of();

    public ResearchOutboxPollingTenantRegistry() {
    }

    public List<String> listActiveTenants() {
        return tenants;
    }

    public void setTenants(List<String> tenants) {
        this.tenants = List.copyOf(Objects.requireNonNull(tenants, "tenants"));
    }
}
