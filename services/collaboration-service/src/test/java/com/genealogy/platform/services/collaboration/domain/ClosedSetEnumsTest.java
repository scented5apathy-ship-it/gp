package com.genealogy.platform.services.collaboration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the closed-set enums. Mirrors the structural
 * test the research-service applies to its certainties /
 * statuses (E6.1).
 */
class ClosedSetEnumsTest {

    @Test
    void proposalKindClosedSetIsFixed() {
        assertEquals(
                List.of("PERSON", "RELATIONSHIP", "LIFE_EVENT", "CLAIM",
                        "SOURCE", "CITATION", "TREE_VISIBILITY"),
                java.util.Arrays.stream(ProposalKind.values()).map(Enum::name).toList());
    }

    @Test
    void proposalStatusClosedSetIsFixed() {
        assertEquals(
                List.of("DRAFT", "SUBMITTED", "IN_REVIEW", "CHANGES_REQUESTED",
                        "APPROVED", "PARTIALLY_MERGED", "MERGED", "REJECTED",
                        "WITHDRAWN", "EXPIRED"),
                java.util.Arrays.stream(ProposalStatus.values()).map(Enum::name).toList());
    }

    @Test
    void proposalStatusTerminalSetIsFixed() {
        assertTrue(ProposalStatus.MERGED.isTerminal());
        assertTrue(ProposalStatus.REJECTED.isTerminal());
        assertTrue(ProposalStatus.WITHDRAWN.isTerminal());
        assertTrue(ProposalStatus.EXPIRED.isTerminal());
        assertFalse(ProposalStatus.DRAFT.isTerminal());
        assertFalse(ProposalStatus.SUBMITTED.isTerminal());
        assertFalse(ProposalStatus.IN_REVIEW.isTerminal());
        assertFalse(ProposalStatus.CHANGES_REQUESTED.isTerminal());
        assertFalse(ProposalStatus.APPROVED.isTerminal());
        assertFalse(ProposalStatus.PARTIALLY_MERGED.isTerminal());
    }

    @Test
    void proposalDecisionClosedSetIsFixed() {
        assertEquals(
                List.of("APPROVE", "REJECT", "REQUEST_CHANGE", "PARTIAL_MERGE", "WITHDRAW"),
                java.util.Arrays.stream(ProposalDecision.values()).map(Enum::name).toList());
    }

    @Test
    void domainCommandKindClosedSetIsFixed() {
        assertEquals(
                List.of("CREATE_PERSON", "UPDATE_PERSON", "ARCHIVE_PERSON",
                        "CREATE_RELATIONSHIP", "UPDATE_RELATIONSHIP", "ARCHIVE_RELATIONSHIP",
                        "CREATE_LIFE_EVENT", "UPDATE_LIFE_EVENT", "ARCHIVE_LIFE_EVENT",
                        "CREATE_CLAIM", "UPDATE_CLAIM", "ARCHIVE_CLAIM",
                        "CREATE_SOURCE", "UPDATE_SOURCE", "ARCHIVE_SOURCE",
                        "CREATE_CITATION", "UPDATE_CITATION", "ARCHIVE_CITATION",
                        "SET_TREE_VISIBILITY"),
                java.util.Arrays.stream(DomainCommandKind.values()).map(Enum::name).toList());
    }

    @Test
    void reviewVerdictClosedSetIsFixed() {
        assertEquals(
                List.of("APPROVED", "REJECTED", "CHANGES_REQUESTED", "PARTIALLY_MERGED"),
                java.util.Arrays.stream(ReviewVerdict.values()).map(Enum::name).toList());
    }

    @Test
    void reviewStatusTerminalSetIsFixed() {
        assertTrue(ReviewStatus.APPROVED.isTerminal());
        assertTrue(ReviewStatus.REJECTED.isTerminal());
        assertTrue(ReviewStatus.CHANGES_REQUESTED.isTerminal());
        assertTrue(ReviewStatus.PARTIAL_MERGED.isTerminal());
        assertFalse(ReviewStatus.PENDING.isTerminal());
    }

    @Test
    void reAuthorizationOutcomeClosedSetIsFixed() {
        assertEquals(
                List.of("ALLOW", "DENY", "ABAC_DENY"),
                java.util.Arrays.stream(ReAuthorizationOutcome.values())
                        .map(Enum::name).toList());
    }

    @Test
    void proposalKindFromWireNormalisesCase() {
        assertSame(ProposalKind.PERSON, ProposalKind.fromWire("person"));
        assertSame(ProposalKind.PERSON, ProposalKind.fromWire(" PERSON "));
        assertThrows(IllegalArgumentException.class, () -> ProposalKind.fromWire(null));
        assertThrows(IllegalArgumentException.class, () -> ProposalKind.fromWire("nope"));
    }

    @Test
    void proposalStatusFromWireNormalisesCase() {
        assertSame(ProposalStatus.DRAFT, ProposalStatus.fromWire("draft"));
        assertSame(ProposalStatus.SUBMITTED, ProposalStatus.fromWire(" SUBMITTED "));
        assertThrows(IllegalArgumentException.class, () -> ProposalStatus.fromWire(null));
        assertThrows(IllegalArgumentException.class, () -> ProposalStatus.fromWire("nope"));
    }

    @Test
    void domainCommandKindFromWireNormalisesCase() {
        assertSame(DomainCommandKind.CREATE_PERSON,
                DomainCommandKind.fromWire("create_person"));
        assertThrows(IllegalArgumentException.class,
                () -> DomainCommandKind.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> DomainCommandKind.fromWire("nope"));
    }

    @Test
    void forbiddenFieldSetIsClosed() {
        assertNotNull(CollaborationInvariants.FORBIDDEN_DOMAIN_COMMAND_FIELDS);
        assertTrue(CollaborationInvariants.FORBIDDEN_DOMAIN_COMMAND_FIELDS
                .contains("dnaRawData"));
        assertTrue(CollaborationInvariants.FORBIDDEN_DOMAIN_COMMAND_FIELDS
                .contains("tenantId"));
    }

    @Test
    void forbiddenProposalKindOperationsArePinned() {
        assertTrue(CollaborationInvariants.FORBIDDEN_PROPOSAL_KIND_OPERATIONS
                .get(ProposalKind.PERSON)
                .contains(DomainCommandKind.SET_TREE_VISIBILITY));
        assertTrue(CollaborationInvariants.FORBIDDEN_PROPOSAL_KIND_OPERATIONS
                .get(ProposalKind.TREE_VISIBILITY)
                .contains(DomainCommandKind.CREATE_PERSON));
    }
}