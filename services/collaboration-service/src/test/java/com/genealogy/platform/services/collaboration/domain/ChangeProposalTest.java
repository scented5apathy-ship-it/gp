package com.genealogy.platform.services.collaboration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link ChangeProposal} aggregate.
 */
class ChangeProposalTest {

    private static CollaborationAuditAttributes audit() {
        return CollaborationAuditAttributes.of("actor-1", "corr-1");
    }

    private static TenantScopedId proposalId() {
        return TenantScopedId.of("tenant-1",
                TenantScopedId.ResourceKind.PROPOSAL, "prop-1");
    }

    private static DomainDiff diff() {
        return DomainDiff.of("person-1", 5L,
                List.of(DomainCommand.of(
                        DomainCommandKind.UPDATE_PERSON, "person-1", 5L,
                        Map.of("givenName", "Anne"))));
    }

    @Test
    void createInitialisesDraftProposal() {
        ChangeProposal p = ChangeProposal.create(
                proposalId(), "fix birth date", null,
                "tree-1", "birth record missing day",
                "register-page-12", ProposalKind.PERSON, diff(),
                List.of("person-1"), "proposer-1", null, audit());
        assertEquals(ProposalStatus.DRAFT, p.status());
        assertEquals(5L, p.baseResourceVersion());
        assertEquals("proposer-1", p.proposerPseudoId());
        assertNotNull(p.submittedAt());
        assertEquals(1L, p.version());
    }

    @Test
    void createRejectsBlankTitle() {
        assertThrows(IllegalArgumentException.class,
                () -> ChangeProposal.create(proposalId(), "", null,
                        "scope", "reason", "source",
                        ProposalKind.PERSON, diff(),
                        List.of("person-1"), "proposer-1", null, audit()));
    }

    @Test
    void createRejectsBlankScope() {
        assertThrows(IllegalArgumentException.class,
                () -> ChangeProposal.create(proposalId(), "title", null,
                        "", "reason", "source",
                        ProposalKind.PERSON, diff(),
                        List.of("person-1"), "proposer-1", null, audit()));
    }

    @Test
    void createRejectsBlankReason() {
        assertThrows(IllegalArgumentException.class,
                () -> ChangeProposal.create(proposalId(), "title", null,
                        "scope", "", "source",
                        ProposalKind.PERSON, diff(),
                        List.of("person-1"), "proposer-1", null, audit()));
    }

    @Test
    void createRejectsBlankSourceReference() {
        assertThrows(IllegalArgumentException.class,
                () -> ChangeProposal.create(proposalId(), "title", null,
                        "scope", "reason", "",
                        ProposalKind.PERSON, diff(),
                        List.of("person-1"), "proposer-1", null, audit()));
    }

    @Test
    void createRejectsBaseVersionMismatch() {
        // Bypass the `create()` helper (which always copies
        // diff.baseVersion into baseResourceVersion) and call
        // the record constructor directly with mismatched
        // values. The constructor MUST reject the proposal.
        DomainDiff diff = DomainDiff.of("person-1", 5L,
                List.of(DomainCommand.of(
                        DomainCommandKind.UPDATE_PERSON, "person-1", 5L,
                        Map.of("givenName", "Anne"))));
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeProposal(proposalId(), "title", null,
                        "scope", "reason", "source",
                        ProposalKind.PERSON, ProposalStatus.DRAFT,
                        diff, 4L,
                        List.of("person-1"), List.of(), "proposer-1",
                        now, null, null, null, 1L, audit()));
    }

    @Test
    void createRejectsOversizedAffectedIds() {
        List<String> huge = new java.util.ArrayList<>();
        for (int i = 0; i < 257; i += 1) {
            huge.add("id-" + i);
        }
        assertThrows(IllegalArgumentException.class,
                () -> ChangeProposal.create(proposalId(), "title", null,
                        "scope", "reason", "source",
                        ProposalKind.PERSON, diff(),
                        huge, "proposer-1", null, audit()));
    }

    @Test
    void createRejectsTtlOutOfRange() {
        Instant submitted = Instant.now();
        Instant tooSoon = submitted.plusSeconds(30);
        Instant tooFar = submitted.plusSeconds(ChangeProposal.MAX_TTL_SECONDS + 60);
        assertThrows(IllegalArgumentException.class,
                () -> ChangeProposal.create(proposalId(), "title", null,
                        "scope", "reason", "source",
                        ProposalKind.PERSON, diff(),
                        List.of("person-1"), "proposer-1", tooSoon, audit()));
        assertThrows(IllegalArgumentException.class,
                () -> ChangeProposal.create(proposalId(), "title", null,
                        "scope", "reason", "source",
                        ProposalKind.PERSON, diff(),
                        List.of("person-1"), "proposer-1", tooFar, audit()));
    }

    @Test
    void withStatusAppendsReAuthorization() {
        ChangeProposal p = ChangeProposal.create(proposalId(), "title", null,
                "scope", "reason", "source",
                ProposalKind.PERSON, diff(),
                List.of("person-1"), "proposer-1", null, audit());
        ReAuthorizationDecision reAuth = ReAuthorizationDecision.allow(
                "actor-1", "corr-1", Instant.now());
        ChangeProposal next = ChangeProposalStateMachine.transition(
                p, ProposalStatus.SUBMITTED, reAuth);
        assertEquals(ProposalStatus.SUBMITTED, next.status());
        assertEquals(1, next.reAuthorizations().size());
        assertEquals(reAuth, next.reAuthorizations().get(0));
        assertEquals(2L, next.version());
    }

    @Test
    void submittedRequiresReAuthorization() {
        ChangeProposal p = ChangeProposal.create(proposalId(), "title", null,
                "scope", "reason", "source",
                ProposalKind.PERSON, diff(),
                List.of("person-1"), "proposer-1", null, audit());
        ChangeProposal submitted = ChangeProposalStateMachine.transition(
                p, ProposalStatus.SUBMITTED, null);
        List<CollaborationInvariants.Finding> findings =
                CollaborationInvariants.check(submitted);
        assertTrue(CollaborationInvariants.hasDeny(findings));
        assertTrue(findings.stream().anyMatch(f -> f.code()
                == CollaborationInvariants.ConflictCode.PROPOSAL_REAUTHORIZATION_REQUIRED));
    }
}