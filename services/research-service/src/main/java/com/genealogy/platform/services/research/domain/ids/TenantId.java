package com.genealogy.platform.services.research.domain.ids;

/**
 * Tenant-scoped tenant identifier. A separate value object from
 * {@code TenantScopedId} so the repository, command service and
 * the trusted context never collide on the same record
 * signature.
 *
 * <p>The id format is opaque and is enforced by the platform
 * trusted-context filter (the {@code X-Tenant-Id} header is
 * matched against the {@code ^[A-Za-z0-9._\\-]{1,64}$} regex).
 */
public record TenantId(String value) {

    public TenantId {
        if (value == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (value.length() > 64) {
            throw new IllegalArgumentException(
                    "tenantId exceeds 64 characters: " + value.length());
        }
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Convenience accessor for the value. The record
     * component already exposes {@link #value()}; this method
     * keeps call sites that prefer the {@code getValue()}
     * convention readable.
     */
    public String getValue() {
        return value;
    }
}
