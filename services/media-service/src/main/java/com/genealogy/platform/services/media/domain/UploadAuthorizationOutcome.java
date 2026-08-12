package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of upload authorization outcomes.
 * Mirrors the comment-authorization closed-set in
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.commentAuthorizationOutcome` (E6.4) and extends it
 * to the upload lifecycle (E7.1).
 *
 * <p>ABAC denial closes the session as
 * {@code UploadSessionStatus.REJECTED} per
 * {@code abacDenyClosesSession=true}.
 */
public enum UploadAuthorizationOutcome {
    ALLOW,
    DENY,
    ABAC_DENY;

    public static UploadAuthorizationOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return UploadAuthorizationOutcome.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown UploadAuthorizationOutcome from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}
