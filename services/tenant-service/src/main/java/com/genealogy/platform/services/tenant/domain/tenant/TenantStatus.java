package com.genealogy.platform.services.tenant.domain.tenant;

import java.time.Instant;
import java.util.Objects;

/**
 * Tenant lifecycle status. Mirrors the gRPC {@code TenantStatus}
 * enum and the Avro {@code TenantStatus} enum. State machine:
 *
 * <pre>
 *   ACTIVE ──suspend──▶ SUSPENDED ──restore──▶ ACTIVE
 *      │                                          │
 *      └──────────────delete──────────────────────┘
 *                          │
 *                          ▼
 *                       DELETED  (terminal)
 * </pre>
 *
 * <p>Transitions are validated by {@link Tenant#suspend},
 * {@link Tenant#restore}, {@link Tenant#softDelete}; the repository
 * never writes {@code status} without first validating the
 * transition through these methods. The V2 migration CHECK
 * constraints enforce the same consistency on the database side:
 * {@code suspended_at IS NOT NULL ⇔ status = 'SUSPENDED'} and
 * {@code deleted_at IS NOT NULL ⇔ status = 'DELETED'}.
 */
public enum TenantStatus {

    ACTIVE,
    SUSPENDED,
    DELETED;

    public boolean canTransitionTo(TenantStatus next) {
        Objects.requireNonNull(next, "next status");
        if (this == next) {
            return false; // idempotent transitions go through restore() etc.
        }
        return switch (this) {
            case ACTIVE -> next == SUSPENDED || next == DELETED;
            case SUSPENDED -> next == ACTIVE || next == DELETED;
            case DELETED -> false;
            default -> false; // future-proof: unknown enum values reject all transitions
        };
    }

    public boolean isTerminal() {
        return this == DELETED;
    }

    /**
     * Mark the timestamp that MUST accompany this status per the V2
     * migration CHECK. Returns null when no timestamp is required.
     */
    public Instant expectedTimestampMarker() {
        return null; // timestamps are domain decisions; see Tenant
    }
}