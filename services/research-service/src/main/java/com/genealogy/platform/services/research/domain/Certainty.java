package com.genealogy.platform.services.research.domain;

import java.util.Objects;

/**
 * Certainty attached to a {@code Citation} or {@code ResearchTask}.
 * Mirrors `contracts/research/research-policy.yaml::
 * spec.certainties` (E6.1) + `requirements.md` R4.4 (every
 * claim carries a certainty).
 *
 * <p>The vocabulary is intentionally aligned with the
 * genealogy-service certainties so the provenance query can
 * join across services via the contract (the YAML is the
 * cross-service source of truth, not the Java class). The
 * research-service MUST NOT import
 * {@code com.genealogy.platform.services.genealogy.domain}.
 */
public enum Certainty {
    HYPOTHESIS,
    ASSERTED,
    VERIFIED,
    DISPUTED;

    public static Certainty fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("certainty must not be null");
        }
        return Certainty.valueOf(wire.trim().toUpperCase(java.util.Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    /**
     * Returns {@code true} when the certainty is strong enough
     * to be carried into a public projection (E8.3).
     */
    public boolean isPublishable() {
        return this == ASSERTED || this == VERIFIED;
    }
}
