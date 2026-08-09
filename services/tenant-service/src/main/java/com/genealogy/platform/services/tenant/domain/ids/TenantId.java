package com.genealogy.platform.services.tenant.domain.ids;

/**
 * Tenant aggregate root identifier. The tenant IS its own scope root:
 * a tenant row carries {@code tenant_id = id} per V2 migration. The
 * type tag prevents accidental cross-domain confusion with
 * {@link UserId} or {@link MembershipId}.
 */
public final class TenantId extends OpaqueId {

    public TenantId(String value) {
        super(value);
    }
}