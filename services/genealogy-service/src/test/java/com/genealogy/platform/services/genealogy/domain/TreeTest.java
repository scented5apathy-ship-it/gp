package com.genealogy.platform.services.genealogy.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

class TreeTest {

    @Test
    void createsActiveTree() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        Tree tree = sampleTree(now);
        assertEquals(LifecycleState.ACTIVE, tree.lifecycleState());
        assertEquals(Visibility.PRIVATE, tree.visibility());
    }

    @Test
    void rejectsBadSlug() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new Tree(
                "tree-1", "tenant-1", "BAD_SLUG", "Display", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, LifecycleState.ACTIVE,
                "en-US", "UTC", "GREGORIAN", Map.of(), "user-1", 1L, now, now));
    }

    @Test
    void visibilityTransitionBumpsVersion() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        Tree tree = sampleTree(now);
        Tree updated = tree.withVisibility(Visibility.PUBLIC, now.plusSeconds(1));
        assertEquals(Visibility.PUBLIC, updated.visibility());
        assertEquals(tree.version() + 1, updated.version());
        assertNotEquals(tree.updatedAt(), updated.updatedAt());
    }

    @Test
    void archiveThenRestoreLifecycle() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        Tree tree = sampleTree(now);
        Tree archived = tree.archived(now.plusSeconds(1));
        assertEquals(LifecycleState.ARCHIVED, archived.lifecycleState());
        Tree restored = archived.restored(now.plusSeconds(2));
        assertEquals(LifecycleState.ACTIVE, restored.lifecycleState());
        assertEquals(archived.version() + 1, restored.version());
    }

    @Test
    void transferUpdatesTenantId() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        Tree tree = sampleTree(now);
        Tree transferred = tree.transferredTo("tenant-2", now.plusSeconds(1));
        assertEquals("tenant-2", transferred.tenantId());
        assertEquals(tree.treeId(), transferred.treeId());
        assertEquals(tree.slug(), transferred.slug());
    }

    @Test
    void deleteIsTerminal() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        Tree tree = sampleTree(now);
        Tree deleted = tree.deleted(now.plusSeconds(1));
        assertEquals(LifecycleState.DELETED, deleted.lifecycleState());
    }

    @Test
    void brandingIsImmutable() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        java.util.Map<String, String> branding = new java.util.HashMap<>();
        branding.put("primaryColor", "#ff0000");
        Tree tree = new Tree(
                "tree-1", "tenant-1", "smith", "Smith Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, LifecycleState.ACTIVE,
                "en-US", "UTC", "GREGORIAN", branding, "user-1", 1L, now, now);
        assertThrows(UnsupportedOperationException.class,
                () -> tree.branding().put("primaryColor", "#000000"));
    }

    @Test
    void normaliseSlugLowercases() {
        assertEquals("smith", Tree.normaliseSlug("Smith"));
        assertTrue(Tree.SLUG_PATTERN.matcher("smith-family-2026").matches());
        assertThrows(IllegalArgumentException.class, () -> Tree.normaliseSlug(null));
    }

    private static Tree sampleTree(Instant now) {
        return new Tree(
                "tree-1", "tenant-1", "smith", "Smith Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, LifecycleState.ACTIVE,
                "en-US", "UTC", "GREGORIAN", Map.of(), "user-1", 1L, now, now);
    }
}
