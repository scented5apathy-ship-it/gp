package com.genealogy.platform.services.genealogy.command;

import com.genealogy.platform.services.genealogy.domain.CollaborationMode;
import com.genealogy.platform.services.genealogy.domain.Tree;
import com.genealogy.platform.services.genealogy.domain.Visibility;
import com.genealogy.platform.services.genealogy.outbox.JdbcTreeOutboxWriter;
import com.genealogy.platform.services.genealogy.outbox.TreeEventPayloads;
import com.genealogy.platform.services.genealogy.persistence.TreeRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Command service for the tree aggregate. Mirrors
 * {@code tasks.md} E4.1:
 *
 * <ul>
 *   <li>Create / read / update / archive / restore / transfer / delete.
 *   <li>Locale / timezone / calendar / branding / collaboration policy.
 *   <li>Visibility transitions: PRIVATE / UNLISTED / PUBLIC. Emits
 *       {@code gp.genealogy.v1.TreeVisibilityChanged}.
 *   <li>Archive / restore / transfer / delete emit the
 *       corresponding events so search + public projections can
 *       rebuild / drop the tree (per {@code design.md} §5.4).
 * </ul>
 *
 * <p>The service is framework-free at the public surface; the
 * gRPC / REST controllers (out of scope for E4.1) wrap it and
 * pass the trusted tenant context from the gRPC metadata per
 * {@code design.md} §6.1.
 */
public final class TreeCommandService {

    private final TreeRepository repository;
    private final JdbcTreeOutboxWriter outbox;
    private final long maxTreesPerTenant;

