package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Reference to a participant in a {@link Relationship}.
 *
 * <p>The participant may be a real {@link Person} (identified
 * by {@code personId}) or the synthetic {@code UNKNOWN}
 * placeholder (identified by a non-Person opaque label) — the
 * latter exists so a family with a missing parent can still
 * attach a date / place / certainty (R4.4 / Epic DoD
 * "unknown participant").
 *
 * <p>{@code participantId} is a synthetic opaque id stable
 * across the lifetime of the relationship so consumers can
 * diff participants without re-reading the whole aggregate.
 */
public record ParticipantRef(
        String participantId,
        ParticipantRole role,
        String personId,
        boolean unknown,
        Instant recordedAt) {

    public ParticipantRef {
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (personId == null && !unknown) {
            throw new IllegalArgumentException(
                    "participantRef requires personId unless unknown=true");
        }
        if (personId != null && unknown) {
            throw new IllegalArgumentException(
                    "participantRef with unknown=true must NOT carry a personId");
        }
    }
}
