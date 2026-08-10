package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Person merge record aggregate root. Mirrors
 * `requirements.md` R4.5 (merge with preview + source
 * preservation + revert), R4.6 (version / actor / timestamp
 * / reason / diff), R5.5 (citations preserved), R8.5
 * (provenance preserved across merge), R10 (collaboration
 * audit), R16 (admin / audit / retention), and
 * `glossary-and-policy-matrix.md` §2.4 (`MERGE` rule).
 *
 * <p>The record carries:
 *
 * <ul>
 *   <li>{@code winnerPersonId} — the Person that absorbs
 *       the loser's references (R4.5).
 *   <li>{@code loserPersonId} — the Person that becomes a
 *       tombstone ({@link PersonLifecycle#MERGED}) and is
 *       NEVER hard-deleted (glossary §2.4 #3).
 *   <li>{@code score} — the candidate's overall score in
 *       [0,1]; preserved verbatim from the scorer.
 *   <li>{@code candidates} — the full scoring breakdown
 *       (audit trail for the score, R4.6).
 *   <li>{@code reviewerUserId} + {@code reason} — required
 *       for any transition to {@link MergeStatus#MERGED} or
 *       {@link MergeStatus#REVERTED} (R10 + R16).
 *   <li>{@code snapshotHash} — pre-merge content hash so
 *       the reverter can prove the loser snapshot is intact
 *       (glossary §2.4 #2).
 *   <li>{@code revertCommandJson} — canonical inverse
 *       command (re-key back winner ↔ loser) so the
 *       reverter is deterministic.
 * </ul>
 *
 * <p>Invariants enforced by {@link MergeInvariants}:
 *
 * <ul>
 *   <li>{@code winnerPersonId != loserPersonId}.
 *   <li>Same tenant on winner + loser (cross-tenant merge
 *       is a hard deny — defense in depth alongside RLS).
 *   <li>{@code status = MERGED} requires a non-blank
 *       {@code reviewerUserId} AND {@code reason}.
 *   <li>{@code status = REVERTED} is only reachable from
 *       {@link MergeStatus#MERGED} AND within the revert
 *       window.
 *   <li>{@code status = MERGED} requires a non-null
 *       {@code snapshotHash}.
 * </ul>
 */
public record MergeRecord(
        MergeId mergeId,
        String tenantId,
        String treeId,
        MergeKind kind,
        String winnerPersonId,
        String loserPersonId,
        MergeStatus status,
        double score,
        List<MergeCandidate> candidates,
        MergeProvenance provenance,
        String reviewerUserId,
        String reason,
        String snapshotHash,
        String revertCommandJson,
        long rekeyedReferenceCount,
        Instant mergedAt,
        Instant revertedAt,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        long version,
        Map<String, String> auditAttributes) {

    /** Reason cap mirrors `person-merge-policy.yaml::spec.maxReasonChars`. */
    public static final int MAX_REASON_CHARS = 2048;

    public MergeRecord {
        Objects.requireNonNull(mergeId, "mergeId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(treeId, "treeId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(winnerPersonId, "winnerPersonId");
        Objects.requireNonNull(loserPersonId, "loserPersonId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(createdBy, "createdBy");
        if (winnerPersonId.equals(loserPersonId)) {
            throw new IllegalArgumentException(
                    "self-merge forbidden: winner == loser ("
                            + winnerPersonId + ")");
        }
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score out of [0,1]: " + score);
        }
        if (rekeyedReferenceCount < 0) {
            throw new IllegalArgumentException(
                    "rekeyedReferenceCount must be >=0: "
                            + rekeyedReferenceCount);
        }
        if (reason != null && reason.length() > MAX_REASON_CHARS) {
            throw new IllegalArgumentException(
                    "reason exceeds " + MAX_REASON_CHARS + " chars: "
                            + reason.length());
        }
        candidates = candidates == null
                ? List.of()
                : Collections.unmodifiableList(candidates);
        auditAttributes = auditAttributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(auditAttributes));
        if (status == MergeStatus.MERGED) {
            if (reviewerUserId == null || reviewerUserId.isBlank()) {
                throw new IllegalArgumentException(
                        "status=MERGED requires a non-blank reviewerUserId");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "status=MERGED requires a non-blank reason");
            }
            if (snapshotHash == null || snapshotHash.isBlank()) {
                throw new IllegalArgumentException(
                        "status=MERGED requires a non-blank snapshotHash");
            }
            if (mergedAt == null) {
                throw new IllegalArgumentException(
                        "status=MERGED requires a non-null mergedAt");
            }
        }
        if (status == MergeStatus.REVERTED) {
            if (reviewerUserId == null || reviewerUserId.isBlank()) {
                throw new IllegalArgumentException(
                        "status=REVERTED requires a non-blank reviewerUserId");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "status=REVERTED requires a non-blank reason");
            }
            if (revertedAt == null) {
                throw new IllegalArgumentException(
                        "status=REVERTED requires a non-null revertedAt");
            }
        }
    }

    /** Build the canonical revert command (winner/loser swap) as JSON. */
    public static String defaultRevertCommandJson(
            MergeId mergeId,
            String winnerPersonId,
            String loserPersonId) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"mergeId\":\"").append(mergeId.wire()).append("\",");
        sb.append("\"action\":\"revert\",");
        sb.append("\"rekeyFrom\":\"").append(winnerPersonId).append("\",");
        sb.append("\"rekeyTo\":\"").append(loserPersonId).append("\"");
        sb.append('}');
        return sb.toString();
    }

    public MergeRecord withStatus(MergeStatus next, Instant at) {
        if (next == null) {
            throw new IllegalArgumentException("next status required");
        }
        return new MergeRecord(
                mergeId, tenantId, treeId, kind,
                winnerPersonId, loserPersonId,
                next, score, candidates, provenance,
                reviewerUserId, reason, snapshotHash, revertCommandJson,
                rekeyedReferenceCount,
                next == MergeStatus.MERGED ? at : mergedAt,
                next == MergeStatus.REVERTED ? at : revertedAt,
                createdAt, at, createdBy,
                version + 1,
                auditAttributes);
    }
}
