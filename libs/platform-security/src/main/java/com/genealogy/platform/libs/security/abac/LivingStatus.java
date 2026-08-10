package com.genealogy.platform.libs.security.abac;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Living status of a person record per
 * {@code glossary-and-policy-matrix.md} §3 living rules.
 *
 * <p>The triplet ({@link #status}, {@link #birthDate},
 * {@link #deathDate}) is the minimum ABAC input that downstream
 * policy uses to apply living / minor redaction on {@code PUBLIC}
 * projections (R4, privacy gate §7 P-01).
 */
public record LivingStatus(
        Status status,
        LocalDate birthDate,
        LocalDate deathDate,
        ZoneId birthDateJurisdiction) {

    public enum Status {
        LIVING,
        DECEASED,
        UNKNOWN
    }

    public LivingStatus {
        Objects.requireNonNull(status, "status");
    }

    public static LivingStatus living(LocalDate birthDate, ZoneId jurisdiction) {
        return new LivingStatus(Status.LIVING, birthDate, null, jurisdiction);
    }

    public static LivingStatus deceased(LocalDate birthDate, LocalDate deathDate) {
        if (deathDate != null && birthDate != null && deathDate.isBefore(birthDate)) {
            throw new IllegalArgumentException(
                    "deathDate " + deathDate + " predates birthDate " + birthDate);
        }
        return new LivingStatus(Status.DECEASED, birthDate, deathDate, null);
    }

    public static LivingStatus unknown() {
        return new LivingStatus(Status.UNKNOWN, null, null, null);
    }

    /**
     * Returns {@code true} when the person is a minor at the
     * supplied evaluation date under the birth-date jurisdiction.
     * Defaults to {@code true} when the jurisdiction cannot be
     * resolved — fail-closed is the privacy posture for minors.
     */
    public boolean isMinor(LocalDate evaluationDate) {
        Objects.requireNonNull(evaluationDate, "evaluationDate");
        if (status != Status.LIVING || birthDate == null) {
            return false;
        }
        LocalDate cutoff = evaluationDate.minusYears(18);
        return birthDate.isAfter(cutoff);
    }

    /**
     * Convenience overload for {@link #isMinor(LocalDate)} with a
     * derived age in years.
     */
    public int ageInYears(LocalDate evaluationDate) {
        if (birthDate == null) {
            return -1;
        }
        return Period.between(birthDate, evaluationDate).getYears();
    }
}
