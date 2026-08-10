package com.genealogy.platform.services.audit.integrity;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.platform.services.audit.domain.AuditEntry;
import com.genealogy.platform.services.audit.domain.HashChainComputer;
import com.genealogy.platform.services.audit.persistence.AuditEntryRepository;
import com.genealogy.platform.spring.audit.AuditClassRegistry;
import com.genealogy.platform.spring.audit.AuditEventEnvelope;
import com.genealogy.platform.services.audit.ingest.AuditIngestService;
import com.genealogy.platform.services.audit.persistence.InMemoryAuditEntryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IntegrityVerifierTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void verifierReportsOkForValidChain() {
        InMemoryAuditEntryRepository repo = new InMemoryAuditEntryRepository();
        AuditIngestService ingest = new AuditIngestService(repo);
        for (int i = 0; i < 5; i++) {
            ingest.ingest(envelope("evt-" + i, T0.plusSeconds(i)));
        }
        IntegrityVerifier verifier = new IntegrityVerifier(repo);
        IntegrityVerifier.VerificationReport report = verifier.verify(
                "tenant-1", "auth", T0.minusSeconds(10), T0.plusSeconds(60));
        if (!report.ok()) {
            for (com.genealogy.platform.services.audit.domain.IntegrityStatus s : report.statuses()) {
                System.err.println("STATUS: " + s);
            }
        }
        assertThat(report.ok()).isTrue();
        assertThat(report.breaches()).isZero();
    }

    @Test
    void verifierDetectsTamperedEntry() {
        // Build a fake repository that returns one valid entry
        // followed by a tampered entry; the verifier must surface
        // the breach.
        AuditEntry goodEntry = makeEntry("evt-1", T0, HashChainComputer.GENESIS_HASH,
                HashChainComputer.GENESIS_HASH, /*tampered=*/ false);
        AuditEntry tamperedEntry = makeEntry("evt-2", T0.plusSeconds(1),
                goodEntry.entryHash(), "0".repeat(64), /*tampered=*/ true);
        List<AuditEntry> rows = List.of(goodEntry, tamperedEntry);
        AuditEntryRepository fake = new FakeRepo(rows);
        IntegrityVerifier verifier = new IntegrityVerifier(fake);
        IntegrityVerifier.VerificationReport report = verifier.verify(
                "tenant-1", "auth", T0.minusSeconds(10), T0.plusSeconds(60));
        assertThat(report.ok()).isFalse();
        assertThat(report.breaches()).isEqualTo(1L);
    }

    private static AuditEntry makeEntry(
            String eventId, Instant occurredAt, String previousHash, String entryHash,
            boolean tampered) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("k", "v");
        String computed = tampered
                ? entryHash
                : HashChainComputer.sha256("v1|" + eventId + "|tenant-1|actor-1|auth|"
                        + "auth.login.succeeded|session|" + eventId + "||corr-1|"
                        + occurredAt.toString() + "|" + previousHash + "|{k=v}");
        return new AuditEntry(
                eventId, "tenant-1", "actor-1",
                AuditClassRegistry.CLASS_AUTH, "auth.login.succeeded",
                "session", eventId, null, "corr-1",
                occurredAt, occurredAt, metadata, previousHash, computed);
    }

    private static AuditEventEnvelope envelope(String eventId, Instant occurredAt) {
        return new AuditEventEnvelope(
                eventId, "tenant-1", "actor-1",
                AuditClassRegistry.CLASS_AUTH, "auth.login.succeeded",
                "session", eventId, null, "corr-1",
                occurredAt, new LinkedHashMap<>());
    }

    /**
     * Minimal fake repository that returns a fixed list of entries
     * for {@code findInWindow} and reports the last as the chain
     * head. Other methods are unsupported and throw.
     */
    private static final class FakeRepo implements AuditEntryRepository {
        private final List<AuditEntry> rows;

        FakeRepo(List<AuditEntry> rows) {
            this.rows = rows;
        }

        @Override
        public boolean exists(String tenantId, String eventId) {
            return rows.stream().anyMatch(e -> e.eventId().equals(eventId));
        }

        @Override
        public AuditEntry append(AuditEntry entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AuditEntry> chainHead(String tenantId) {
            return Optional.of(rows.get(rows.size() - 1));
        }

        @Override
        public List<AuditEntry> findInWindow(
                String tenantId, String auditClass, Instant from, Instant to) {
            List<AuditEntry> filtered = new ArrayList<>();
            for (AuditEntry entry : rows) {
                if ("all".equals(auditClass) || auditClass.equals(entry.auditClass())) {
                    if (!entry.occurredAt().isBefore(from) && entry.occurredAt().isBefore(to)) {
                        filtered.add(entry);
                    }
                }
            }
            return filtered;
        }

        @Override
        public long countOlderThan(String tenantId, String auditClass, Instant before) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AuditEntry> findOlderThan(
                String tenantId, String auditClass, Instant before, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Long> classCounts(String tenantId, Instant from, Instant to) {
            throw new UnsupportedOperationException();
        }
    }
}
