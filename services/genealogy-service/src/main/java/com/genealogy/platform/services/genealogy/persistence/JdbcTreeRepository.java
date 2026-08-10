package com.genealogy.platform.services.genealogy.persistence;

import com.genealogy.platform.services.genealogy.domain.Tree;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC implementation of {@link TreeRepository}. Used in production
 * with a per-service PostgreSQL schema (per ADR-E0.5-02) and
 * tenant-scoped RLS (per {@code design.md} §5.1).
 *
 * <p>Optimistic concurrency is enforced via a CAS on {@code version}
 * in {@code update}. The slug uniqueness constraint is enforced at
 * the database level (composite unique index on
 * {@code (tenant_id, slug)}) as a defence-in-depth.
 */
public class JdbcTreeRepository implements TreeRepository {

    private final JdbcTemplate jdbc;

    public JdbcTreeRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void insert(Tree tree) {
        try {
            jdbc.update(
                    "INSERT INTO tree_service.tree ("
                            + " tree_id, tenant_id, slug, display_name, visibility,"
                            + " collaboration_mode, lifecycle_state, default_locale,"
                            + " default_timezone, default_calendar, branding, owner_id,"
                            + " version, created_at, updated_at"
                            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)",
                    tree.treeId(),
                    tree.tenantId(),
                    tree.slug(),
                    tree.displayName(),
                    tree.visibility().wire(),
                    tree.collaborationMode().wire(),
                    tree.lifecycleState().wire(),
                    tree.defaultLocale(),
                    tree.defaultTimezone(),
                    tree.defaultCalendar(),
                    BrandingJson.encode(tree.branding()),
                    tree.ownerId(),
                    tree.version(),
                    java.sql.Timestamp.from(tree.createdAt()),
                    java.sql.Timestamp.from(tree.updatedAt()));
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("duplicate tree or slug: " + tree.slug(), e);
        }
    }

    @Override
    public void update(Tree tree) {
        int rows = jdbc.update(
                "UPDATE tree_service.tree SET"
                        + " display_name = ?, visibility = ?, collaboration_mode = ?,"
                        + " lifecycle_state = ?, default_locale = ?, default_timezone = ?,"
                        + " default_calendar = ?, branding = ?::jsonb, owner_id = ?,"
                        + " version = ?, updated_at = ?"
                        + " WHERE tree_id = ? AND tenant_id = ? AND version = ?",
                tree.displayName(),
                tree.visibility().wire(),
                tree.collaborationMode().wire(),
                tree.lifecycleState().wire(),
                tree.defaultLocale(),
                tree.defaultTimezone(),
                tree.defaultCalendar(),
                BrandingJson.encode(tree.branding()),
                tree.ownerId(),
                tree.version(),
                java.sql.Timestamp.from(tree.updatedAt()),
                tree.treeId(),
                tree.tenantId(),
                tree.version() - 1);
        if (rows == 0) {
            throw new IllegalStateException("stale version for tree " + tree.treeId());
        }
    }

    @Override
    public void updateTenant(Tree tree, String fromTenantId) {
        int rows = jdbc.update(
                "UPDATE tree_service.tree SET"
                        + " tenant_id = ?, display_name = ?, visibility = ?,"
                        + " collaboration_mode = ?, lifecycle_state = ?, default_locale = ?,"
                        + " default_timezone = ?, default_calendar = ?, branding = ?::jsonb,"
                        + " owner_id = ?, version = ?, updated_at = ?"
                        + " WHERE tree_id = ? AND tenant_id = ? AND version = ?",
                tree.tenantId(),
                tree.displayName(),
                tree.visibility().wire(),
                tree.collaborationMode().wire(),
                tree.lifecycleState().wire(),
                tree.defaultLocale(),
                tree.defaultTimezone(),
                tree.defaultCalendar(),
                BrandingJson.encode(tree.branding()),
                tree.ownerId(),
                tree.version(),
                java.sql.Timestamp.from(tree.updatedAt()),
                tree.treeId(),
                fromTenantId,
                tree.version() - 1);
        if (rows == 0) {
            throw new IllegalStateException(
                    "stale version for tree transfer: " + tree.treeId());
        }
    }

    @Override
    public Optional<Tree> findById(String tenantId, String treeId) {
        List<Tree> rows = jdbc.query(
                "SELECT * FROM tree_service.tree WHERE tree_id = ? AND tenant_id = ?",
                ROW_MAPPER,
                treeId,
                tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<Tree> findBySlug(String tenantId, String slug) {
        List<Tree> rows = jdbc.query(
                "SELECT * FROM tree_service.tree WHERE tenant_id = ? AND slug = ?",
                ROW_MAPPER,
                tenantId,
                slug);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Tree> listByTenant(String tenantId, int limit, int offset) {
        return jdbc.query(
                "SELECT * FROM tree_service.tree WHERE tenant_id = ?"
                        + " ORDER BY created_at ASC LIMIT ? OFFSET ?",
                ROW_MAPPER,
                tenantId,
                limit,
                offset);
    }

    @Override
    public void purge(String tenantId, String treeId) {
        jdbc.update(
                "DELETE FROM tree_service.tree WHERE tree_id = ? AND tenant_id = ? AND lifecycle_state = 'DELETED'",
                treeId,
                tenantId);
    }

    @Override
    public long countByTenant(String tenantId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tree_service.tree WHERE tenant_id = ?",
                Long.class,
                tenantId);
        return count == null ? 0 : count;
    }

    private static final RowMapper<Tree> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

    static Tree mapRow(ResultSet rs) throws SQLException {
        try (PreparedStatement ignored = null) {
            // placeholder to keep imports
        }
        String visibility = rs.getString("visibility");
        String collaborationMode = rs.getString("collaboration_mode");
        String lifecycleState = rs.getString("lifecycle_state");
        return new Tree(
                rs.getString("tree_id"),
                rs.getString("tenant_id"),
                rs.getString("slug"),
                rs.getString("display_name"),
                com.genealogy.platform.services.genealogy.domain.Visibility.fromWire(visibility),
                com.genealogy.platform.services.genealogy.domain.CollaborationMode.fromWire(collaborationMode),
                com.genealogy.platform.services.genealogy.domain.LifecycleState.fromWire(lifecycleState),
                rs.getString("default_locale"),
                rs.getString("default_timezone"),
                rs.getString("default_calendar"),
                BrandingJson.decode(rs.getString("branding")),
                rs.getString("owner_id"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
