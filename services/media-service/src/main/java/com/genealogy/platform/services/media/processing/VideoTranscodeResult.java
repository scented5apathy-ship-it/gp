package com.genealogy.platform.services.media.processing;

import java.util.List;
import java.util.Objects;

/**
 * Result of the FFmpeg video-encoder activity. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingOutcomes + derivedAssetFormats + videoPresets`
 * (E7.3) + `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>{@code bitrateKbps} MUST lie within the
 * {@code videoMinBitrateKbps=200 / videoMaxBitrateKbps=20000}
 * bounds (validated by the orchestrator + the linter).
 */
public record VideoTranscodeResult(
        String processingId,
        ProcessingOutcome outcome,
        ProcessingEngine engine,
        DerivedAssetFormat format,
        VideoPreset preset,
        String derivedObjectKey,
        long derivedBytes,
        int bitrateKbps,
        List<String> warnings) {

    public static final int MAX_PROCESSING_ID_LENGTH = 128;
    public static final int MAX_DERIVED_KEY_LENGTH = 1024;
    public static final long MAX_DERIVED_BYTES = 1073741824L;
    public static final int MIN_BITRATE_KBPS = 200;
    public static final int MAX_BITRATE_KBPS = 20000;
    public static final int MAX_WARNINGS = 64;
    public static final int MAX_WARNING_LENGTH = 1024;

    public VideoTranscodeResult {
        Objects.requireNonNull(processingId, "processingId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(preset, "preset");
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
        if (bitrateKbps < MIN_BITRATE_KBPS
                || bitrateKbps > MAX_BITRATE_KBPS) {
            throw new IllegalArgumentException(
                    "bitrateKbps out of bounds ["
                            + MIN_BITRATE_KBPS + ", "
                            + MAX_BITRATE_KBPS + "]");
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

    public static VideoTranscodeResult success(
            String processingId,
            VideoPreset preset,
            DerivedAssetFormat format,
            int bitrateKbps) {
        return new VideoTranscodeResult(
                processingId,
                ProcessingOutcome.SUCCESS,
                ProcessingEngine.FFMPEG,
                format,
                preset,
                "media/derived/" + processingId,
                4096L,
                bitrateKbps,
                List.of());
    }
}