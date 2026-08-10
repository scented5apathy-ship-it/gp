package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set of merge record provenances. Mirrors
 * {@code contracts/genealogy/person-merge-policy.yaml::
 * spec.mergeProvenances}.
 *
 * <p>Tracks how the merge was proposed (R4.5 + R10): the
 * renderer surfaces the chip so the operator knows whether
 * the candidate came from the automated scorer, a manual
 * review, an import flow, or a correction to a prior merge.
 */
public enum MergeProvenance {
    USER_REVIEW,
    AUTOMATED_SCORER,
    IMPORTED,
    CORRECTION;

    public static MergeProvenance fromWire(String wire) {
        if (wire == null || wire.isBlank()) {
            return MergeProvenance.USER_REVIEW;
        }
        return MergeProvenance.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
