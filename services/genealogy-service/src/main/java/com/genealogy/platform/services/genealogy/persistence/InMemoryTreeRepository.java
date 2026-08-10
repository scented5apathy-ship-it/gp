package com.genealogy.platform.services.genealogy.persistence;

import com.genealogy.platform.services.genealogy.domain.Tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link TreeRepository} used by unit
 * tests and as a fast-path default when the JDBC driver is
 * unavailable (e.g. local smoke tests). Production code paths
 * always resolve the JDBC-backed implementation.
 */
public final class InMemoryTreeRepository implements TreeRepository {

    private final Map<String, Tree> byId = new ConcurrentHashMap<>();

    @Override
    public void insert(Tree tree) {
        if (byId.putIfAbsent(tree.treeId(), tree) != null) {
            throw new IllegalStateException("duplicate treeId: " + tree.treeId());
        }
        for (Tree existing : byId.values()) {
            if (existing.tenantId().equals(tree.tenantId())
                    && existing.slug().equals(tree.slug())
                    && !existing.treeId().equals(tree.treeId())) {
                byId.remove(tree.treeId());
                throw new IllegalStateException(
                        "duplicate slug within tenant: " + tree.slug());
            }
        }
    }

    @Override
    public void update(Tree tree) {
        Tree existing = byId.get(tree.treeId());
        if (existing == null) {
            throw new IllegalStateException("tree not found: " + tree.treeId());
        }
        if (!existing.tenantId().equals(tree.tenantId())) {
            throw new IllegalStateException(
                    "tenant mismatch on update: " + tree.tenantId());
        }
        if (existing.version() + 1 != tree.version()) {
            throw new IllegalStateException(
                    "stale version, expected " + (existing.version() + 1) + " got " + tree.version());
        }
        byId.put(tree.treeId(), tree);
    }

    @Override
    public void updateTenant(Tree tree, String fromTenantId) {
        Tree existing = byId.get(tree.treeId());
        if (existing == null) {
            throw new IllegalStateException("tree not found: " + tree.treeId());
        }
        if (!existing.tenantId().equals(fromTenantId)) {
            throw new IllegalStateException(
                    "tenant mismatch on transfer, expected from = " + fromTenantId
                            + " but tree is in " + existing.tenantId());
        }
        if (existing.version() + 1 != tree.version()) {
            throw new IllegalStateException(
                    "stale version, expected " + (existing.version() + 1) + " got " + tree.version());
        }
        byId.put(tree.treeId(), tree);
    }

    @Override
    public Optional<Tree> findById(String tenantId, String treeId) {
        Tree tree = byId.get(treeId);
        if (tree != null && tree.tenantId().equals(tenantId)) {
            return Optional.of(tree);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Tree> findBySlug(String tenantId, String slug) {
        for (Tree tree : byId.values()) {
            if (tree.tenantId().equals(tenantId) && tree.slug().equals(slug)) {
                return Optional.of(tree);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Tree> listByTenant(String tenantId, int limit, int offset) {
        List<Tree> all = new ArrayList<>();
        for (Tree tree : byId.values()) {
            if (tree.tenantId().equals(tenantId)) {
                all.add(tree);
            }
        }
        all.sort(Comparator.comparing(Tree::createdAt));
        int end = Math.min(all.size(), offset + limit);
        if (offset >= all.size()) {
            return List.of();
        }
        return new ArrayList<>(all.subList(offset, end));
    }

    @Override
    public void purge(String tenantId, String treeId) {
        Tree existing = byId.get(treeId);
        if (existing == null || !existing.tenantId().equals(tenantId)) {
            return;
        }
        byId.remove(treeId);
    }

    @Override
    public long countByTenant(String tenantId) {
        long count = 0;
        for (Tree tree : byId.values()) {
            if (tree.tenantId().equals(tenantId)) {
                count += 1;
            }
        }
        return count;
    }
}
