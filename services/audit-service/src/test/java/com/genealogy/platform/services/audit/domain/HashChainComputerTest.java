package com.genealogy.platform.services.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HashChainComputerTest {

    @Test
    void genesisHashIsSixtyFourZeros() {
        assertThat(HashChainComputer.GENESIS_HASH).hasSize(64).matches("[0]+");
    }

    @Test
    void sameCanonicalBytesProduceSameHash() {
        AuditEntry entry = makeEntry("evt-1", "tenant-1", "auth", "auth.login.succeeded",
                Instant.parse("2026-01-01T00:00:00Z"), HashChainComputer.GENESIS_HASH);
        String first = HashChainComputer.entryHash(entry);
        String second = HashChainComputer.entryHash(entry);
        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void differentTenantProduceDifferentHash() {
        AuditEntry a = makeEntry("evt-1", "tenant-1", "auth", "auth.login.succeeded",
                Instant.parse("2026-01-01T00:00:00Z"), HashChainComputer.GENESIS_HASH);
        AuditEntry b = makeEntry("evt-1", "tenant-2", "auth", "auth.login.succeeded",
                Instant.parse("2026-01-01T00:00:00Z"), HashChainComputer.GENESIS_HASH);
        assertThat(HashChainComputer.entryHash(a)).isNotEqualTo(HashChainComputer.entryHash(b));
    }

    @Test
    void verifyAcceptsValidChain() {
        AuditEntry entry = makeEntry("evt-1", "tenant-1", "auth", "auth.login.succeeded",
                Instant.parse("2026-01-01T00:00:00Z"), HashChainComputer.GENESIS_HASH);
        AuditEntry canonical = new AuditEntry(
                entry.eventId(), entry.tenantId(), entry.actorId(), entry.auditClass(),
                entry.action(), entry.resourceType(), entry.resourceId(), entry.reasonCode(),
                entry.correlationId(), entry.occurredAt(), entry.receivedAt(),
                entry.metadata(), entry.previousHash(), HashChainComputer.entryHash(entry));
        IntegrityStatus status = HashChainComputer.verify(canonical, HashChainComputer.GENESIS_HASH);
        assertThat(status.valid()).isTrue();
    }

    @Test
    void verifyDetectsTamperedEntryHash() {
        AuditEntry entry = makeEntry("evt-1", "tenant-1", "auth", "auth.login.succeeded",
                Instant.parse("2026-01-01T00:00:00Z"), HashChainComputer.GENESIS_HASH);
        AuditEntry tampered = new AuditEntry(
                entry.eventId(), entry.tenantId(), entry.actorId(), entry.auditClass(),
                entry.action(), entry.resourceType(), entry.resourceId(), entry.reasonCode(),
                entry.correlationId(), entry.occurredAt(), entry.receivedAt(),
                entry.metadata(), entry.previousHash(), "0".repeat(64));
        IntegrityStatus status = HashChainComputer.verify(tampered, HashChainComputer.GENESIS_HASH);
        assertThat(status.valid()).isFalse();
        assertThat(status.detail()).startsWith(IntegrityStatus.TAMPER_MARKER);
    }

    @Test
    void verifyDetectsBrokenPreviousHash() {
        AuditEntry entry = makeEntry("evt-1", "tenant-1", "auth", "auth.login.succeeded",
                Instant.parse("2026-01-01T00:00:00Z"), HashChainComputer.GENESIS_HASH);
        AuditEntry canonical = new AuditEntry(
                entry.eventId(), entry.tenantId(), entry.actorId(), entry.auditClass(),
                entry.action(), entry.resourceType(), entry.resourceId(), entry.reasonCode(),
                entry.correlationId(), entry.occurredAt(), entry.receivedAt(),
                entry.metadata(), entry.previousHash(), HashChainComputer.entryHash(entry));
        IntegrityStatus status = HashChainComputer.verify(canonical, "1".repeat(64));
        assertThat(status.valid()).isFalse();
        assertThat(status.detail()).contains("previous_hash mismatch");
    }

    private static AuditEntry makeEntry(
            String eventId,
            String tenantId,
            String auditClass,
            String action,
            Instant occurredAt,
            String previousHash) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("k", "v");
        return new AuditEntry(
                eventId, tenantId, "actor-1", auditClass, action, "session", eventId,
                null, "corr-1", occurredAt, occurredAt, metadata, previousHash,
                "0".repeat(64));
    }
}
