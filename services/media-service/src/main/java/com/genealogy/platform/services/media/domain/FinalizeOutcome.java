package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of finalize outcomes. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.finalizeOutcomes` (E7.1) + `design.md` §8.2.
 *
 * <p>Only {@link #READY} assets are linked to persons /
 * events / sources (E7.2). {@link #REJECTED} closes the
 * session with an audit reason; {@link #QUARANTINED} is the
 * initial state until the worker pipeline completes and
 * promotes the asset to {@code READY} or {@code REJECTED}.
 */
public enum FinalizeOutcome {
    READY,
    REJECTED,
    QUARANTINED,
    FAILED;

    public static FinalizeOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return FinalizeOutcome.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown FinalizeOutcome from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}
