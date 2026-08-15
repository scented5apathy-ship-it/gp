package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of metadata extractor outcomes.
 * Mirrors `contracts/media/malware-metadata-pipeline-policy.yaml
 * ::spec.metadataExtractOutcomes` (E7.2) +
 * `requirements.md` R9.3 (worker SHALL tạo thumbnail,
 * preview, OCR và transcoding theo loại file) +
 * `design.md` §11 (Apache Tika trích metadata/text tài
 * liệu).
 *
 * <p>{@link #SUCCESS} and {@link #EMPTY} are the two outcomes
 * that permit the pipeline to reach
 * {@link PipelineStatus#READY}; the rest fail the pipeline
 * and leave the asset in quarantine per
 * `extractorFailureRetainsInQuarantine` +
 * `requireSuccessOrEmptyMetadataForReady` guard rails.
 */
public enum MetadataExtractOutcome {
    SUCCESS,
    EMPTY,
    EXTRACT_TIMEOUT,
    EXTRACT_ERROR,
    UNSUPPORTED_MIME,
    SANDBOX_DENIED;

    public static MetadataExtractOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return MetadataExtractOutcome.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown MetadataExtractOutcome from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}