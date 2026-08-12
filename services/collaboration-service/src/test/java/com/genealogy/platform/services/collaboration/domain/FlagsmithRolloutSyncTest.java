package com.genealogy.platform.services.collaboration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Flagsmith rollout sync tests (E6.3). Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml`
 * ::spec.{flagsmithRolloutStrategies, flagsmithSyncOutcomes,
 * flagsmithSyncIntervalSeconds, flagsmithSnapshotMaxAgeSeconds,
 * flagsmithRolloutContractSupersedesFlag,
 * flagsmithKillSwitchRefusesAllDirectEdits} and
 * `requirements.md` R10.4.
 */
class FlagsmithRolloutSyncTest {

    private static FlagsmithSnapshot snapshot(
            FlagsmithRolloutStrategy strategy,
            boolean enabled,
            Instant capturedAt) {
        return new FlagsmithSnapshot(
                "collab.mixedPolicy.v2",
                "production",
                "v1",
                strategy,
                enabled,
                Map.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                capturedAt);
    }

    @Test
    void safeDefaultInSync() {
        FlagsmithSyncResult r = FlagsmithRolloutSync.sync(
                snapshot(FlagsmithRolloutStrategy.SAFE_DEFAULT, false, Instant.now()),
                Instant.now(),
                900L,
                Set.of(FlagsmithRolloutStrategy.SAFE_DEFAULT),
                true);
        assertEquals(FlagsmithSyncOutcome.IN_SYNC, r.outcome());
        assertEquals("FLAGSMITH_SNAPSHOT_IN_SYNC", r.reasonCode());
    }

    @Test
    void staleSnapshotDetected() {
        FlagsmithSyncResult r = FlagsmithRolloutSync.sync(
                snapshot(
                        FlagsmithRolloutStrategy.SAFE_DEFAULT,
                        false,
                        Instant.now().minusSeconds(3600)),
                Instant.now(),
                900L,
                Set.of(FlagsmithRolloutStrategy.SAFE_DEFAULT),
                true);
        assertEquals(FlagsmithSyncOutcome.STALE, r.outcome());
        assertEquals("FLAGSMITH_SNAPSHOT_STALE", r.reasonCode());
        assertEquals(3600L, r.snapshotAgeSeconds());
    }

    @Test
    void missingSnapshotDetected() {
        FlagsmithSyncResult r = FlagsmithRolloutSync.sync(
                null,
                Instant.now(),
                900L,
                Set.of(FlagsmithRolloutStrategy.SAFE_DEFAULT),
                true);
        assertEquals(FlagsmithSyncOutcome.MISSING, r.outcome());
        assertEquals("FLAGSMITH_SNAPSHOT_MISSING", r.reasonCode());
    }

    @Test
    void unknownStrategyYieldDrift() {
        FlagsmithSyncResult r = FlagsmithRolloutSync.sync(
                snapshot(FlagsmithRolloutStrategy.KILL_SWITCH, false, Instant.now()),
                Instant.now(),
                900L,
                Set.of(FlagsmithRolloutStrategy.SAFE_DEFAULT),
                true);
        assertEquals(FlagsmithSyncOutcome.DRIFT, r.outcome());
        assertEquals("FLAGSMITH_STRATEGY_NOT_PERMITTED", r.reasonCode());
    }

    @Test
    void killSwitchEnabledRejected() {
        FlagsmithSyncResult r = FlagsmithRolloutSync.sync(
                snapshot(FlagsmithRolloutStrategy.KILL_SWITCH, true, Instant.now()),
                Instant.now(),
                900L,
                Set.of(
                        FlagsmithRolloutStrategy.SAFE_DEFAULT,
                        FlagsmithRolloutStrategy.KILL_SWITCH),
                true);
        assertEquals(FlagsmithSyncOutcome.DRIFT, r.outcome());
        assertEquals("FLAGSMITH_KILL_SWITCH_ACTIVE", r.reasonCode());
    }

    @Test
    void contractSupersedesFlagBlocksRolloutNotYetEnabled() {
        FlagsmithSyncResult r = FlagsmithRolloutSync.sync(
                snapshot(FlagsmithRolloutStrategy.PROGRESSIVE, false, Instant.now()),
                Instant.now(),
                900L,
                Set.of(
                        FlagsmithRolloutStrategy.SAFE_DEFAULT,
                        FlagsmithRolloutStrategy.PROGRESSIVE),
                true);
        assertEquals(FlagsmithSyncOutcome.STALE, r.outcome());
        assertEquals("FLAGSMITH_ROLLOUT_NOT_YET_ENABLED", r.reasonCode());
    }

    @Test
    void rejectsZeroMaxAge() {
        assertThrows(IllegalArgumentException.class, () -> FlagsmithRolloutSync.sync(
                snapshot(FlagsmithRolloutStrategy.SAFE_DEFAULT, false, Instant.now()),
                Instant.now(),
                0L,
                Set.of(FlagsmithRolloutStrategy.SAFE_DEFAULT),
                true));
    }

    @Test
    void progressiveEnabledInSyncWhenContractAllows() {
        FlagsmithSyncResult r = FlagsmithRolloutSync.sync(
                snapshot(FlagsmithRolloutStrategy.PROGRESSIVE, true, Instant.now()),
                Instant.now(),
                900L,
                Set.of(
                        FlagsmithRolloutStrategy.SAFE_DEFAULT,
                        FlagsmithRolloutStrategy.PROGRESSIVE),
                true);
        assertEquals(FlagsmithSyncOutcome.IN_SYNC, r.outcome());
        assertEquals("FLAGSMITH_SNAPSHOT_IN_SYNC", r.reasonCode());
    }
}
