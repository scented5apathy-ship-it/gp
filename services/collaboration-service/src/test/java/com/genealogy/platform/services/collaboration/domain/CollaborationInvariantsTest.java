package com.genealogy.platform.services.collaboration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CollaborationInvariants}.
 */
class CollaborationInvariantsTest {

    private static CollaborationAuditAttributes audit() {
        return CollaborationAuditAttributes.of("actor-1", "corr-1");
    }

    private static TenantScopedId proposalId() {
        return TenantScopedId.of("tenant-1",
                TenantScopedId.ResourceKind.PROPOSAL, "prop-1");
    }

    private static TenantScopedId reviewId() {
        return TenantScopedId.of("tenant-1",
                TenantScopedId.ResourceKind.REVIEW, "rev-1");
    }

    private static DomainCommand updatePerson(String resourceId, long baseVersion,
                                              Map<String, String> fields) {
        return DomainCommand.of(DomainCommandKind.UPDATE_PERSON, resourceId, baseVersion, fields);
    }

    private static Map<String, String> field(String key, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }

    @Test
    void proposalWithForbiddenFieldFails() {
        DomainDiff diff = DomainDiff.of("person-1", 5L,
                List.of(updatePerson("person-1", 5L,
                        field("dnaRawData", "ACGT"))));
        ChangeProposal p = ChangeProposal.create(proposalId(), "title", null,
                "scope", "reason", "source",
                ProposalKind.PERSON, diff,
                List.of("person-1"), "proposer-1", null, audit());
        List<CollaborationInvariants.Finding> findings =
                CollaborationInvariants.check(p);
        assertTrue(CollaborationInvariants.hasDeny(findings));
        assertTrue(findings.stream().anyMatch(f -> f.code()
                == CollaborationInvariants.ConflictCode.PROPOSAL_DOMAIN_COMMAND_FORBIDDEN_FIELD));
    }

    @Test
    void proposalWithTenantIdMutationFails() {
        DomainDiff diff = DomainDiff.of("person-1", 5L,
                List.of(updatePerson("person-1", 5L,
                        field("tenantId", "tenant-OTHER"))));
        ChangeProposal p = ChangeProposal.create(proposalId(), "title", null,
                "scope", "reason", "source",
                ProposalKind.PERSON, diff,
                List.of("person-1"), "proposer-1", null, audit());
        List<CollaborationInvariants.Finding> findings =
                CollaborationInvariants.check(p);
        assertTrue(CollaborationInvariants.hasDeny(findings));
    }

    @Test
    void personProposalWithTreeVisibilityCommandFails() {
        DomainDiff diff = DomainDiff.of("tree-1", 5L,
                List.of(DomainCommand.of(DomainCommandKind.SET_TREE_VISIBILITY,
                        "tree-1", 5L,
                        field("visibility", "PRIVATE"))));
        ChangeProposal p = ChangeProposal.create(proposalId(), "title", null,
                "scope", "reason", "source",
                ProposalKind.PERSON, diff,
                List.of("tree-1"), "proposer-1", null, audit());
        List<CollaborationInvariants.Finding> findings =
                CollaborationInvariants.check(p);
        assertTrue(CollaborationInvariants.hasDeny(findings));
        assertTrue(findings.stream().anyMatch(f -> f.code()
                == CollaborationInvariants.ConflictCode.PROPOSAL_DOMAIN_COMMAND_FORBIDDEN_OPERATION));
    }

    @Test
    void treeVisibilityProposalWithPersonCommandFails() {
        DomainDiff diff = DomainDiff.of("person-1", 5L,
                List.of(updatePerson("person-1", 5L,
                        field("givenName", "Anne"))));
        ChangeProposal p = ChangeProposal.create(proposalId(), "title", null,
                "scope", "reason", "source",
                ProposalKind.TREE_VISIBILITY, diff,
                List.of("person-1"), "proposer-1", null, audit());
        List<CollaborationInvariants.Finding> findings =
                CollaborationInvariants.check(p);
        assertTrue(CollaborationInvariants.hasDeny(findings));
    }

