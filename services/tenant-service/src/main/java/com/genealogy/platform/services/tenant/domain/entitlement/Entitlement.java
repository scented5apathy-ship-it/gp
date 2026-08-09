package com.genealogy.platform.services.tenant.domain.entitlement;

import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.tenant.TenantPlan;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Per-tenant entitlement (plan + quota map + billing reference). One
 * row per tenant; the {@code tenant_id} is the primary key (V2
 * migration).
 *
 * <h2>DRAFT numerics</h2>
 *
 * The numeric quota values (memberLimit, treeLimit, storageLimitMb,
 * retentionDays) are <strong>DRAFT</strong> pending E0.6 sign-off
 * (architecture-decisions.md §A). They follow the convention
 * {@code 0 = unlimited} so the service can be exercised with
 * non-final values. E3.2 does NOT enforce quotas — see
 * {@code evidence/E3.2e.md} for the ADR exception note that defers
 * quota enforcement to E11.4 / E15.
 *
 * <p>This entity is framework-free: no Spring / JPA / jOOQ
 * annotations. The repository (E3.2c) adapts the V2 columns.
 */
public final class Entitlement {

    private final TenantId tenantId;
    private TenantPlan plan;
    private int memberLimit;
    private int treeLimit;
    private int storageLimitMb;
    private int retentionDays;
    private String billingExternalId;
    private Instant updatedAt;

    public Entitlement(
            TenantId tenantId,
            TenantPlan plan,
            int memberLimit,
            int treeLimit,
            int storageLimitMb,
            int retentionDays,
            String billingExternalId,
            Instant updatedAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.plan = Objects.requireNonNull(plan, "plan");
        setMemberLimit(memberLimit);
        setTreeLimit(treeLimit);
        setStorageLimitMb(storageLimitMb);
        setRetentionDays(retentionDays);
        this.billingExternalId = billingExternalId;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Default entitlement for a freshly-created tenant (FREE plan, no quotas). */
    public static Entitlement defaultFor(TenantId tenantId, Clock clock) {
        return new Entitlement(
                tenantId,
                TenantPlan.FREE,
                0, // unlimited per ADR exception; sign-off pending
                0,
                0,
                0,
                null,
                clock.instant());
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public TenantPlan plan() {
        return plan;
    }

    public int memberLimit() {
        return memberLimit;
    }

    public int treeLimit() {
        return treeLimit;
    }

    public int storageLimitMb() {
        return storageLimitMb;
    }

    public int retentionDays() {
        return retentionDays;
    }

    public String billingExternalId() {
        return billingExternalId;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void changePlan(TenantPlan newPlan, Clock clock) {
        this.plan = Objects.requireNonNull(newPlan, "newPlan");
        bump(clock);
    }

    public void changeQuotas(
            int newMemberLimit,
            int newTreeLimit,
            int newStorageLimitMb,
            int newRetentionDays,
            Clock clock) {
        setMemberLimit(newMemberLimit);
        setTreeLimit(newTreeLimit);
        setStorageLimitMb(newStorageLimitMb);
        setRetentionDays(newRetentionDays);
        bump(clock);
    }

    public void setBillingExternalId(String value, Clock clock) {
        this.billingExternalId = value;
        bump(clock);
    }

    /**
     * Quota check. Convention {@code 0 = unlimited} means the
     * comparison is {@code limit == 0 || current < limit}. The
     * result is intentionally advisory in E3.2b — the application
     * service (E3.2c) emits a warning event but does NOT reject the
     * mutation; quota enforcement lands in E11.4.
     */
    public boolean canAddMember(int currentMemberCount) {
        return memberLimit == 0 || currentMemberCount < memberLimit;
    }

    private void setMemberLimit(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("memberLimit must be >= 0 (got " + value + ")");
        }
        this.memberLimit = value;
    }

    private void setTreeLimit(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("treeLimit must be >= 0 (got " + value + ")");
        }
        this.treeLimit = value;
    }

    private void setStorageLimitMb(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("storageLimitMb must be >= 0 (got " + value + ")");
        }
        this.storageLimitMb = value;
    }

    private void setRetentionDays(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("retentionDays must be >= 0 (got " + value + ")");
        }
        this.retentionDays = value;
    }

    private void bump(Clock clock) {
        this.updatedAt = clock.instant();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entitlement other)) return false;
        return tenantId.equals(other.tenantId);
    }

    @Override
    public int hashCode() {
        return tenantId.hashCode();
    }

    @Override
    public String toString() {
        return "Entitlement[tenant=" + tenantId
                + ", plan=" + plan
                + ", memberLimit=" + memberLimit
                + ", treeLimit=" + treeLimit
                + ", storageLimitMb=" + storageLimitMb
                + ", retentionDays=" + retentionDays + "]";
    }
}