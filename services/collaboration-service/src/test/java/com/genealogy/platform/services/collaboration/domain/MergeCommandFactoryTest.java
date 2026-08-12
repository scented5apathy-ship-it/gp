package com.genealogy.platform.services.collaboration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Merge command factory tests (E6.3). Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml`
 * ::spec.{conflictResolutions, mergeOutcomeKinds} and
 * `requirements.md` R10.3 + `design.md` §8.3 — merge
 * produces a new domain command, never an arbitrary JSON
 * patch on a forbidden field.
 */
class MergeCommandFactoryTest {

    private static ConflictDetectionRequest sameRequest() {
        return new ConflictDetectionRequest(
                "p-1",
                1L,
                1L,
                2L,
                List.of(new ConflictComparison(
                        "p-1", "name", ConflictFieldKind.SAME, "Le Van A", "Le Van A", "Le Van A")));
    }

    private static ConflictDetectionRequest differentRequest() {
        return new ConflictDetectionRequest(
                "p-1",
                1L,
                1L,
                2L,
                List.of(new ConflictComparison(
                        "p-1", "name", ConflictFieldKind.DIFFERENT, "An", "Binh", "Cuong")));
    }

    private static Map<ProposalKind, Set<DomainCommandKind>> forbiddenOps() {
        return Map.of(
                ProposalKind.PERSON, Set.of(DomainCommandKind.SET_TREE_VISIBILITY),
                ProposalKind.TREE_VISIBILITY, Set.of(
                        DomainCommandKind.CREATE_PERSON,
                        DomainCommandKind.UPDATE_PERSON));
    }

    @Test
    void autoMergeCleanRequestMaterialisesCommand() {
        MergeOutcome r = MergeCommandFactory.merge(
                sameRequest(),
                ConflictResolution.AUTO_MERGE,
                List.of(),
                Set.of("dnaRawData"),
                forbiddenOps(),
                3L,
                256);
        assertEquals(MergeOutcomeKind.AUTO_MERGED, r.kind());
        assertEquals(ConflictResolution.AUTO_MERGE, r.resolution());
        assertEquals(1, r.materialisedCommands().size());
        assertEquals(DomainCommandKind.UPDATE_PERSON, r.materialisedCommands().get(0).kind());
    }

    @Test
    void autoMergeDowngradesToManualWhenConflictExists() {
        MergeOutcome r = MergeCommandFactory.merge(
                differentRequest(),
                ConflictResolution.AUTO_MERGE,
                List.of(),
                Set.of("dnaRawData"),
                forbiddenOps(),
                3L,
                256);
        assertEquals(MergeOutcomeKind.MANUAL_MERGED, r.kind());
        assertEquals("CONFLICT_AUTO_MERGE_NOT_PERMITTED", r.reasonCode());
    }

    @Test
    void manualMergeRequiresAuditPlan() {
        MergeOutcome r = MergeCommandFactory.merge(
                differentRequest(),
                ConflictResolution.MANUAL_MERGE,
                null,
                Set.of("dnaRawData"),
                forbiddenOps(),
                3L,
                256);
        assertEquals(MergeOutcomeKind.MANUAL_MERGED, r.kind());
        assertEquals("CONFLICT_MANUAL_MERGE_AUDIT_REQUIRED", r.reasonCode());
    }

    @Test
    void manualMergeWithPlanMaterialisesCommand() {
        MergeOutcome r = MergeCommandFactory.merge(
                differentRequest(),
                ConflictResolution.MANUAL_MERGE,
                List.of("name"),
                Set.of("dnaRawData"),
                forbiddenOps(),
                3L,
                256);
        assertEquals(MergeOutcomeKind.MANUAL_MERGED, r.kind());
        assertEquals(1, r.materialisedCommands().size());
        assertEquals("Binh", r.materialisedCommands().get(0).fieldChanges().get("name"));
    }

    @Test
    void manualMergeForbidsForbiddenField() {
        MergeOutcome r = MergeCommandFactory.merge(
                sameRequest(),
                ConflictResolution.MANUAL_MERGE,
                List.of("dnaRawData"),
                Set.of("dnaRawData"),
                forbiddenOps(),
                3L,
                256);
        assertEquals(MergeOutcomeKind.FORBIDDEN_FIELD, r.kind());
        assertEquals("CONFLICT_FORBIDDEN_FIELD", r.reasonCode());
    }

    @Test
    void abandonedStops() {
        MergeOutcome r = MergeCommandFactory.merge(
                differentRequest(),
                ConflictResolution.ABANDONED,
                List.of(),
                Set.of("dnaRawData"),
                forbiddenOps(),
                3L,
                256);
        assertEquals(MergeOutcomeKind.ABANDONED, r.kind());
        assertEquals(ConflictResolution.ABANDONED, r.resolution());
        assertTrue(r.materialisedCommands().isEmpty());
    }

    @Test
    void rejectsZeroMergedVersion() {
        try {
            MergeCommandFactory.merge(
                    sameRequest(),
                    ConflictResolution.AUTO_MERGE,
                    List.of(),
                    Set.of(),
                    forbiddenOps(),
                    0L,
                    256);
            org.junit.jupiter.api.Assertions.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