    public TreeCommandService(TreeRepository repository,
                              JdbcTreeOutboxWriter outbox,
                              long maxTreesPerTenant) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.maxTreesPerTenant = maxTreesPerTenant;
    }

    public Tree createTree(CreateTreeCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        if (repository.findBySlug(cmd.tenantId(), cmd.slug()).isPresent()) {
            throw new IllegalStateException("slug already in use: " + cmd.slug());
        }
        if (repository.countByTenant(cmd.tenantId()) >= maxTreesPerTenant) {
            throw new IllegalStateException(
                    "max trees per tenant reached: " + maxTreesPerTenant);
        }
        String slug = Tree.normaliseSlug(cmd.slug());
        Instant now = cmd.now();
        Tree tree = new Tree(
                UUID.randomUUID().toString(),
                cmd.tenantId(),
                slug,
                cmd.displayName(),
                cmd.visibility(),
                cmd.collaborationMode(),
                com.genealogy.platform.services.genealogy.domain.LifecycleState.ACTIVE,
                cmd.defaultLocale(),
                cmd.defaultTimezone(),
                cmd.defaultCalendar(),
                cmd.branding() == null ? Map.of() : cmd.branding(),
                cmd.ownerId(),
                1L,
                now,
                now);
        repository.insert(tree);
        outbox.enqueue(
                tree.treeId(),
                tree.tenantId(),
                TreeEventPayloads.EVENT_TREE_CREATED,
                TreeEventPayloads.TreeCreatedEvent.fromTree(tree),
                now,
                cmd.correlationId());
        return tree;
    }

    public Tree updateMetadata(UpdateMetadataCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Tree tree = requireActive(cmd.tenantId(), cmd.treeId());
        Tree updated = tree.withMetadata(
                cmd.displayName(),
                cmd.defaultLocale(),
                cmd.defaultTimezone(),
                cmd.defaultCalendar(),
                cmd.branding() == null ? tree.branding() : cmd.branding(),
                cmd.now());
        repository.update(updated);
        return updated;
    }

    public Tree changeVisibility(ChangeVisibilityCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Tree tree = requireActive(cmd.tenantId(), cmd.treeId());
        Visibility from = tree.visibility();
        Visibility to = cmd.to();
        Tree updated = tree.withVisibility(to, cmd.now());
        repository.update(updated);
        outbox.enqueue(
                updated.treeId(),
                updated.tenantId(),
                TreeEventPayloads.EVENT_TREE_VISIBILITY_CHANGED,
                new TreeEventPayloads.TreeVisibilityChangedEvent(
                        updated.treeId(),
                        from.wire(),
                        to.wire(),
                        cmd.actorId(),
                        cmd.reason(),
                        cmd.now()),
                cmd.now(),
                cmd.correlationId());
        return updated;
    }

    public Tree changeCollaborationMode(ChangeCollaborationModeCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Tree tree = requireActive(cmd.tenantId(), cmd.treeId());
        CollaborationMode from = tree.collaborationMode();
        CollaborationMode to = cmd.to();
        Tree updated = tree.withCollaborationMode(to, cmd.now());
        repository.update(updated);
        return updated;
    }

    public Tree archive(ArchiveCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Tree tree = requireActive(cmd.tenantId(), cmd.treeId());
        Tree updated = tree.archived(cmd.now());
        repository.update(updated);
        outbox.enqueue(
                updated.treeId(),
                updated.tenantId(),
                TreeEventPayloads.EVENT_TREE_ARCHIVED,
                new TreeEventPayloads.TreeArchivedEvent(
                        updated.treeId(),
                        cmd.actorId(),
                        cmd.reason(),
                        cmd.now()),
                cmd.now(),
                cmd.correlationId());
        return updated;
    }

    public Tree restore(RestoreCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Tree tree = repository.findById(cmd.tenantId(), cmd.treeId())
                .orElseThrow(() -> new IllegalStateException("tree not found: " + cmd.treeId()));
        if (tree.lifecycleState() != com.genealogy.platform.services.genealogy.domain.LifecycleState.ARCHIVED) {
            throw new IllegalStateException(
                    "tree is not ARCHIVED, current state: " + tree.lifecycleState());
        }
        Tree updated = tree.restored(cmd.now());
        repository.update(updated);
        outbox.enqueue(
                updated.treeId(),
                updated.tenantId(),
                TreeEventPayloads.EVENT_TREE_RESTORED,
                new TreeEventPayloads.TreeRestoredEvent(
                        updated.treeId(),
                        cmd.actorId(),
                        cmd.reason(),
                        cmd.now()),
                cmd.now(),
                cmd.correlationId());
        return updated;
    }

    public Tree transfer(TransferCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Tree tree = requireActive(cmd.tenantId(), cmd.treeId());
        Tree updated = tree.transferredTo(cmd.toTenantId(), cmd.now());
        repository.updateTenant(updated, tree.tenantId());
        outbox.enqueue(
                updated.treeId(),
                tree.tenantId(),
                TreeEventPayloads.EVENT_TREE_TRANSFERRED,
                new TreeEventPayloads.TreeTransferredEvent(
                        updated.treeId(),
                        tree.tenantId(),
                        cmd.toTenantId(),
                        cmd.actorId(),
                        cmd.reason(),
                        cmd.now()),
                cmd.now(),
                cmd.correlationId());
        return updated;
    }

    public Tree delete(DeleteCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Tree tree = repository.findById(cmd.tenantId(), cmd.treeId())
                .orElseThrow(() -> new IllegalStateException("tree not found: " + cmd.treeId()));
        if (tree.lifecycleState() == com.genealogy.platform.services.genealogy.domain.LifecycleState.DELETED) {
            return tree;
        }
        Tree updated = tree.deleted(cmd.now());
        repository.update(updated);
        outbox.enqueue(
                updated.treeId(),
                updated.tenantId(),
                TreeEventPayloads.EVENT_TREE_DELETED,
                new TreeEventPayloads.TreeDeletedEvent(
                        updated.treeId(),
                        updated.tenantId(),
                        cmd.actorId(),
                        cmd.reason(),
                        cmd.now()),
                cmd.now(),
                cmd.correlationId());
        return updated;
    }

    private Tree requireActive(String tenantId, String treeId) {
        Tree tree = repository.findById(tenantId, treeId)
                .orElseThrow(() -> new IllegalStateException("tree not found: " + treeId));
        if (tree.lifecycleState() != com.genealogy.platform.services.genealogy.domain.LifecycleState.ACTIVE) {
            throw new IllegalStateException(
                    "tree is not ACTIVE, current state: " + tree.lifecycleState());
        }
        return tree;
    }

    public record CreateTreeCommand(
            String tenantId,
            String slug,
            String displayName,
            Visibility visibility,
            CollaborationMode collaborationMode,
            String defaultLocale,
            String defaultTimezone,
            String defaultCalendar,
            Map<String, String> branding,
            String ownerId,
            String correlationId,
            Instant now) {
    }

    public record UpdateMetadataCommand(
            String tenantId,
            String treeId,
            String displayName,
            String defaultLocale,
            String defaultTimezone,
            String defaultCalendar,
            Map<String, String> branding,
            String correlationId,
            Instant now) {
    }

    public record ChangeVisibilityCommand(
            String tenantId,
            String treeId,
            Visibility to,
            String actorId,
            String reason,
            String correlationId,
            Instant now) {
    }

    public record ChangeCollaborationModeCommand(
            String tenantId,
            String treeId,
            CollaborationMode to,
            String correlationId,
            Instant now) {
    }

    public record ArchiveCommand(
            String tenantId,
            String treeId,
            String actorId,
            String reason,
            String correlationId,
            Instant now) {
    }

    public record RestoreCommand(
            String tenantId,
            String treeId,
            String actorId,
            String reason,
            String correlationId,
            Instant now) {
    }

    public record TransferCommand(
            String tenantId,
            String treeId,
            String toTenantId,
            String actorId,
            String reason,
            String correlationId,
            Instant now) {
    }

    public record DeleteCommand(
            String tenantId,
            String treeId,
            String actorId,
            String reason,
            String correlationId,
            Instant now) {
    }
}
