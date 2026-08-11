package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set Flagsmith rollout strategy. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.flagsmithRolloutStrategies` (E6.3) and
 * `requirements.md` R10.4 (policy changes flow through
 * Flagsmith only as a rollout switch — the YAML contract
 * remains the source of truth). The sync executor
 * validates the snapshot against this closed-set and
 * against the contract vocabulary before applying the
 * flag override.
 */
public enum FlagsmithRolloutStrategy {
    SAFE_DEFAULT,
    PROGRESSIVE,
    CANARY,
    KILL_SWITCH;

    public static FlagsmithRolloutStrategy fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("flagsmithRolloutStrategy must not be null");
        }
        return FlagsmithRolloutStrategy.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
