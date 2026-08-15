package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of signature database freshness.
 * Mirrors
 * `contracts/media/malware-metadata-pipeline-policy.yaml
 * ::spec.signatureStatuses` (E7.2) +
 * `requirements.md` R9.2 + `design.md` §11.
 *
 * <p>The worker MUST check signature freshness before any
 * scan per `signatureUpToDateRequiredBeforeScan=true`;
 * {@link #STALE} and {@link #UNKNOWN} force the pipeline
 * into {@link PipelineStatus#FAILED} with
 * {@link PipelineFailureReason#SIGNATURE_STALE}.
 */
public enum SignatureStatus {
    UP_TO_DATE,
    STALE,
    UNKNOWN;

    public static SignatureStatus fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return SignatureStatus.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown SignatureStatus from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}