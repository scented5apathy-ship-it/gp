package com.genealogy.platform.services.media.domain;

import java.util.Objects;

/**
 * Authorization decision for an upload-lifecycle action.
 * Mirrors the {@code CommentAuthorizationDecision} convention
 * from E6.4 and
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.uploadSessionReAuthorizationRequired*` (E7.1).
 *
 * <p>Reason codes are pinned to the closed-set
 * {@code spec.invariants}; every non-allow decision MUST set
 * a non-blank reason code that starts with {@code MEDIA_}.
 */
public record UploadAuthorizationDecision(
        UploadAuthorizationOutcome outcome, String reasonCode) {

    public UploadAuthorizationDecision {
        Objects.requireNonNull(outcome, "outcome");
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        if (outcome != UploadAuthorizationOutcome.ALLOW
                && !reasonCode.startsWith("MEDIA_")) {
            throw new IllegalArgumentException(
                    "non-allow outcome requires MEDIA_ reason code, got: " + reasonCode);
        }
    }

    public static UploadAuthorizationDecision allow(String reasonCode) {
        return new UploadAuthorizationDecision(UploadAuthorizationOutcome.ALLOW, reasonCode);
    }

    public static UploadAuthorizationDecision deny(String reasonCode) {
        return new UploadAuthorizationDecision(UploadAuthorizationOutcome.DENY, reasonCode);
    }

    public static UploadAuthorizationDecision abacDeny(String reasonCode) {
        return new UploadAuthorizationDecision(
                UploadAuthorizationOutcome.ABAC_DENY, reasonCode);
    }

    public boolean isAllow() {
        return outcome == UploadAuthorizationOutcome.ALLOW;
    }
}
