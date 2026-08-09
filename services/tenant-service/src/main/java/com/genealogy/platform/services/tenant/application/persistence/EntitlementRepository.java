package com.genealogy.platform.services.tenant.application.persistence;

import com.genealogy.platform.services.tenant.domain.entitlement.Entitlement;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.tenant.TenantPlan;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcTemplate repository for the {@code entitlements} entity table.
 * The entitlement is an entity (single row per tenant) not an
 * aggregate root, so the write methods take the row state by
 * argument instead of mutator calls.
 *
 * <p>{@code Propagation.MANDATORY} + RLS guarantee the same
 * transaction-bound safety as the other repositories.
 */
public class EntitlementRepository {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public EntitlementRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Entitlement entitlement) {
        jdbc.update(
                "INSERT INTO tenant_service.entitlements "
                        + "(tenant_id, plan, member_limit, tree_limit, storage_limit_mb, "
                        + "retention_days, billing_external_id, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                entitlement.tenantId().getValue(),
                entitlement.plan().name(),
                entitlement.memberLimit(),
                entitlement.treeLimit(),
                entitlement.storageLimitMb(),
                entitlement.retentionDays(),
                entitlement.billingExternalId(),
                Timestamp.from(entitlement.updatedAt()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Entitlement entitlement) {
        jdbc.update(
                "UPDATE tenant_service.entitlements SET "
                        + "plan = ?, member_limit = ?, tree_limit = ?, "
                        + "storage_limit_mb = ?, retention_days = ?, "
                        + "billing_external_id = ?, updated_at = ? "
                        + "WHERE tenant_id = ?",
                entitlement.plan().name(),
                entitlement.memberLimit(),
                entitlement.treeLimit(),
                entitlement.storageLimitMb(),
                entitlement.retentionDays(),
                entitlement.billingExternalId(),
                Timestamp.from(entitlement.updatedAt()),
                entitlement.tenantId().getValue());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Entitlement> findByTenantId(TenantId tenantId) {
        try {
            Entitlement e = jdbc.queryForObject(
                    "SELECT tenant_id, plan, member_limit, tree_limit, storage_limit_mb, "
                            + "retention_days, billing_external_id, updated_at "
                            + "FROM tenant_service.entitlements WHERE tenant_id = ?",
                    (rs, rowNum) -> rehydrate(rs),
                    tenantId.getValue());
            return Optional.ofNullable(e);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private static Entitlement rehydrate(ResultSet rs) throws SQLException {
        TenantId id = new TenantId(rs.getString("tenant_id"));
        TenantPlan plan = TenantPlan.valueOf(rs.getString("plan"));
        int memberLimit = rs.getInt("member_limit");
        int treeLimit = rs.getInt("tree_limit");
        int storageLimitMb = rs.getInt("storage_limit_mb");
        int retentionDays = rs.getInt("retention_days");
        String billingId = rs.getString("billing_external_id");
        java.time.Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        return new Entitlement(id, plan, memberLimit, treeLimit, storageLimitMb,
                retentionDays, billingId, updatedAt);
    }
}
