package com.genealogy.platform.services.collaboration.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Pure Flagsmith sync executor. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.flagsmithSyncOutcomes` (E6.3) and `requirements.md`
 * R10.4 (policy changes flow through Flagsmith only as a
 * rollout switch — the YAML contract remains the source
 * of truth, never the Flagsmith flag value).
 *
 * <p>Sync rules:
 * <ul>
 *   <li>A snapshot older than `flagsmithSnapshotMaxAgeSeconds`
 *       yields {@code STALE} (audit reason
 *       `FLAGSMITH_SNAPSHOT_STALE`).
 *   <li>A missing snapshot yields {@code MISSING} (audit
 *       reason `FLAGSMITH_SNAPSHOT_MISSING`).
 *   <li>A snapshot whose strategy is not in the contract
 *       closed-set yields `DRIFT` (audit reason
 *       `FLAGSMITH_STRATEGY_NOT_PERMITTED`).
 *   <li>A snapshot whose `enabled=true` but whose
 *       `strategy == KILL_SWITCH` flips the safe-default
 *       off (audit reason `FLAGSMITH_KILL_SWITCH_ACTIVE`).
 *   <li>A snapshot whose `strategy` is not
 *       `SAFE_DEFAULT` (and the contract-supersedes-flag
 *       toggle is false) yields `STALE` (audit reason
 *       `FLAGSMITH_ROLLOUT_NOT_YET_ENABLED`).
 *   <li>Otherwise the result is `IN_SYNC` (audit reason
 *       `FLAGSMITH_SNAPSHOT_IN_SYNC`).
 * </ul>
 *
 * <p>The executor refuses to apply a flag override that
 * contradicts the contract: the
 * `flagsmithRolloutContractSupersedesFlag` toggle is
 * pinned to true in the YAML contract and enforced by
 * the contract supersedes rule (strategy not in the
 * closed-set yields DRIFT).
 */
public final class FlagsmithRolloutSync {

    private FlagsmithRolloutSync() {
    }

    public static FlagsmithSyncResult sync(
            FlagsmithSnapshot snapshot,
            Instant now,
            long maxSnapshotAgeSeconds,
            Set<FlagsmithRolloutStrategy> permittedStrategies,
            boolean contractSupersedesFlag) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(permittedStrategies, "permittedStrategies");
        if (maxSnapshotAgeSeconds <= 0) {
            throw new IllegalArgumentException("maxSnapshotAgeSeconds must be positive");
        }
        if (snapshot == null) {
            return new FlagsmithSyncResult(
                    FlagsmithSyncOutcome.MISSING,
                    "",
                    FlagsmithRolloutStrategy.SAFE_DEFAULT,
                    now,
                    0L,
                    "FLAGSMITH_SNAPSHOT_MISSING");
        }
        long ageSeconds = Math.max(0L, Duration.between(snapshot.capturedAt(), now).getSeconds());
        if (ageSeconds > maxSnapshotAgeSeconds) {
            return new FlagsmithSyncResult(
                    FlagsmithSyncOutcome.STALE,
                    snapshot.snapshotVersion(),
                    snapshot.strategy(),
                    now,
                    ageSeconds,
                    "FLAGSMITH_SNAPSHOT_STALE");
        }
        if (!permittedStrategies.contains(snapshot.strategy())) {
            return new FlagsmithSyncResult(
                    FlagsmithSyncOutcome.DRIFT,
                    snapshot.snapshotVersion(),
                    snapshot.strategy(),
                    now,
                    ageSeconds,
                    "FLAGSMITH_STRATEGY_NOT_PERMITTED");
        }
        if (snapshot.strategy() == FlagsmithRolloutStrategy.KILL_SWITCH && snapshot.enabled()) {
            return new FlagsmithSyncResult(
                    FlagsmithSyncOutcome.DRIFT,
                    snapshot.snapshotVersion(),
                    snapshot.strategy(),
                    now,
                    ageSeconds,
                    "FLAGSMITH_KILL_SWITCH_ACTIVE");
        }
        if (contractSupersedesFlag
                && snapshot.strategy() != FlagsmithRolloutStrategy.SAFE_DEFAULT
                && !snapshot.enabled()) {
            return new FlagsmithSyncResult(
                    FlagsmithSyncOutcome.STALE,
                    snapshot.snapshotVersion(),
                    snapshot.strategy(),
                    now,
                    ageSeconds,
                    "FLAGSMITH_ROLLOUT_NOT_YET_ENABLED");
        }
        return new FlagsmithSyncResult(
                FlagsmithSyncOutcome.IN_SYNC,
                snapshot.snapshotVersion(),
                snapshot.strategy(),
                now,
                ageSeconds,
                "FLAGSMITH_SNAPSHOT_IN_SYNC");
    }
}
