package com.genealogy.platform.services.research.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the research-task state machine + invariants.
 */
class ResearchTaskStateMachineTest {

    private static ResearchAuditAttributes audit() {
        return ResearchAuditAttributes.of("actor-1", "corr-1");
    }

    private static TenantScopedId id() {
        return TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.RESEARCH_TASK, "task-1");
    }

    private static ResearchTask openTask() {
        return ResearchTask.create(id(), "verify birth date", null, "person-1", "PERSON", audit());
    }

    @Test
    void openToInProgressIsAllowedByStateMachine() {
        ResearchTask transitioned = ResearchTaskStateMachine.transition(
                openTask(), ResearchTaskStatus.IN_PROGRESS, null, null);
        assertTrue(ResearchTaskStateMachine.canTransition(
                ResearchTaskStatus.OPEN, ResearchTaskStatus.IN_PROGRESS));
        assertTrue(ResearchInvariants.hasDeny(ResearchInvariants.check(transitioned)));
    }

    @Test
    void inProgressWithAssignmentPassesInvariants() {
        ResearchTask transitioning = ResearchTaskStateMachine.transition(
                openTask(), ResearchTaskStatus.IN_PROGRESS, null, null);
        ResearchTask assigned = transitioning.withAssignment(
                new ResearchTask.Assignment("user-1", "editor", Instant.now(), null, null));
        assertFalse(ResearchInvariants.hasDeny(ResearchInvariants.check(assigned)));
    }

    @Test
    void openToBlockedRequiresBlockedReason() {
        ResearchTask moved = ResearchTaskStateMachine.transition(
                openTask(), ResearchTaskStatus.BLOCKED, "archive access denied", null);
        assertFalse(ResearchInvariants.hasDeny(ResearchInvariants.check(moved)));
    }

    @Test
    void resolvedRequiresProof() {
        ResearchTask transitioning = ResearchTaskStateMachine.transition(
                openTask(), ResearchTaskStatus.IN_PROGRESS, null, null);
        ResearchTask assigned = transitioning.withAssignment(
                new ResearchTask.Assignment("user-1", "editor", Instant.now(), null, null));
        ResearchTask resolved = ResearchTaskStateMachine.transition(
                assigned, ResearchTaskStatus.RESOLVED, null, "page 12 of register");
        assertFalse(ResearchInvariants.hasDeny(ResearchInvariants.check(resolved)));
    }

    @Test
    void resolvedWithoutProofFails() {
        ResearchTask transitioning = ResearchTaskStateMachine.transition(
                openTask(), ResearchTaskStatus.IN_PROGRESS, null, null);
        ResearchTask assigned = transitioning.withAssignment(
                new ResearchTask.Assignment("user-1", "editor", Instant.now(), null, null));
        ResearchTask resolved = ResearchTaskStateMachine.transition(
                assigned, ResearchTaskStatus.RESOLVED, null, "");
        assertTrue(ResearchInvariants.hasDeny(ResearchInvariants.check(resolved)));
    }

    @Test
    void openToResolvedIsRejected() {
        assertThrows(IllegalStateException.class, () -> ResearchTaskStateMachine.transition(
                openTask(), ResearchTaskStatus.RESOLVED, null, "proof"));
    }

    @Test
    void terminalStatesRejectTransitions() {
        for (ResearchTaskStatus terminal : new ResearchTaskStatus[] {
                ResearchTaskStatus.RESOLVED,
                ResearchTaskStatus.ABANDONED,
        }) {
            for (ResearchTaskStatus next : ResearchTaskStatus.values()) {
                assertFalse(ResearchTaskStateMachine.canTransition(terminal, next),
                        "terminal " + terminal + " -> " + next);
            }
        }
    }
}
