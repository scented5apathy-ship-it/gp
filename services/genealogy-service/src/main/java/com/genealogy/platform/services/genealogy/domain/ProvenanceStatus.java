package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set of provenance statuses. Mirrors
 * {@code contracts/genealogy/relationship-graph-policy.yaml::
 * spec.provenanceStatuses}.
 *
 * <p>Distinct from {@link Certainty}: certainty describes how
 * sure we are, provenance describes where the row came from.
 * A row can be {@code CERTAINTY=ASSERTED} but
 * {@code PROVENANCE=IMPORTED} (e.g. a GEDCOM import) — the
 * two axes are independent so the merge / dispute workflow
 * (E4.6) can replay them.
 */
public enum ProvenanceStatus {
    USER_ENTERED,
    IMPORTED,
    VERIFIED_BY_SOURCE,
    CORRECTION;

    public static ProvenanceStatus fromWire(String wire) {
        if (wire == null) {
            return USER_ENTERED;
        }
        return ProvenanceStatus.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    public boolean isImported() {
        return this == IMPORTED;
    }
}
