package com.genealogy.platform.libs.security.abac;

import java.util.Objects;
import java.util.Optional;

/**
 * Closed set of ABAC reason codes (per
 * {@code design.md} §6.2 — "policy decision has allow/deny,
 * obligations (redact, watermark, audit) and reason code").
 *
 * <p>Codes are stable identifiers consumed by:
 * <ul>
 *   <li>the REST layer to map denial to {@code 403 Forbidden}
 *       (RFC 9457 {@code type} URI);</li>
 *   <li>the audit pipeline to bucket denial volume per code;</li>
 *   <li>the on-call dashboard to spot regressions.</li>
 * </ul>
 */
public record ReasonCode(String id, String description) {

    public ReasonCode {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-z][a-z0-9_]{2,63}")) {
            throw new IllegalArgumentException(
                    "invalid reason code id: " + id);
        }
        Objects.requireNonNull(description, "description");
    }

    public static final ReasonCode LIVING_REDACTED =
            new ReasonCode("living_redacted",
                    "Field is part of a living person record; ABAC redacts on public projection.");

    public static final ReasonCode MINOR_GUARDIAN_REQUIRED =
            new ReasonCode("minor_guardian_required",
                    "Subject is a minor and the request is missing a guardian consent tuple.");

    public static final ReasonCode PRIVACY_CLASS_RESTRICTED =
            new ReasonCode("privacy_class_restricted",
                    "Resource privacy class forbids the requested action without consent.");

    public static final ReasonCode CONSENT_MISSING =
            new ReasonCode("consent_missing",
                    "No active consent record for the requested purpose / action.");

    public static final ReasonCode CONSENT_REVOKED =
            new ReasonCode("consent_revoked",
                    "Consent was revoked; downstream processing must stop per privacy gate §D-06.");

    public static final ReasonCode JURISDICTION_BLOCKED =
            new ReasonCode("jurisdiction_blocked",
                    "Jurisdiction disallows the requested processing (e.g. genetic data export).");

    public static final ReasonCode CONTEXTUAL_DENY =
            new ReasonCode("contextual_deny",
                    "Contextual rule denies: support window expired, soft-deleted, suspended, etc.");

    public static final ReasonCode OBLIGATION_REDACT =
            new ReasonCode("obligation_redact",
                    "ABAC allowed but obligation requires field redaction before serialization.");

    public static final ReasonCode OBLIGATION_WATERMARK =
            new ReasonCode("obligation_watermark",
                    "ABAC allowed but obligation requires watermark on the response payload.");

    public static final ReasonCode OBLIGATION_AUDIT =
            new ReasonCode("obligation_audit",
                    "ABAC allowed but obligation requires an audit entry before/after the action.");

    public static final ReasonCode OPENFGA_DENY =
            new ReasonCode("openfga_deny",
                    "OpenFGA relationship check returned deny.");

    public static final ReasonCode OPENFGA_ABAC_MISSING =
            new ReasonCode("openfga_abac_missing",
                    "OpenFGA allow returned without an ABAC overlay call (Semgrep violation).");

    public Optional<String> asProblemType() {
        return Optional.of("/problems/abac/" + id);
    }
}
