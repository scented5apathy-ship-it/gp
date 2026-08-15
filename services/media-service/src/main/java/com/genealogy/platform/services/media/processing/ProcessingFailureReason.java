package com.genealogy.platform.services.media.processing;

/**
 * Closed-set enumeration of processing failure reasons.
 * Mirrors `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingFailureReasons` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>{@link #EXIF_PII_LEAKED} describes a derived artefact
 * that retained EXIF GPS coordinates or camera serial
 * numbers. {@link #CONTAINER_CORRUPT} describes a derived
 * artefact whose container (WebP / MP4 / PDF) fails ffprobe /
 * pdfinfo. {@link #UNSUPPORTED_DERIVED_FORMAT} is the
 * reason reserved for {@link ProcessingEngine#IMAGEMAGICK}
 * requests. {@link #DERIVED_OBJECT_KEY_COLLISION} is the
 * reason when the deterministic + versioned output key
 * already exists with a different content hash.
 */
public enum ProcessingFailureReason {
    PROCESS_TIMEOUT,
    PROCESS_ERROR,
    PROCESSOR_UNAVAILABLE,
    SANDBOX_NETWORK_DENIED,
    SANDBOX_RESOURCE_LIMIT,
    OBJECT_TOO_LARGE,
    INTEGRITY_CHECKSUM_MISMATCH,
    VALIDATION_FAILED,
    EXIF_PII_LEAKED,
    CONTAINER_CORRUPT,
    UNSUPPORTED_DERIVED_FORMAT,
    DNA_OBJECT_REJECTED,
    DERIVED_OBJECT_KEY_COLLISION;

    public static ProcessingFailureReason fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return ProcessingFailureReason.valueOf(
                    wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown ProcessingFailureReason from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}