package com.genealogy.platform.services.research.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ResearchInvariants}.
 */
class ResearchInvariantsTest {

    private static ResearchAuditAttributes audit() {
        return ResearchAuditAttributes.of("actor-1", "corr-1");
    }

    @Test
    void repositoryPrivateHoldingEmitsInfo() {
        Repository repo = Repository.create(
                TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.REPOSITORY, "repo-1"),
                "private", RepositoryKind.FAMILY_HOLDING, null, null, null, audit());
        List<ResearchInvariants.Finding> findings = ResearchInvariants.check(repo);
        assertTrue(findings.stream().anyMatch(f -> f.severity() == ResearchInvariants.Severity.INFO));
        assertFalse(ResearchInvariants.hasDeny(findings));
    }

    @Test
    void sourceArchiveRequiresAttachment() {
        Source source = Source.create(
                TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.SOURCE, "src-1"),
                TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.REPOSITORY, "repo-1"),
                "register", SourceKind.ARCHIVE, null, null, null, null, null, null, null, audit());
        assertTrue(ResearchInvariants.hasDeny(ResearchInvariants.check(source)));
    }

    @Test
    void citationExternalUrlRequiresCanonical() {
        Citation citation = Citation.create(
                TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.CITATION, "cit-1"),
                TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.SOURCE, "src-1"),
                "claim-1", null, Locator.of("page 1"),
                CitationQuality.ORIGINAL, Citation.Disposition.SUPPORTS,
                Certainty.ASSERTED, null, null, null,
                List.of(new AttachmentRef(AttachmentKind.EXTERNAL_URL, "obj-1",
                        null, null, null)),
                null, audit());
        assertTrue(ResearchInvariants.hasDeny(ResearchInvariants.check(citation)));
    }

    @Test
    void conflictRequiresMultipleParticipants() {
        // The compact constructor rejects conflicts with < 2
        // participants; the invariant service flags the same
        // rule when the constructor is bypassed (e.g. JDBC
        // rehydration) — pin that the constructor rule fires
        // first.
        assertThrows(IllegalArgumentException.class, () -> new Conflict(
                TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.CONFLICT, "conf-1"),
                "summary", ConflictKind.SOURCE_DISAGREES, null,
                List.of(new Conflict.Participant("a", null, null, List.of())),
                List.of(), Conflict.ConflictStatus.OPEN, null, null,
                java.time.Instant.now(), java.time.Instant.now(), null, 1L, audit()));
    }

    @Test
    void conflictResolvedRequiresProof() {
        Conflict conflict = new Conflict(
                TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.CONFLICT, "conf-1"),
                "summary", ConflictKind.SOURCE_DISAGREES, null,
                List.of(
                        new Conflict.Participant("a", null, null, List.of()),
                        new Conflict.Participant("b", null, null, List.of())),
                List.of(), Conflict.ConflictStatus.RESOLVED, "winner a", null,
                java.time.Instant.now(), java.time.Instant.now(), null, 1L, audit());
        assertTrue(ResearchInvariants.hasDeny(ResearchInvariants.check(conflict)));
    }

    @Test
    void transcriptLineOutOfOrderEmitsWarn() {
        Citation citation = Citation.create(
                TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.CITATION, "cit-1"),
                TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.SOURCE, "src-1"),
                "claim-1", null, Locator.of("page 1"),
                CitationQuality.TRANSCRIPT, Citation.Disposition.SUPPORTS,
                Certainty.ASSERTED, null, null,
                List.of(
                        new TranscriptSegment(2, "second", null, null, null),
                        new TranscriptSegment(1, "first", null, null, null)),
                null, null, audit());
        List<ResearchInvariants.Finding> findings = ResearchInvariants.check(citation);
        assertTrue(findings.stream().anyMatch(f -> f.code()
                == ResearchInvariants.ConflictCode.TRANSCRIPT_LINE_OUT_OF_ORDER));
    }

    @Test
    void hasDenyFalseForEmptyFindings() {
        assertFalse(ResearchInvariants.hasDeny(List.of()));
    }
}
