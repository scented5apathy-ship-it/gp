package com.genealogy.platform.services.audit.persistence;

import com.genealogy.platform.services.audit.domain.AuditEntry;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistence boundary for the audit-service ledger. The default
 * production implementation uses jOOQ against PostgreSQL; tests
 * supply an in-memory variant.
 *
 * <p>The interface is intentionally narrow: append + idempotent
 * lookup + chain-head read + retention-batch read + deletion-
 * evidence insert. Mutations beyond INSERT are intentionally
 * <em>not</em> exposed because the trigger in V1 rejects
 * UPDATE/DELETE.
 */
public interface AuditEntryRepository {

    /** Returns true if a row with {@code eventId} already exists (idempotency check). */
    boolean exists(String tenantId, String eventId);

    /**
     * Appends a new entry. Implementations MUST enforce
     * {@code (eventId)} uniqueness and surface the previous hash
     * from the chain head as {@code entry.previousHash()}. Returns
     * the persisted entry with the database-generated {@code id}.
     */
    AuditEntry append(AuditEntry entry);

    /** Returns the most recent entry in the chain for a tenant, or empty. */
    Optional<AuditEntry> chainHead(String tenantId);

    /** Returns entries within the [from, to) window for a tenant + audit class. */
    List<AuditEntry> findInWindow(String tenantId, String auditClass, java.time.Instant from, java.time.Instant to);

    /** Returns the count of entries in the (tenant, audit class, before) window. */
    long countOlderThan(String tenantId, String auditClass, java.time.Instant before);

    /** Returns entries eligible for retention sweep (older than {@code before}). */
    List<AuditEntry> findOlderThan(String tenantId, String auditClass, java.time.Instant before, int limit);

    /** Counts the entries grouped by audit class for a tenant. Used by export manifest. */
    Map<String, Long> classCounts(String tenantId, java.time.Instant from, java.time.Instant to);
}
