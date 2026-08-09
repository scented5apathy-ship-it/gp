package com.genealogy.platform.services.tenant.domain.tenant;

/**
 * Tenant subscription plan. Mirrors the gRPC {@code TenantPlan}
 * enum and the Avro {@code TenantPlan} enum
 * ({@code contracts/events/tenant/v1/tenant-plan.avsc}). Quota
 * numerics tied to these plans are DRAFT pending E0.6 sign-off
 * (architecture-decisions.md §A) and live in the
 * {@link com.genealogy.platform.services.tenant.domain.entitlement.Entitlement}
 * entity, not in this enum.
 */
public enum TenantPlan {

    /** Free tier. Self-serve sign-up; quotas enforced (TBD). */
    FREE,

    /** Family tier. Single household; quotas slightly higher than FREE. */
    FAMILY,

    /** Power-user tier. Higher quotas + priority support. */
    PRO,

    /**
     * Enterprise tier. On-premise option per ADR-E0.5-02; quotas
     * negotiated per contract.
     */
    ENTERPRISE
}