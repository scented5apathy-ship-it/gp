package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set Flagsmith sync outcome. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.flagsmithSyncOutcomes` (E6.3). The output of the
 * snapshot validator is one of these four values; the
 * executor refuses to apply a flag override unless the
 * snapshot is `IN_SYNC` (or the contract-supersedes-flag
 * toggle is enabled and the snapshot is `STALE` but the
 * contract is still valid).
 */
public enum FlagsmithSyncOutcome {
    IN_SYNC,
    STALE,
    DRIFT,
    MISSING;

    public static FlagsmithSyncOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("flagsmithSyncOutcome must not be null");
        }
        return FlagsmithSyncOutcome.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
