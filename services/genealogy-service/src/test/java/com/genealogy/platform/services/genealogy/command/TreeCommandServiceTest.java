package com.genealogy.platform.services.genealogy.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.genealogy.domain.CollaborationMode;
import com.genealogy.platform.services.genealogy.domain.LifecycleState;
import com.genealogy.platform.services.genealogy.domain.Tree;
import com.genealogy.platform.services.genealogy.domain.Visibility;
import com.genealogy.platform.services.genealogy.outbox.InMemoryOutboxWriter;
import com.genealogy.platform.services.genealogy.outbox.JdbcTreeOutboxWriter;
import com.genealogy.platform.services.genealogy.outbox.TreeEventPayloads;
import com.genealogy.platform.services.genealogy.persistence.InMemoryTreeRepository;
import com.genealogy.platform.services.genealogy.persistence.TreeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TreeCommandServiceTest {

    private InMemoryTreeRepository repo;
    private InMemoryOutboxWriter outbox;
    private TreeCommandService service;

    @BeforeEach
    void setup() {
        repo = new InMemoryTreeRepository();
        outbox = new InMemoryOutboxWriter();
        // JdbcTreeOutboxWriter isn't usable without a DataSource.
        // We swap in the in-memory writer through a thin shim — see
        // below for the test-only `OutboxShim`.
        service = new TreeCommandService(repo, shim(), 100);
    }

    private JdbcTreeOutboxWriter shim() {
        // The command service requires a JdbcTreeOutboxWriter.
        // We can't instantiate it without a DataSource, so we
        // wrap an in-memory writer behind the same public method
        // via an anonymous subclass.
        return new JdbcTreeOutboxWriter(mock(DataSource.class), new ObjectMapper()) {
            @Override
            public String enqueue(String aggregateId, String tenantId, String eventType,
                                  Object payload, Instant occurredAt, String correlationId) {
                return outbox.enqueue(aggregateId, tenantId, eventType, payload,
                        occurredAt, correlationId);
            }
        };
    }

    @Test
    void createEmitsTreeCreatedEvent() {
        Tree tree = service.createTree(new TreeCommandService.CreateTreeCommand(
                "tenant-1", "smith", "Smith Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, "en-US", "UTC", "GREGORIAN",
                Map.of(), "user-1", "corr-1", Instant.parse("2026-08-10T10:00:00Z")));
        assertEquals(LifecycleState.ACTIVE, tree.lifecycleState());
        assertEquals(1, outbox.countsByEventType().get(TreeEventPayloads.EVENT_TREE_CREATED));
    }

    @Test
    void changeVisibilityEmitsTransitionEvent() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        Tree tree = service.createTree(new TreeCommandService.CreateTreeCommand(
                "tenant-1", "smith", "Smith Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, "en-US", "UTC", "GREGORIAN",
                Map.of(), "user-1", "corr-1", t0));
        Instant t1 = t0.plusSeconds(60);
        Tree updated = service.changeVisibility(new TreeCommandService.ChangeVisibilityCommand(
                "tenant-1", tree.treeId(), Visibility.PUBLIC, "user-1", "publish", "corr-2", t1));
        assertEquals(Visibility.PUBLIC, updated.visibility());
        assertEquals(1, outbox.countsByEventType()
                .getOrDefault(TreeEventPayloads.EVENT_TREE_VISIBILITY_CHANGED, 0L));
    }

    @Test
    void archiveEmitsArchiveEvent() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        Tree tree = service.createTree(new TreeCommandService.CreateTreeCommand(
                "tenant-1", "smith", "Smith Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, "en-US", "UTC", "GREGORIAN",
                Map.of(), "user-1", "corr-1", t0));
        Instant t1 = t0.plusSeconds(60);
        Tree archived = service.archive(new TreeCommandService.ArchiveCommand(
                "tenant-1", tree.treeId(), "user-1", "housekeeping", "corr-3", t1));
        assertEquals(LifecycleState.ARCHIVED, archived.lifecycleState());
        assertEquals(1, outbox.countsByEventType()
                .getOrDefault(TreeEventPayloads.EVENT_TREE_ARCHIVED, 0L));
        // Subsequent mutation must be rejected on archived tree.
        assertThrows(IllegalStateException.class, () -> service.changeVisibility(
                new TreeCommandService.ChangeVisibilityCommand(
                        "tenant-1", tree.treeId(), Visibility.PUBLIC, "user-1",
                        "should fail", "corr-4", t1.plusSeconds(60))));
    }

    @Test
    void restoreBringsTreeBackToActive() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        Tree tree = service.createTree(new TreeCommandService.CreateTreeCommand(
                "tenant-1", "smith", "Smith Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, "en-US", "UTC", "GREGORIAN",
                Map.of(), "user-1", "corr-1", t0));
        Instant t1 = t0.plusSeconds(60);
        service.archive(new TreeCommandService.ArchiveCommand(
                "tenant-1", tree.treeId(), "user-1", "housekeeping", "corr-2", t1));
        Tree restored = service.restore(new TreeCommandService.RestoreCommand(
                "tenant-1", tree.treeId(), "user-1", "false alarm", "corr-3", t1.plusSeconds(60)));
        assertEquals(LifecycleState.ACTIVE, restored.lifecycleState());
        assertEquals(1, outbox.countsByEventType()
                .getOrDefault(TreeEventPayloads.EVENT_TREE_RESTORED, 0L));
    }

    @Test
    void transferChangesTenantIdAndEmitsEvent() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        Tree tree = service.createTree(new TreeCommandService.CreateTreeCommand(
                "tenant-1", "smith", "Smith Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, "en-US", "UTC", "GREGORIAN",
                Map.of(), "user-1", "corr-1", t0));
        Instant t1 = t0.plusSeconds(60);
        Tree transferred = service.transfer(new TreeCommandService.TransferCommand(
                "tenant-1", tree.treeId(), "tenant-2", "user-1", "ownership transfer",
                "corr-2", t1));
        assertEquals("tenant-2", transferred.tenantId());
        assertEquals(1, outbox.countsByEventType()
                .getOrDefault(TreeEventPayloads.EVENT_TREE_TRANSFERRED, 0L));
    }

    @Test
    void deleteIsTerminal() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        Tree tree = service.createTree(new TreeCommandService.CreateTreeCommand(
                "tenant-1", "smith", "Smith Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, "en-US", "UTC", "GREGORIAN",
                Map.of(), "user-1", "corr-1", t0));
        Instant t1 = t0.plusSeconds(60);
        Tree deleted = service.delete(new TreeCommandService.DeleteCommand(
                "tenant-1", tree.treeId(), "user-1", "GDPR request", "corr-2", t1));
        assertEquals(LifecycleState.DELETED, deleted.lifecycleState());
        assertEquals(1, outbox.countsByEventType()
                .getOrDefault(TreeEventPayloads.EVENT_TREE_DELETED, 0L));
        // Re-delete is idempotent — same lifecycle, no extra event.
        Tree second = service.delete(new TreeCommandService.DeleteCommand(
                "tenant-1", tree.treeId(), "user-1", "GDPR request", "corr-3", t1.plusSeconds(60)));
        assertEquals(LifecycleState.DELETED, second.lifecycleState());
        assertEquals(1, outbox.countsByEventType()
                .getOrDefault(TreeEventPayloads.EVENT_TREE_DELETED, 0L));
    }

    @Test
    void changeCollaborationModePersistsNewMode() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        Tree tree = service.createTree(new TreeCommandService.CreateTreeCommand(
                "tenant-1", "smith", "Smith Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, "en-US", "UTC", "GREGORIAN",
                Map.of(), "user-1", "corr-1", t0));
        Instant t1 = t0.plusSeconds(60);
        Tree updated = service.changeCollaborationMode(
                new TreeCommandService.ChangeCollaborationModeCommand(
                        "tenant-1", tree.treeId(), CollaborationMode.APPROVAL_REQUIRED,
                        "corr-2", t1));
        assertEquals(CollaborationMode.APPROVAL_REQUIRED, updated.collaborationMode());
        assertNotEquals(tree.version(), updated.version());
    }

    @Test
    void duplicateSlugRejected() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        service.createTree(new TreeCommandService.CreateTreeCommand(
                "tenant-1", "smith", "Smith Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, "en-US", "UTC", "GREGORIAN",
                Map.of(), "user-1", "corr-1", t0));
        assertThrows(IllegalStateException.class, () -> service.createTree(
                new TreeCommandService.CreateTreeCommand(
                        "tenant-1", "smith", "Smith Family 2", Visibility.PRIVATE,
                        CollaborationMode.DIRECT_EDIT, "en-US", "UTC", "GREGORIAN",
                        Map.of(), "user-2", "corr-2", t0)));
    }

    @Test
    void quotaEnforced() {
        TreeCommandService tiny = new TreeCommandService(repo, shim(), 1);
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        tiny.createTree(new TreeCommandService.CreateTreeCommand(
                "tenant-1", "smith", "Smith Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, "en-US", "UTC", "GREGORIAN",
                Map.of(), "user-1", "corr-1", t0));
        assertThrows(IllegalStateException.class, () -> tiny.createTree(
                new TreeCommandService.CreateTreeCommand(
                        "tenant-1", "jones", "Jones Family", Visibility.PRIVATE,
                        CollaborationMode.DIRECT_EDIT, "en-US", "UTC", "GREGORIAN",
                        Map.of(), "user-1", "corr-2", t0)));
        assertFalse(repo.listByTenant("tenant-1", 10, 0).isEmpty());
        assertTrue(repo.countByTenant("tenant-1") <= 1);
    }

    @Test
    void rejectsArchivedTreeMutation() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        Tree tree = service.createTree(new TreeCommandService.CreateTreeCommand(
                "tenant-1", "smith", "Smith Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, "en-US", "UTC", "GREGORIAN",
                Map.of(), "user-1", "corr-1", t0));
        Instant t1 = t0.plusSeconds(60);
        service.archive(new TreeCommandService.ArchiveCommand(
                "tenant-1", tree.treeId(), "user-1", "housekeeping", "corr-2", t1));
        assertThrows(IllegalStateException.class, () -> service.updateMetadata(
                new TreeCommandService.UpdateMetadataCommand(
                        "tenant-1", tree.treeId(), "Renamed", "en-US", "UTC",
                        "GREGORIAN", Map.of(), "corr-3", t1.plusSeconds(60))));
    }
}
