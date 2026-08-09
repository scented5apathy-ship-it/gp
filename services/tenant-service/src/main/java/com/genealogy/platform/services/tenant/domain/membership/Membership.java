package com.genealogy.platform.services.tenant.domain.membership;

import com.genealogy.platform.services.tenant.domain.ids.IdGenerator;
import com.genealogy.platform.services.tenant.domain.ids.MembershipId;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.ids.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * Per-tenant membership aggregate. A {@link UserId} can hold at most
 * one membership per tenant (V2 migration UNIQUE on
 * {@code (tenant_id, user_id)}) but the same user can belong to
 * multiple tenants.
 *
 * <p>The {@code personId} field is intentionally NULL for E3.2b —
 * the link between {@code Membership} (user-binding) and
 * {@code Person} (genealogical subject) is gated by the E4.x
 * verification workflow per R3 ("Không liên kết User↔Person nếu
 * chưa qua verification workflow").
 *
 * <p>The aggregate holds timestamps that mirror the V2 migration
 * CHECK constraints; E3.2c persists them in the same transaction
 * that emits the matching outbox row.
 */
public final class Membership {

    private final MembershipId id;
    private final TenantId tenantId;
    private final UserId userId;
    private MembershipRole role;
    private MembershipStatus status;
    private Instant invitedAt;
    private Instant joinedAt;
    private Instant suspendedAt;
    private Instant revokedAt;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Membership(
            MembershipId id,
            TenantId tenantId,
            UserId userId,
            MembershipRole role,
            MembershipStatus status,
            Instant invitedAt,
            Instant joinedAt,
            Instant suspendedAt,
            Instant revokedAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.role = Objects.requireNonNull(role, "role");
        this.status = Objects.requireNonNull(status, "status");
        this.invitedAt = invitedAt;
        this.joinedAt = joinedAt;
        this.suspendedAt = suspendedAt;
        this.revokedAt = revokedAt;
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Factory for a freshly-invited membership (status INVITED,
     * {@code invitedAt} set, no join timestamp). The repository
     * layer persists this row together with the
     * {@code invitations} row that originated it (the two are
     * linked by email + idempotency_key).
     */
    public static Membership invite(
            IdGenerator idGenerator,
            TenantId tenantId,
            UserId userId,
            MembershipRole role,
            java.time.Clock clock) {
        Objects.requireNonNull(idGenerator, "idGenerator");
        Objects.requireNonNull(clock, "clock");
        Instant now = clock.instant();
        return new Membership(
                new MembershipId(idGenerator.nextId()),
                tenantId,
                userId,
                role,
                MembershipStatus.INVITED,
                now,
                null,
                null,
                null,
                1L,
                now,
                now);
    }

    /**
     * Rehydrate from V2 columns. Enforces the same timestamp
     * consistency as the database CHECK constraint so an
     * inconsistent row is rejected at the aggregate boundary, not
     * at the application service boundary.
     */
    public static Membership rehydrate(
            MembershipId id,
            TenantId tenantId,
            UserId userId,
            MembershipRole role,
            MembershipStatus status,
            Instant invitedAt,
            Instant joinedAt,
            Instant suspendedAt,
            Instant revokedAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        // Mirror V2 migration CHECK.
        switch (status) {
            case INVITED -> {
                if (invitedAt == null || joinedAt != null) {
                    throw new IllegalStateException(
                            "INVITED requires invitedAt != null and joinedAt == null");
                }
            }
            case ACTIVE -> {
                if (joinedAt == null) {
                    throw new IllegalStateException(
                            "ACTIVE requires joinedAt != null");
                }
            }
            case SUSPENDED -> {
                if (joinedAt == null || suspendedAt == null) {
                    throw new IllegalStateException(
                            "SUSPENDED requires joinedAt and suspendedAt != null");
                }
            }
            case REVOKED -> {
                if (revokedAt == null) {
                    throw new IllegalStateException(
                            "REVOKED requires revokedAt != null");
                }
            }
            default -> {
                // Unreachable: the enum is exhaustive and the cases above
                // cover every symbol. Defensive throw so future enum
                // additions fail loudly here rather than silently passing
                // an inconsistent row through the rehydrate path.
                throw new IllegalStateException(
                        "unknown MembershipStatus: " + status);
            }
        }
        return new Membership(
                id, tenantId, userId, role, status,
                invitedAt, joinedAt, suspendedAt, revokedAt,
                version, createdAt, updatedAt);
    }

    public MembershipId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UserId userId() {
        return userId;
    }

    public MembershipRole role() {
        return role;
    }

    public MembershipStatus status() {
        return status;
    }

    public Instant invitedAt() {
        return invitedAt;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public Instant suspendedAt() {
        return suspendedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    // -----------------------------------------------------------------------
    // Mutating operations. Each bumps version + updatedAt.
    // -----------------------------------------------------------------------

    /** Transition INVITED -> ACTIVE on user acceptance. */
    public void activate(java.time.Clock clock) {
        if (status != MembershipStatus.INVITED) {
            throw new IllegalStateException(
                    "cannot ACTIVATE from status=" + status);
        }
        this.status = MembershipStatus.ACTIVE;
        this.joinedAt = clock.instant();
        bump(clock);
    }

    /** Transition ACTIVE -> SUSPENDED (admin suspension). */
    public void suspend(java.time.Clock clock) {
        if (!status.canTransitionTo(MembershipStatus.SUSPENDED)) {
            throw new IllegalStateException(
                    "cannot SUSPEND from status=" + status);
        }
        this.status = MembershipStatus.SUSPENDED;
        this.suspendedAt = clock.instant();
        bump(clock);
    }

    /** Transition SUSPENDED -> ACTIVE. */
    public void restore(java.time.Clock clock) {
        if (status != MembershipStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "cannot RESTORE from status=" + status);
        }
        this.status = MembershipStatus.ACTIVE;
        this.suspendedAt = null;
        bump(clock);
    }

    /** Transition any non-terminal status -> REVOKED (terminal). */
    public void revoke(java.time.Clock clock) {
        if (status.isTerminal()) {
            throw new IllegalStateException("membership is already REVOKED");
        }
        this.status = MembershipStatus.REVOKED;
        this.revokedAt = clock.instant();
        bump(clock);
    }

    /** Change role (admin re-grant). Cannot demote the last OWNER — checked by E3.2c. */
    public void changeRole(MembershipRole newRole, java.time.Clock clock) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "cannot change role on a REVOKED membership");
        }
        this.role = Objects.requireNonNull(newRole, "newRole");
        bump(clock);
    }

    private void bump(java.time.Clock clock) {
        this.version += 1;
        this.updatedAt = clock.instant();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Membership other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Membership[id=" + id
                + ", tenant=" + tenantId
                + ", user=" + userId
                + ", role=" + role
                + ", status=" + status
                + ", version=" + version + "]";
    }
}