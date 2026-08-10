package com.genealogy.platform.services.genealogy.outbox;

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
 * pending rows, hands each one to
 * {@link RelayOutboxPublisher}, and persists the next
 * state via {@link OutboxRepository}. The poll cadence is
 * driven by the contract's
 * {@code pollBatchSize} / {@code pollIntervalMillis} /
 * {@code claimLeaseSeconds}.
 *
 * <p>The relay is the only component that mutates the
 * outbox row status. It emits audit hooks via the
 * {@link OutboxAuditHook} port (the production
 * implementation calls the audit-service; the unit tests
 * use a fake).
 */
public final class OutboxRelay {

    private final OutboxRepository repository;
    private final RelayOutboxPublisher publisher;
    private final OutboxAuditHook auditHook;
    private final int pollBatchSize;
    private final Duration pollInterval;
    private final Duration claimLease;

    public OutboxRelay(OutboxRepository repository,
            RelayOutboxPublisher publisher,
            OutboxAuditHook auditHook,
            int pollBatchSize,
            Duration pollInterval,
            Duration claimLease) {
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
        this.repository = Objects.requireNonNull(repository, "repository");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.auditHook = Objects.requireNonNull(auditHook, "auditHook");
        this.pollBatchSize = pollBatchSize;
        this.pollInterval = pollInterval;
        this.claimLease = claimLease;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    public int pollBatchSize() {
        return pollBatchSize;
    }

    /**
     * Single tick of the relay loop. Returns the number
     * of rows processed (published + retried +
     * dead-lettered).
     */
    public RelayTickResult tick(String tenantContext, Instant now) {
        Objects.requireNonNull(tenantContext, "tenantContext");
        Objects.requireNonNull(now, "now");
        List<OutboxEventRecord> rows = repository.claimPending(tenantContext, pollBatchSize, now);
        List<OutboxEventRecord> next = new ArrayList<>(rows.size());
        int published = 0;
        int retried = 0;
        int deadLettered = 0;
        for (OutboxEventRecord row : rows) {
            OutboxEventRecord claimed = row.withClaim(now, now.plus(claimLease));
            repository.save(claimed);
            RelayDecision decision = publisher.publish(claimed, tenantContext, now);
            repository.save(decision.nextRow());
            next.add(decision.nextRow());
            if (decision.published()) {
                published += 1;
                auditHook.onPublished(decision.nextRow());
            } else if (decision.deadLettered()) {
                deadLettered += 1;
                auditHook.onDeadLettered(decision.nextRow());
            } else {
                retried += 1;
                auditHook.onRetried(decision.nextRow());
            }
        }
        return new RelayTickResult(published, retried, deadLettered, next);
    }

    /** Audit hook the relay emits per outcome. */
    public interface OutboxAuditHook {
        void onPublished(OutboxEventRecord row);

        void onRetried(OutboxEventRecord row);

        void onDeadLettered(OutboxEventRecord row);
    }

    /** Result of one tick (used by tests + dashboards). */
    public record RelayTickResult(int published, int retried, int deadLettered,
            List<OutboxEventRecord> nextRows) {
        public RelayTickResult {
            nextRows = List.copyOf(nextRows);
        }

        public int processed() {
            return published + retried + deadLettered;
        }
    }

    /**
     * In-memory fake for unit tests. Mirrors the
     * semantics of {@code SELECT ... FOR UPDATE SKIP LOCKED}
     * via a per-tenant claim flag.
     */
    public static final class InMemoryOutboxRepository implements OutboxRepository {
        private final Map<String, OutboxEventRecord> byId = new ConcurrentHashMap<>();

        public InMemoryOutboxRepository() {
        }

        public InMemoryOutboxRepository(List<OutboxEventRecord> seed) {
            for (OutboxEventRecord r : seed) {
                byId.put(r.eventId(), r);
            }
        }

        public void add(OutboxEventRecord row) {
            byId.put(row.eventId(), row);
        }

        @Override
        public List<OutboxEventRecord> claimPending(String tenantId, int limit, Instant now) {
            List<OutboxEventRecord> result = new ArrayList<>();
            for (OutboxEventRecord r : byId.values()) {
                if (!r.tenantId().equals(tenantId)) continue;
                if (r.status() != OutboxStatus.PENDING) continue;
                if (r.nextAttemptAt() != null && r.nextAttemptAt().isAfter(now)) continue;
                if (r.claimLeaseUntil() != null && r.claimLeaseUntil().isAfter(now)) continue;
                result.add(r);
                if (result.size() >= limit) break;
            }
            return result;
        }

        @Override
        public void save(OutboxEventRecord row) {
            byId.put(row.eventId(), row);
        }

        @Override
        public Optional<OutboxEventRecord> findById(String eventId) {
            return Optional.ofNullable(byId.get(eventId));
        }

        public Map<String, OutboxEventRecord> snapshot() {
            return new HashMap<>(byId);
        }
    }
}
