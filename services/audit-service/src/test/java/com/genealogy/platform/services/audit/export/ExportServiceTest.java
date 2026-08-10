package com.genealogy.platform.services.audit.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.genealogy.platform.services.audit.integrity.IntegrityVerifier;
import com.genealogy.platform.services.audit.persistence.InMemoryAuditEntryRepository;
import com.genealogy.platform.services.audit.persistence.AuditEntryRepository;
import com.genealogy.platform.spring.audit.AuditClassRegistry;
import com.genealogy.platform.spring.audit.AuditEventEnvelope;
import com.genealogy.platform.services.audit.ingest.AuditIngestService;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class ExportServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void exportRejectsTwoPersonRuleViolation() {
        ExportService service = build(new InMemoryAuditEntryRepository());
        ExportService.ExportRequest request = new ExportService.ExportRequest(
                "tenant-1",
                java.util.List.of(AuditClassRegistry.CLASS_AUTH),
                T0,
                T0.plusSeconds(86400),
                "alice",
                "alice",
                "audit_export",
                ExportService.ExportRequest.Format.JSONL);
        assertThatThrownBy(() -> service.exportBundle(request))
                .isInstanceOf(ExportService.ExportRejectionException.class)
                .hasMessageContaining("two-person rule");
    }

    @Test
    void exportRejectsTimeWindowTooLarge() {
        ExportService service = build(new InMemoryAuditEntryRepository());
        ExportService.ExportRequest request = new ExportService.ExportRequest(
                "tenant-1",
                java.util.List.of(AuditClassRegistry.CLASS_AUTH),
                T0,
                T0.plusSeconds(400L * 86400L),
                "alice",
                "bob",
                "audit_export",
                ExportService.ExportRequest.Format.JSONL);
        assertThatThrownBy(() -> service.exportBundle(request))
                .isInstanceOf(ExportService.ExportRejectionException.class)
                .hasMessageContaining("time window");
    }

    @Test
    void exportReturnsManifestWithClassCountsAndChainHead() {
        InMemoryAuditEntryRepository repo = new InMemoryAuditEntryRepository();
        AuditIngestService ingest = new AuditIngestService(repo);
        for (int i = 0; i < 3; i++) {
            ingest.ingest(envelope("evt-" + i, T0.plusSeconds(i)));
        }
        ExportService service = build(repo);
        ExportService.ExportRequest request = new ExportService.ExportRequest(
                "tenant-1",
                java.util.List.of(AuditClassRegistry.CLASS_AUTH),
                T0.minusSeconds(10),
                T0.plusSeconds(60),
                "alice",
                "bob",
                "audit_export",
                ExportService.ExportRequest.Format.JSONL);
        ExportService.Bundle bundle = service.exportBundle(request);
        assertThat(bundle.manifest().requestedBy()).isEqualTo("alice");
        assertThat(bundle.manifest().approvedBy()).isEqualTo("bob");
        assertThat(bundle.manifest().exportSchemaVersion()).isEqualTo("audit-export-bundle/v1");
        assertThat(bundle.manifest().auditClassCounts()).containsEntry("auth", 3L);
        assertThat(bundle.manifest().integrityStatus()).isEqualTo("OK");
        assertThat(bundle.entries()).hasSize(3);
        assertThat(bundle.integrityHash()).isNotBlank();
    }

    private static ExportService build(AuditEntryRepository repository) {
        IntegrityVerifier verifier = new IntegrityVerifier(repository);
        return new ExportService(repository, verifier, manifest -> "signer-output");
    }

    private static AuditEventEnvelope envelope(String eventId, Instant occurredAt) {
        return new AuditEventEnvelope(
                eventId, "tenant-1", "actor-1",
                AuditClassRegistry.CLASS_AUTH, "auth.login.succeeded",
                "session", eventId, null, "corr-1",
                occurredAt, new LinkedHashMap<>());
    }
}
