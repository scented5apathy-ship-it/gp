package com.genealogy.platform.services.media.processing;

import java.util.List;
import java.util.Objects;

/**
 * Result of the Gotenberg document-renderer activity.
 * Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingOutcomes + derivedAssetFormats` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>{@code pages} is the number of pages rendered; the
 * validation gate rejects derived artefacts with
 * {@code pages > ocrMaxPagesPerAsset=200}.
 */
public record DocumentRenderResult(
        String processingId,
        ProcessingOutcome outcome,
        ProcessingEngine engine,
        DerivedAssetFormat format,
        String derivedObjectKey,
        long derivedBytes,
        int pages,
        List<String> warnings) {

    public static final int MAX_PROCESSING_ID_LENGTH = 128;
    public static final int MAX_DERIVED_KEY_LENGTH = 1024;
    public static final long MAX_DERIVED_BYTES = 33554432L;
    public static final int MAX_PAGES = 200;
    public static final int MAX_WARNINGS = 64;
    public static final int MAX_WARNING_LENGTH = 1024;

    public DocumentRenderResult {
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
        if (pages < 0 || pages > MAX_PAGES) {
            throw new IllegalArgumentException(
                    "pages out of bounds [0, " + MAX_PAGES + "]");
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

    public static DocumentRenderResult success(
            String processingId,
            int pages) {
        return new DocumentRenderResult(
                processingId,
                ProcessingOutcome.SUCCESS,
                ProcessingEngine.GOTENBERG,
                DerivedAssetFormat.PDF_PREVIEW,
                "media/derived/" + processingId,
                2048L,
                pages,
                List.of());
    }
}