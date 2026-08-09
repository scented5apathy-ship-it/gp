package com.genealogy.platform.services.tenant.domain.invitation;

import com.genealogy.platform.services.tenant.domain.ids.IdGenerator;
import com.genealogy.platform.services.tenant.domain.ids.InvitationId;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.ids.UserId;
import com.genealogy.platform.services.tenant.domain.membership.MembershipRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Pending invitation aggregate. The invitation links an email + role
 * + idempotency-key to a future {@link com.genealogy.platform.services.tenant.domain.membership.Membership}
 * row; on acceptance E3.2c materialises the membership and emits
 * the {@code MembershipInvited -> MembershipActivated} event pair.
 *
 * <p>Invitations are tenant-scoped (V2 migration) and the
 * {@link #idempotencyKey} matches the REST {@code Idempotency-Key}
 * header (E3.2d) so retries from the same caller collapse into the
 * same invitation row.
 *
 * <p>The aggregate holds the expiry timestamp; {@link #isExpired}
 * is computed at acceptance time. The token hash is the salted hash
 * of the raw token — see {@link TokenHash}.
 */
public final class Invitation {

    /**
     * Default invitation TTL — 7 days per R1 acceptance criterion 2
     * ("email/link có hạn dùng"). E3.2c exposes a config flag
     * {@code platform.tenant.invitation.ttl-days} so operators can
     * shorten it for high-risk tenants without redeploying the
     * service.
     */
    public static final Duration DEFAULT_TTL = Duration.ofDays(7);

    private final InvitationId id;
    private final TenantId tenantId;
    private final Email email;
    private final MembershipRole role;
    private final TokenHash tokenHash;
    private final String idempotencyKey;
    private final UserId invitedByUserId;
    private final Instant expiresAt;
    private Instant acceptedAt;
    private Instant revokedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Invitation(
            InvitationId id,
            TenantId tenantId,
            Email email,
            MembershipRole role,
            TokenHash tokenHash,
            String idempotencyKey,
            UserId invitedByUserId,
            Instant expiresAt,
            Instant acceptedAt,
            Instant revokedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.email = Objects.requireNonNull(email, "email");
        this.role = Objects.requireNonNull(role, "role");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        this.invitedByUserId = Objects.requireNonNull(invitedByUserId, "invitedByUserId");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.acceptedAt = acceptedAt;
        this.revokedAt = revokedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Factory for a new invitation. {@code expiresAt} defaults to
     * {@code now + DEFAULT_TTL} so the most common case is a single
     * argument.
     */
    public static Invitation create(
            IdGenerator idGenerator,
            TenantId tenantId,
            Email email,
            MembershipRole role,
            TokenHash tokenHash,
            String idempotencyKey,
            UserId invitedByUserId,
            Clock clock) {
        return create(idGenerator, tenantId, email, role, tokenHash,
                idempotencyKey, invitedByUserId, DEFAULT_TTL, clock);
    }

    public static Invitation create(
            IdGenerator idGenerator,
            TenantId tenantId,
            Email email,
            MembershipRole role,
            TokenHash tokenHash,
            String idempotencyKey,
            UserId invitedByUserId,
            Duration ttl,
            Clock clock) {
        Objects.requireNonNull(idGenerator, "idGenerator");
        Objects.requireNonNull(ttl, "ttl");
        Objects.requireNonNull(clock, "clock");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be > 0");
        }
        Instant now = clock.instant();
        return new Invitation(
                new InvitationId(idGenerator.nextId()),
                tenantId,
                email,
                role,
                tokenHash,
                idempotencyKey,
                invitedByUserId,
                now.plus(ttl),
                null,
                null,
                now,
                now);
    }

    public static Invitation rehydrate(
            InvitationId id,
            TenantId tenantId,
            Email email,
            MembershipRole role,
            TokenHash tokenHash,
            String idempotencyKey,
            UserId invitedByUserId,
            Instant expiresAt,
            Instant acceptedAt,
            Instant revokedAt,
            Instant createdAt,
            Instant updatedAt) {
        if (acceptedAt != null && revokedAt != null) {
            throw new IllegalStateException(
                    "invitation cannot be both accepted and revoked");
        }
        return new Invitation(id, tenantId, email, role, tokenHash,
                idempotencyKey, invitedByUserId,
                expiresAt, acceptedAt, revokedAt,
                createdAt, updatedAt);
    }

    public InvitationId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public Email email() {
        return email;
    }

    public MembershipRole role() {
        return role;
    }

    public TokenHash tokenHash() {
        return tokenHash;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public UserId invitedByUserId() {
        return invitedByUserId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean isExpired(Clock clock) {
        return clock.instant().isAfter(expiresAt);
    }

    public boolean isPending() {
        return acceptedAt == null && revokedAt == null;
    }

    public void markAccepted(Clock clock) {
        if (!isPending()) {
            throw new IllegalStateException("invitation is not pending");
        }
        if (isExpired(clock)) {
            throw new IllegalStateException("invitation has expired");
        }
        this.acceptedAt = clock.instant();
        this.updatedAt = this.acceptedAt;
    }

    public void revoke(Clock clock) {
        if (!isPending()) {
            throw new IllegalStateException("invitation is not pending");
        }
        this.revokedAt = clock.instant();
        this.updatedAt = this.revokedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Invitation other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Invitation[id=" + id
                + ", tenant=" + tenantId
                + ", email=" + email.value()
                + ", role=" + role
                + ", expiresAt=" + expiresAt + "]";
    }
}