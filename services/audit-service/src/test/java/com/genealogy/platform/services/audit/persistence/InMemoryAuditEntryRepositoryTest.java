package com.genealogy.platform.services.audit.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.platform.services.audit.domain.AuditEntry;
import com.genealogy.platform.services.audit.domain.HashChainComputer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryAuditEntryRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void appendAssignsGenesisPreviousHash() {
        InMemoryAuditEntryRepository repo = new InMemoryAuditEntryRepository();
        AuditEntry entry = repo.append(makeEntry("evt-1", "tenant-1", "auth",
                "auth.login.succeeded", T0));
        assertThat(entry.previousHash()).isEqualTo(HashChainComputer.GENESIS_HASH);
        assertThat(entry.entryHash()).hasSize(64);
    }

    @Test
    void chainHeadPointsToMostRecentEntry() {
        InMemoryAuditEntryRepository repo = new InMemoryAuditEntryRepository();
        AuditEntry first = repo.append(makeEntry("evt-1", "tenant-1", "auth",
                "auth.login.succeeded", T0));
        AuditEntry second = repo.append(makeEntry("evt-2", "tenant-1", "auth",
                "auth.login.succeeded", T0.plusSeconds(1)));
        assertThat(repo.chainHead("tenant-1")).isPresent();
        assertThat(repo.chainHead("tenant-1").get().eventId()).isEqualTo("evt-2");
        assertThat(second.previousHash()).isEqualTo(first.entryHash());
    }

    @Test
    void duplicateEventIdIsIdempotent() {
        InMemoryAuditEntryRepository repo = new InMemoryAuditEntryRepository();
        AuditEntry first = repo.append(makeEntry("evt-1", "tenant-1", "auth",
                "auth.login.succeeded", T0));
        AuditEntry second = repo.append(makeEntry("evt-1", "tenant-1", "auth",
                "auth.login.succeeded", T0));
        assertThat(second.eventId()).isEqualTo(first.eventId());
        assertThat(repo.chainHead("tenant-1").get().eventId()).isEqualTo("evt-1");
        // chainHead should still be the first appended entry (no
        // second insert happened).
        assertThat(repo.findInWindow("tenant-1", "all", T0.minusSeconds(60), T0.plusSeconds(60)))
                .hasSize(1);
    }

    @Test
    void tenantsAreIsolated() {
        InMemoryAuditEntryRepository repo = new InMemoryAuditEntryRepository();
        repo.append(makeEntry("evt-1", "tenant-1", "auth", "auth.login.succeeded", T0));
        repo.append(makeEntry("evt-1", "tenant-2", "auth", "auth.login.succeeded", T0));
        assertThat(repo.chainHead("tenant-1").get().entryHash())
                .isNotEqualTo(repo.chainHead("tenant-2").get().entryHash());
    }

    @Test
    void findInWindowHonoursBoundsAndClass() {
        InMemoryAuditEntryRepository repo = new InMemoryAuditEntryRepository();
        repo.append(makeEntry("a", "tenant-1", "auth", "auth.login.succeeded", T0));
        repo.append(makeEntry("b", "tenant-1", "policy", "abac.policy_reloaded",
                T0.plusSeconds(60)));
        repo.append(makeEntry("c", "tenant-1", "auth", "auth.login.failed",
                T0.plusSeconds(120)));
        assertThat(repo.findInWindow("tenant-1", "auth", T0.minusSeconds(10), T0.plusSeconds(90)))
                .hasSize(1);
        assertThat(repo.findInWindow("tenant-1", "all", T0.minusSeconds(10), T0.plusSeconds(200)))
                .hasSize(3);
    }

    @Test
    void classCountsGroupsByAuditClass() {
        InMemoryAuditEntryRepository repo = new InMemoryAuditEntryRepository();
        repo.append(makeEntry("a", "tenant-1", "auth", "auth.login.succeeded", T0));
        repo.append(makeEntry("b", "tenant-1", "auth", "auth.login.succeeded",
                T0.plusSeconds(1)));
        repo.append(makeEntry("c", "tenant-1", "consent", "consent.granted",
                T0.plusSeconds(2)));
        Map<String, Long> counts = repo.classCounts("tenant-1", T0.minusSeconds(10),
                T0.plusSeconds(10));
        assertThat(counts).containsEntry("auth", 2L).containsEntry("consent", 1L);
    }

    private static AuditEntry makeEntry(
            String eventId, String tenantId, String auditClass, String action, Instant occurredAt) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("k", "v");
        return new AuditEntry(
                eventId, tenantId, "actor-1", auditClass, action, "session", eventId,
                null, "corr-1", occurredAt, occurredAt, metadata,
                "0".repeat(64), "0".repeat(64));
    }
}
