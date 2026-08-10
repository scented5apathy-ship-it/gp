package com.genealogy.platform.services.genealogy.persistence;

import com.genealogy.platform.services.genealogy.domain.Tree;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the tree aggregate. Every method that returns
 * a tenant-scoped row enforces the tenant predicate at the
 * application boundary; PostgreSQL Row-Level Security provides a
 * second line of defence per {@code design.md} §5.1.
 */
public interface TreeRepository {

    /** Insert a new tree. Throws on duplicate slug within tenant. */
    void insert(Tree tree);

    /**
     * Update an existing tree (CAS on version). Throws on stale
     * version. Used for ordinary mutations whose
     * {@code tenantId} matches the persisted row.
     */
    void update(Tree tree);

    /**
     * Update an existing tree when the {@code tenantId} changes
     * (i.e. cross-tenant transfer). The previous tenant id is
     * passed as {@code fromTenantId} and the new tenant id as
     * {@code tree.tenantId()}. The CAS still applies on
     * {@code version}.
     */
    void updateTenant(Tree tree, String fromTenantId);

    /** Find by id, scoped to tenant. */
    Optional<Tree> findById(String tenantId, String treeId);

    /** Find by slug, scoped to tenant. */
    Optional<Tree> findBySlug(String tenantId, String slug);

    /** List trees for a tenant (paged). */
    List<Tree> listByTenant(String tenantId, int limit, int offset);

    /** Hard-delete the row (used only after the DELETED terminal state is committed). */
    void purge(String tenantId, String treeId);

    /** Count trees for a tenant. Used for quota enforcement. */
    long countByTenant(String tenantId);
}
