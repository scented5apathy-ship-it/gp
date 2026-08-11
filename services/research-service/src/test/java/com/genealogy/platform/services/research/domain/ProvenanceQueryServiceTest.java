package com.genealogy.platform.services.research.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link ProvenanceQueryService} in-memory
 * executor.
 */
class ProvenanceQueryServiceTest {

    private static ResearchAuditAttributes audit() {
        return ResearchAuditAttributes.of("actor-1", "corr-1");
    }

    private static Repository repository() {
        return Repository.create(
                TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.REPOSITORY, "repo-1"),
                "National Archives", RepositoryKind.ARCHIVE,
                "Hanoi", null, null, audit());
    }

    private static Source source() {
        TenantScopedId sourceId = TenantScopedId.of("tenant-1",
                TenantScopedId.ResourceKind.SOURCE, "src-1");
        TenantScopedId repoId = TenantScopedId.of("tenant-1",
                TenantScopedId.ResourceKind.REPOSITORY, "repo-1");
        return Source.create(sourceId, repoId, "birth register 1842",
                SourceKind.PRIMARY, null, null, 1842, "Hanoi",
                Locator.of("volume 12, page 5"), null, null, audit());
    }

    private static Citation citation(String claimRef, double confidence, String idSuffix) {
        TenantScopedId citId = TenantScopedId.of("tenant-1",
                TenantScopedId.ResourceKind.CITATION, "cit-" + idSuffix);
        TenantScopedId sourceId = TenantScopedId.of("tenant-1",
                TenantScopedId.ResourceKind.SOURCE, "src-1");
        return Citation.create(citId, sourceId, claimRef, null, Locator.of("page 1"),
                CitationQuality.ORIGINAL, Citation.Disposition.SUPPORTS,
                Certainty.VERIFIED, confidence, null, null, null, null, audit());
    }

    @Test
    void traverseReturnsSortedHops() {
        Repository repo = repository();
        Source src = source();
        Citation cit1 = citation("claim-1", 0.7, "a");
        Citation cit2 = citation("claim-1", 0.9, "b");
        ProvenanceQueryService service = ProvenanceQueryService.builder()
                .addRepository(repo)
                .addSource(src)
                .addCitation(cit1)
                .addCitation(cit2)
                .build();
        ProvenanceQueryService.ProvenanceChain chain = service.traverse("tenant-1", "claim-1");
        assertEquals(2, chain.size());
        assertEquals("repo-1", chain.hops().get(0).repositoryId());
        assertEquals("National Archives", chain.hops().get(0).repositoryName());
        assertEquals(RepositoryKind.ARCHIVE, chain.hops().get(0).repositoryKind());
        assertEquals(CitationQuality.ORIGINAL, chain.hops().get(0).quality());
        assertEquals(Citation.Disposition.SUPPORTS, chain.hops().get(0).disposition());
        assertEquals(0.7, chain.hops().get(0).confidence());
    }

    @Test
    void traverseReturnsEmptyForUnknownClaim() {
        ProvenanceQueryService service = ProvenanceQueryService.builder().build();
        ProvenanceQueryService.ProvenanceChain chain = service.traverse("tenant-1", "missing");
        assertTrue(chain.isEmpty());
    }

    @Test
    void traverseRejectsBlankClaimReference() {
        ProvenanceQueryService service = ProvenanceQueryService.builder().build();
        try {
            service.traverse("tenant-1", "");
            org.junit.jupiter.api.Assertions.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("claimReference"));
        }
    }

    @Test
    void traverseByCitationReturnsSingleHop() {
        Repository repo = repository();
        Source src = source();
        Citation cit = citation("claim-1", 0.5, "x");
        ProvenanceQueryService service = ProvenanceQueryService.builder()
                .addRepository(repo)
                .addSource(src)
                .addCitation(cit)
                .build();
        ProvenanceQueryService.ProvenanceChain chain =
                service.traverseByCitation("tenant-1", "cit-x");
        assertEquals(1, chain.size());
        assertEquals("src-1", chain.hops().get(0).sourceId());
    }

    @Test
    void citationCountAggregatesByClaim() {
        ProvenanceQueryService service = ProvenanceQueryService.builder()
                .addCitation(citation("claim-1", 0.5, "a"))
                .addCitation(citation("claim-1", 0.6, "b"))
                .addCitation(citation("claim-2", 0.4, "c"))
                .build();
        assertEquals(2, service.citationCount("tenant-1", "claim-1"));
        assertEquals(1, service.citationCount("tenant-1", "claim-2"));
        assertEquals(0, service.citationCount("tenant-1", "claim-3"));
    }

    @Test
    void traversableForUnknownTenantIsEmpty() {
        ProvenanceQueryService service = ProvenanceQueryService.builder()
                .addCitation(citation("claim-1", 0.5, "a"))
                .build();
        assertTrue(service.traverse("OTHER", "claim-1").isEmpty());
    }
}
