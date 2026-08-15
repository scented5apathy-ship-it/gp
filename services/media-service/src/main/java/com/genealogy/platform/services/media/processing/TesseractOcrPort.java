package com.genealogy.platform.services.media.processing;

/**
 * Port for the Tesseract OCR adapter. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingEngines + ocrLanguages + ocrOutputModes`
 * (E7.3) + `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>The port is the seam the Temporal worker uses to call
 * Tesseract. The implementation lives in the worker
 * subproject (E7.x / E11.x); this contract is the pure
 * interface. The worker MUST pre-resolve the language pack
 * from the {@link OcrLanguage} closed-set and refuse
 * unknown languages per
 * {@code ocrLanguagePacksPinned=true}.
 */
public interface TesseractOcrPort {

    /**
     * Synchronous OCR. The adapter MUST enforce the
     * {@code language} closed-set + the
     * {@code dpi} range ({@code ocrMinDpi=150 /
     * ocrMaxDpi=600}) + the
     * {@code ocrMaxPagesPerAsset=200} cap + the
     * deterministic + versioned output key derivation.
     *
     * @param processingId workflow-scoped idempotency key
     * @param objectKey input S3 / MinIO object key
     * @param objectSizeBytes declared input size (bytes)
     * @param expectedSha256 E7.2-declared checksum
     * @param language OCR language pack (EN / VI / FR / DE
     *                 / ZH)
     * @param outputMode output mode (TEXT / HOCR /
     *                   PDF_SEARCHABLE)
     * @param dpi DPI resolution (150 ≤ dpi ≤ 600)
     * @param engineVersion Tesseract version baked into the
     *                       deterministic key
     * @return OCR result; never {@code null}
     */
    OcrResult extract(
            String processingId,
            String objectKey,
            long objectSizeBytes,
            String expectedSha256,
            OcrLanguage language,
            OcrOutputMode outputMode,
            int dpi,
            String engineVersion);
}