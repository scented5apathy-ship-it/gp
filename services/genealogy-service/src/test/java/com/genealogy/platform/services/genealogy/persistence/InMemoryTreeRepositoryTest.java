package com.genealogy.platform.services.genealogy.persistence;

import com.genealogy.platform.services.genealogy.domain.CollaborationMode;
import com.genealogy.platform.services.genealogy.domain.LifecycleState;
import com.genealogy.platform.services.genealogy.domain.Tree;
import com.genealogy.platform.services.genealogy.domain.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryTreeRepositoryTest {

    private InMemoryTreeRepository repo;

    @BeforeEach
    void setup() {
        repo = new InMemoryTreeRepository();
    }

    @Test
    void insertAndFindById() {
        Tree tree = sample("tree-1", "tenant-1", "smith");
        repo.insert(tree);
        Optional<Tree> found = repo.findById("tenant-1", "tree-1");
        assertTrue(found.isPresent());
        assertEquals(tree, found.get());
    }

    @Test
    void tenantIsolationEnforced() {
        Tree tree = sample("tree-1", "tenant-1", "smith");
        repo.insert(tree);
        assertEquals(Optional.empty(), repo.findById("tenant-2", "tree-1"));
    }

    @Test
    void duplicateSlugWithinTenantRejected() {
        repo.insert(sample("tree-1", "tenant-1", "smith"));
        assertThrows(IllegalStateException.class,
                () -> repo.insert(sample("tree-2", "tenant-1", "smith")));
    }

    @Test
    void sameSlugDifferentTenantsAllowed() {
        repo.insert(sample("tree-1", "tenant-1", "smith"));
        repo.insert(sample("tree-2", "tenant-2", "smith"));
        assertEquals(2, repo.countByTenant("tenant-1") + repo.countByTenant("tenant-2"));
    }

    @Test
    void optimisticConcurrencyOnUpdate() {
        Tree tree = sample("tree-1", "tenant-1", "smith");
        repo.insert(tree);
        Tree advanced = tree.withVisibility(Visibility.PUBLIC, Instant.now());
        repo.update(advanced);
        assertThrows(IllegalStateException.class, () -> repo.update(tree));
    }

    @Test
    void updateIncrementsVersion() {
        Tree tree = sample("tree-1", "tenant-1", "smith");
        repo.insert(tree);
        Tree advanced = tree.withVisibility(Visibility.PUBLIC, Instant.now());
        repo.update(advanced);
        Optional<Tree> found = repo.findById("tenant-1", "tree-1");
        assertTrue(found.isPresent());
        assertNotEquals(tree.version(), found.get().version());
    }

    @Test
    void listByTenantFiltersOtherTenants() {
        repo.insert(sample("tree-1", "tenant-1", "alpha"));
        repo.insert(sample("tree-2", "tenant-1", "beta"));
        repo.insert(sample("tree-3", "tenant-2", "alpha"));
        assertEquals(2, repo.listByTenant("tenant-1", 10, 0).size());
        assertEquals(1, repo.listByTenant("tenant-2", 10, 0).size());
    }

    private static Tree sample(String id, String tenant, String slug) {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        return new Tree(
                id, tenant, slug, "Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, LifecycleState.ACTIVE,
                "en-US", "UTC", "GREGORIAN", Map.of(), "user-1", 1L, now, now);
    }
}
