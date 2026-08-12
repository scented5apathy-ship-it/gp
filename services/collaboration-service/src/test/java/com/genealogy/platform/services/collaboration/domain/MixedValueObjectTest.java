package com.genealogy.platform.services.collaboration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * E6.3 value-object + executor tests. Pins every
 * compact-constructor rejection for the routing / merge /
 * Flagsmith records.
 */
class MixedValueObjectTest {

    @Test
    void routingRequestRejectsBlankResourceId() {
        assertThrows(IllegalArgumentException.class, () -> new RoutingRequest(
                new TenantScopedId("t", TenantScopedId.ResourceKind.PROPOSAL, "p"),
                CollaborationRole.EDITOR,
                TreeBranch.TRUNK,
                RoutingResourceType.LIFE_EVENT,
                "",
                1L));
    }

    @Test
    void routingRequestRejectsForbiddenCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new RoutingRequest(
                new TenantScopedId("t", TenantScopedId.ResourceKind.PROPOSAL, "p"),
                CollaborationRole.EDITOR,
                TreeBranch.TRUNK,
                RoutingResourceType.LIFE_EVENT,
                "bad/id",
                1L));
    }

    @Test
    void routingRequestRejectsNonPositiveBaseVersion() {
        assertThrows(IllegalArgumentException.class, () -> new RoutingRequest(
                new TenantScopedId("t", TenantScopedId.ResourceKind.PROPOSAL, "p"),
                CollaborationRole.EDITOR,
                TreeBranch.TRUNK,
                RoutingResourceType.LIFE_EVENT,
                "p-1",
                0L));
    }

    @Test
    void routingDecisionRejectsBlankReasonCode() {
        assertThrows(IllegalArgumentException.class, () -> new RoutingDecisionRecord(
                RoutingDecision.DIRECT_EDIT,
                CollaborationRole.EDITOR,
                TreeBranch.TRUNK,
                RoutingResourceType.LIFE_EVENT,
                "p-1",
                1L,
                ""));
    }

    @Test
    void routingDecisionRejectsBlankResourceId() {
        assertThrows(IllegalArgumentException.class, () -> new RoutingDecisionRecord(
                RoutingDecision.DIRECT_EDIT,
                CollaborationRole.EDITOR,
                TreeBranch.TRUNK,
                RoutingResourceType.LIFE_EVENT,
                "",
                1L,
                "OK"));
    }

    @Test
    void conflictComparisonRejectsBlankField() {
        assertThrows(IllegalArgumentException.class, () -> new ConflictComparison(
                "p-1", "", ConflictFieldKind.SAME, "a", "a", "a"));
    }

    @Test
    void conflictComparisonRejectsFieldOverLength() {
        assertThrows(IllegalArgumentException.class, () -> new ConflictComparison(
                "p-1", "x".repeat(65), ConflictFieldKind.SAME, "a", "a", "a"));
    }

    @Test
    void conflictDetectionRequestRejectsBaseVersionMismatch() {
        ConflictComparison c = new ConflictComparison("p-1", "name", ConflictFieldKind.SAME, "a", "a", "a");
        assertThrows(IllegalArgumentException.class, () -> new ConflictDetectionRequest(
                "p-2", 1L, 1L, 1L, java.util.List.of(c)));
    }

    @Test
    void conflictDetectionRequestRejectsTooManyComparisons() {
        java.util.List<ConflictComparison> many = new java.util.ArrayList<>();
        for (int i = 0; i < 65; i += 1) {
            many.add(new ConflictComparison("p-1", "f" + i, ConflictFieldKind.SAME, "a", "a", "a"));
        }
        assertThrows(IllegalArgumentException.class, () -> new ConflictDetectionRequest(
                "p-1", 1L, 1L, 1L, many));
    }

    @Test
    void mergeOutcomeRejectsEmptyAutoMergedCommands() {
        assertThrows(IllegalArgumentException.class, () -> new MergeOutcome(
                MergeOutcomeKind.AUTO_MERGED,
                ConflictResolution.AUTO_MERGE,
                "p-1",
                1L,
                2L,
                3L,
                java.util.List.of(),
                java.util.List.of(),
                "OK"));
    }

    @Test
    void mergeOutcomeRejectsEmptyManualMergedComparisons() {
        assertThrows(IllegalArgumentException.class, () -> new MergeOutcome(
                MergeOutcomeKind.MANUAL_MERGED,
                ConflictResolution.MANUAL_MERGE,
                "p-1",
                1L,
                2L,
                3L,
                java.util.List.of(new DomainCommand(
                        DomainCommandKind.UPDATE_PERSON,
                        "p-1",
                        1L,
                        Map.of())),
                java.util.List.of(),
                "OK"));
    }

    @Test
    void patchValidationAcceptsCleanFields() {
        PatchValidation r = PatchValidator.validate(
                Map.of("name", "Le Van A"),
                Set.of("dnaRawData"),
                64, 4096, 256);
        assertTrue(r.acceptable());
        assertEquals("PATCH_ACCEPTED", r.reasonCode());
        assertEquals(Map.of("name", "Le Van A"), r.sanitizedFieldChanges());
    }

    @Test
    void patchValidationRejectsForbiddenField() {
        PatchValidation r = PatchValidator.validate(
                Map.of("dnaRawData", "raw"),
                Set.of("dnaRawData"),
                64, 4096, 256);
        assertFalse(r.acceptable());
        assertTrue(r.forbiddenFieldsTouched().contains("dnaRawData"));
        assertEquals("PATCH_OPERATION_FORBIDDEN_FIELD", r.reasonCode());
    }

    @Test
    void patchValidationRejectsOversizedValue() {
        PatchValidation r = PatchValidator.validate(
                Map.of("bio", "x".repeat(4097)),
                Set.of(),
                64, 4096, 256);
        assertFalse(r.acceptable());
        assertTrue(r.forbiddenFieldsTouched().contains("bio"));
    }

    @Test
    void patchValidationRejectsOverMaxOperations() {
        java.util.Map<String, String> big = new java.util.HashMap<>();
        for (int i = 0; i < 257; i += 1) {
            big.put("k" + i, "v");
        }
        PatchValidation r = PatchValidator.validate(
                big, Set.of(), 64, 4096, 256);
        assertFalse(r.acceptable());
        assertEquals("PATCH_OPERATION_TOO_LARGE", r.reasonCode());
    }

    @Test
    void flagsmithSnapshotRejectsBlankFields() {
        assertThrows(IllegalArgumentException.class, () -> new FlagsmithSnapshot(
                "", "production", "v1", FlagsmithRolloutStrategy.SAFE_DEFAULT,
                false, Map.of(), Set.of(), Set.of(), Set.of(), Instant.now()));
    }

    @Test
    void flagsmithSyncRejectsMissingSnapshot() {
        FlagsmithSyncResult r = FlagsmithRolloutSync.sync(
                null, Instant.now(), 900L,
                Set.of(FlagsmithRolloutStrategy.SAFE_DEFAULT), true);
        assertEquals(FlagsmithSyncOutcome.MISSING, r.outcome());
        assertEquals("FLAGSMITH_SNAPSHOT_MISSING", r.reasonCode());
    }

    @Test
    void flagsmithSyncRejectsStaleSnapshot() {
        FlagsmithSnapshot snap = new FlagsmithSnapshot(
                "collab.mixedPolicy.v2", "production", "v1",
                FlagsmithRolloutStrategy.SAFE_DEFAULT, false,
                Map.of(), Set.of(), Set.of(), Set.of(),
                Instant.now().minusSeconds(3600));
        FlagsmithSyncResult r = FlagsmithRolloutSync.sync(
                snap, Instant.now(), 900L,
                Set.of(FlagsmithRolloutStrategy.SAFE_DEFAULT), true);
        assertEquals(FlagsmithSyncOutcome.STALE, r.outcome());
        assertEquals("FLAGSMITH_SNAPSHOT_STALE", r.reasonCode());
    }

    @Test
    void flagsmithSyncRejectsUnknownStrategy() {
        FlagsmithSnapshot snap = new FlagsmithSnapshot(
                "collab.mixedPolicy.v2", "production", "v1",
                FlagsmithRolloutStrategy.KILL_SWITCH, false,
                Map.of(), Set.of(), Set.of(), Set.of(),
                Instant.now());
        FlagsmithSyncResult r = FlagsmithRolloutSync.sync(
                snap, Instant.now(), 900L,
                Set.of(FlagsmithRolloutStrategy.SAFE_DEFAULT), true);
        assertEquals(FlagsmithSyncOutcome.DRIFT, r.outcome());
        assertEquals("FLAGSMITH_STRATEGY_NOT_PERMITTED", r.reasonCode());
    }

    @Test
    void flagsmithSyncInSyncWhenSafeDefault() {
        FlagsmithSnapshot snap = new FlagsmithSnapshot(
                "collab.mixedPolicy.v2", "production", "v1",
                FlagsmithRolloutStrategy.SAFE_DEFAULT, false,
                Map.of(), Set.of(), Set.of(), Set.of(),
                Instant.now());
        FlagsmithSyncResult r = FlagsmithRolloutSync.sync(
                snap, Instant.now(), 900L,
                Set.of(FlagsmithRolloutStrategy.SAFE_DEFAULT), true);
        assertEquals(FlagsmithSyncOutcome.IN_SYNC, r.outcome());
        assertEquals("FLAGSMITH_SNAPSHOT_IN_SYNC", r.reasonCode());
    }
}
