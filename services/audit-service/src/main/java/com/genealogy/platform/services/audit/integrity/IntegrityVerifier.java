package com.genealogy.platform.services.audit.integrity;

import com.genealogy.platform.services.audit.domain.AuditEntry;
import com.genealogy.platform.services.audit.domain.HashChainComputer;
import com.genealogy.platform.services.audit.domain.IntegrityStatus;
import com.genealogy.platform.services.audit.persistence.AuditEntryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replays the per-tenant hash chain end-to-end. Runs on a
 * scheduled cron (see <code>application.yml::audit.integrity.verification-cron</code>)
 * and on demand from DPO exports. Reports per-event status so the
 * alerting pipeline can route on {@code INTEGRITY_BREACH}.
 *
 * <p>The verifier treats a window as a contiguous slice of the
 * chain starting at the genesis head. If the slice does NOT start
 * at genesis (i.e. there are entries before {@code from} that are
 * not in the window), the verifier trusts the producer-supplied
 * {@code previousHash} on the first entry in the window. A full
 * verification calls {@code verify(...)} with {@code from =
 * Instant.EPOCH}.
 */
public class IntegrityVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrityVerifier.class);

    private final AuditEntryRepository repository;

    public IntegrityVerifier(AuditEntryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public VerificationReport verify(String tenantId, String auditClass, Instant from, Instant to) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(auditClass, "auditClass");
        List<AuditEntry> entries = repository.findInWindow(tenantId, auditClass, from, to);
        // For a window that begins at the genesis head, every
        // entry's previousHash should equal genesis (only the
        // first) or the previous entry's entryHash. For a window
        // that begins mid-chain, the producer supplied the correct
        // previousHash when appending; we still walk forward and
        // verify the entry hash of every row.
        String previousHash = entries.isEmpty()
                ? HashChainComputer.GENESIS_HASH
                : entries.get(0).previousHash();
        List<IntegrityStatus> statuses = new ArrayList<>(entries.size());
        for (AuditEntry entry : entries) {
            IntegrityStatus status = HashChainComputer.verify(entry, previousHash);
            statuses.add(status);
            if (!status.valid()) {
                LOG.warn(
                        "audit integrity breach tenant_id={} audit_class={} event_id={} detail={}",
                        tenantId, auditClass, status.eventId(), status.detail());
            }
            previousHash = entry.entryHash();
        }
        return new VerificationReport(tenantId, auditClass, from, to, statuses);
    }

    public record VerificationReport(
            String tenantId,
            String auditClass,
            Instant from,
            Instant to,
            List<IntegrityStatus> statuses) {

        public boolean ok() {
            return statuses.stream().allMatch(IntegrityStatus::valid);
        }

        public long breaches() {
            return statuses.stream().filter(s -> !s.valid()).count();
        }
    }
}
