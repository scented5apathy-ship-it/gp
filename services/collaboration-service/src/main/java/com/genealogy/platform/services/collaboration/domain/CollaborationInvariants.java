package com.genealogy.platform.services.collaboration.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure invariant checker for every collaboration aggregate.
 * Mirrors `contracts/collaboration/collaboration-policy.yaml
 * ::spec.invariants` (E6.2) + `requirements.md` R10.1 + R10.2
 * + R10.3 + R10.6 + `design.md` §8.3 + §6.2 (ABAC
 * obligations).
 *
 * <p>Findings are emitted with three severity levels:
 *
 * <ul>
 *   <li>{@link Severity#DENY} — the executor MUST NOT
 *       persist the state. Equivalent to a hard constraint.
 *   <li>{@link Severity#WARN} — the executor MAY persist;
 *       the editor must be informed (UI live region).
 *   <li>{@link Severity#INFO} — purely informational.
 * </ul>
 *
 * <p>Reason codes are closed-set; adding a new code requires
 * an ADR supersession and an update to the contract. The
 * {@code lint-collaboration-config} script enforces the
 * closed-set.
 */
public final class CollaborationInvariants {

    public enum Severity {
        DENY,
        WARN,
        INFO
    }

    public enum ConflictCode {
        PROPOSAL_REQUIRED_BASE_VERSION,
        PROPOSAL_BASE_VERSION_NOT_POSITIVE,
        PROPOSAL_REASON_REQUIRED,
        PROPOSAL_SCOPE_REQUIRED,
        PROPOSAL_SOURCE_REFERENCE_REQUIRED,
        PROPOSAL_DOMAIN_COMMAND_REQUIRED,
        PROPOSAL_DOMAIN_COMMAND_FORBIDDEN_FIELD,
        PROPOSAL_DOMAIN_COMMAND_FORBIDDEN_OPERATION,
        PROPOSAL_TOO_LARGE,
        PROPOSAL_TTL_OUT_OF_RANGE,
        PROPOSAL_REAUTHORIZATION_REQUIRED,
        PROPOSAL_REAUTHORIZATION_DENIED,
        PROPOSAL_REAUTHORIZATION_ABAC_DENIED,
        REVIEW_REQUIRED_COMMENT_FOR_REJECT,
        REVIEW_REQUIRED_COMMENT_FOR_REQUEST_CHANGE,
        REVIEW_PARTIAL_MERGE_REQUIRES_OPERATIONS,
        REVIEW_TOO_MANY_DECISIONS,
        REVIEW_DUPLICATE_DECISION,
        PROPOSAL_NOT_IN_REVIEWABLE_STATE,
        PROPOSAL_NOT_PARTIALLY_MERGEABLE,
        CONFLICT_REQUIRE_BASE_VERSION_MATCH,
        CONFLICT_REQUIRED_PARTIAL_MERGE_PLAN,
        AUDIT_KEY_FORBIDDEN,
        FORBIDDEN_DOMAIN_COMMAND_TARGET
    }

    /**
     * Mirrors `contracts/collaboration/collaboration-policy.yaml
     * ::spec.forbiddenDomainCommandFields`. The executor
     * refuses any {@link DomainCommand} whose
     * {@code fieldChanges} touches a key in this set. Adding
     * a key requires an ADR supersession.
     */
    public static final Set<String> FORBIDDEN_DOMAIN_COMMAND_FIELDS = Set.of(
            "dnaRawData",
            "dnaMatchId",
            "consentReceipt",
            "livingMarker",
            "visibility",
            "redactedFields",
            "rawEmail",
            "rawPhone",
            "rawSsn",
            "rawPassport",
            "ownerPseudoId",
            "tenantId"
    );

    /**
     * Mirrors `contracts/collaboration/collaboration-policy.yaml
     * ::spec.forbiddenProposalKindOperations`. Maps a
     * {@link ProposalKind} to the set of
     * {@link DomainCommandKind} values the proposal MUST NOT
     * carry.
     */
    public static final Map<ProposalKind, Set<DomainCommandKind>>
            FORBIDDEN_PROPOSAL_KIND_OPERATIONS;

    static {
        Map<ProposalKind, Set<DomainCommandKind>> m = new HashMap<>();
        Set<DomainCommandKind> nonVisibility = Set.of(
                DomainCommandKind.SET_TREE_VISIBILITY);
        for (ProposalKind k : new ProposalKind[] {
                ProposalKind.PERSON,
                ProposalKind.RELATIONSHIP,
                ProposalKind.LIFE_EVENT,
                ProposalKind.CLAIM,
                ProposalKind.SOURCE,
                ProposalKind.CITATION
        }) {
            m.put(k, nonVisibility);
        }
        Set<DomainCommandKind> treeVisibilityOnly = Set.of(
                DomainCommandKind.CREATE_PERSON,
                DomainCommandKind.UPDATE_PERSON,
                DomainCommandKind.ARCHIVE_PERSON,
                DomainCommandKind.CREATE_RELATIONSHIP,
                DomainCommandKind.UPDATE_RELATIONSHIP,
                DomainCommandKind.ARCHIVE_RELATIONSHIP,
                DomainCommandKind.CREATE_LIFE_EVENT,
                DomainCommandKind.UPDATE_LIFE_EVENT,
                DomainCommandKind.ARCHIVE_LIFE_EVENT,
                DomainCommandKind.CREATE_CLAIM,
                DomainCommandKind.UPDATE_CLAIM,
                DomainCommandKind.ARCHIVE_CLAIM,
                DomainCommandKind.CREATE_SOURCE,
                DomainCommandKind.UPDATE_SOURCE,
                DomainCommandKind.ARCHIVE_SOURCE,
                DomainCommandKind.CREATE_CITATION,
                DomainCommandKind.UPDATE_CITATION,
                DomainCommandKind.ARCHIVE_CITATION);
        m.put(ProposalKind.TREE_VISIBILITY, treeVisibilityOnly);
        FORBIDDEN_PROPOSAL_KIND_OPERATIONS = Collections.unmodifiableMap(m);
    }

    public record Finding(Severity severity, ConflictCode code, String message) {
        public Finding {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    private CollaborationInvariants() {
    }

    public static List<Finding> check(ChangeProposal proposal) {
        Objects.requireNonNull(proposal, "proposal");
        List<Finding> findings = new ArrayList<>();
        findings.addAll(checkDiff(proposal.diff()));
        findings.addAll(checkProposalKindOperations(proposal));
        findings.addAll(checkReAuthorizations(proposal));
        return Collections.unmodifiableList(findings);
    }

    public static List<Finding> check(Review review) {
        Objects.requireNonNull(review, "review");
        List<Finding> findings = new ArrayList<>();
        if ((review.decision() == ProposalDecision.REJECT
                || review.decision() == ProposalDecision.REQUEST_CHANGE)
                && (review.comment() == null || review.comment().isBlank())) {
            findings.add(new Finding(
                    Severity.DENY,
                    review.decision() == ProposalDecision.REJECT
                            ? ConflictCode.REVIEW_REQUIRED_COMMENT_FOR_REJECT
                            : ConflictCode.REVIEW_REQUIRED_COMMENT_FOR_REQUEST_CHANGE,
                    "decision=" + review.decision() + " requires a non-blank comment"));
        }
        if (review.decision() == ProposalDecision.PARTIAL_MERGE
                && review.partialMergeOperations().isEmpty()) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.REVIEW_PARTIAL_MERGE_REQUIRES_OPERATIONS,
                    "decision=PARTIAL_MERGE requires at least one partialMergeOperation"));
        }
        return Collections.unmodifiableList(findings);
    }

    public static List<Finding> check(DomainCommand command) {
        Objects.requireNonNull(command, "command");
        return checkSingleCommand(command);
    }

    public static boolean hasDeny(List<Finding> findings) {
        for (Finding f : findings) {
            if (f.severity() == Severity.DENY) {
                return true;
            }
        }
        return false;
    }

    private static List<Finding> checkDiff(DomainDiff diff) {
        List<Finding> findings = new ArrayList<>();
        if (diff.baseVersion() <= 0) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.PROPOSAL_BASE_VERSION_NOT_POSITIVE,
                    "baseVersion must be positive, got " + diff.baseVersion()));
        }
        if (diff.commands().isEmpty()) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.PROPOSAL_DOMAIN_COMMAND_REQUIRED,
                    "diff must contain at least one command"));
        }
        for (DomainCommand command : diff.commands()) {
            findings.addAll(checkSingleCommand(command));
        }
        return findings;
    }

    private static List<Finding> checkSingleCommand(DomainCommand command) {
        List<Finding> findings = new ArrayList<>();
        if (command.baseVersion() <= 0) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.PROPOSAL_BASE_VERSION_NOT_POSITIVE,
                    "command.baseVersion must be positive, got " + command.baseVersion()));
        }
        for (String field : command.fieldChanges().keySet()) {
            if (FORBIDDEN_DOMAIN_COMMAND_FIELDS.contains(field)) {
                findings.add(new Finding(
                        Severity.DENY,
                        ConflictCode.PROPOSAL_DOMAIN_COMMAND_FORBIDDEN_FIELD,
                        "fieldChanges key '" + field + "' is forbidden by policy"));
            }
        }
        return findings;
    }

    private static List<Finding> checkProposalKindOperations(ChangeProposal proposal) {
        List<Finding> findings = new ArrayList<>();
        Set<DomainCommandKind> forbidden = FORBIDDEN_PROPOSAL_KIND_OPERATIONS
                .getOrDefault(proposal.kind(), Set.of());
        for (DomainCommand command : proposal.diff().commands()) {
            if (forbidden.contains(command.kind())) {
                findings.add(new Finding(
                        Severity.DENY,
                        ConflictCode.PROPOSAL_DOMAIN_COMMAND_FORBIDDEN_OPERATION,
                        "proposalKind=" + proposal.kind()
                                + " forbids domainCommandKind=" + command.kind()));
            }
        }
        return findings;
    }

    private static List<Finding> checkReAuthorizations(ChangeProposal proposal) {
        List<Finding> findings = new ArrayList<>();
        if (proposal.status() == ProposalStatus.SUBMITTED
                && proposal.reAuthorizations().isEmpty()) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.PROPOSAL_REAUTHORIZATION_REQUIRED,
                    "proposal SUBMITTED requires at least one reAuthorization decision"));
        }
        for (ReAuthorizationDecision reAuth : proposal.reAuthorizations()) {
            if (reAuth.outcome() == ReAuthorizationOutcome.DENY) {
                findings.add(new Finding(
                        Severity.DENY,
                        ConflictCode.PROPOSAL_REAUTHORIZATION_DENIED,
                        "reAuthorization denied: " + reAuth.reasonCode()));
            } else if (reAuth.outcome() == ReAuthorizationOutcome.ABAC_DENY) {
                findings.add(new Finding(
                        Severity.DENY,
                        ConflictCode.PROPOSAL_REAUTHORIZATION_ABAC_DENIED,
                        "reAuthorization abac denied: " + reAuth.reasonCode()));
            }
        }
        return findings;
    }
}