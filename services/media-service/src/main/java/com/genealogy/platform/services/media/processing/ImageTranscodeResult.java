package com.genealogy.platform.services.media.processing;

import java.util.List;
import java.util.Objects;

/**
 * Result of the libvips image-optimizer activity. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingOutcomes + derivedAssetFormats` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>{@code exifScrubbed} is {@code true} when the worker
 * successfully stripped EXIF GPS coordinates + camera
 * serial numbers from the derived artefact. A derived
 * artefact with {@code exifScrubbed=false} is forced to
 * {@link DerivedAssetStatus#FAILED} with
 * {@link ProcessingFailureReason#EXIF_PII_LEAKED} per the
 * {@code exifScrubbedRequiredForDerivedReady=true} guard
 * rail.
 */
public record ImageTranscodeResult(
        String processingId,
        ProcessingOutcome outcome,
        ProcessingEngine engine,
        DerivedAssetFormat format,
        String derivedObjectKey,
        long derivedBytes,
        boolean exifScrubbed,
        long longestEdgePx,
        List<String> warnings) {

    public static final int MAX_PROCESSING_ID_LENGTH = 128;
    public static final int MAX_DERIVED_KEY_LENGTH = 1024;
    public static final long MAX_DERIVED_BYTES = 33554432L;
    public static final int MAX_LONGEST_EDGE_PX = 4096;
    public static final int MIN_LONGEST_EDGE_PX = 128;
    public static final int MAX_WARNINGS = 64;
    public static final int MAX_WARNING_LENGTH = 1024;

    public ImageTranscodeResult {
        Objects.requireNonNull(processingId, "processingId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(derivedObjectKey, "derivedObjectKey");
        Objects.requireNonNull(warnings, "warnings");
        if (processingId.isBlank()
                || processingId.length() > MAX_PROCESSING_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "processingId length out of bounds");
        }
        if (derivedObjectKey.isBlank()
                || derivedObjectKey.length() > MAX_DERIVED_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "derivedObjectKey length out of bounds");
        }
        if (derivedBytes < 0L || derivedBytes > MAX_DERIVED_BYTES) {
            throw new IllegalArgumentException(
                    "derivedBytes out of bounds [0, "
                            + MAX_DERIVED_BYTES + "]");
        }
        if (longestEdgePx < MIN_LONGEST_EDGE_PX
                || longestEdgePx > MAX_LONGEST_EDGE_PX) {
            throw new IllegalArgumentException(
                    "longestEdgePx out of bounds ["
                            + MIN_LONGEST_EDGE_PX + ", "
                            + MAX_LONGEST_EDGE_PX + "]");
        }
        List<String> safeWarnings = warnings.isEmpty()
                ? List.of()
                : List.copyOf(warnings);
        if (safeWarnings.size() > MAX_WARNINGS) {
            throw new IllegalArgumentException(
                    "warnings exceeds " + MAX_WARNINGS + " entries");
        }
        for (String w : safeWarnings) {
            if (w == null || w.isBlank()
                    || w.length() > MAX_WARNING_LENGTH) {
                throw new IllegalArgumentException(
                        "warning entry out of bounds");
            }
        }
        warnings = safeWarnings;
    }

    /**
     * Convenience factory for short-lived tests.
     */
    public static ImageTranscodeResult success(
            String processingId,
            DerivedAssetFormat format,
            long longestEdgePx) {
        return new ImageTranscodeResult(
                processingId,
                ProcessingOutcome.SUCCESS,
                ProcessingEngine.LIBVIPS,
                format,
                "media/derived/" + processingId,
                1024L,
                true,
                longestEdgePx,
                List.of());
    }
}