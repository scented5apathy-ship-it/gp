package com.genealogy.platform.services.media.processing;

/**
 * Closed-set enumeration of validation checks. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.validationChecks` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>The validation gate runs AFTER the processing activity
 * and BEFORE {@link DerivedAssetStatus#DERIVED_READY}. A
 * check that returns {@link ValidationCheckResult#FAIL}
 * forces {@link ProcessingFailureReason#VALIDATION_FAILED}
 * — the asset stays {@link DerivedAssetStatus#FAILED}.
 * {@link #DNA_BUCKET_ISOLATED} is a forward-looking check;
 * the full DNA bucket prefix enforcement lands in E7.4.
 */
public enum ValidationCheck {
    SIGNATURE_UP_TO_DATE,
    INTEGRITY_CHECKSUM,
    MAGIC_BYTES,
    CONTAINER_INTEGRITY,
    EXIF_SCRUBBED,
    DNA_BUCKET_ISOLATED;

    public static ValidationCheck fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return ValidationCheck.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown ValidationCheck from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}