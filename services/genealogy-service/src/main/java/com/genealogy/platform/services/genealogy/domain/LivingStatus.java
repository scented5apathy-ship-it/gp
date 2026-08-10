package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Closed-set living status for a {@link Person}. Mirrors
 * {@code contracts/genealogy/person-policy.yaml::spec.livingStatuses}
 * and `requirements.md` R4.1 / R4.4.
 *
 * <p>{@link #LIVING} and {@link #INFERRED_LIVING} force the
 * platform to apply living-person redaction before the row is
 * exposed to PUBLIC / UNLISTED projections (see
 * {@code design.md} §6.3 and {@code design.md} §6.2 obligations).
 */
public enum LivingStatus {
    LIVING,
    DECEASED,
    UNKNOWN,
    INFERRED_LIVING;

    public static LivingStatus fromWire(String wire) {
        if (wire == null) {
            return UNKNOWN;
        }
        return LivingStatus.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    /**
     * Living-person predicate per `design.md` §6.2. Both
     * {@link #LIVING} and {@link #INFERRED_LIVING} force
     * redaction in PUBLIC / UNLISTED projections.
     */
    public boolean isLiving() {
        return this == LIVING || this == INFERRED_LIVING;
    }

    public static LivingStatus require(Object ignored, LivingStatus value) {
        Objects.requireNonNull(value, "livingStatus");
        return value;
    }
}
