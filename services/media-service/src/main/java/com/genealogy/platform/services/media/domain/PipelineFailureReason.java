package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of pipeline failure reasons.
 * Mirrors `contracts/media/malware-metadata-pipeline-policy.yaml
 * ::spec.pipelineFailureReasons` (E7.2) +
 * `requirements.md` R9.2 + `design.md` §11.
 *
 * <p>{@link #SCAN_TIMEOUT} / {@link #SCAN_ERROR} /
 * {@link #SCANNER_UNAVAILABLE} /
 * {@link #SANDBOX_NETWORK_DENIED} /
 * {@link #SANDBOX_RESOURCE_LIMIT} /
 * {@link #INTEGRITY_CHECKSUM_MISMATCH} describe failures
 * during the ClamAV scan. {@link #MALWARE_INFECTED} /
 * {@link #MALWARE_SUSPICIOUS} describe the (terminal)
 * infected state. {@link #EXTRACT_TIMEOUT} /
 * {@link #EXTRACT_ERROR} / {@link #EXTRACTOR_UNAVAILABLE}
 * describe failures during Apache Tika metadata extraction.
 * {@link #SIGNATURE_STALE} forces the worker to schedule
 * {@code MALWARE_SIGNATURE_UPDATE} before any scan.
 * {@link #OBJECT_TOO_LARGE} forces chunked scan via
 * {@code INSTREAM}.
 */
public enum PipelineFailureReason {
    SCAN_TIMEOUT,
    SCAN_ERROR,
    MALWARE_INFECTED,
    MALWARE_SUSPICIOUS,
    EXTRACT_TIMEOUT,
    EXTRACT_ERROR,
    SCANNER_UNAVAILABLE,
    EXTRACTOR_UNAVAILABLE,
    SANDBOX_NETWORK_DENIED,
    SANDBOX_RESOURCE_LIMIT,
    SIGNATURE_STALE,
    OBJECT_TOO_LARGE,
    INTEGRITY_CHECKSUM_MISMATCH;

    public static PipelineFailureReason fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return PipelineFailureReason.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown PipelineFailureReason from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}