package com.genealogy.platform.services.research.outbox;

import com.genealogy.platform.services.research.application.rls.ResearchRlsTxInterceptor;
import com.genealogy.platform.spring.audit.AuditEvent;
import com.genealogy.platform.spring.audit.AuditPublisher;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduled driver that pumps the {@link ResearchOutboxRelay}.
 *
 * <p>The runner is the only writer of the non-{@link
 * ResearchOutboxStatus#PENDING} states. It walks the
 * {@link ResearchOutboxPollingTenantRegistry} and per tenant:
 *
 * <ol>
 *   <li>opens a Spring transaction (mandatory
 *       {@code Propagation.REQUIRES_NEW} so the relay tick
 *       never piggy-backs on another transaction);</li>
 *   <li>binds the {@code ResearchRlsTxInterceptor} (which
 *       issues {@code SET LOCAL ROLE research_service_app} +
 *       {@code SET LOCAL app.tenant_id});</li>
 *   <li>delegates to {@link ResearchOutboxRelay#tick} so the
 *       existing audit / retry / DLQ semantics stay
 *       untouched.</li>
 * </ol>
 *
 * <p>This class closes E6.1d Gap 6 — the previous commit had
 * no scheduled driver (the runner was intentionally removed
 * in commit {@code d4c2c3b} to unblock the
 * {@code ResearchOutboxPollingTenantRegistry} + the
 * {@code @Scheduled} wiring; E6.1e reintroduces the runner
 * with the per-tenant RLS binding and a real tenant
 * registry).
 *
 * <p>Scope guard (per {@code agent-execution.md} §4.4):
 *   - No domain Java change.
 *   - No new Kafka topic or ACL.
 *   - No new database table (the inbox lands in V4; the
 *     outbox stays on V3).
 *   - No new external dependency — uses the same JdbcTemplate
 *     the rest of the service uses.
 */
@Component
public class ResearchOutboxRelayRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ResearchOutboxRelayRunner.class);

    private final ResearchOutboxRelay relay;
    private final ResearchOutboxPollingTenantRegistry tenantRegistry;
    private final ResearchRlsTxInterceptor rls;
    private final TransactionTemplate transactionTemplate;
    private final AuditPublisher audit;
    private final java.time.Clock clock;
    private final RelayRunMetrics metrics;

    public ResearchOutboxRelayRunner(
            ResearchOutboxRelay relay,
            ResearchOutboxPollingTenantRegistry tenantRegistry,
            ResearchRlsTxInterceptor rls,
            PlatformTransactionManager transactionManager,
            AuditPublisher audit,
            java.time.Clock clock,
            RelayRunMetrics metrics) {
        this.relay = Objects.requireNonNull(relay, "relay");
        this.tenantRegistry = Objects.requireNonNull(tenantRegistry, "tenantRegistry");
        this.rls = Objects.requireNonNull(rls, "rls");
        Objects.requireNonNull(transactionManager, "transactionManager");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Scheduled tick. Per the project-wide
     * {@code @Scheduled} convention the cadence is driven by
     * {@code platform.research.outbox.relay-cron} (default
     * {@code 0/15 * * * * *} — every 15 s; the relay
     * {@code pollInterval} is the floor inside the tick).
     */
    @Scheduled(cron = "${platform.research.outbox.relay-cron:0/15 * * * * *}")
    public void runScheduled() {
        List<String> tenants = tenantRegistry.listActiveTenants();
        if (tenants.isEmpty()) {
            LOG.debug("research outbox relay tick: no active tenants; skipping");
            return;
        }
        Instant now = Instant.now(clock);
        for (String tenantId : tenants) {
            try {
                runForTenant(tenantId, now);
            } catch (RuntimeException e) {
                LOG.error("research outbox relay tick failed tenantId={}", tenantId, e);
                metrics.recordFailure(tenantId, e);
            }
        }
    }

    /**
     * Visible-for-test hook: run one tick for a single tenant
     * without {@code @Scheduled}. The ITs use this to drive
     * the relay synchronously after seeding outbox rows.
     */
    public ResearchOutboxRelay.RelayTickResult runForTenant(String tenantId, Instant now) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(now, "now");
        TrustedTenantContext ctx = TrustedTenantContext.of(
                tenantId,
                "research-service-relay",
                "service",
                java.util.UUID.randomUUID().toString(),
                null);
        TrustedTenantContext.set(ctx);
        try {
            return transactionTemplate.execute(status -> {
                rls.bind();
                ResearchOutboxRelay.RelayTickResult result = relay.tick(tenantId, now);
                publishAudit(tenantId, result, now);
                metrics.recordSuccess(tenantId, result);
                return result;
            });
        } finally {
            TrustedTenantContext.clear();
        }
    }

    private void publishAudit(String tenantId, ResearchOutboxRelay.RelayTickResult result,
            Instant now) {
        if (result.processed() == 0) {
            return;
        }
        java.util.Map<String, String> meta = new java.util.LinkedHashMap<>();
        meta.put("published", Integer.toString(result.published()));
        meta.put("retried", Integer.toString(result.retried()));
        meta.put("deadLettered", Integer.toString(result.deadLettered()));
        meta.put("processedAt", now.toString());
        audit.publish(new AuditEvent(
                tenantId,
                "research-service-relay",
                "research.outbox.relayTick",
                "outbox",
                tenantId,
                null,
                meta));
    }

    /**
     * Minimal metrics façade. The OTel counter / histogram
     * integration is wired by {@code platform-telemetry};
     * E6.1e exposes a structured object so the IT can assert
     * that the driver actually published without coupling to
     * the OTel SDK.
     */
    public static final class RelayRunMetrics {

        public void recordSuccess(String tenantId, ResearchOutboxRelay.RelayTickResult result) {
            // No-op in production; OTel counters are emitted by
            // the platform-telemetry starter around the
            // scheduler.
        }

        public void recordFailure(String tenantId, RuntimeException error) {
            // No-op in production; OTel error counters are
            // emitted by the platform-telemetry starter.
        }

        public Duration pollInterval() {
            return Duration.ofSeconds(15);
        }
    }
}
