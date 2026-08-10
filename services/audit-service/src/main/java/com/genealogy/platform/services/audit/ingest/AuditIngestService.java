package com.genealogy.platform.services.audit.ingest;

import com.genealogy.platform.services.audit.domain.AuditEntry;
import com.genealogy.platform.services.audit.persistence.AuditEntryRepository;
import com.genealogy.platform.spring.audit.AuditEventEnvelope;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Idempotent inbox for audit events consumed off the Kafka audit
 * topic. Dedupes on {@code (tenantId, eventId)} so a re-delivery
 * does not fork the hash chain. Production wiring pulls events off
 * Kafka via a Spring Kafka listener; the test path uses
 * {@link #ingest(AuditEventEnvelope)} directly.
 *
 * <p>Per <code>ownership-catalog.md</code> section 2.11 the
 * audit-service is a <em>sink</em>: it never publishes business
 * events, only audit entries.
 */
public class AuditIngestService {

    private final AuditEntryRepository repository;

    public AuditIngestService(AuditEntryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Idempotent ingest. Returns the persisted entry (or the
     * existing one when the event id was already seen).
     */
    public AuditEntry ingest(AuditEventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        AuditEntry candidate = toEntry(envelope);
        return repository.append(candidate);
    }

    private static AuditEntry toEntry(AuditEventEnvelope envelope) {
        Map<String, String> metadata = envelope.getMetadata() == null
                ? Map.of()
                : new LinkedHashMap<>(envelope.getMetadata());
        // previousHash + entryHash are filled in by the repository;
        // the values here are placeholders overridden atomically.
        return new AuditEntry(
                envelope.getEventId(),
                envelope.getTenantId(),
                envelope.getActorId(),
                envelope.getAuditClass(),
                envelope.getAction(),
                envelope.getResourceType(),
                envelope.getResourceId(),
                envelope.getReasonCode(),
                envelope.getCorrelationId(),
                envelope.getOccurredAt(),
                Instant.now(),
                metadata,
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000");
    }
}
