package com.genealogy.platform.services.research.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Relay orchestrator. Polls the outbox repository for
 * pending rows, hands each one to the
 * {@link ResearchKafkaProducerPort}, and persists the next
 * state via {@link ResearchOutboxRepository}. The poll cadence
 * is driven by the contract's {@code pollBatchSize} /
 * {@code pollIntervalMillis} / {@code claimLeaseSeconds}.
 *
 * <p>The relay is the only component that mutates the
 * non-{@link ResearchOutboxStatus#PENDING} states. It emits
 * audit hooks via the {@link ResearchOutboxAuditHook} port so
 * the platform audit-service picks up the publish / retry /
 * DLQ outcomes.
 *
 * <p>Per ADR-E0.5-08 + {@code design.md} §7.3 the relay
 * re-validates the payload against the
 * {@link ResearchPayloadForbiddenFieldScan} before publishing;
 * a forbidden field flips the row to
 * {@link ResearchOutboxStatus#DEAD_LETTERED} with reason
 * {@link ResearchDlqReason#PAYLOAD_ENCODE_FAILED}.
 */
public final class ResearchOutboxRelay {

    private final ResearchOutboxRepository repository;
    private final ResearchKafkaProducerPort producer;
    private final ResearchPayloadForbiddenFieldScan payloadScan;
    private final ResearchOutboxAuditHook auditHook;
    private final int pollBatchSize;
    private final Duration pollInterval;
    private final Duration claimLease;
    private final int maxAttempts;

    public ResearchOutboxRelay(
            ResearchOutboxRepository repository,
            ResearchKafkaProducerPort producer,
            ResearchPayloadForbiddenFieldScan payloadScan,
            ResearchOutboxAuditHook auditHook,
            int pollBatchSize,
            Duration pollInterval,
            Duration claimLease,
            int maxAttempts) {
        if (pollBatchSize <= 0 || pollBatchSize > 1000) {
            throw new IllegalArgumentException("pollBatchSize must be 1..1000");
        }
        Objects.requireNonNull(pollInterval, "pollInterval");
        if (pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("pollInterval must be > 0");
        }
        Objects.requireNonNull(claimLease, "claimLease");
        if (claimLease.isNegative() || claimLease.isZero()) {
            throw new IllegalArgumentException("claimLease must be > 0");
        }
        if (maxAttempts <= 0 || maxAttempts > 100) {
            throw new IllegalArgumentException("maxAttempts must be 1..100");
        }
        this.repository = Objects.requireNonNull(repository, "repository");
        this.producer = Objects.requireNonNull(producer, "producer");
        this.payloadScan = Objects.requireNonNull(payloadScan, "payloadScan");
        this.auditHook = Objects.requireNonNull(auditHook, "auditHook");
        this.pollBatchSize = pollBatchSize;
        this.pollInterval = pollInterval;
        this.claimLease = claimLease;
        this.maxAttempts = maxAttempts;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    public int pollBatchSize() {
        return pollBatchSize;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Single tick of the relay loop. Returns the number of
     * rows processed (published + retried + dead-lettered).
     */
    public RelayTickResult tick(String tenantId, Instant now) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(now, "now");
        List<ResearchOutboxEventRecord> rows = repository.claimPending(tenantId, pollBatchSize, now);
        List<ResearchOutboxEventRecord> next = new ArrayList<>(rows.size());
        int published = 0;
        int retried = 0;
        int deadLettered = 0;
        for (ResearchOutboxEventRecord row : rows) {
            ResearchOutboxEventRecord claimed = row.withClaim(now, now.plus(claimLease));
            repository.save(claimed);

            ResearchOutboxEventRecord nextRow;
            try {
                if (payloadScan.containsForbiddenField(row.payloadJson())) {
                    nextRow = row.withDeadLettered(
                            ResearchDlqReason.PAYLOAD_ENCODE_FAILED,
                            "payload contains forbidden field (raw DNA / PII / secret)",
                            newAuditEventId(row));
                    deadLettered += 1;
                    auditHook.onDeadLettered(nextRow);
                } else {
                    ResearchKafkaProducerPort.Result result = producer.publish(row, now);
                    if (result.isPublished()) {
                        nextRow = row.withPublished(now);
                        published += 1;
                        auditHook.onPublished(nextRow);
                    } else if (result.isPermanentFailure()
                            || row.attempts() + 1 >= maxAttempts) {
                        nextRow = row.withDeadLettered(
                                result.isPermanentFailure()
                                        ? ResearchDlqReason.PAYLOAD_ENCODE_FAILED
                                        : ResearchDlqReason.PRODUCER_RETRY_EXHAUSTED,
                                result.error(),
                                newAuditEventId(row));
                        deadLettered += 1;
                        auditHook.onDeadLettered(nextRow);
                    } else {
                        nextRow = row.withAttempt(now, result.error());
                        nextRow = nextRow.withNextAttempt(now.plus(claimLease));
                        retried += 1;
                        auditHook.onRetried(nextRow);
                    }
                }
            } catch (RuntimeException e) {
                nextRow = row.withAttempt(now, e.getMessage());
                nextRow = nextRow.withNextAttempt(now.plus(claimLease));
                retried += 1;
                auditHook.onRetried(nextRow);
            }

            repository.save(nextRow);
            next.add(nextRow);
        }
        return new RelayTickResult(published, retried, deadLettered, next);
    }

    private static String newAuditEventId(ResearchOutboxEventRecord row) {
        // The audit-service ingests the publish / retry / DLQ
        // outcomes keyed by the outbox event id; the audit
        // entry id is the same id so the audit-service can
        // dedupe with the original aggregate write.
        return row.eventId();
    }

    public interface ResearchOutboxAuditHook {
        void onPublished(ResearchOutboxEventRecord row);

        void onRetried(ResearchOutboxEventRecord row);

        void onDeadLettered(ResearchOutboxEventRecord row);
    }

    public record RelayTickResult(int published, int retried, int deadLettered,
            List<ResearchOutboxEventRecord> nextRows) {
        public RelayTickResult {
            nextRows = List.copyOf(nextRows);
        }

        public int processed() {
            return published + retried + deadLettered;
        }
    }

    /**
     * In-memory fake for unit tests. Mirrors the semantics of
     * {@code SELECT ... FOR UPDATE SKIP LOCKED} via a per-tenant
     * claim flag.
     */
    public static final class InMemoryOutboxRepository implements ResearchOutboxRepository {
        private final Map<String, ResearchOutboxEventRecord> byId = new ConcurrentHashMap<>();

        public InMemoryOutboxRepository() {
        }

        public InMemoryOutboxRepository(List<ResearchOutboxEventRecord> seed) {
            for (ResearchOutboxEventRecord r : seed) {
                byId.put(r.eventId(), r);
            }
        }

        public void add(ResearchOutboxEventRecord row) {
            byId.put(row.eventId(), row);
        }

        @Override
        public List<ResearchOutboxEventRecord> claimPending(String tenantId, int limit,
                Instant now) {
            List<ResearchOutboxEventRecord> result = new ArrayList<>();
            for (ResearchOutboxEventRecord r : byId.values()) {
                if (!r.tenantId().equals(tenantId)) continue;
                if (r.status() != ResearchOutboxStatus.PENDING) continue;
                if (r.nextAttemptAt() != null && r.nextAttemptAt().isAfter(now)) continue;
                if (r.claimLeaseUntil() != null && r.claimLeaseUntil().isAfter(now)) continue;
                result.add(r);
                if (result.size() >= limit) break;
            }
            return result;
        }

        @Override
        public void save(ResearchOutboxEventRecord row) {
            byId.put(row.eventId(), row);
        }

        @Override
        public Optional<ResearchOutboxEventRecord> findById(String eventId) {
            return Optional.ofNullable(byId.get(eventId));
        }

        public Map<String, ResearchOutboxEventRecord> snapshot() {
            return new HashMap<>(byId);
        }
    }
}
