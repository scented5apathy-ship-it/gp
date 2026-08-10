package com.genealogy.platform.services.audit.retention;

import com.genealogy.platform.services.audit.domain.HashChainComputer;
import com.genealogy.platform.services.audit.persistence.AuditEntryRepository;
import com.genealogy.platform.services.audit.persistence.JdbcAuditEntryRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retention sweeper per <code>contracts/audit/retention.yaml</code>.
 *
 * <p>The sweeper does NOT mutate {@code audit_entry} (append-only is
 * enforced by the V1 trigger). It only:
 * <ul>
 *   <li>Counts how many entries are eligible for sweep (older than
 *       the per-class hot tier).
 *   <li>Writes a deletion-evidence row per batch so DPOs can audit
 *       the lifecycle.
 *   <li>Emits a structured log entry per run for observability.
 * </ul>
 *
 * <p>Actual hard-delete of the aged-out entries is gated by the
 * legal hold check ({@code requireLegalHoldCheck: true} in the
 * contract). This MVP writes the evidence only; a future epic wires
 * the actual storage-tier transition (hot Kafka topic → warm S3 →
 * cold S3) per the <code>defaultTiers</code> in the contract.
 */
public class RetentionSweeper {

    private final AuditEntryRepository repository;
    private final NamedParameterJdbcTemplate jdbc;
    private final RetentionPolicy policy;
    private final LegalHoldProbe legalHoldProbe;

    public RetentionSweeper(
            AuditEntryRepository repository,
            DataSource dataSource,
            RetentionPolicy policy,
            LegalHoldProbe legalHoldProbe) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
        this.policy = Objects.requireNonNull(policy, "policy");
        this.legalHoldProbe = Objects.requireNonNull(legalHoldProbe, "legalHoldProbe");
    }

    public SweepReport sweep(Instant now, String performedBy) {
        SweepReport report = new SweepReport(now, performedBy);
        String runId = "sweep-" + now.toEpochMilli();
        for (Map.Entry<String, Duration> entry : policy.hotDaysByClass().entrySet()) {
            String auditClass = entry.getKey();
            Duration hot = entry.getValue();
            Instant cutoff = now.minus(hot);
            long count = repository.countOlderThan("*", auditClass, cutoff);
            // NOTE: "*" sentinel tells the repo to ignore tenant
            // for the count query; the SQL implementation rewrites
            // it. To stay framework-free here we sum per tenant at
            // the caller level. This MVP only emits the evidence;
            // see future epic for the multi-tenant walk.
            report.addClass(auditClass, count, cutoff, runId);
            if (count > 0) {
                recordEvidence(runId, "*", auditClass, count, cutoff, now, performedBy);
            }
        }
        return report;
    }

    @Transactional
    void recordEvidence(
            String runId,
            String tenantId,
            String auditClass,
            long count,
            Instant earliestOccurredAt,
            Instant sweptAt,
            String performedBy) {
        boolean legalHold = legalHoldProbe.isActive(tenantId);
        if (legalHold && policy.legalHold() == LegalHoldMode.HARD_BLOCK) {
            // Emit a marker evidence row so DPOs see that a sweep
            // tried to delete and was blocked. The marker is still
            // append-only.
            jdbc.update(
                    "INSERT INTO audit_service.deletion_evidence "
                            + "(sweep_run_id, tenant_id, audit_class, swept_count, earliest_occurred_at, "
                            + " latest_occurred_at, swept_at, reason_code, integrity_hash, "
                            + " legal_hold_override, performed_by, notes) "
                            + "VALUES (:runId, :tenantId, :auditClass, 0, :earliestOccurredAt, "
                            + " :earliestOccurredAt, :sweptAt, :reasonCode, :integrityHash, "
                            + " FALSE, :performedBy, :notes)",
                    new MapSqlParameterSource()
                            .addValue("runId", runId)
                            .addValue("tenantId", tenantId)
                            .addValue("auditClass", auditClass)
                            .addValue("earliestOccurredAt", Timestamp.from(earliestOccurredAt))
                            .addValue("sweptAt", Timestamp.from(sweptAt))
                            .addValue("reasonCode", "LEGAL_HOLD_BLOCK")
                            .addValue("integrityHash", HashChainComputer.GENESIS_HASH)
                            .addValue("performedBy", performedBy)
                            .addValue("notes", "sweep blocked by legal hold (HARD_BLOCK)"));
            return;
        }
        jdbc.update(
                "INSERT INTO audit_service.deletion_evidence "
                        + "(sweep_run_id, tenant_id, audit_class, swept_count, earliest_occurred_at, "
                        + " latest_occurred_at, swept_at, reason_code, integrity_hash, "
                        + " legal_hold_override, performed_by, notes) "
                        + "VALUES (:runId, :tenantId, :auditClass, :count, :earliestOccurredAt, "
                        + " :earliestOccurredAt, :sweptAt, :reasonCode, :integrityHash, "
                        + " :legalHoldOverride, :performedBy, :notes)",
                new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("tenantId", tenantId)
                        .addValue("auditClass", auditClass)
                        .addValue("count", count)
                        .addValue("earliestOccurredAt", Timestamp.from(earliestOccurredAt))
                        .addValue("sweptAt", Timestamp.from(sweptAt))
                        .addValue("reasonCode", "RETENTION_SWEEP")
                        .addValue("integrityHash", HashChainComputer.GENESIS_HASH)
                        .addValue("legalHoldOverride", legalHold)
                        .addValue("performedBy", performedBy)
                        .addValue("notes", "MVP evidence only; storage-tier transition lands in a follow-up epic."));
    }

    public static final class SweepReport {
        private final Instant sweptAt;
        private final String performedBy;
        private final Map<String, ClassSweep> classes = new LinkedHashMap<>();

        public SweepReport(Instant sweptAt, String performedBy) {
            this.sweptAt = sweptAt;
            this.performedBy = performedBy;
        }

        public void addClass(String auditClass, long count, Instant cutoff, String runId) {
            classes.put(auditClass, new ClassSweep(auditClass, count, cutoff, runId));
        }

        public Instant sweptAt() {
            return sweptAt;
        }

        public String performedBy() {
            return performedBy;
        }

        public Map<String, ClassSweep> classes() {
            return classes;
        }

        public long totalSwept() {
            return classes.values().stream().mapToLong(ClassSweep::count).sum();
        }

        public record ClassSweep(String auditClass, long count, Instant cutoff, String runId) {
        }
    }

    /**
     * Pluggable probe so production can read legal hold state from
     * the platform's legal-hold service (out of scope for E3.6)
     * and tests can supply a deterministic answer.
     */
    @FunctionalInterface
    public interface LegalHoldProbe {
        boolean isActive(String tenantId);
    }

    public enum LegalHoldMode {
        HARD_BLOCK,
        SOFT
    }

    public record RetentionPolicy(Map<String, Duration> hotDaysByClass, LegalHoldMode legalHold) {
    }
}
