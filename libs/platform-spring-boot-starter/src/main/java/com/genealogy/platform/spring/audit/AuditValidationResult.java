package com.genealogy.platform.spring.audit;

import java.util.Objects;

/**
 * Result of validating a {@link AuditEvent} against the closed-set
 * catalogue in {@link AuditClassRegistry}. The validator runs on
 * the publisher side BEFORE the event leaves the originating
 * service so a misconfigured caller can never poison the
 * {@code audit-service} ledger with an unknown audit class or
 * action.
 *
 * <p>Per {@code privacy-and-legal-gate.md} §11 + {@code ownership-catalog.md}
 * §2.11 the catalogue is the audit contract; adding a new class or
 * action requires an ADR supersession.
 */
public final class AuditValidationResult {

    public enum Violation {
        UNKNOWN_AUDIT_CLASS,
        UNKNOWN_ACTION,
        ACTION_CLASS_MISMATCH,
        MISSING_TENANT_ID,
        MISSING_RESOURCE_TYPE,
    }

    private final boolean valid;
    private final Violation violation;
    private final String detail;

    private AuditValidationResult(boolean valid, Violation violation, String detail) {
        this.valid = valid;
        this.violation = violation;
        this.detail = detail;
    }

    public static AuditValidationResult ok() {
        return new AuditValidationResult(true, null, null);
    }

    public static AuditValidationResult fail(Violation violation, String detail) {
        return new AuditValidationResult(false, Objects.requireNonNull(violation, "violation"), detail);
    }

    public boolean isValid() {
        return valid;
    }

    public Violation getViolation() {
        return violation;
    }

    public String getDetail() {
        return detail;
    }
}
