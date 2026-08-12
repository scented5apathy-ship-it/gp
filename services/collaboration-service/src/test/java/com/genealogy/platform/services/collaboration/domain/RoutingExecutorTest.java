package com.genealogy.platform.services.collaboration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Routing executor + DirectEditMatrix tests (E6.3). Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml`
 * ::spec.{directEditMatrix, alwaysApprovalRequired,
 * directEditPermittedResources, directEditForbiddenRoles,
 * defaultRoutingDecision}.
 */
class RoutingExecutorTest {

    private static DirectEditMatrix matrix() {
        return new DirectEditMatrix(
                RoutingDecision.APPROVAL_REQUIRED,
                Map.of(
                        CollaborationRole.TENANT_ADMIN, Map.of(
                                TreeBranch.TRUNK, Map.of(
                                        RoutingResourceType.LIFE_EVENT, RoutingDecision.DIRECT_EDIT,
                                        RoutingResourceType.PERSON, RoutingDecision.DIRECT_EDIT,
                                        RoutingResourceType.TREE_VISIBILITY,
                                        RoutingDecision.APPROVAL_REQUIRED)),
                        CollaborationRole.EDITOR, Map.of(
                                TreeBranch.TRUNK, Map.of(
                                        RoutingResourceType.LIFE_EVENT, RoutingDecision.DIRECT_EDIT))));
    }

    @Test
    void directEditMatrixGrantsTenantAdminOnTrunk() {
        DirectEditMatrix m = matrix();
        assertEquals(
                RoutingDecision.DIRECT_EDIT,
                m.decide(
                        CollaborationRole.TENANT_ADMIN,
                        TreeBranch.TRUNK,
                        RoutingResourceType.LIFE_EVENT));
    }

    @Test
    void directEditMatrixForcesApprovalOnTreeVisibility() {
        DirectEditMatrix m = matrix();
        assertEquals(
                RoutingDecision.APPROVAL_REQUIRED,
                m.decide(
                        CollaborationRole.TENANT_ADMIN,
                        TreeBranch.TRUNK,
                        RoutingResourceType.TREE_VISIBILITY));
    }

    @Test
    void directEditMatrixDefaultsToApprovalWhenBranchMissing() {
        DirectEditMatrix m = matrix();
        assertEquals(
                RoutingDecision.APPROVAL_REQUIRED,
                m.decide(
                        CollaborationRole.TENANT_ADMIN,
                        TreeBranch.CUSTOM,
                        RoutingResourceType.PERSON));
    }

    @Test
    void directEditMatrixDefaultsToApprovalWhenRoleMissing() {
        DirectEditMatrix m = matrix();
        assertEquals(
                RoutingDecision.APPROVAL_REQUIRED,
                m.decide(
                        CollaborationRole.DNA_STEWARD,
                        TreeBranch.TRUNK,
                        RoutingResourceType.LIFE_EVENT));
    }

    @Test
    void viewerAlwaysDenied() {
        DirectEditMatrix m = matrix();
        assertEquals(
                RoutingDecision.DENY,
                m.decide(
                        CollaborationRole.VIEWER,
                        TreeBranch.TRUNK,
                        RoutingResourceType.LIFE_EVENT));
    }

    @Test
    void contributorAlwaysApprovalRequired() {
        DirectEditMatrix m = matrix();
        assertEquals(
                RoutingDecision.APPROVAL_REQUIRED,
                m.decide(
                        CollaborationRole.CONTRIBUTOR,
                        TreeBranch.TRUNK,
                        RoutingResourceType.LIFE_EVENT));
    }

    @Test
    void executorRoutesDirectEditForLifeEvent() {
        RoutingDecisionRecord r = RoutingExecutor.route(
                new RoutingRequest(
                        new TenantScopedId("t", TenantScopedId.ResourceKind.PROPOSAL, "p"),
                        CollaborationRole.TENANT_ADMIN,
                        TreeBranch.TRUNK,
                        RoutingResourceType.LIFE_EVENT,
                        "p-1",
                        1L),
                matrix());
        assertEquals(RoutingDecision.DIRECT_EDIT, r.decision());
        assertEquals("ROUTING_DIRECT_EDIT_GRANTED", r.reasonCode());
    }

    @Test
    void executorForcesApprovalForTreeVisibility() {
        RoutingDecisionRecord r = RoutingExecutor.route(
                new RoutingRequest(
                        new TenantScopedId("t", TenantScopedId.ResourceKind.PROPOSAL, "p"),
                        CollaborationRole.TENANT_ADMIN,
                        TreeBranch.TRUNK,
                        RoutingResourceType.TREE_VISIBILITY,
                        "p-1",
                        1L),
                matrix());
        assertEquals(RoutingDecision.APPROVAL_REQUIRED, r.decision());
        assertEquals("ROUTING_DEFAULT_REQUIRED", r.reasonCode());
    }

    @Test
    void executorDeniesViewer() {
        RoutingDecisionRecord r = RoutingExecutor.route(
                new RoutingRequest(
                        new TenantScopedId("t", TenantScopedId.ResourceKind.PROPOSAL, "p"),
                        CollaborationRole.VIEWER,
                        TreeBranch.TRUNK,
                        RoutingResourceType.LIFE_EVENT,
                        "p-1",
                        1L),
                matrix());
        assertEquals(RoutingDecision.DENY, r.decision());
        assertEquals("ROUTING_FORBIDDEN_ROLE", r.reasonCode());
    }

    @Test
    void executorForcesApprovalForContributor() {
        RoutingDecisionRecord r = RoutingExecutor.route(
                new RoutingRequest(
                        new TenantScopedId("t", TenantScopedId.ResourceKind.PROPOSAL, "p"),
                        CollaborationRole.CONTRIBUTOR,
                        TreeBranch.TRUNK,
                        RoutingResourceType.LIFE_EVENT,
                        "p-1",
                        1L),
                matrix());
        assertEquals(RoutingDecision.APPROVAL_REQUIRED, r.decision());
        assertEquals("ROUTING_DEFAULT_REQUIRED", r.reasonCode());
    }

    @Test
    void executorForcesApprovalWhenEditingPermittedResourceButMatrixMissing() {
        RoutingDecisionRecord r = RoutingExecutor.route(
                new RoutingRequest(
                        new TenantScopedId("t", TenantScopedId.ResourceKind.PROPOSAL, "p"),
                        CollaborationRole.EDITOR,
                        TreeBranch.TRUNK,
                        RoutingResourceType.LIFE_EVENT,
                        "p-1",
                        1L),
                matrix());
        assertEquals(RoutingDecision.DIRECT_EDIT, r.decision());
        assertEquals("ROUTING_DIRECT_EDIT_GRANTED", r.reasonCode());
    }

    @Test
    void executorForbidsDirectEditOnPersonEvenWhenMatrixSaysDirectEdit() {
        RoutingDecisionRecord r = RoutingExecutor.route(
                new RoutingRequest(
                        new TenantScopedId("t", TenantScopedId.ResourceKind.PROPOSAL, "p"),
                        CollaborationRole.TENANT_ADMIN,
                        TreeBranch.TRUNK,
                        RoutingResourceType.PERSON,
                        "p-1",
                        1L),
                matrix());
        assertEquals(RoutingDecision.APPROVAL_REQUIRED, r.decision());
        assertEquals("ROUTING_FORBIDDEN_RESOURCE", r.reasonCode());
    }

    @Test
    void directEditMatrixHasEntryCheck() {
        DirectEditMatrix m = matrix();
        assertTrue(m.hasEntry(
                CollaborationRole.TENANT_ADMIN,
                TreeBranch.TRUNK,
                RoutingResourceType.LIFE_EVENT));
        assertFalse(m.hasEntry(
                CollaborationRole.TENANT_ADMIN,
                TreeBranch.MATERNAL,
                RoutingResourceType.PERSON));
    }

    @Test
    void directEditMatrixRolesEnumerated() {
        DirectEditMatrix m = matrix();
        assertTrue(m.roles().contains(CollaborationRole.TENANT_ADMIN));
        assertTrue(m.roles().contains(CollaborationRole.EDITOR));
    }
}
