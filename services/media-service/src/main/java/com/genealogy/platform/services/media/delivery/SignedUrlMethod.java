package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of signed-URL HTTP methods.
 * Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.signedUrlMethods` (E7.4) +
 * `requirements.md` R9.5 + `design.md` §12.
 *
 * <p>{@link #GET} is the canonical download method (the
 * E7.4 default per {@code signedUrlMethodDefault=GET});
 * {@link #HEAD} is the metadata probe;
 * {@link #PUT} is reserved for future upload-path reuse
 * but is NOT issued by the E7.4 contract today.
 */
public enum SignedUrlMethod {
    GET,
    HEAD,
    PUT;

    public static SignedUrlMethod fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return SignedUrlMethod.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown SignedUrlMethod from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}