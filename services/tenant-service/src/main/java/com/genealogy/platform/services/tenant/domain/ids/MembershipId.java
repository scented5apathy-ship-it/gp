package com.genealogy.platform.services.tenant.domain.ids;

/**
 * Per-tenant membership row identifier. The type tag keeps it
 * distinct from {@link TenantId} (one tenant, many memberships) and
 * from {@link UserId} (one user, many memberships across tenants).
 */
public final class MembershipId extends OpaqueId {

    public MembershipId(String value) {
        super(value);
    }
}