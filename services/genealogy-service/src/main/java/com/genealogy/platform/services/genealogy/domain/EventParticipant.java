package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One participant in a {@link LifeEvent}. Mirrors
 * `requirements.md` R4.1 (many participants with roles).
 *
 * <p>An {@code EventParticipant} differs from
 * {@link ParticipantRef} (Relationship) only in the role
 * vocabulary: events add WITNESS / OFFICIANT / INFORMANT.
 * The (role, personId|unknown) pair must be unique within a
 * single event so the renderer never has to disambiguate
 * two rows that point to the same person in the same role.
 */
public record EventParticipant(
        String participantId,
        EventParticipantRole role,
        String personId,
        boolean unknown,
        Instant recordedAt) {

    public EventParticipant {
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (unknown && personId != null) {
            throw new IllegalArgumentException(
                    "unknown=true requires personId=null");
        }
        if (!unknown && personId == null) {
            throw new IllegalArgumentException(
                    "unknown=false requires non-null personId");
        }
    }
}
