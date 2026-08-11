package com.genealogy.platform.services.genealogy.projection;

import java.util.Locale;
import java.util.Objects;

/**
 * Closed-set of redaction reason codes attached to a projection
 * node. Mirrors
 * {@code contracts/genealogy/tree-projection-policy.yaml::
 * spec.redactionObligations[*].reasonCode} and
 * {@code glossary-and-policy-matrix.md} §11 reason codes. The
 * same set is enumerated in
 * {@code contracts/abac/policy.default.yaml::spec.reasonCodes}.
 */
public enum ProjectionRedactionReasonCode {
    LIVING_REDACTED,
    MINOR_GUARDIAN_REQUIRED,
    PRIVACY_CLASS_RESTRICTED,
    VISIBILITY_UNLISTED_TOKEN_INVALID;

    public String wire() {
        switch (this) {
            case LIVING_REDACTED:
                return "living_redacted";
            case MINOR_GUARDIAN_REQUIRED:
                return "minor_guardian_required";
            case PRIVACY_CLASS_RESTRICTED:
                return "privacy_class_restricted";
            case VISIBILITY_UNLISTED_TOKEN_INVALID:
                return "visibility_unlisted_token_invalid";
            default:
                return name().toLowerCase(Locale.ROOT);
        }
    }

    public static ProjectionRedactionReasonCode fromWire(String wire) {
        Objects.requireNonNull(wire, "reasonCode");
        return ProjectionRedactionReasonCode.valueOf(
                wire.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}