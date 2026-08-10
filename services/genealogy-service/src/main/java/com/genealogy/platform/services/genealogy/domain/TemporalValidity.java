package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Normalized temporal validity window for a
 * {@link Relationship}.
 *
 * <p>Mirrors `design.md` §5.3 (normalized interval, original
 * expression kept for renderer). A relationship that is
 * "still active" has {@code validUntil == null}; the renderer
 * MUST treat {@code validUntil == null} as open-ended.
 *
 * <p>The domain NEVER coerces a date to UTC silently — the
 * E4.3 {@code DateValue} carries the original expression +
 * IANA timezone + calendar; this record only stores the
 * normalized UTC bounds so the command service can sort /
 * check overlap without re-parsing a calendar.
 */
public record TemporalValidity(Instant validFrom, Instant validUntil) {

    public TemporalValidity {
        if (validFrom == null && validUntil == null) {
            throw new IllegalArgumentException(
                    "temporalValidity must carry at least one bound");
        }
        if (validFrom != null && validUntil != null && validFrom.isAfter(validUntil)) {
            throw new IllegalArgumentException(
                    "validFrom must be <= validUntil: " + validFrom + " > " + validUntil);
        }
    }

    /** {@code true} when the window is still open. */
    public boolean isOpenEnded() {
        return validUntil == null;
    }

    public boolean contains(Instant at) {
        Objects.requireNonNull(at, "at");
        if (validFrom != null && at.isBefore(validFrom)) {
            return false;
        }
        return validUntil == null || !at.isAfter(validUntil);
    }

    public boolean overlaps(TemporalValidity other) {
        Objects.requireNonNull(other, "other");
        if (this.validUntil != null && other.validFrom != null
                && this.validUntil.isBefore(other.validFrom)) {
            return false;
        }
        if (other.validUntil != null && this.validFrom != null
                && other.validUntil.isBefore(this.validFrom)) {
            return false;
        }
        return true;
    }
}