    @Test
    void personProposalWithAllowedUpdateCommandPasses() {
        DomainDiff diff = DomainDiff.of("person-1", 5L,
                List.of(updatePerson("person-1", 5L,
                        field("givenName", "Anne"))));
        ChangeProposal p = ChangeProposal.create(proposalId(), "title", null,
                "scope", "reason", "source",
                ProposalKind.PERSON, diff,
                List.of("person-1"), "proposer-1", null, audit());
        ChangeProposal submitted = ChangeProposalStateMachine.transition(
                p, ProposalStatus.SUBMITTED,
                ReAuthorizationDecision.allow("actor-1", "corr-1", Instant.now()));
        List<CollaborationInvariants.Finding> findings =
                CollaborationInvariants.check(submitted);
        assertFalse(CollaborationInvariants.hasDeny(findings));
    }

    @Test
    void denyReAuthorizationClosesProposal() {
        DomainDiff diff = DomainDiff.of("person-1", 5L,
                List.of(updatePerson("person-1", 5L,
                        field("givenName", "Anne"))));
        ChangeProposal p = ChangeProposal.create(proposalId(), "title", null,
                "scope", "reason", "source",
                ProposalKind.PERSON, diff,
                List.of("person-1"), "proposer-1", null, audit());
        ChangeProposal submitted = ChangeProposalStateMachine.transition(
                p, ProposalStatus.SUBMITTED,
                ReAuthorizationDecision.deny("actor-1", "corr-1",
                        "tuple_missing", Instant.now()));
        List<CollaborationInvariants.Finding> findings =
                CollaborationInvariants.check(submitted);
        assertTrue(CollaborationInvariants.hasDeny(findings));
        assertTrue(findings.stream().anyMatch(f -> f.code()
                == CollaborationInvariants.ConflictCode.PROPOSAL_REAUTHORIZATION_DENIED));
    }

    @Test
    void abacDenyReAuthorizationClosesProposal() {
        DomainDiff diff = DomainDiff.of("person-1", 5L,
                List.of(updatePerson("person-1", 5L,
                        field("givenName", "Anne"))));
        ChangeProposal p = ChangeProposal.create(proposalId(), "title", null,
                "scope", "reason", "source",
                ProposalKind.PERSON, diff,
                List.of("person-1"), "proposer-1", null, audit());
        ChangeProposal submitted = ChangeProposalStateMachine.transition(
                p, ProposalStatus.SUBMITTED,
                ReAuthorizationDecision.abacDeny("actor-1", "corr-1",
                        "living_marker", Instant.now()));
        List<CollaborationInvariants.Finding> findings =
                CollaborationInvariants.check(submitted);
        assertTrue(CollaborationInvariants.hasDeny(findings));
        assertTrue(findings.stream().anyMatch(f -> f.code()
                == CollaborationInvariants.ConflictCode.PROPOSAL_REAUTHORIZATION_ABAC_DENIED));
    }

    @Test
    void emptyDiffFails() {
        DomainDiff empty = DomainDiff.of("person-1", 5L, List.of(
                DomainCommand.of(DomainCommandKind.UPDATE_PERSON, "person-1", 5L,
                        field("givenName", "Anne"))));
        empty.commands();
        // DomainDiff constructor already rejects empty; pin that.
        assertThrows(IllegalArgumentException.class,
                () -> DomainDiff.of("person-1", 5L, List.of()));
    }

    @Test
    void reviewPartialMergeWithoutOperationsFlaggedByInvariants() {
        // The compact constructor of Review rejects null
        // partialMergeOperations for decision=PARTIAL_MERGE.
        assertThrows(IllegalArgumentException.class,
                () -> Review.create(reviewId(), proposalId(),
                        "reviewer-1", "proposer-1",
                        ProposalDecision.PARTIAL_MERGE,
                        ReAuthorizationDecision.allow("reviewer-1", "corr-1", Instant.now()),
                        "merge given name only", null, audit()));
        // A valid PARTIAL_MERGE review with at least one
        // operation survives.
        DomainCommand op = DomainCommand.of(
                DomainCommandKind.UPDATE_PERSON, "person-1", 5L,
                field("givenName", "Anne"));
        Review valid = Review.create(reviewId(), proposalId(),
                "reviewer-2", "proposer-1",
                ProposalDecision.PARTIAL_MERGE,
                ReAuthorizationDecision.allow("reviewer-2", "corr-1", Instant.now()),
                "merge given name only", List.of(op), audit());
        assertNotNull(valid);
    }
}