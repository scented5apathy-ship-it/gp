package com.genealogy.platform.services.research.outbox;

import com.genealogy.platform.services.research.events.ResearchEventPayloads;
import com.genealogy.platform.services.research.outbox.ResearchOutboxRelay.ResearchOutboxAuditHook;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the research outbox relay.
 *
 * <p>E6.1d ships the framework-free relay class
 * ({@link ResearchOutboxRelay}) + the audit hook adapter
 * ({@link ResearchOutboxAuditHookAdapter}) + the Kafka
 * producer port ({@link ResearchSpringKafkaProducerAdapter});
 * the production driver of the relay is intentionally
 * <em>not</em> shipped in this Epic.
 *
 * <p>Per ADR-E0.5-08 + the operator runbook the relay MUST
 * run as a dedicated deployment of the same JAR with the
 * {@code research-service.outbox.relay.enabled} flag set to
 * {@code true}; the E6.1d commit ships the framework-free
 * relay, the JDBC writer, the producer adapter and the Avro
 * schemas so the E6.1e deployment can wire the driver without
 * a code change. The integration tests call
 * {@link ResearchOutboxRelay#tick(String, java.time.Instant)}
 * directly so the unit-test path stays in scope.
 *
 * <p>Residual gaps (see {@code evidence/E6.1d.md}):
 *   - Production Kafka topic wiring to canonical
 *     {@code genealogy.research.v1.v1} (per {@code platform/kafka/topics.yaml})
 *     plus the matching research-service-producer/
 *     research-service-consumer ACLs.
 *   - Real JDBC {@link ResearchOutboxRepository} implementation
 *     (E6.1d ships the in-memory fake for unit tests; the
 *     JdbcTemplate impl lands in E6.1e alongside the relay driver).
 *   - Production tenant directory ({@link ResearchOutboxPollingTenantRegistry}
 *     defaults to an empty list).
 *   - Spring {@code @KafkaListener} for the two upstream
 *     genealogy events ({@code TreeVisibilityChanged},
 *     {@code PersonRedacted}).
 *   - Consumer durable inbox / idempotency table.
 */
@Configuration
public class ResearchOutboxRelayWiring {

    @Bean
    public ResearchOutboxRelay researchOutboxRelay(
            ResearchOutboxRepository repository,
            ResearchKafkaProducerPort producer,
            ResearchPayloadForbiddenFieldScan scan,
            ResearchOutboxAuditHook hook,
            @Value("${research-service.outbox.relay.poll-batch-size:50}") int batchSize,
            @Value("${research-service.outbox.relay.poll-interval-ms:5000}") long pollIntervalMs,
            @Value("${research-service.outbox.relay.claim-lease-ms:30000}") long claimLeaseMs,
            @Value("${research-service.outbox.relay.max-attempts:5}") int maxAttempts) {
        return new ResearchOutboxRelay(
                repository,
                producer,
                scan,
                hook,
                batchSize,
                java.time.Duration.ofMillis(pollIntervalMs),
                java.time.Duration.ofMillis(claimLeaseMs),
                maxAttempts);
    }

    /**
     * Production JdbcTemplate-backed outbox repository.
     * E6.1d shipped the {@code InMemoryOutboxRepository} inside
     * the relay for unit-test path; E6.1e (this commit) adds
     * the production bean. The relay uses the JdbcTemplate
     * variant in production and the in-memory fake in unit
     * tests.
     */
    @Bean
    public ResearchJdbcOutboxRepository researchOutboxRepository(
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return new ResearchJdbcOutboxRepository(jdbcTemplate);
    }

    @Bean
    public ResearchOutboxRelayRunner.RelayRunMetrics researchOutboxRelayRunMetrics() {
        return new ResearchOutboxRelayRunner.RelayRunMetrics();
    }

    @Bean
    public ResearchOutboxAuditHook researchOutboxAuditHook(
            com.genealogy.platform.spring.audit.AuditPublisher publisher) {
        return new ResearchOutboxAuditHookAdapter(publisher);
    }

    @Bean
    public ResearchOutboxPollingTenantRegistry researchOutboxPollingTenantRegistry() {
        return new ResearchOutboxPollingTenantRegistry();
    }

    @Bean
    public ResearchPayloadForbiddenFieldScan researchPayloadForbiddenFieldScan() {
        return new ResearchPayloadForbiddenFieldScan();
    }

    @Bean
    public ResearchKafkaProducerPort researchKafkaProducerPort(
            org.springframework.kafka.core.KafkaTemplate<String, byte[]> kafkaTemplate) {
        Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        return new ResearchSpringKafkaProducerAdapter(kafkaTemplate);
    }

    /**
     * E6.1d ships the research outbox + Avro schemas + the
     * framework-free relay + the producer adapter; the
     * consumer side keeps the {@code ResearchEventPayloads}
     * constants so a follow-up Epic can wire the listener
     * without re-deriving the topic names.
     */
    public static final class TopicNames {
        public static String forEventType(String eventType) {
            return switch (eventType) {
                case ResearchEventPayloads.EVENT_CITATION_CREATED,
                     ResearchEventPayloads.EVENT_CLAIM_VERIFIED,
                     ResearchEventPayloads.EVENT_CONFLICT_DETECTED ->
                    "genealogy.research.v1.v1";
                default -> throw new IllegalArgumentException(
                        "unknown eventType for research topic routing: " + eventType);
            };
        }
    }
}
