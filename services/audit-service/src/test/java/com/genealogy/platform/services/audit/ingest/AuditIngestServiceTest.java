package com.genealogy.platform.services.audit.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.platform.services.audit.domain.AuditEntry;
import com.genealogy.platform.services.audit.domain.HashChainComputer;
import com.genealogy.platform.services.audit.persistence.InMemoryAuditEntryRepository;
import com.genealogy.platform.spring.audit.AuditClassRegistry;
import com.genealogy.platform.spring.audit.AuditEventEnvelope;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditIngestServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void ingestPersistsEntryWithCanonicalChain() {
        InMemoryAuditEntryRepository repo = new InMemoryAuditEntryRepository();
        AuditIngestService ingest = new AuditIngestService(repo);
        AuditEventEnvelope envelope = makeEnvelope("evt-1", "tenant-1",
                AuditClassRegistry.CLASS_AUTH, "auth.login.succeeded", T0);
        AuditEntry entry = ingest.ingest(envelope);
        assertThat(entry.previousHash()).isEqualTo(HashChainComputer.GENESIS_HASH);
        assertThat(entry.entryHash()).hasSize(64);
        assertThat(repo.chainHead("tenant-1")).isPresent();
    }

    @Test
    void ingestIsIdempotent() {
        InMemoryAuditEntryRepository repo = new InMemoryAuditEntryRepository();
        AuditIngestService ingest = new AuditIngestService(repo);
        AuditEventEnvelope envelope = makeEnvelope("evt-1", "tenant-1",
                AuditClassRegistry.CLASS_AUTH, "auth.login.succeeded", T0);
        AuditEntry first = ingest.ingest(envelope);
        AuditEntry second = ingest.ingest(envelope);
        assertThat(second.entryHash()).isEqualTo(first.entryHash());
        assertThat(repo.findInWindow("tenant-1", "all", T0.minusSeconds(10),
                T0.plusSeconds(10))).hasSize(1);
    }

    @Test
    void secondIngestContinuesChain() {
        InMemoryAuditEntryRepository repo = new InMemoryAuditEntryRepository();
        AuditIngestService ingest = new AuditIngestService(repo);
        AuditEntry first = ingest.ingest(makeEnvelope("evt-1", "tenant-1",
                AuditClassRegistry.CLASS_AUTH, "auth.login.succeeded", T0));
        AuditEntry second = ingest.ingest(makeEnvelope("evt-2", "tenant-1",
                AuditClassRegistry.CLASS_AUTH, "auth.login.succeeded", T0.plusSeconds(1)));
        assertThat(second.previousHash()).isNotEqualTo(HashChainComputer.GENESIS_HASH);
        assertThat(second.previousHash()).isEqualTo(first.entryHash());
        assertThat(repo.chainHead("tenant-1").get().eventId()).isEqualTo("evt-2");
    }

    @Test
    void ingestCarriesRedactedMetadataAsIs() {
        InMemoryAuditEntryRepository repo = new InMemoryAuditEntryRepository();
        AuditIngestService ingest = new AuditIngestService(repo);
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("email", "[REDACTED:email]");
        metadata.put("display_name", "Smith");
        AuditEventEnvelope envelope = new AuditEventEnvelope(
                "evt-3", "tenant-1", "actor-1",
                AuditClassRegistry.CLASS_AUTHORIZATION, "tenant.created",
                "tenant", "smith", null, "corr-1", T0, metadata);
        AuditEntry entry = ingest.ingest(envelope);
        assertThat(entry.metadata()).containsEntry("email", "[REDACTED:email]");
        assertThat(entry.metadata()).containsEntry("display_name", "Smith");
    }

    private static AuditEventEnvelope makeEnvelope(
            String eventId, String tenantId, String auditClass, String action, Instant occurredAt) {
        return new AuditEventEnvelope(
                eventId, tenantId, "actor-1", auditClass, action, "session", eventId,
                null, "corr-1", occurredAt, new LinkedHashMap<>());
    }
}
