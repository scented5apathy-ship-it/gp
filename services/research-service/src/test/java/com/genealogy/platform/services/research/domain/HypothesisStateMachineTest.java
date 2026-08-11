package com.genealogy.platform.services.research.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the hypothesis state machine + invariants.
 */
class HypothesisStateMachineTest {

    private static ResearchAuditAttributes audit() {
        return ResearchAuditAttributes.of("actor-1", "corr-1");
    }

    private static TenantScopedId id() {
        return TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.HYPOTHESIS, "hyp-1");
    }

    private static Hypothesis draft() {
        return Hypothesis.create(id(), "John was born in 1842", "person-1", "PERSON",
                Certainty.HYPOTHESIS, 0.5, audit());
    }

    @Test
    void draftToActiveIsAllowed() {
        assertTrue(HypothesisStateMachine.canTransition(
                HypothesisStatus.DRAFT, HypothesisStatus.ACTIVE));
    }

    @Test
    void activeToCorroboratedRequiresCitation() {
        Hypothesis active = HypothesisStateMachine.transition(draft(), HypothesisStatus.ACTIVE);
        Hypothesis corr = HypothesisStateMachine.transition(active, HypothesisStatus.CORROBORATED);
        assertTrue(ResearchInvariants.hasDeny(ResearchInvariants.check(corr)));
        TenantScopedId cit = TenantScopedId.of("tenant-1",
                TenantScopedId.ResourceKind.CITATION, "cit-1");
        Hypothesis withCite = corr.withCorroboratingCitation(cit);
        assertFalse(ResearchInvariants.hasDeny(ResearchInvariants.check(withCite)));
    }

    @Test
    void supersededRequiresBackReference() {
        Hypothesis active = HypothesisStateMachine.transition(draft(), HypothesisStatus.ACTIVE);
        Hypothesis superseded = HypothesisStateMachine.transition(
                active, HypothesisStatus.SUPERSEDED);
        assertTrue(ResearchInvariants.hasDeny(ResearchInvariants.check(superseded)));
        assertThrows(IllegalArgumentException.class,
                () -> superseded.withSupersededBy(""));
        Hypothesis withBack = superseded.withSupersededBy("hyp-2");
        assertFalse(ResearchInvariants.hasDeny(ResearchInvariants.check(withBack)));
    }

    @Test
    void terminalStatesRejectTransitions() {
        for (HypothesisStatus terminal : new HypothesisStatus[] {
                HypothesisStatus.REFUTED,
                HypothesisStatus.SUPERSEDED,
        }) {
            for (HypothesisStatus next : HypothesisStatus.values()) {
                assertFalse(HypothesisStateMachine.canTransition(terminal, next),
                        "terminal " + terminal + " -> " + next);
            }
        }
    }
}
