package com.genealogy.platform.services.media.processing;

/**
 * Closed-set enumeration of processing outcomes. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingOutcomes` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>{@link #PARTIAL} is a non-success outcome — partial
 * derived artefacts are not safe to link per the
 * {@code partialOutcomeNeverYieldsDerivedReady=true} guard
 * rail. {@link #VALIDATION_FAILED} is the outcome that
 * triggers the validation gate before {@link DerivedAssetStatus#DERIVED_READY}.
 */
public enum ProcessingOutcome {
    SUCCESS,
    PARTIAL,
    PROCESS_TIMEOUT,
    PROCESS_ERROR,
    UNSUPPORTED_FORMAT,
    SANDBOX_DENIED,
    OUTPUT_KEY_COLLISION,
    VALIDATION_FAILED;

    public static ProcessingOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return ProcessingOutcome.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown ProcessingOutcome from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}