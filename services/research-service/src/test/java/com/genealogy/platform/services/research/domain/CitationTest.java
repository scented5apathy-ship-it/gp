package com.genealogy.platform.services.research.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Citation} aggregate.
 */
class CitationTest {

    private static ResearchAuditAttributes audit() {
        return ResearchAuditAttributes.of("actor-1", "corr-1");
    }

    private static TenantScopedId sourceId() {
        return TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.SOURCE, "src-1");
    }

    private static TenantScopedId citationId() {
        return TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.CITATION, "cit-1");
    }

    @Test
    void createRejectsBlankClaimReference() {
        assertThrows(IllegalArgumentException.class, () -> Citation.create(
                citationId(), sourceId(), "", null, null, CitationQuality.ORIGINAL,
                Citation.Disposition.SUPPORTS, Certainty.ASSERTED, null, null,
                null, null, null, audit()));
    }

    @Test
    void transcriptQualityRequiresSegment() {
        Citation citation = Citation.create(
                citationId(), sourceId(), "claim-1", null, Locator.of("page 12"),
                CitationQuality.TRANSCRIPT, Citation.Disposition.SUPPORTS,
                Certainty.ASSERTED, null, null, null, null, null, audit());
        List<ResearchInvariants.Finding> findings = ResearchInvariants.check(citation);
        assertTrue(ResearchInvariants.hasDeny(findings));
        assertTrue(findings.stream().anyMatch(f -> f.code()
                == ResearchInvariants.ConflictCode.TRANSCRIPT_QUALITY_REQUIRES_SEGMENT));
    }

    @Test
    void transcriptWithSegmentPasses() {
        Citation citation = Citation.create(
                citationId(), sourceId(), "claim-1", null, Locator.of("page 12"),
                CitationQuality.TRANSCRIPT, Citation.Disposition.SUPPORTS,
                Certainty.ASSERTED, null, null,
                List.of(new TranscriptSegment(1, "John son of William", null, null, null)),
                null, null, audit());
        List<ResearchInvariants.Finding> findings = ResearchInvariants.check(citation);
        assertFalse(ResearchInvariants.hasDeny(findings));
    }

    @Test
    void citationRequiresLocatorOrQuote() {
        Citation citation = Citation.create(
                citationId(), sourceId(), "claim-1", null, null,
                CitationQuality.ORIGINAL, Citation.Disposition.SUPPORTS,
                Certainty.ASSERTED, null, null, null, null, null, audit());
        List<ResearchInvariants.Finding> findings = ResearchInvariants.check(citation);
        assertTrue(ResearchInvariants.hasDeny(findings));
        assertTrue(findings.stream().anyMatch(f -> f.code()
                == ResearchInvariants.ConflictCode.CITATION_REQUIRES_LOCATOR_OR_QUOTE));
    }

    @Test
    void tenanciesMismatchedFails() {
        TenantScopedId wrongSource = TenantScopedId.of("tenant-OTHER",
                TenantScopedId.ResourceKind.SOURCE, "src-1");
        assertThrows(IllegalArgumentException.class, () -> Citation.create(
                citationId(), wrongSource, "claim-1", null, Locator.of("page 1"),
                CitationQuality.ORIGINAL, Citation.Disposition.SUPPORTS,
                Certainty.ASSERTED, null, null, null, null, null, audit()));
    }

    @Test
    void confidenceOutOfRangeFails() {
        assertThrows(IllegalArgumentException.class, () -> Citation.create(
                citationId(), sourceId(), "claim-1", null, Locator.of("page 1"),
                CitationQuality.ORIGINAL, Citation.Disposition.SUPPORTS,
                Certainty.ASSERTED, 1.5, null, null, null, null, audit()));
    }
}
