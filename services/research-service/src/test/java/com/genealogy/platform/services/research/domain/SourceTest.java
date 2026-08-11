package com.genealogy.platform.services.research.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Source} aggregate.
 */
class SourceTest {

    private static ResearchAuditAttributes audit() {
        return ResearchAuditAttributes.of("actor-1", "corr-1");
    }

    private static TenantScopedId repoId() {
        return TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.REPOSITORY, "repo-1");
    }

    private static TenantScopedId sourceId() {
        return TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.SOURCE, "src-1");
    }

    @Test
    void createRejectsBlankTitle() {
        assertThrows(IllegalArgumentException.class, () -> Source.create(
                sourceId(), repoId(), "", SourceKind.PRIMARY, null, null, null, null,
                null, null, null, audit()));
    }

    @Test
    void createRejectsTenantMismatch() {
        TenantScopedId wrongRepo = TenantScopedId.of("tenant-OTHER",
                TenantScopedId.ResourceKind.REPOSITORY, "repo-1");
        assertThrows(IllegalArgumentException.class, () -> Source.create(
                sourceId(), wrongRepo, "title", SourceKind.PRIMARY, null, null, null, null,
                null, null, null, audit()));
    }

    @Test
    void createRejectsIdResourceKind() {
        TenantScopedId wrongId = TenantScopedId.of("tenant-1",
                TenantScopedId.ResourceKind.CITATION, "cit-1");
        assertThrows(IllegalArgumentException.class, () -> Source.create(
                wrongId, repoId(), "title", SourceKind.PRIMARY, null, null, null, null,
                null, null, null, audit()));
    }

    @Test
    void publicationYearMustBeFourDigit() {
        assertThrows(IllegalArgumentException.class, () -> Source.create(
                sourceId(), repoId(), "title", SourceKind.PRIMARY, null, null, 999, null,
                null, null, null, audit()));
        assertThrows(IllegalArgumentException.class, () -> Source.create(
                sourceId(), repoId(), "title", SourceKind.PRIMARY, null, null, 10000, null,
                null, null, null, audit()));
    }

    @Test
    void archiveCitationCapEnforced() {
        Source source = Source.create(sourceId(), repoId(), "title",
                SourceKind.PRIMARY, null, null, null, null, null, null, null, audit());
        java.util.List<Citation> oversize = new java.util.ArrayList<>();
        for (int i = 0; i < 257; i += 1) {
            TenantScopedId cid = TenantScopedId.of("tenant-1",
                    TenantScopedId.ResourceKind.CITATION, "cit-" + i);
            oversize.add(Citation.create(cid, sourceId(), "claim-" + i, null, null,
                    CitationQuality.ORIGINAL, Citation.Disposition.SUPPORTS,
                    Certainty.ASSERTED, null, null, null, null, null, audit()));
        }
        assertThrows(IllegalArgumentException.class, () -> source.withCitations(oversize));
    }

    @Test
    void pointerOnlyRequiresAttachment() {
        // The constructor allows zero attachments; the
        // invariant service emits the DENY. We assert that
        // here.
        Source source = Source.create(sourceId(), repoId(), "register",
                SourceKind.ARCHIVE, null, null, null, null, null, null, null, audit());
        assertTrue(source.isPointerOnly());
        List<ResearchInvariants.Finding> findings = ResearchInvariants.check(source);
        assertTrue(ResearchInvariants.hasDeny(findings));
        assertTrue(findings.stream().anyMatch(f -> f.code()
                == ResearchInvariants.ConflictCode.SOURCE_POINTER_REQUIRES_ATTACHMENT));
    }

    @Test
    void primarySourceAcceptsNoAttachment() {
        Source source = Source.create(sourceId(), repoId(), "register",
                SourceKind.PRIMARY, null, null, null, null, null, null, null, audit());
        assertFalse(source.isPointerOnly());
        List<ResearchInvariants.Finding> findings = ResearchInvariants.check(source);
        assertFalse(ResearchInvariants.hasDeny(findings));
    }
}
