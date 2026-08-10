package com.genealogy.platform.services.genealogy.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MergeCandidateTest {

    @Test
    void merge_candidate_rejects_self_pair() {
        assertThrows(IllegalArgumentException.class, () ->
                new MergeCandidate(
                        "cand-1",
                        "person-1",
                        "person-1",
                        0.9, 0.5, 0.5, 0.5,
                        0.6,
                        MergeProvenance.AUTOMATED_SCORER));
    }

    @Test
    void merge_candidate_rejects_out_of_range_components() {
        assertThrows(IllegalArgumentException.class, () ->
                new MergeCandidate(
                        "cand-1",
                        "person-w",
                        "person-l",
                        1.5, 0.5, 0.5, 0.5,
                        0.6,
                        MergeProvenance.AUTOMATED_SCORER));
        assertThrows(IllegalArgumentException.class, () ->
                new MergeCandidate(
                        "cand-1",
                        "person-w",
                        "person-l",
                        0.5, 0.5, 0.5, 0.5,
                        1.2,
                        MergeProvenance.AUTOMATED_SCORER));
    }

    @Test
    void merge_candidate_rejects_null_provenance() {
        assertThrows(IllegalArgumentException.class, () ->
                new MergeCandidate(
                        "cand-1",
                        "person-w",
                        "person-l",
                        0.5, 0.5, 0.5, 0.5,
                        0.5,
                        null));
    }

    @Test
    void merge_candidate_rejects_blank_person_ids() {
        assertThrows(IllegalArgumentException.class, () ->
                new MergeCandidate(
                        "cand-1",
                        "",
                        "person-l",
                        0.5, 0.5, 0.5, 0.5,
                        0.5,
                        MergeProvenance.AUTOMATED_SCORER));
        assertThrows(IllegalArgumentException.class, () ->
                new MergeCandidate(
                        "cand-1",
                        "person-w",
                        null,
                        0.5, 0.5, 0.5, 0.5,
                        0.5,
                        MergeProvenance.AUTOMATED_SCORER));
    }
}
