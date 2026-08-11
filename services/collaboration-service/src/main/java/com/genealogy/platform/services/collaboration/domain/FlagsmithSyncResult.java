package com.genealogy.platform.services.collaboration.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Output of the Flagsmith sync executor. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.flagsmithSyncOutcomes` (E6.3) and `requirements.md`
 * R10.4 (policy changes flow through Flagsmith only as a
 * rollout switch — the YAML contract remains the source
 * of truth).
 */
public record FlagsmithSyncResult(
        FlagsmithSyncOutcome outcome,
        String snapshotVersion,
        FlagsmithRolloutStrategy strategy,
        Instant syncedAt,
        long snapshotAgeSeconds,
        String reasonCode) {

    public FlagsmithSyncResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(snapshotVersion, "snapshotVersion");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(syncedAt, "syncedAt");
        Objects.requireNonNull(reasonCode, "reasonCode");
        if (outcome != FlagsmithSyncOutcome.MISSING && snapshotVersion.isBlank()) {
            throw new IllegalArgumentException("snapshotVersion must not be blank");
        }
        if (reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        if (reasonCode.length() > 128) {
            throw new IllegalArgumentException("reasonCode exceeds 128 characters");
        }
        if (snapshotAgeSeconds < 0) {
            throw new IllegalArgumentException(
                    "snapshotAgeSeconds must be non-negative, got " + snapshotAgeSeconds);
        }
    }
}
