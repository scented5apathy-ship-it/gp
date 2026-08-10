package com.genealogy.platform.services.audit.persistence;

import com.genealogy.platform.services.audit.domain.AuditEntry;
import com.genealogy.platform.services.audit.domain.HashChainComputer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for tests. Mirrors the JDBC contract:
 * append + idempotent dedupe + per-tenant chain head + retention
 * queries + class counts.
 */
public class InMemoryAuditEntryRepository implements AuditEntryRepository {

    private final Map<String, List<AuditEntry>> byTenant = new ConcurrentHashMap<>();
    private final Map<String, AuditEntry> byEventId = new ConcurrentHashMap<>();

    @Override
    public boolean exists(String tenantId, String eventId) {
        return byEventId.containsKey(key(tenantId, eventId));
    }

    @Override
    public AuditEntry append(AuditEntry entry) {
        Objects.requireNonNull(entry, "entry");
        String k = key(entry.tenantId(), entry.eventId());
        if (byEventId.containsKey(k)) {
            return byEventId.get(k);
        }
        List<AuditEntry> tenantEntries = byTenant.computeIfAbsent(entry.tenantId(), t -> new ArrayList<>());
        String previousHash = tenantEntries.isEmpty()
                ? HashChainComputer.GENESIS_HASH
                : tenantEntries.get(tenantEntries.size() - 1).entryHash();
        // The repository overrides the producer-supplied
        // previousHash with the authoritative chain head so a
        // racing producer cannot fork the chain. The
        // corresponding entryHash is recomputed AFTER the override
        // so the verifier can detect any subsequent tamper by
        // recomputing the canonical bytes.
        AuditEntry withPrevious = new AuditEntry(
                entry.eventId(),
                entry.tenantId(),
                entry.actorId(),
                entry.auditClass(),
                entry.action(),
                entry.resourceType(),
                entry.resourceId(),
                entry.reasonCode(),
                entry.correlationId(),
                entry.occurredAt(),
                entry.receivedAt(),
                entry.metadata(),
                previousHash,
                "0".repeat(64));
        AuditEntry canonical = new AuditEntry(
                withPrevious.eventId(),
                withPrevious.tenantId(),
                withPrevious.actorId(),
                withPrevious.auditClass(),
                withPrevious.action(),
                withPrevious.resourceType(),
                withPrevious.resourceId(),
                withPrevious.reasonCode(),
                withPrevious.correlationId(),
                withPrevious.occurredAt(),
                withPrevious.receivedAt(),
                withPrevious.metadata(),
                withPrevious.previousHash(),
                HashChainComputer.entryHash(withPrevious));
        tenantEntries.add(canonical);
        byEventId.put(k, canonical);
        return canonical;
    }

    @Override
    public Optional<AuditEntry> chainHead(String tenantId) {
        List<AuditEntry> tenantEntries = byTenant.getOrDefault(tenantId, List.of());
        if (tenantEntries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(tenantEntries.get(tenantEntries.size() - 1));
    }

    @Override
    public List<AuditEntry> findInWindow(String tenantId, String auditClass, Instant from, Instant to) {
        List<AuditEntry> tenantEntries = byTenant.getOrDefault(tenantId, List.of());
        return tenantEntries.stream()
                .filter(e -> matchesAuditClass(auditClass, e))
                .filter(e -> !e.occurredAt().isBefore(from) && e.occurredAt().isBefore(to))
                .sorted(Comparator.comparing(AuditEntry::occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public long countOlderThan(String tenantId, String auditClass, Instant before) {
        List<AuditEntry> tenantEntries = byTenant.getOrDefault(tenantId, List.of());
        return tenantEntries.stream()
                .filter(e -> matchesAuditClass(auditClass, e))
                .filter(e -> e.occurredAt().isBefore(before))
                .count();
    }

    @Override
    public List<AuditEntry> findOlderThan(String tenantId, String auditClass, Instant before, int limit) {
        List<AuditEntry> tenantEntries = byTenant.getOrDefault(tenantId, List.of());
        return tenantEntries.stream()
                .filter(e -> matchesAuditClass(auditClass, e))
                .filter(e -> e.occurredAt().isBefore(before))
                .sorted(Comparator.comparing(AuditEntry::occurredAt))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Long> classCounts(String tenantId, Instant from, Instant to) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AuditEntry entry : byTenant.getOrDefault(tenantId, List.of())) {
            if (entry.occurredAt().isBefore(from) || !entry.occurredAt().isBefore(to)) {
                continue;
            }
            counts.merge(entry.auditClass(), 1L, Long::sum);
        }
        return counts;
    }

    private static boolean matchesAuditClass(String auditClass, AuditEntry entry) {
        return "all".equals(auditClass) || auditClass.equals(entry.auditClass());
    }

    private static String key(String tenantId, String eventId) {
        return tenantId + "|" + eventId;
    }
}
