package com.genealogy.platform.services.genealogy.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimTest {

    private static final Instant T = Instant.parse("2026-08-10T10:00:00Z");

    private static ClaimSourceReference src(SourceReferenceKind kind, String sourceId) {
        return new ClaimSourceReference(kind, sourceId, null, null, 0.8, null);
    }

    private static Claim baseClaim(
            Certainty certainty,
            ProvenanceStatus provenance,
            Double confidence,
            List<ClaimSourceReference> sources,
            String correctsClaimId) {
        return new Claim(
                ClaimId.of("claim-1"),
                "tenant-1",
                "tree-1",
                "PERSON",
                "person-1",
                certainty,
                provenance,
                confidence,
                "Some claim statement",
                sources,
                correctsClaimId,
                null,
                "user-1",
                T,
                T,
                1L,
                null);
    }

    @Test
    void claim_with_source_and_asserted_ok() {
        Claim c = baseClaim(
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                0.7,
                List.of(src(SourceReferenceKind.URL, "https://example.org/doc")),
                null);
        assertEquals(Certainty.ASSERTED, c.certainty());
        assertEquals(1, c.sources().size());
        assertFalse(LifeEventInvariants.hasDeny(List.of()));
    }

    @Test
    void claim_without_source_is_rejected() {
        Claim c = baseClaim(
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                0.7,
                List.of(),
                null);
        List<ClaimInvariants.Finding> findings = ClaimInvariants.checkIntrinsic(c);
        assertTrue(ClaimInvariants.hasDeny(findings));
        assertEquals(ClaimInvariants.ConflictCode.SOURCE_REQUIRED, findings.get(0).code());
    }

    @Test
    void imported_claim_cannot_be_verified() {
        assertThrows(IllegalArgumentException.class, () -> baseClaim(
                Certainty.VERIFIED,
                ProvenanceStatus.IMPORTED,
                0.7,
                List.of(src(SourceReferenceKind.URL, "https://example.org/x")),
                null));
    }

    @Test
    void verified_by_source_requires_asserted_or_verified() {
        assertThrows(IllegalArgumentException.class, () -> baseClaim(
                Certainty.HYPOTHESIS,
                ProvenanceStatus.VERIFIED_BY_SOURCE,
                0.9,
                List.of(src(SourceReferenceKind.URL, "https://ancestry.example/x")),
                null));
    }

    @Test
    void correction_requires_back_reference() {
        assertThrows(IllegalArgumentException.class, () -> baseClaim(
                Certainty.ASSERTED,
                ProvenanceStatus.CORRECTION,
                0.7,
                List.of(src(SourceReferenceKind.URL, "https://example.org/x")),
                null));
    }

    @Test
    void correction_forbidden_on_non_correction_provenance() {
        assertThrows(IllegalArgumentException.class, () -> baseClaim(
                Certainty.ASSERTED,
                ProvenanceStatus.IMPORTED,
                0.7,
                List.of(src(SourceReferenceKind.URL, "https://example.org/x")),
                "previous-claim-id"));
    }

    @Test
    void confidence_out_of_range_rejected() {
        assertThrows(IllegalArgumentException.class, () -> baseClaim(
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                1.5,
                List.of(src(SourceReferenceKind.URL, "https://example.org/x")),
                null));
        assertThrows(IllegalArgumentException.class, () -> baseClaim(
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                -0.1,
                List.of(src(SourceReferenceKind.URL, "https://example.org/x")),
                null));
    }

    @Test
    void confidence_null_emits_warn_only() {
        Claim c = baseClaim(
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                null,
                List.of(src(SourceReferenceKind.URL, "https://example.org/x")),
                null);
        List<ClaimInvariants.Finding> findings = ClaimInvariants.checkIntrinsic(c);
        assertFalse(ClaimInvariants.hasDeny(findings));
        assertTrue(findings.stream()
                .anyMatch(f -> f.severity() == ClaimInvariants.Severity.WARN));
    }

    @Test
    void sources_cap_at_32() {
        java.util.List<ClaimSourceReference> overflow = new java.util.ArrayList<>();
        for (int i = 0; i < 33; i += 1) {
            overflow.add(src(SourceReferenceKind.URL, "https://example.org/" + i));
        }
        assertThrows(IllegalArgumentException.class, () -> baseClaim(
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                0.7,
                overflow,
                null));
    }

    @Test
    void correction_claim_ok_when_back_reference_present() {
        Claim c = baseClaim(
                Certainty.ASSERTED,
                ProvenanceStatus.CORRECTION,
                0.95,
                List.of(src(SourceReferenceKind.URL, "https://example.org/x")),
                "previous-claim-id");
        List<ClaimInvariants.Finding> findings = ClaimInvariants.checkIntrinsic(c);
        assertFalse(ClaimInvariants.hasDeny(findings));
    }

    @Test
    void with_updated_bumps_version() {
        Claim c = baseClaim(
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                0.7,
                List.of(src(SourceReferenceKind.URL, "https://example.org/x")),
                null);
        Claim next = c.withUpdated(
                Certainty.VERIFIED,
                ProvenanceStatus.USER_ENTERED,
                0.95,
                "verified later",
                null,
                T);
        assertEquals(2L, next.version());
        assertEquals(Certainty.VERIFIED, next.certainty());
        assertEquals(0.95, next.confidence());
    }
}
