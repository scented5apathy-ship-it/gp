package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Date / calendar value attached to a {@link Person} (birth /
 * death / baptism) or to an {@code Event} (E4.5). Mirrors
 * {@code contracts/genealogy/date-place-policy.yaml} and
 * `requirements.md` R4.1 / R4.4 / R10.
 *
 * <p>The contract keeps FOUR orthogonal fields:
 *
 * <ol>
 *   <li>{@link #originalExpression} — the verbatim text the user
 *       entered (or the GEDCOM line, or the import parser's
 *       output). The renderer MUST be able to round-trip this
 *       byte-for-byte per R10 + NFR4.
 *   <li>{@link #calendar} + {@link #qualifier} + {@link #timezone} —
 *       the metadata the renderer needs to interpret the
 *       expression and to format it in the user's locale.
 *   <li>{@link #normalizedInterval} — the canonical UTC bounds so
 *       the database can answer "events between A and B" without
 *       re-parsing every row (`design.md` §5.3).
 *   <li>{@link #certainty} — provenance / confidence per
 *       `design.md` §5.3 (HYPOTHESIS / ASSERTED / VERIFIED /
 *       DISPUTED).
 * </ol>
 */
public record DateValue(
        String originalExpression,
        CalendarId calendar,
        DateQualifier qualifier,
        String timezone,
        NormalizedInterval normalizedInterval,
        Certainty certainty,
        Instant recordedAt) {

    /** Cap mirrors `date-place-policy.yaml::spec.maxOriginalExpressionChars`. */
    public static final int MAX_ORIGINAL_EXPRESSION_CHARS = 512;

    private static final java.util.regex.Pattern IANA_TZ_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z][A-Za-z0-9_+\\-/]{2,63}$");

    public DateValue {
        Objects.requireNonNull(originalExpression, "originalExpression");
        Objects.requireNonNull(calendar, "calendar");
        Objects.requireNonNull(qualifier, "qualifier");
        Objects.requireNonNull(timezone, "timezone");
        Objects.requireNonNull(normalizedInterval, "normalizedInterval");
        Objects.requireNonNull(certainty, "certainty");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (originalExpression.isBlank()) {
            throw new IllegalArgumentException("originalExpression must not be blank");
        }
        if (originalExpression.length() > MAX_ORIGINAL_EXPRESSION_CHARS) {
            throw new IllegalArgumentException(
                    "originalExpression exceeds "
                            + MAX_ORIGINAL_EXPRESSION_CHARS + " chars");
        }
        if (!IANA_TZ_PATTERN.matcher(timezone).matches()) {
            throw new IllegalArgumentException("timezone not IANA-shaped: " + timezone);
        }
        validateQualifierVsInterval(qualifier, normalizedInterval);
    }

    private static void validateQualifierVsInterval(
            DateQualifier qualifier, NormalizedInterval interval) {
        switch (qualifier) {
            case BEFORE -> {
                if (interval.latest() == null) {
                    throw new IllegalArgumentException(
                            "BEFORE requires NormalizedInterval.latest");
                }
            }
            case AFTER -> {
                if (interval.earliest() == null) {
                    throw new IllegalArgumentException(
                            "AFTER requires NormalizedInterval.earliest");
                }
            }
            case BETWEEN -> {
                if (interval.earliest() == null || interval.latest() == null) {
                    throw new IllegalArgumentException(
                            "BETWEEN requires both bounds");
                }
                if (interval.isPoint()) {
                    throw new IllegalArgumentException(
                            "BETWEEN must be a non-point interval");
                }
            }
            case EXACT, ABOUT, UNKNOWN -> {
                if (!interval.isPoint()) {
                    throw new IllegalArgumentException(
                            qualifier + " must be a point interval");
                }
            }
            default ->
                throw new IllegalArgumentException(
                        "unhandled qualifier: " + qualifier);
        }
    }

    public String displayKey() {
        return calendar.wire() + ":" + qualifier.wire() + ":" + timezone;
    }

    public DateValue withCertainty(Certainty next, Instant at) {
        return new DateValue(
                originalExpression, calendar, qualifier, timezone,
                normalizedInterval, next, at);
    }
}
