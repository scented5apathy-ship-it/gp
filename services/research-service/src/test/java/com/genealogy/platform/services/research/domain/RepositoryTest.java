package com.genealogy.platform.services.research.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Repository} aggregate.
 */
class RepositoryTest {

    private static ResearchAuditAttributes audit() {
        return ResearchAuditAttributes.of("actor-1", "corr-1");
    }

    private static TenantScopedId id() {
        return TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.REPOSITORY, "repo-1");
    }

    @Test
    void createRejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> Repository.create(
                id(), "", RepositoryKind.ARCHIVE, null, null, null, audit()));
    }

    @Test
    void createRejectsOversizedName() {
        String huge = "n".repeat(257);
        assertThrows(IllegalArgumentException.class, () -> Repository.create(
                id(), huge, RepositoryKind.ARCHIVE, null, null, null, audit()));
    }

    @Test
    void familyHoldingIsForcedPrivate() {
        Repository repo = Repository.create(
                id(), "private album", RepositoryKind.FAMILY_HOLDING, null, null, null, audit());
        assertTrue(repo.privateHolding());
        assertFalse(repo.isArchived());
    }

    @Test
    void archiveIsIdempotent() {
        Repository repo = Repository.create(
                id(), "archive", RepositoryKind.ARCHIVE, null, null, null, audit());
        Repository archived = repo.archive(Instant.now(), audit().withReason("retired"));
        assertTrue(archived.isArchived());
        assertThrows(IllegalStateException.class, () -> archived.archive(Instant.now(), audit()));
    }

    @Test
    void rejectTenantMismatchOnId() {
        TenantScopedId badId = TenantScopedId.of("tenant-1",
                TenantScopedId.ResourceKind.CITATION, "cit-1");
        assertThrows(IllegalArgumentException.class, () -> Repository.create(
                badId, "name", RepositoryKind.ARCHIVE, null, null, null, audit()));
    }

    @Test
    void metadataSizeCapped() {
        java.util.LinkedHashMap<String, String> meta = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 33; i += 1) {
            meta.put("k" + i, "v");
        }
        assertThrows(IllegalArgumentException.class, () -> new Repository(
                id(), "name", RepositoryKind.ARCHIVE, null, null, null, false,
                Instant.now(), Instant.now(), null, 1L, audit(), meta));
    }
}
