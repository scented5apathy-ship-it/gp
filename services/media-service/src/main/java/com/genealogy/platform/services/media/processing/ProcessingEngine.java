package com.genealogy.platform.services.media.processing;

/**
 * Closed-set enumeration of processing engines. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingEngines` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>{@link #LIBVIPS} is the canonical image engine;
 * {@link #IMAGEMAGICK} is reserved in the closed-set only so
 * the executor can refuse it explicitly with
 * {@link ProcessingFailureReason#UNSUPPORTED_DERIVED_FORMAT}.
 * {@link #FALLBACK_NONE} is the in-memory deterministic
 * fallback that returns {@link ProcessingOutcome#PROCESS_ERROR}
 * per the {@code imageMagickFallbackPolicy=NEVER} guard rail.
 */
public enum ProcessingEngine {
    LIBVIPS,
    IMAGEMAGICK,
    FFMPEG,
    TESSERACT,
    GOTENBERG,
    FALLBACK_NONE;

    public static ProcessingEngine fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return ProcessingEngine.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown ProcessingEngine from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}