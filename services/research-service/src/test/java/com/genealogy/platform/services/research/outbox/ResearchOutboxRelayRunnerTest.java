/*
 * E6.1e — unit tests for the scheduled relay runner.
 *
 * <p>The runner is exercised with the framework-free
 * {@code InMemoryOutboxRepository} so the test path does not
 * need Testcontainers; the production path is covered by
 * {@code ResearchJdbcOutboxRepositoryIT}.
 */
package com.genealogy.platform.services.research.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.platform.services.research.application.rls.ResearchRlsTxInterceptorStub;
import com.genealogy.platform.services.research.outbox.ResearchOutboxRelay.InMemoryOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ResearchOutboxRelayRunnerTest {

    @Test
    @DisplayName("runForTenant claims PENDING rows and routes them through the producer")
    void runForTenantHappyPath() {
        String tenantId = "tenant-" + UUID.randomUUID();
        InMemoryOutboxRepository repo = new InMemoryOutboxRepository();
        ResearchOutboxEventRecord row = newPending(tenantId, "agg-1");
        repo.add(row);
        ResearchKafkaProducerPort producer = new FakeProducer(true, false, "");
        ResearchOutboxRelay relay = new ResearchOutboxRelay(
                repo, producer, new ResearchPayloadForbiddenFieldScan(),
                new NoopAuditHook(), 10, Duration.ofMillis(500),
                Duration.ofMillis(1000), 5);
        PlatformTransactionManager tx = new NoopTxManager();
        ResearchOutboxRelayRunner runner = new ResearchOutboxRelayRunner(
                relay, new ResearchOutboxPollingTenantRegistry(),
                new ResearchRlsTxInterceptorStub(), tx, new NoopAuditPublisher(),
                Clock.systemUTC(),
                new ResearchOutboxRelayRunner.RelayRunMetrics());

        ResearchOutboxRelay.RelayTickResult result = runner.runForTenant(tenantId, Instant.now());
        assertThat(result.published()).isEqualTo(1);
        assertThat(repo.snapshot().get(row.eventId()).status())
                .isEqualTo(ResearchOutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("runScheduled iterates every active tenant")
    void runScheduledIterates() {
        InMemoryOutboxRepository repo = new InMemoryOutboxRepository();
        ResearchOutboxEventRecord rowA = newPending("tenant-a", "agg-a");
        ResearchOutboxEventRecord rowB = newPending("tenant-b", "agg-b");
        repo.add(rowA);
        repo.add(rowB);
        ResearchKafkaProducerPort producer = new FakeProducer(true, false, "");
        ResearchOutboxRelay relay = new ResearchOutboxRelay(
                repo, producer, new ResearchPayloadForbiddenFieldScan(),
                new NoopAuditHook(), 10, Duration.ofMillis(500),
                Duration.ofMillis(1000), 5);
        ResearchOutboxPollingTenantRegistry registry = new ResearchOutboxPollingTenantRegistry();
        registry.setTenants(List.of("tenant-a", "tenant-b"));
        PlatformTransactionManager tx = new NoopTxManager();
        ResearchOutboxRelayRunner runner = new ResearchOutboxRelayRunner(
                relay, registry, new ResearchRlsTxInterceptorStub(),
                tx, new NoopAuditPublisher(), Clock.systemUTC(),
                new ResearchOutboxRelayRunner.RelayRunMetrics());

        runner.runScheduled();
        assertThat(repo.snapshot().get(rowA.eventId()).status())
                .isEqualTo(ResearchOutboxStatus.PUBLISHED);
        assertThat(repo.snapshot().get(rowB.eventId()).status())
                .isEqualTo(ResearchOutboxStatus.PUBLISHED);
    }

    private static ResearchOutboxEventRecord newPending(String tenantId, String aggregateId) {
        String eventId = "evt-" + UUID.randomUUID();
        String payload = "{\"x\":1}";
        return new ResearchOutboxEventRecord(
                eventId,
                tenantId,
                aggregateId,
                "gp.research.v1.CitationCreated",
                "research/v1/citation-created.avsc",
                payload,
                payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                Instant.now(),
                "corr-" + UUID.randomUUID(),
                "trace-" + UUID.randomUUID(),
                tenantId + "|" + aggregateId,
                ResearchPartitionKeyClass.TENANT_PLUS_AGGREGATE,
                ResearchOutboxStatus.PENDING,
                0,
                null, null, null, null, null,
                null, null, null);
    }

    private static final class FakeProducer implements ResearchKafkaProducerPort {
        private final Result result;

        FakeProducer(boolean published, boolean permanent, String error) {
            Result.Outcome outcome;
            if (published) {
                outcome = Result.Outcome.PUBLISHED;
            } else if (permanent) {
                outcome = Result.Outcome.PERMANENT_FAILURE;
            } else {
                outcome = Result.Outcome.TRANSIENT_FAILURE;
            }
            this.result = new Result(outcome, error);
        }

        @Override
        public Result publish(ResearchOutboxEventRecord row, Instant now) {
            return result;
        }
    }

    private static final class NoopAuditHook implements ResearchOutboxRelay.ResearchOutboxAuditHook {
        @Override
        public void onPublished(ResearchOutboxEventRecord row) {
        }

        @Override
        public void onRetried(ResearchOutboxEventRecord row) {
        }

        @Override
        public void onDeadLettered(ResearchOutboxEventRecord row) {
        }
    }

    private static final class NoopAuditPublisher
            implements com.genealogy.platform.spring.audit.AuditPublisher {
        @Override
        public void publish(com.genealogy.platform.spring.audit.AuditEvent event) {
        }
    }

    private static final class NoopTxManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition def) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}

