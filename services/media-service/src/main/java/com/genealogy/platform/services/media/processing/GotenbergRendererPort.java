package com.genealogy.platform.services.media.processing;

/**
 * Port for the Gotenberg document-renderer adapter. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingEngines` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>The port is the seam the Temporal worker uses to call
 * Gotenberg (Chromium under the hood) for PDF / DOCX /
 * HTML preview generation. The implementation lives in the
 * worker subproject (E7.x / E11.x); this contract is the
 * pure interface.
 */
public interface GotenbergRendererPort {

    /**
     * Synchronous document render. The adapter MUST
     * enforce the {@code pages} cap + the
     * {@code ocrMaxPagesPerAsset=200} invariant + the
     * deterministic + versioned output key derivation.
     *
     * @param processingId workflow-scoped idempotency key
     * @param objectKey input S3 / MinIO object key
     * @param objectSizeBytes declared input size (bytes)
     * @param expectedSha256 E7.2-declared checksum
     * @param engineVersion Gotenberg version baked into
     *                       the deterministic key
     * @return render result; never {@code null}
     */
    DocumentRenderResult render(
            String processingId,
            String objectKey,
            long objectSizeBytes,
            String expectedSha256,
            String engineVersion);
}