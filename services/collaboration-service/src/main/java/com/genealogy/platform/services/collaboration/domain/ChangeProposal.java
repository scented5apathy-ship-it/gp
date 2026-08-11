package com.genealogy.platform.services.collaboration.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * ChangeProposal aggregate root. A proposal is "I want to
 * mutate resources X with these commands; here is the base
 * version I read, here is my source, here is my reason, here
 * is the scope". Mirrors `requirements.md` R10.1 + R10.2 +
 * R10.3 + R10.6 + `design.md` §8.3 +
 * `contracts/collaboration/collaboration-policy.yaml::
 * spec.proposalSchema` (E6.2).
 *
 * <p>Invariants enforced by the compact constructor:
 *
 * <ul>
 *   <li>Tenant scope is mandatory (NFR1).
 *   <li>Title, scope, reason + source reference are
 *       mandatory, non-blank and length-bounded.
 *   <li>Diff carries at least one {@link DomainCommand} +
 *       the {@code baseVersion} the proposer read.
 *   <li>Status transitions are enforced by
 *       {@link ChangeProposalStateMachine}.
 *   <li>Once the status is terminal ({@link ProposalStatus#MERGED},
 *       {@link ProposalStatus#REJECTED},
 *       {@link ProposalStatus#WITHDRAWN},
 *       {@link ProposalStatus#EXPIRED}) no further mutation
 *       is allowed; the partial-merge executor creates a
 *       new materialised command list rather than mutating
 *       the proposal in place.
 * </ul>
 */
public record ChangeProposal(
        TenantScopedId id,
        String title,
        String summary,
        String scope,
        String reason,
        String sourceReference,
        ProposalKind kind,
        ProposalStatus status,
        DomainDiff diff,
        long baseResourceVersion,
        List<String> affectedResourceIds,
        List<ReAuthorizationDecision> reAuthorizations,
        String proposerPseudoId,
        Instant submittedAt,
        Instant decidedAt,
        Instant mergedAt,
        Instant expiresAt,
        long version,
        CollaborationAuditAttributes audit) {

    public static final int MAX_TITLE_LENGTH = 256;
    public static final int MAX_SUMMARY_LENGTH = 1024;
    public static final int MAX_REASON_LENGTH = 4096;
    public static final int MAX_SCOPE_LENGTH = 512;
    public static final int MAX_SOURCE_REFERENCE_LENGTH = 128;
    public static final int MAX_AFFECTED_RESOURCE_IDS = 256;
    public static final long MIN_TTL_SECONDS = 60L;
    public static final long MAX_TTL_SECONDS = 2_592_000L;

    public ChangeProposal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(sourceReference, "sourceReference");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(diff, "diff");
        Objects.requireNonNull(proposerPseudoId, "proposerPseudoId");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(audit, "audit");
        if (id.resourceKind() != TenantScopedId.ResourceKind.PROPOSAL) {
            throw new IllegalArgumentException(
                    "TenantScopedId resourceKind must be PROPOSAL, got "
                            + id.resourceKind());
        }
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "title exceeds " + MAX_TITLE_LENGTH + " characters");
        }
        if (summary != null && summary.length() > MAX_SUMMARY_LENGTH) {
            throw new IllegalArgumentException(
                    "summary exceeds " + MAX_SUMMARY_LENGTH + " characters");
        }
        if (summary != null && summary.isBlank()) {
            summary = null;
        }
        if (scope.isBlank()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
        if (scope.length() > MAX_SCOPE_LENGTH) {
            throw new IllegalArgumentException(
                    "scope exceeds " + MAX_SCOPE_LENGTH + " characters");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "reason exceeds " + MAX_REASON_LENGTH + " characters");
        }
        if (sourceReference.isBlank()) {
            throw new IllegalArgumentException("sourceReference must not be blank");
        }
        if (sourceReference.length() > MAX_SOURCE_REFERENCE_LENGTH) {
            throw new IllegalArgumentException(
                    "sourceReference exceeds "
                            + MAX_SOURCE_REFERENCE_LENGTH + " characters");
        }
        if (baseResourceVersion <= 0) {
            throw new IllegalArgumentException(
                    "baseResourceVersion must be positive, got " + baseResourceVersion);
        }
        if (diff.baseVersion() != baseResourceVersion) {
            throw new IllegalArgumentException(
                    "diff.baseVersion must equal baseResourceVersion, got diff="
                            + diff.baseVersion() + " proposal="
                            + baseResourceVersion);
        }
        affectedResourceIds = affectedResourceIds == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(affectedResourceIds));
        if (affectedResourceIds.size() > MAX_AFFECTED_RESOURCE_IDS) {
            throw new IllegalArgumentException(
                    "affectedResourceIds exceeds "
                            + MAX_AFFECTED_RESOURCE_IDS + ": "
                            + affectedResourceIds.size());
        }
        for (String rid : affectedResourceIds) {
            if (rid == null || rid.isBlank()) {
                throw new IllegalArgumentException(
                        "affectedResourceIds entries must not be blank");
            }
            if (rid.length() > 128) {
                throw new IllegalArgumentException(
                        "affectedResourceIds entry exceeds 128 characters: " + rid);
            }
            if (!rid.matches("[A-Za-z0-9._\\-]+")) {
                throw new IllegalArgumentException(
                        "affectedResourceIds entry contains forbidden characters: " + rid);
            }
        }
        reAuthorizations = reAuthorizations == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(reAuthorizations));
        if (reAuthorizations.size() > 16) {
            throw new IllegalArgumentException(
                    "reAuthorizations exceeds 16: " + reAuthorizations.size());
        }
        if (proposerPseudoId.isBlank()) {
            throw new IllegalArgumentException("proposerPseudoId must not be blank");
        }
        if (proposerPseudoId.length() > 128) {
            throw new IllegalArgumentException(
                    "proposerPseudoId exceeds 128 characters");
        }
        if (expiresAt != null) {
            long ttlSeconds = expiresAt.getEpochSecond() - submittedAt.getEpochSecond();
            if (ttlSeconds < MIN_TTL_SECONDS || ttlSeconds > MAX_TTL_SECONDS) {
                throw new IllegalArgumentException(
                        "expiresAt must be between "
                                + MIN_TTL_SECONDS + " and " + MAX_TTL_SECONDS
                                + " seconds after submittedAt, got " + ttlSeconds);
            }
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
    }

    public static ChangeProposal create(
            TenantScopedId id,
            String title,
            String summary,
            String scope,
            String reason,
            String sourceReference,
            ProposalKind kind,
            DomainDiff diff,
            List<String> affectedResourceIds,
            String proposerPseudoId,
            Instant expiresAt,
            CollaborationAuditAttributes audit) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(sourceReference, "sourceReference");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(diff, "diff");
        Objects.requireNonNull(proposerPseudoId, "proposerPseudoId");
        Objects.requireNonNull(audit, "audit");
        return new ChangeProposal(id, title, summary, scope, reason, sourceReference,
                kind, ProposalStatus.DRAFT, diff, diff.baseVersion(),
                affectedResourceIds == null ? List.of() : List.copyOf(affectedResourceIds),
                List.of(), proposerPseudoId,
                Instant.now(), null, null, expiresAt, 1L, audit);
    }

    public ChangeProposal withStatus(
            ProposalStatus next,
            Instant explicitDecidedAt,
            Instant explicitMergedAt,
            ReAuthorizationDecision reAuth) {
        Objects.requireNonNull(next, "next");
        Instant now = Instant.now();
        Instant nextDecidedAt = (explicitDecidedAt != null) ? explicitDecidedAt
                : (next.isTerminal() || next == ProposalStatus.APPROVED
                        || next == ProposalStatus.CHANGES_REQUESTED
                        || next == ProposalStatus.PARTIALLY_MERGED
                        || next == ProposalStatus.REJECTED
                        || next == ProposalStatus.IN_REVIEW) ? now : null;
        Instant nextMergedAt = next == ProposalStatus.MERGED
                ? (explicitMergedAt != null ? explicitMergedAt : now)
                : null;
        List<ReAuthorizationDecision> nextReAuth = reAuthorizations;
        if (reAuth != null) {
            java.util.List<ReAuthorizationDecision> updated = new ArrayList<>(reAuthorizations);
            updated.add(reAuth);
            nextReAuth = updated;
        }
        return new ChangeProposal(id, title, summary, scope, reason, sourceReference,
                kind, next, diff, baseResourceVersion, affectedResourceIds, nextReAuth,
                proposerPseudoId, submittedAt, nextDecidedAt, nextMergedAt,
                expiresAt, version + 1, audit);
    }
}