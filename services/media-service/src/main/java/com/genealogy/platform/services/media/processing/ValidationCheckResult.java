package com.genealogy.platform.services.media.processing;

/**
 * Closed-set enumeration of validation check results.
 * Mirrors `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.validationCheckResults` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>{@link #FAIL} forces
 * {@link DerivedAssetStatus#FAILED} per the
 * {@code validationFailNeverYieldsDerivedReady=true} guard
 * rail. {@link #WARN} is a non-blocking warning that is
 * recorded in the {@link ValidationReport} but does NOT
 * prevent {@link DerivedAssetStatus#DERIVED_READY}.
 * {@link #SKIPPED} is recorded when the check is not
 * applicable (e.g. {@link ValidationCheck#CONTAINER_INTEGRITY}
 * on a text file).
 */
public enum ValidationCheckResult {
    PASS,
    WARN,
    FAIL,
    SKIPPED;

    public static ValidationCheckResult fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return ValidationCheckResult.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown ValidationCheckResult from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}