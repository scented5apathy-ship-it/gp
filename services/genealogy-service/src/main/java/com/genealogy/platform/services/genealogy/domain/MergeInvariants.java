package com.genealogy.platform.services.genealogy.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure invariant checks for {@link MergeRecord} and the
 * merge workflow. Mirrors `requirements.md` R4.5 / R4.6 /
 * R5.5 / R8.5 / R10 / R16 and `glossary-and-policy-matrix.
 * md` §2.4 (`MERGE` rule).
 *
 * <p>Policy mapping (driven by
 * {@code person-merge-policy.yaml}):
 *
 * <ul>
 *   <li>{@code selfLinkPolicy = deny}: a merge MUST NOT
 *       pair a Person with themselves. {@link MergeRecord}
 *       already rejects this in its compact constructor;
 *       this service re-checks at the cross-record level so
 *       the command service that bypassed the constructor
 *       (e.g. JDBC rehydration) still gets the same answer.
 *   <li>{@code revertWindowDays = 30}: a {@code MERGED}
 *       record can transition to {@code REVERTED} only
 *       within the window. Outside the window the merge is
 *       immutable.
 *   <li>{@code status = MERGED}: requires non-blank
 *       {@code reviewerUserId} + {@code reason} +
 *       {@code snapshotHash}. The audit trail cannot lose
 *       the reviewer attribution (R10 + R16).
 *   <li>{@code sourcePreservationRequired = true}: every
 *       Claim / Citation / Source attached to a losing
 *       Person is preserved verbatim (R5.5 + R8.5). The
 *       merge command must NOT hard-delete any citation
 *       row.
 *   <li>{@code redirectPreservationRequired = true}:
 *       external identifiers (share tokens, public URLs)
 *       attached to a losing Person must be reissued on
 *       the winner and revoked on the loser; the platform
 *       emits the redirect for external callers.
 * </ul>
 */
public final class MergeInvariants {

    /** Severity of an invariant finding. */
    public enum Severity {
        DENY,
        WARN,
        INFO
    }

    /** Closed-set reason codes emitted by the invariant service. */
    public enum ConflictCode {
        SELF_MERGE_FORBIDDEN,
        CROSS_TENANT_FORBIDDEN,
        MISSING_REVIEWER,
        MISSING_REASON,
        MISSING_SNAPSHOT_HASH,
        REVERT_WINDOW_EXPIRED,
        REVERT_NOT_FROM_MERGED,
        REKEY_LIMIT_EXCEEDED,
        BELOW_MANUAL_SCORE_FLOOR,
        REJECTED_NOT_FROM_SCORED,
        TOMBSTONE_NOT_RESTORABLE,
    }

