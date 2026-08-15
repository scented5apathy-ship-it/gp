package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of subject visibility classes.
 * Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliverySubjectVisibilityClass` (E7.4) +
 * `requirements.md` R4.2 + R9.5 + `design.md` §6.2 +
 * `glossary-and-policy-matrix.md` §2.1
 * (110-year living inference).
 *
 * <p>{@link #LIVING} subjects are inferred living (or
 * recently deceased) and require a watermark +
 * redaction overlay before delivery;
 * {@link #MINOR} subjects are children under
 * {@code MINORITY_AGE=18} (per `glossary-and-policy-matrix.md`
 * §1.2) and require both watermark + jurisdiction
 * check; {@link #HISTORICAL} subjects are pre-1900 or
 * known-deceased > 110 years and follow the
 * privacy-level-default from R4.2.
 */
public enum DeliverySubjectVisibilityClass {
    LIVING,
    MINOR,
    HISTORICAL;

    public static DeliverySubjectVisibilityClass fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DeliverySubjectVisibilityClass.valueOf(
                    wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DeliverySubjectVisibilityClass from wire: "
                            + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}