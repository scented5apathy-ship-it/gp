package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Normalized UTC instant used for sort / range queries per
 * `design.md` §5.3. The original expression + calendar +
 * timezone are kept on the enclosing {@link DateValue}; the
 * interval carries the canonical UTC bounds so the database can
 * answer "events between A and B" without re-parsing every row.
 *
 * <p>Either bound MAY be {@code null} to represent an
 * open-ended {@link DateQualifier#AFTER} or {@link DateQualifier#BEFORE}.
 * For {@link DateQualifier#EXACT} / {@link DateQualifier#ABOUT} /
 * {@link DateQualifier#UNKNOWN}, both bounds are equal.
 */
public record NormalizedInterval(Instant earliest, Instant latest) {

    public NormalizedInterval {
        if (earliest == null && latest == null) {
            throw new IllegalArgumentException(
                    "normalized interval must have at least one bound");
        }
        if (earliest != null && latest != null && earliest.isAfter(latest)) {
            throw new IllegalArgumentException(
                    "earliest must be <= latest: " + earliest + " > " + latest);
        }
    }

    /** Point-in-time interval ({@link DateQualifier#EXACT}). */
    public static NormalizedInterval point(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return new NormalizedInterval(instant, instant);
    }

    /** Open-ended lower bound ({@link DateQualifier#BEFORE}). */
    public static NormalizedInterval openUpper(Instant latest) {
        Objects.requireNonNull(latest, "latest");
        return new NormalizedInterval(null, latest);
    }

    /** Open-ended upper bound ({@link DateQualifier#AFTER}). */
    public static NormalizedInterval openLower(Instant earliest) {
        Objects.requireNonNull(earliest, "earliest");
        return new NormalizedInterval(earliest, null);
    }

    public boolean isPoint() {
        return earliest != null && earliest.equals(latest);
    }
}
