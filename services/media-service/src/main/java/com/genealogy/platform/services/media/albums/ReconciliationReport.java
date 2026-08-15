package com.genealogy.platform.services.media.albums;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only report emitted by the reconciliation worker.
 *
 * <p>The report is consumed by the UI to show the editor
 * journey which albums need attention (dangling references,
 * revoked references, soft-deleted items pending purge).
 * Reports are persisted to the audit service so legal hold /
 * replay can re-derive them.
 */
public record ReconciliationReport(
        String reportId,
        String albumId,
        String tenantScopeId,
        Instant generatedAt,
        ReconciliationOutcome outcome,
        int totalItems,
        int resolvedItems,
        int danglingItems,
        int revokedItems,
        int orphanItems,
        Map<AlbumReferenceKind, Integer> danglingByKind,
        boolean reconciliationPurgeScheduled,
        Instant purgeAfter,
        String actorPseudoId,
        String correlationId) {

    public ReconciliationReport {
        Objects.requireNonNull(reportId, "reportId");
        Objects.requireNonNull(albumId, "albumId");
        Objects.requireNonNull(tenantScopeId, "tenantScopeId");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(danglingByKind, "danglingByKind");
        Objects.requireNonNull(actorPseudoId, "actorPseudoId");
        Objects.requireNonNull(correlationId, "correlationId");
        if (totalItems < 0 || resolvedItems < 0
                || danglingItems < 0 || revokedItems < 0
                || orphanItems < 0) {
            throw new IllegalArgumentException("negative counts");
        }
        if (resolvedItems + danglingItems + revokedItems
                + orphanItems > totalItems) {
            throw new IllegalArgumentException(
                    "resolved+dangling+revoked+orphan > total");
        }
        danglingByKind = Map.copyOf(danglingByKind);
    }

    public static ReconciliationReport healthy(
            String reportId,
            String albumId,
            String tenantScopeId,
            int totalItems,
            int resolvedItems,
            String actorPseudoId,
            String correlationId,
            Instant now) {
        return new ReconciliationReport(
                reportId, albumId, tenantScopeId, now,
                ReconciliationOutcome.HEALTHY,
                totalItems, resolvedItems, 0, 0, 0,
                Map.of(), false, null,
                actorPseudoId, correlationId);
    }
}