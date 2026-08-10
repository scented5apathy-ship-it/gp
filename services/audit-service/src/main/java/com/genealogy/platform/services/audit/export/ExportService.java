package com.genealogy.platform.services.audit.export;

import com.genealogy.platform.services.audit.domain.AuditEntry;
import com.genealogy.platform.services.audit.domain.HashChainComputer;
import com.genealogy.platform.services.audit.integrity.IntegrityVerifier;
import com.genealogy.platform.services.audit.persistence.AuditEntryRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds audit-export bundles per
 * <code>contracts/audit/export.yaml</code>. The MVP returns the
 * manifest + payload in memory; the production path signs the
 * manifest and uploads to the WORM bucket. The signing path is
 * mocked behind {@link BundleSigner} so the contract stays
 * testable without KMS credentials.
 *
 * <p>Two-person rule: the caller supplies {@code requestedBy} and
 * {@code approvedBy}; if they are equal, the request is rejected.
 * The list of allowed roles is enforced by the BFF (out of scope
 * here).
 */
public class ExportService {

    public static final String EXPORT_SCHEMA_VERSION = "audit-export-bundle/v1";

    private final AuditEntryRepository repository;
    private final IntegrityVerifier integrityVerifier;
    private final BundleSigner signer;

    public ExportService(
            AuditEntryRepository repository,
            IntegrityVerifier integrityVerifier,
            BundleSigner signer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.integrityVerifier = Objects.requireNonNull(integrityVerifier, "integrityVerifier");
        this.signer = Objects.requireNonNull(signer, "signer");
    }

    public Bundle exportBundle(ExportRequest request) {
        Objects.requireNonNull(request, "request");
        validateRequest(request);
        Map<String, Long> classCounts = repository.classCounts(
                request.tenantId(), request.fromInstant(), request.toInstant());
        IntegrityVerifier.VerificationReport integrity = integrityVerifier.verify(
                request.tenantId(), "all", request.fromInstant(), request.toInstant());
        String bundleId = "bundle-" + UUID.randomUUID();
        Instant generatedAt = Instant.now();
        Manifest manifest = new Manifest(
                bundleId,
                request.tenantId(),
                generatedAt,
                request.requestedBy(),
                request.approvedBy(),
                classCounts,
                classCounts.values().stream().mapToLong(Long::longValue).sum(),
                integrity.ok() ? "OK" : "BREACH",
                integrity.breaches(),
                repository.chainHead(request.tenantId()).map(AuditEntry::entryHash)
                        .orElse(HashChainComputer.GENESIS_HASH),
                "sweep-run-" + generatedAt.toEpochMilli(),
                EXPORT_SCHEMA_VERSION);
        List<AuditEntry> entries = repository.findInWindow(
                request.tenantId(), "all", request.fromInstant(), request.toInstant());
        return new Bundle(manifest, entries, signer.sign(manifest));
    }

    private void validateRequest(ExportRequest request) {
        if (request.requestedBy().equals(request.approvedBy())) {
            throw new ExportRejectionException(
                    "two-person rule violated: requestedBy == approvedBy (" + request.requestedBy() + ")");
        }
        if (request.toInstant().isBefore(request.fromInstant())) {
            throw new ExportRejectionException("toInstant must be >= fromInstant");
        }
        long windowDays = java.time.Duration.between(request.fromInstant(), request.toInstant()).toDays();
        if (windowDays > 366) {
            throw new ExportRejectionException(
                    "time window exceeds contract max (366 days); got " + windowDays + " days");
        }
    }

    @FunctionalInterface
    public interface BundleSigner {
        String sign(Manifest manifest);
    }

    public record ExportRequest(
            String tenantId,
            List<String> auditClasses,
            Instant fromInstant,
            Instant toInstant,
            String requestedBy,
            String approvedBy,
            String reasonCode,
            Format format) {

        public enum Format {
            CSV,
            JSONL,
            PARQUET
        }
    }

    public record Manifest(
            String bundleId,
            String tenantId,
            Instant generatedAt,
            String requestedBy,
            String approvedBy,
            Map<String, Long> auditClassCounts,
            long totalEntries,
            String integrityStatus,
            long integrityBreaches,
            String chainHead,
            String retentionSweepRunId,
            String exportSchemaVersion) {
    }

    public record Bundle(Manifest manifest, List<AuditEntry> entries, String integrityHash) {
    }

    public static class ExportRejectionException extends RuntimeException {
        public ExportRejectionException(String message) {
            super(message);
        }
    }

    static Map<String, Long> emptyCounts() {
        return new LinkedHashMap<>();
    }
}
