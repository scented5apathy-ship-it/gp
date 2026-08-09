package com.genealogy.platform.services.tenant.domain.membership;

import java.util.Objects;

/**
 * Per-membership lifecycle status. Mirrors the gRPC
 * {@code MembershipStatus} enum and the Avro {@code MembershipStatus}
 * enum. The V2 migration CHECK constraint enforces that the right
 * timestamp is populated:
 *
 * <ul>
 *   <li>{@code INVITED}  ⇒ {@code invited_at IS NOT NULL}</li>
 *   <li>{@code ACTIVE}   ⇒ {@code joined_at  IS NOT NULL}</li>
 *   <li>{@code SUSPENDED} ⇒ {@code joined_at IS NOT NULL} AND {@code suspended_at IS NOT NULL}</li>
 *   <li>{@code REVOKED}   ⇒ {@code revoked_at IS NOT NULL}</li>
 * </ul>
 *
 * <p>State machine:
 *
 * <pre>
 *   INVITED ──accept──▶ ACTIVE ──suspend──▶ SUSPENDED ──restore──▶ ACTIVE
 *      │                  │                                            │
 *      │                  └────────────revoke──────────────────────────┤
 *      │                                                               │
 *      └───────────────────────revoke──────────────────────────────────┘
 *                                  │
 *                                  ▼
 *                              REVOKED  (terminal)
 * </pre>
 *
 * <p>{@link #SUSPENDED} and {@link #REVOKED} can also be reached from
 * {@link #INVITED} (admin revocation of an unaccepted invite).
 */
public enum MembershipStatus {

    INVITED,
    ACTIVE,
    SUSPENDED,
    REVOKED;

    public boolean isTerminal() {
        return this == REVOKED;
    }

    public boolean canTransitionTo(MembershipStatus next) {
        Objects.requireNonNull(next, "next");
        if (this == next) {
            return false;
        }
        return switch (this) {
            case INVITED -> next == ACTIVE || next == REVOKED;
            case ACTIVE -> next == SUSPENDED || next == REVOKED;
            case SUSPENDED -> next == ACTIVE || next == REVOKED;
            case REVOKED -> false;
            default -> false; // future-proof: unknown enum values reject all transitions
        };
    }
}