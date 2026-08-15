package com.genealogy.platform.services.media.albums;

/**
 * Closed-set reconciliation report outcome.
 *
 * <p>Mirrors {@code contracts/media/albums-linking-policy.yaml
 * ::spec.reconciliationOutcomes} (E7.5). The reconciliation
 * Temporal workflow ({@code spec.reconciliationWorkflowId =
 * media-album-reconciliation}) re-resolves every cross-service
 * reference once per {@code spec.reconciliationCadenceHours=24}
 * hour window and emits the outcome through the audit hook
 * + the outbox envelope.
 */
public enum ReconciliationOutcome {
    HEALTHY,
    DANGLING_REFERENCES,
    REVOKED_REFERENCES,
    ORPHAN_ASSETS,
    QUOTA_EXCEEDED,
    PURGED;

    public String wire() {
        return name();
    }

    public static ReconciliationOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (ReconciliationOutcome v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown reconciliationOutcome: " + wire);
    }
}