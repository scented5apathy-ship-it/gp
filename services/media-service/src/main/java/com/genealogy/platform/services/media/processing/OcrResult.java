package com.genealogy.platform.services.media.processing;

import java.util.List;
import java.util.Objects;

/**
 * Result of the Tesseract OCR activity. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingOutcomes + derivedAssetFormats + ocrLanguages`
 * (E7.3) + `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>{@code language} MUST be one of the closed-set
 * {@link OcrLanguage} values; {@code dpi} MUST lie within
 * {@code ocrMinDpi=150 / ocrMaxDpi=600}.
 */
public record OcrResult(
        String processingId,
        ProcessingOutcome outcome,
        ProcessingEngine engine,
        DerivedAssetFormat format,
        OcrLanguage language,
        OcrOutputMode outputMode,
        String derivedObjectKey,
        long derivedBytes,
        int pages,
        int dpi,
        List<String> warnings) {

    public static final int MAX_PROCESSING_ID_LENGTH = 128;
    public static final int MAX_DERIVED_KEY_LENGTH = 1024;
    public static final long MAX_DERIVED_BYTES = 67108864L;
    public static final int MAX_PAGES = 200;
    public static final int MIN_DPI = 150;
    public static final int MAX_DPI = 600;
    public static final int MAX_WARNINGS = 64;
    public static final int MAX_WARNING_LENGTH = 1024;

    public OcrResult {
        Objects.requireNonNull(processingId, "processingId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(outputMode, "outputMode");
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
        if (pages < 0 || pages > MAX_PAGES) {
            throw new IllegalArgumentException(
                    "pages out of bounds [0, " + MAX_PAGES + "]");
        }
        if (dpi < MIN_DPI || dpi > MAX_DPI) {
            throw new IllegalArgumentException(
                    "dpi out of bounds ["
                            + MIN_DPI + ", " + MAX_DPI + "]");
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

    public static OcrResult success(
            String processingId,
            OcrLanguage language,
            OcrOutputMode outputMode,
            int pages,
            int dpi) {
        return new OcrResult(
                processingId,
                ProcessingOutcome.SUCCESS,
                ProcessingEngine.TESSERACT,
                DerivedAssetFormat.OCR_TEXT,
                language,
                outputMode,
                "media/derived/" + processingId,
                4096L,
                pages,
                dpi,
                List.of());
    }
}