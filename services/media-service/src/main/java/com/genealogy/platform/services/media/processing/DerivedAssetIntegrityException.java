package com.genealogy.platform.services.media.processing;

/**
 * Thrown when the integrity check between the E7.2-declared
 * checksum and the worker-observed checksum on a derived
 * artefact fails. Mirrors the
 * {@code PROCESSING_INTEGRITY_CHECKSUM_REQUIRED} /
 * {@code INTEGRITY_CHECKSUM_MISMATCH} invariants in
 * `contracts/media/media-processing-pipeline-policy.yaml`
 * (E7.3) + `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>The exception is intentionally a {@link RuntimeException}
 * so the Temporal activity can short-circuit on mismatch;
 * the orchestrator catches it and produces a
 * {@link DerivedAssetStatus#FAILED} decision with
 * {@link ProcessingFailureReason#INTEGRITY_CHECKSUM_MISMATCH}.
 */
public class DerivedAssetIntegrityException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DerivedAssetIntegrityException(String message) {
        super(message);
    }
}