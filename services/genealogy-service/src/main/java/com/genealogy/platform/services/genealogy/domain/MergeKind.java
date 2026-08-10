package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set of person merge kinds. Mirrors
 * {@code contracts/genealogy/person-merge-policy.yaml::
 * spec.mergeKinds} and `requirements.md` R4.5 + `glossary-
 * and-policy-matrix.md` §2.4 (`MERGE` rule).
 *
 * <p>E4.6 lands {@link #DUPLICATE_PERSON_MERGE} only. Other
 * merge kinds (relationship / event / claim / source) are
 * deferred to follow-up tasks per the E4.6 scope guard; the
 * closed-set is preserved so the merge service vocabulary
 * stays deterministic.
 */
public enum MergeKind {
    DUPLICATE_PERSON_MERGE;

    public static MergeKind fromWire(String wire) {
        if (wire == null || wire.isBlank()) {
            return MergeKind.DUPLICATE_PERSON_MERGE;
        }
        return MergeKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
