package com.genealogy.platform.services.media.domain;

/**
 * Thrown when the integrity check between the declared
 * checksum (captured at finalize time per the E7.1
 * contract) and the observed checksum (computed by the
 * worker before the scan / extract activity) fails.
 *
 * <p>Mirrors the
 * {@code PIPELINE_INTEGRITY_CHECKSUM_REQUIRED} /
 * {@code INTEGRITY_CHECKSUM_MISMATCH} invariants in
 * `contracts/media/malware-metadata-pipeline-policy.yaml`
 * (E7.2) + `requirements.md` R9.2 + `design.md` §11.
 */
public class PipelineIntegrityException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PipelineIntegrityException(String message) {
        super(message);
    }
}