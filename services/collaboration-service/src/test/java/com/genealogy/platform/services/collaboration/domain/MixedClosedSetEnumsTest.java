package com.genealogy.platform.services.collaboration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Closed-set enum tests for the E6.3 mixed-collaboration
 * vocabularies. Mirrors `contracts/collaboration/mixed-
 * collaboration-policy.yaml` ::spec.{collaborationRoles,
 * treeBranches, resourceTypes, routingDecisions,
 * conflictResolutions, conflictFieldKinds, mergeOutcomeKinds,
 * flagsmithRolloutStrategies, flagsmithSyncOutcomes}.
 */
class MixedClosedSetEnumsTest {

    @Test
    void collaborationRoleClosedSetIsFixed() {
        assertEquals(
                List.of("TENANT_ADMIN", "TREE_ADMIN", "EDITOR", "REVIEWER",
                        "CONTRIBUTOR", "VIEWER", "GUARDIAN", "DNA_STEWARD"),
                Arrays.stream(CollaborationRole.values()).map(Enum::name).toList());
    }

    @Test
    void treeBranchClosedSetIsFixed() {
        assertEquals(
                List.of("TRUNK", "MATERNAL", "PATERNAL", "ADOPTIVE",
                        "STEP", "GUARDIAN", "CUSTOM"),
                Arrays.stream(TreeBranch.values()).map(Enum::name).toList());
    }

    @Test
    void resourceTypeClosedSetIsFixed() {
        assertEquals(
                List.of("PERSON", "RELATIONSHIP", "LIFE_EVENT", "CLAIM",
                        "SOURCE", "CITATION", "TREE_VISIBILITY"),
                Arrays.stream(RoutingResourceType.values()).map(Enum::name).toList());
    }

    @Test
    void routingDecisionClosedSetIsFixed() {
        assertEquals(
                List.of("DIRECT_EDIT", "APPROVAL_REQUIRED", "DENY"),
                Arrays.stream(RoutingDecision.values()).map(Enum::name).toList());
    }

    @Test
    void conflictResolutionClosedSetIsFixed() {
        assertEquals(
                List.of("AUTO_MERGE", "MANUAL_MERGE", "ABANDONED"),
                Arrays.stream(ConflictResolution.values()).map(Enum::name).toList());
    }

    @Test
    void conflictFieldKindClosedSetIsFixed() {
        assertEquals(
                List.of("SAME", "DIFFERENT", "ONLY_BASE", "ONLY_INCOMING", "ONLY_LOCAL"),
                Arrays.stream(ConflictFieldKind.values()).map(Enum::name).toList());
    }

    @Test
    void mergeOutcomeKindClosedSetIsFixed() {
        assertEquals(
                List.of("AUTO_MERGED", "MANUAL_MERGED", "ABANDONED", "FORBIDDEN_FIELD",
                        "FORBIDDEN_OPERATION", "BASE_VERSION_STALE", "RESOURCE_ID_NOT_IN_SCOPE"),
                Arrays.stream(MergeOutcomeKind.values()).map(Enum::name).toList());
    }

    @Test
    void flagsmithStrategyClosedSetIsFixed() {
        assertEquals(
                List.of("SAFE_DEFAULT", "PROGRESSIVE", "CANARY", "KILL_SWITCH"),
                Arrays.stream(FlagsmithRolloutStrategy.values()).map(Enum::name).toList());
    }

    @Test
    void flagsmithOutcomeClosedSetIsFixed() {
        assertEquals(
                List.of("IN_SYNC", "STALE", "DRIFT", "MISSING"),
                Arrays.stream(FlagsmithSyncOutcome.values()).map(Enum::name).toList());
    }

    @Test
    void fromWireNormalisesCase() {
        assertEquals(CollaborationRole.TENANT_ADMIN, CollaborationRole.fromWire("tenant_admin"));
        assertEquals(TreeBranch.MATERNAL, TreeBranch.fromWire(" MATERNAL "));
        assertEquals(RoutingDecision.DIRECT_EDIT, RoutingDecision.fromWire("direct_edit"));
        assertEquals(ConflictResolution.AUTO_MERGE, ConflictResolution.fromWire("AUTO_MERGE"));
        assertEquals(ConflictFieldKind.ONLY_BASE, ConflictFieldKind.fromWire("only_base"));
        assertEquals(MergeOutcomeKind.AUTO_MERGED, MergeOutcomeKind.fromWire("AUTO_MERGED"));
        assertEquals(FlagsmithRolloutStrategy.PROGRESSIVE, FlagsmithRolloutStrategy.fromWire("PROGRESSIVE"));
        assertEquals(FlagsmithSyncOutcome.IN_SYNC, FlagsmithSyncOutcome.fromWire("IN_SYNC"));
    }

    @Test
    void fromWireRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> CollaborationRole.fromWire(null));
        assertThrows(IllegalArgumentException.class, () -> TreeBranch.fromWire(null));
        assertThrows(IllegalArgumentException.class, () -> RoutingResourceType.fromWire(null));
        assertThrows(IllegalArgumentException.class, () -> RoutingDecision.fromWire(null));
        assertThrows(IllegalArgumentException.class, () -> ConflictResolution.fromWire(null));
        assertThrows(IllegalArgumentException.class, () -> ConflictFieldKind.fromWire(null));
        assertThrows(IllegalArgumentException.class, () -> MergeOutcomeKind.fromWire(null));
        assertThrows(IllegalArgumentException.class, () -> FlagsmithRolloutStrategy.fromWire(null));
        assertThrows(IllegalArgumentException.class, () -> FlagsmithSyncOutcome.fromWire(null));
    }

    @Test
    void fromWireRejectsUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> CollaborationRole.fromWire("NOT_REAL"));
        assertThrows(IllegalArgumentException.class, () -> TreeBranch.fromWire("NOT_REAL"));
        assertThrows(IllegalArgumentException.class, () -> RoutingResourceType.fromWire("NOT_REAL"));
        assertThrows(IllegalArgumentException.class, () -> RoutingDecision.fromWire("NOT_REAL"));
        assertThrows(IllegalArgumentException.class, () -> ConflictResolution.fromWire("NOT_REAL"));
        assertThrows(IllegalArgumentException.class, () -> ConflictFieldKind.fromWire("NOT_REAL"));
        assertThrows(IllegalArgumentException.class, () -> MergeOutcomeKind.fromWire("NOT_REAL"));
        assertThrows(IllegalArgumentException.class, () -> FlagsmithRolloutStrategy.fromWire("NOT_REAL"));
        assertThrows(IllegalArgumentException.class, () -> FlagsmithSyncOutcome.fromWire("NOT_REAL"));
    }

    @Test
    void wireEchoIsClosedSetName() {
        assertEquals("TENANT_ADMIN", CollaborationRole.TENANT_ADMIN.wire());
        assertEquals("MATERNAL", TreeBranch.MATERNAL.wire());
        assertEquals("PERSON", RoutingResourceType.PERSON.wire());
        assertEquals("DIRECT_EDIT", RoutingDecision.DIRECT_EDIT.wire());
    }
}
