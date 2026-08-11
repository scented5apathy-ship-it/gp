package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set merge outcome classification. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.mergeOutcomeKinds` (E6.3) and `requirements.md`
 * R10.3 + `design.md` §8.3 (merge produces a new domain
 * command rather than an arbitrary JSON patch on a
 * forbidden field). The executor refuses any merge that
 * touches a forbidden field / operation, has a stale base
 * version, or references a resource id that is not in the
 * proposal scope.
 */
public enum MergeOutcomeKind {
    AUTO_MERGED,
    MANUAL_MERGED,
    ABANDONED,
    FORBIDDEN_FIELD,
    FORBIDDEN_OPERATION,
    BASE_VERSION_STALE,
    RESOURCE_ID_NOT_IN_SCOPE;

    public static MergeOutcomeKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("mergeOutcomeKind must not be null");
        }
        return MergeOutcomeKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
