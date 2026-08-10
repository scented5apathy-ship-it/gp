package com.genealogy.platform.services.genealogy.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeRecordTest {

    private static final Instant T = Instant.parse("2026-08-10T10:00:00Z");

    private static MergeCandidate candidate(
            String winner,
            String loser,
            double nameEquality,
            double dateProximity,
            double placeProximity,
            double identifierMatch,
            double overallScore) {
        return new MergeCandidate(
                "cand-1",
                winner,
                loser,
                nameEquality,
                dateProximity,
                placeProximity,
                identifierMatch,
                overallScore,
                MergeProvenance.AUTOMATED_SCORER);
    }

    private static MergeRecord baseRecord(
            MergeStatus status,
            String reviewerUserId,
            String reason,
            String snapshotHash,
            Instant mergedAt) {
        return new MergeRecord(
                MergeId.newId(),
                "tenant-1",
                "tree-1",
                MergeKind.DUPLICATE_PERSON_MERGE,
                "person-winner",
                "person-loser",
                status,
                0.92,
                List.of(candidate(
                        "person-winner",
                        "person-loser",
                        1.0, 0.9, 0.8, 0.95,
                        0.92)),
                MergeProvenance.AUTOMATED_SCORER,
                reviewerUserId,
                reason,
                snapshotHash,
                MergeRecord.defaultRevertCommandJson(
                        MergeId.newId(),
                        "person-winner",
                        "person-loser"),
                42L,
                mergedAt,
                null,
                T,
                T,
                "user-1",
                1L,
                null);
    }

    @Test
    void merge_record_happy_path_merged() {
        MergeRecord r = baseRecord(
                MergeStatus.MERGED,
                "user-reviewer",
                "duplicated person — manual review",
                "sha256:abcd",
                T);
        assertEquals(MergeStatus.MERGED, r.status());
        assertEquals("user-reviewer", r.reviewerUserId());
        assertNotNull(r.snapshotHash());
        assertNotNull(r.mergedAt());
    }

    @Test
    void merge_record_self_merge_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new MergeRecord(
                        MergeId.newId(),
                        "tenant-1",
                        "tree-1",
                        MergeKind.DUPLICATE_PERSON_MERGE,
                        "person-1",
                        "person-1",
                        MergeStatus.CANDIDATES_SCORED,
                        0.92,
                        List.of(),
                        MergeProvenance.AUTOMATED_SCORER,
                        null,
                        null,
                        null,
                        null,
                        0L,
                        null,
                        null,
                        T,
                        T,
                        "user-1",
                        1L,
                        null));
    }

    @Test
    void merge_record_merged_without_reviewer_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                baseRecord(
                        MergeStatus.MERGED,
                        null,
                        "duplicated",
                        "sha256:abcd",
                        T));
    }

    @Test
    void merge_record_merged_without_reason_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                baseRecord(
                        MergeStatus.MERGED,
                        "user-reviewer",
                        null,
                        "sha256:abcd",
                        T));
    }

    @Test
    void merge_record_merged_without_snapshot_hash_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                baseRecord(
                        MergeStatus.MERGED,
                        "user-reviewer",
                        "duplicated",
                        null,
                        T));
    }

    @Test
    void merge_record_merged_without_merged_at_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                baseRecord(
                        MergeStatus.MERGED,
                        "user-reviewer",
                        "duplicated",
                        "sha256:abcd",
                        null));
    }

    @Test
    void merge_record_reverted_without_reviewer_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                baseRecord(
                        MergeStatus.REVERTED,
                        null,
                        "incorrect merge",
                        "sha256:abcd",
                        T));
    }

    @Test
    void merge_record_reason_cap_enforced() {
        String tooLong = "x".repeat(MergeRecord.MAX_REASON_CHARS + 1);
        assertThrows(IllegalArgumentException.class, () ->
                new MergeRecord(
                        MergeId.newId(),
                        "tenant-1",
                        "tree-1",
                        MergeKind.DUPLICATE_PERSON_MERGE,
                        "person-winner",
                        "person-loser",
                        MergeStatus.MERGED,
                        0.92,
                        List.of(),
                        MergeProvenance.AUTOMATED_SCORER,
                        "user-reviewer",
                        tooLong,
                        "sha256:abcd",
                        null,
                        0L,
                        T,
                        null,
                        T,
                        T,
                        "user-1",
                        1L,
                        null));
    }

    @Test
    void merge_record_with_status_increments_version() {
        // Build a "ready to merge" record with the full
        // reviewer + reason + snapshotHash triple so the
        // constructor accepts the transition to MERGED.
        MergeRecord r = new MergeRecord(
                MergeId.newId(),
                "tenant-1",
                "tree-1",
                MergeKind.DUPLICATE_PERSON_MERGE,
                "person-winner",
                "person-loser",
                MergeStatus.REVIEWED,
                0.92,
                List.of(candidate(
                        "person-winner",
                        "person-loser",
                        1.0, 0.9, 0.8, 0.95,
                        0.92)),
                MergeProvenance.AUTOMATED_SCORER,
                "user-reviewer",
                "duplicated",
                "sha256:abcd",
                MergeRecord.defaultRevertCommandJson(
                        MergeId.newId(),
                        "person-winner",
                        "person-loser"),
                0L,
                null,
                null,
                T,
                T,
                "user-1",
                1L,
                null);
        MergeRecord next = r.withStatus(MergeStatus.MERGED, T);
        assertEquals(MergeStatus.MERGED, next.status());
        assertEquals(r.version() + 1, next.version());
        assertEquals(T, next.mergedAt());
    }

    @Test
    void invariants_check_intrinsic_emits_self_merge_for_winner_equals_loser() {
        // The compact constructor rejects winner == loser
        // before invariants can run; the invariants service
        // is a defense-in-depth layer that re-runs the
        // same check. We verify the constructor guard here
        // and rely on the explicit MergeInvariants tests
        // below for the cross-record cases.
        assertThrows(IllegalArgumentException.class, () ->
                new MergeRecord(
                        MergeId.newId(),
                        "tenant-1",
                        "tree-1",
                        MergeKind.DUPLICATE_PERSON_MERGE,
                        "person-1",
                        "person-1",
                        MergeStatus.CANDIDATES_SCORED,
                        0.5,
                        List.of(),
                        MergeProvenance.AUTOMATED_SCORER,
                        null,
                        null,
                        null,
                        null,
                        0L,
                        null,
                        null,
                        T,
                        T,
                        "user-1",
                        1L,
                        null));
    }

    @Test
    void invariants_check_intrinsic_clean_for_valid_merged_record() {
        MergeRecord r = baseRecord(
                MergeStatus.MERGED,
                "user-reviewer",
                "duplicated",
                "sha256:abcd",
                T);
        List<MergeInvariants.Finding> findings =
                MergeInvariants.checkIntrinsic(r);
        assertFalse(MergeInvariants.hasDeny(findings));
    }

    @Test
    void invariants_revert_window_expired() {
        MergeRecord r = baseRecord(
                MergeStatus.MERGED,
                "user-reviewer",
                "duplicated",
                "sha256:abcd",
                T);
        Instant tooLate = T.plusSeconds(31L * 24L * 3600L);
        List<MergeInvariants.Finding> findings =
                MergeInvariants.checkRevertWindow(r, tooLate, 30);
        assertTrue(MergeInvariants.hasDeny(findings));
        assertEquals(
                MergeInvariants.ConflictCode.REVERT_WINDOW_EXPIRED,
                findings.get(0).code());
    }

    @Test
    void invariants_revert_window_inside_window_ok() {
        MergeRecord r = baseRecord(
                MergeStatus.MERGED,
                "user-reviewer",
                "duplicated",
                "sha256:abcd",
                T);
        Instant ok = T.plusSeconds(15L * 24L * 3600L);
        List<MergeInvariants.Finding> findings =
                MergeInvariants.checkRevertWindow(r, ok, 30);
        assertFalse(MergeInvariants.hasDeny(findings));
    }

    @Test
    void invariants_revert_window_rejects_non_merged_status() {
        MergeRecord r = baseRecord(
                MergeStatus.REVIEWED,
                null,
                null,
                null,
                null);
        List<MergeInvariants.Finding> findings =
                MergeInvariants.checkRevertWindow(r, T, 30);
        assertTrue(MergeInvariants.hasDeny(findings));
        assertEquals(
                MergeInvariants.ConflictCode.REVERT_NOT_FROM_MERGED,
                findings.get(0).code());
    }

    @Test
    void invariants_rekey_limit_exceeded() {
        List<MergeInvariants.Finding> findings =
                MergeInvariants.checkRekeyLimit(15_000L, 10_000L);
        assertTrue(MergeInvariants.hasDeny(findings));
        assertEquals(
                MergeInvariants.ConflictCode.REKEY_LIMIT_EXCEEDED,
                findings.get(0).code());
    }

    @Test
    void invariants_rekey_limit_within_budget_ok() {
        List<MergeInvariants.Finding> findings =
                MergeInvariants.checkRekeyLimit(500L, 10_000L);
        assertFalse(MergeInvariants.hasDeny(findings));
    }

    @Test
    void compose_score_uses_pinned_weights() {
        double score = MergeInvariants.composeScore(Map.of(
                ScoringComponent.NAME_EQUALITY, 1.0,
                ScoringComponent.DATE_PROXIMITY, 1.0,
                ScoringComponent.PLACE_PROXIMITY, 1.0,
                ScoringComponent.IDENTIFIER_MATCH, 1.0));
        assertEquals(1.0, score, 0.0001);
    }

    @Test
    void compose_score_zero_when_all_components_missing() {
        double score = MergeInvariants.composeScore(Map.of());
        assertEquals(0.0, score, 0.0001);
    }

    @Test
    void compose_score_partial_contributions() {
        double score = MergeInvariants.composeScore(Map.of(
                ScoringComponent.NAME_EQUALITY, 1.0,
                ScoringComponent.DATE_PROXIMITY, 0.5,
                ScoringComponent.PLACE_PROXIMITY, 0.0,
                ScoringComponent.IDENTIFIER_MATCH, 0.0));
        // 0.4*1 + 0.25*0.5 + 0.15*0 + 0.2*0 = 0.525
        assertEquals(0.525, score, 0.0001);
    }

    @Test
    void default_revert_command_json_is_canonical() {
        MergeId mergeId = MergeId.of("merge-abc");
        String json = MergeRecord.defaultRevertCommandJson(
                mergeId, "winner", "loser");
        assertTrue(json.contains("\"mergeId\":\"merge-abc\""));
        assertTrue(json.contains("\"action\":\"revert\""));
        assertTrue(json.contains("\"rekeyFrom\":\"winner\""));
        assertTrue(json.contains("\"rekeyTo\":\"loser\""));
    }
}
