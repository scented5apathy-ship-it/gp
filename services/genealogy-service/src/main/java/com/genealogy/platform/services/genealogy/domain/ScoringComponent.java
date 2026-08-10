package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set of merge scoring components. Mirrors
 * {@code contracts/genealogy/person-merge-policy.yaml::
 * spec.scoringComponents}.
 *
 * <p>Each candidate row carries a per-component value in
 * [0,1]; the merge scorer multiplies the value by the
 * component weight from the contract and sums the
 * contributions to produce the candidate's overall score
 * (also in [0,1]).
 */
public enum ScoringComponent {
    NAME_EQUALITY,
    DATE_PROXIMITY,
    PLACE_PROXIMITY,
    IDENTIFIER_MATCH;

    public static ScoringComponent fromWire(String wire) {
        if (wire == null || wire.isBlank()) {
            return ScoringComponent.NAME_EQUALITY;
        }
        return ScoringComponent.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