    /** One invariant finding. */
    public record Finding(Severity severity, ConflictCode code, String message) {
        public Finding {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    /** Default revert window mirrors the contract default. */
    public static final int DEFAULT_REVERT_WINDOW_DAYS = 30;
    /** Default manual-score floor mirrors the contract default. */
    public static final double DEFAULT_MANUAL_SCORE_FLOOR = 0.5;

    private MergeInvariants() {}

    /**
     * Check the aggregate's intrinsic invariants. The compact
     * constructor of {@link MergeRecord} already enforces the
     * structural ones (self-merge, status-requires-reviewer,
     * status-requires-reason, snapshot-hash-on-MERGED); this
     * method re-runs them so a command service that bypassed
     * the constructor (e.g. JDBC rehydration) still gets the
     * same answer.
     */
    public static List<Finding> checkIntrinsic(MergeRecord record) {
        Objects.requireNonNull(record, "record");
        List<Finding> findings = new ArrayList<>();
        if (record.winnerPersonId().equals(record.loserPersonId())) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.SELF_MERGE_FORBIDDEN,
                    "merge requires winner != loser ("
                            + record.winnerPersonId() + ")"));
        }
        if (record.status() == MergeStatus.MERGED) {
            if (record.reviewerUserId() == null
                    || record.reviewerUserId().isBlank()) {
                findings.add(new Finding(
                        Severity.DENY,
                        ConflictCode.MISSING_REVIEWER,
                        "status=MERGED requires a non-blank reviewerUserId"));
            }
            if (record.reason() == null || record.reason().isBlank()) {
                findings.add(new Finding(
                        Severity.DENY,
                        ConflictCode.MISSING_REASON,
                        "status=MERGED requires a non-blank reason"));
            }
            if (record.snapshotHash() == null
                    || record.snapshotHash().isBlank()) {
                findings.add(new Finding(
                        Severity.DENY,
                        ConflictCode.MISSING_SNAPSHOT_HASH,
                        "status=MERGED requires a non-blank snapshotHash"));
            }
        }
        if (record.status() == MergeStatus.REVERTED) {
            if (record.reviewerUserId() == null
                    || record.reviewerUserId().isBlank()) {
                findings.add(new Finding(
                        Severity.DENY,
                        ConflictCode.MISSING_REVIEWER,
                        "status=REVERTED requires a non-blank reviewerUserId"));
            }
            if (record.reason() == null || record.reason().isBlank()) {
                findings.add(new Finding(
                        Severity.DENY,
                        ConflictCode.MISSING_REASON,
                        "status=REVERTED requires a non-blank reason"));
            }
        }
        if (record.score() < DEFAULT_MANUAL_SCORE_FLOOR) {
            findings.add(new Finding(
                    Severity.WARN,
                    ConflictCode.BELOW_MANUAL_SCORE_FLOOR,
                    "score " + record.score()
                            + " is below the manual floor "
                            + DEFAULT_MANUAL_SCORE_FLOOR));
        }
        return findings;
    }

    /**
     * Check whether a {@code MERGED} record is still
     * revertible at the supplied instant. The merge is
     * immutable outside the window per
     * {@code spec.revertWindowDays}.
     */
    public static List<Finding> checkRevertWindow(
            MergeRecord record,
            Instant now,
            int revertWindowDays) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(now, "now");
        List<Finding> findings = new ArrayList<>();
        if (record.status() != MergeStatus.MERGED) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.REVERT_NOT_FROM_MERGED,
                    "revert only allowed from status=MERGED, current="
                            + record.status().wire()));
            return findings;
        }
        if (record.mergedAt() == null) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.REVERT_NOT_FROM_MERGED,
                    "revert requires mergedAt to be set"));
            return findings;
        }
        Duration elapsed = Duration.between(record.mergedAt(), now);
        long maxSeconds = (long) revertWindowDays * 24L * 3600L;
        if (elapsed.getSeconds() > maxSeconds) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.REVERT_WINDOW_EXPIRED,
                    "revert window (" + revertWindowDays
                            + " days) expired; merged "
                            + elapsed.toDays() + " days ago"));
        }
        return findings;
    }

    /**
     * Check whether a (winner, loser) pair touches more than
     * {@code maxRekeyedReferencesPerMerge} rows. The merge
     * service MUST reject any pair that exceeds the cap so
     * the transaction stays atomic; the operator is told to
     * split.
     */
    public static List<Finding> checkRekeyLimit(
            long rekeyedCount,
            long maxRekeyedReferencesPerMerge) {
        if (rekeyedCount < 0) {
            throw new IllegalArgumentException(
                    "rekeyedCount must be >=0: " + rekeyedCount);
        }
        if (maxRekeyedReferencesPerMerge <= 0) {
            throw new IllegalArgumentException(
                    "maxRekeyedReferencesPerMerge must be >0: "
                            + maxRekeyedReferencesPerMerge);
        }
        List<Finding> findings = new ArrayList<>();
        if (rekeyedCount > maxRekeyedReferencesPerMerge) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.REKEY_LIMIT_EXCEEDED,
                    "rekey count " + rekeyedCount
                            + " exceeds cap "
                            + maxRekeyedReferencesPerMerge));
        }
        return findings;
    }

    /**
     * Compose a deterministic merge score from the per-
     * component contributions. The contract pins the weights
     * (sum = 1.0); any change in the weight policy is a
     * contract revision and MUST flow through the linter
     * gate.
     */
    public static double composeScore(Map<ScoringComponent, Double> values) {
        Objects.requireNonNull(values, "values");
        double n = n(values, ScoringComponent.NAME_EQUALITY);
        double d = n(values, ScoringComponent.DATE_PROXIMITY);
        double p = n(values, ScoringComponent.PLACE_PROXIMITY);
        double i = n(values, ScoringComponent.IDENTIFIER_MATCH);
        double s = 0.4 * n + 0.25 * d + 0.15 * p + 0.2 * i;
        if (s < 0.0) return 0.0;
        if (s > 1.0) return 1.0;
        return s;
    }

    private static double n(Map<ScoringComponent, Double> v, ScoringComponent k) {
        Double d = v.get(k);
        return d == null ? 0.0 : d;
    }

    /** Convenience: aggregate every check into one pass. */
    public static List<Finding> checkAll(
            MergeRecord record,
            Instant now,
            int revertWindowDays,
            long rekeyedCount,
            long maxRekeyedReferencesPerMerge) {
        List<Finding> findings = new ArrayList<>();
        findings.addAll(checkIntrinsic(record));
        if (!hasDeny(findings)) {
            findings.addAll(checkRevertWindow(record, now, revertWindowDays));
        }
        if (!hasDeny(findings)) {
            findings.addAll(
                    checkRekeyLimit(rekeyedCount, maxRekeyedReferencesPerMerge));
        }
        return Collections.unmodifiableList(findings);
    }

    public static boolean hasDeny(List<Finding> findings) {
        for (Finding f : findings) {
            if (f.severity() == Severity.DENY) {
                return true;
            }
        }
        return false;
    }
}
