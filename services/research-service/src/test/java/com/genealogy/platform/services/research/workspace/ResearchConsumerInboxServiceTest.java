/*
 * E6.1e — unit tests for the durable consumer inbox service.
 *
 * <p>The service is exercised with an in-memory fake
 * repository; the production path is covered by
 * {@code ResearchJdbcConsumerInboxIT}.
 */
package com.genealogy.platform.services.research.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.platform.services.research.application.rls.ResearchRlsTxInterceptorStub;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResearchConsumerInboxServiceTest {

    @Test
    @DisplayName("First delivery runs the body and records PROCESSED")
    void firstDeliveryProcessed() {
        InMemoryInboxRepository repo = new InMemoryInboxRepository();
        AtomicInteger bodyCalls = new AtomicInteger();
        ResearchConsumerInboxService service = new ResearchConsumerInboxService(
                repo, new ResearchRlsTxInterceptorStub(), Clock.systemUTC());

        ResearchConsumerInboxRow.Outcome outcome = service.apply(
                "tenant-A", "topic-x", "evt-1", "gp.genealogy.v1.TreeVisibilityChanged",
                "{\"x\":1}", "actor-1", "corr-1",
                () -> {
                    bodyCalls.incrementAndGet();
                    return null;
                });

        assertThat(outcome).isEqualTo(ResearchConsumerInboxRow.Outcome.PROCESSED);
        assertThat(bodyCalls.get()).isEqualTo(1);
        assertThat(repo.last().outcome()).isEqualTo(ResearchConsumerInboxRow.Outcome.PROCESSED);
    }

    @Test
    @DisplayName("Second delivery observes the row and skips the body")
    void duplicateSkipped() {
        InMemoryInboxRepository repo = new InMemoryInboxRepository();
        AtomicInteger bodyCalls = new AtomicInteger();
        ResearchConsumerInboxService service = new ResearchConsumerInboxService(
                repo, new ResearchRlsTxInterceptorStub(), Clock.systemUTC());

        service.apply("tenant-A", "topic-x", "evt-1",
                "gp.genealogy.v1.TreeVisibilityChanged",
                "{\"x\":1}", "actor-1", "corr-1",
                () -> { bodyCalls.incrementAndGet(); return null; });
        ResearchConsumerInboxRow.Outcome outcome = service.apply(
                "tenant-A", "topic-x", "evt-1", "gp.genealogy.v1.TreeVisibilityChanged",
                "{\"x\":1}", "actor-1", "corr-1",
                () -> {
                    bodyCalls.incrementAndGet();
                    return null;
                });

        assertThat(outcome).isEqualTo(ResearchConsumerInboxRow.Outcome.SKIPPED_DUPLICATE);
        assertThat(bodyCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Failed body flips the row to FAILED and propagates the exception")
    void failedBodyRecordsOutcome() {
        InMemoryInboxRepository repo = new InMemoryInboxRepository();
        ResearchConsumerInboxService service = new ResearchConsumerInboxService(
                repo, new ResearchRlsTxInterceptorStub(), Clock.systemUTC());

        try {
            service.apply("tenant-A", "topic-x", "evt-2",
                    "gp.genealogy.v1.PersonRedacted",
                    "{\"p\":\"x\"}", "actor-1", "corr-1",
                    () -> { throw new RuntimeException("kaboom"); });
        } catch (RuntimeException expected) {
            assertThat(expected.getMessage()).isEqualTo("kaboom");
        }
        assertThat(repo.last().outcome()).isEqualTo(ResearchConsumerInboxRow.Outcome.FAILED);
        assertThat(repo.last().lastError()).isEqualTo("kaboom");
    }

    @Test
    @DisplayName("SHA-256 hash is stable across runs")
    void sha256IsStable() {
        String first = ResearchConsumerInboxService.sha256Hex("hello");
        String second = ResearchConsumerInboxService.sha256Hex("hello");
        assertThat(first).isEqualTo(second);
        assertThat(first).matches("^[a-f0-9]{64}$");
    }

    private static final class InMemoryInboxRepository implements ResearchConsumerInboxRepository {
        private final Map<String, ResearchConsumerInboxRow> byKey = new HashMap<>();
        private ResearchConsumerInboxRow last;

        @Override
        public boolean tryClaim(ResearchConsumerInboxRow row) {
            String key = key(row.tenantId(), row.sourceTopic(), row.eventId());
            if (byKey.containsKey(key)) {
                return false;
            }
            byKey.put(key, row);
            last = row;
            return true;
        }

        @Override
        public Optional<ResearchConsumerInboxRow> find(
                String tenantId, String sourceTopic, String eventId) {
            return Optional.ofNullable(byKey.get(key(tenantId, sourceTopic, eventId)));
        }

        @Override
        public void finalizeOutcome(ResearchConsumerInboxRow row) {
            byKey.put(key(row.tenantId(), row.sourceTopic(), row.eventId()), row);
            last = row;
        }

        ResearchConsumerInboxRow last() {
            return last;
        }

        private static String key(String tenantId, String topic, String eventId) {
            return tenantId + "|" + topic + "|" + eventId;
        }
    }
}
