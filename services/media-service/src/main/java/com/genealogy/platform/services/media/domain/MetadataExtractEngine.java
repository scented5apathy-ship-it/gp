package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of metadata extractor engines.
 * Mirrors
 * `contracts/media/malware-metadata-pipeline-policy.yaml
 * ::spec.metadataExtractEngines` (E7.2) +
 * `design.md` §11 (Apache Tika trích metadata/text tài
 * liệu).
 *
 * <p>{@link #TIKA_FULL} runs the full parser stack
 * (container + embedded streams + recursive MIME
 * detection); {@link #TIKA_LITE} runs the default detector
 * only (no embedded streams); {@link #TIKA} is the alias
 * the platform keeps for backwards compatibility with the
 * E7.1 contract. {@link #FALLBACK_NONE} forces the pipeline
 * into {@link PipelineStatus#FAILED} per
 * `extractorFailureRetainsInQuarantine=true`.
 */
public enum MetadataExtractEngine {
    TIKA,
    TIKA_LITE,
    TIKA_FULL,
    FALLBACK_NONE;

    public static MetadataExtractEngine fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return MetadataExtractEngine.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown MetadataExtractEngine from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}