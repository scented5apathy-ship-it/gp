package com.genealogy.platform.spring.audit;

import java.util.Map;
import java.util.Objects;

/**
 * Publisher-side guard for {@link AuditEvent} values. Rejects any
 * event whose {@code auditClass} or {@code action} falls outside the
 * closed-set catalogue declared in
 * {@code contracts/audit/policy.yaml}.
 *
 * <p>The validator intentionally does NOT log the rejected event
 * itself (which would defeat the purpose of the audit hook); it
 * increments a {@code platform.audit.rejected} counter and
 * surfaces the violation through a structured WARN log that names
 * the offending {@code action} + {@code resource} only. Services
 * can plug a custom handler via {@link #onViolation(BiConsumer)}
 * to bridge into OTel or paging.
 */
public final class AuditEventValidator {

    @FunctionalInterface
    public interface ViolationHandler {
        void handle(AuditEvent event, AuditValidationResult.Violation violation, String detail);
    }

    private final ViolationHandler violationHandler;

    public AuditEventValidator() {
        this((event, violation, detail) -> {
            // Default handler: best-effort WARN that intentionally
            // omits the event payload (privacy-by-design).
        });
    }

    public AuditEventValidator(ViolationHandler violationHandler) {
        this.violationHandler = Objects.requireNonNull(violationHandler, "violationHandler");
    }

    public AuditValidationResult validate(AuditEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.getTenantId() == null || event.getTenantId().isBlank()) {
            AuditValidationResult result = AuditValidationResult.fail(
                    AuditValidationResult.Violation.MISSING_TENANT_ID,
                    "tenantId is required on every audit event");
            violationHandler.handle(event, result.getViolation(), result.getDetail());
            return result;
        }
        if (event.getAction() == null || event.getAction().isBlank()) {
            AuditValidationResult result = AuditValidationResult.fail(
                    AuditValidationResult.Violation.UNKNOWN_ACTION,
                    "action is required on every audit event");
            violationHandler.handle(event, result.getViolation(), result.getDetail());
            return result;
        }
        if (!AuditClassRegistry.isKnownAction(event.getAction())) {
            AuditValidationResult result = AuditValidationResult.fail(
                    AuditValidationResult.Violation.UNKNOWN_ACTION,
                    "action '" + event.getAction() + "' is not in the audit catalogue");
            violationHandler.handle(event, result.getViolation(), result.getDetail());
            return result;
        }
        if (event.getResource() == null || event.getResource().isBlank()) {
            AuditValidationResult result = AuditValidationResult.fail(
                    AuditValidationResult.Violation.MISSING_RESOURCE_TYPE,
                    "resource type is required for downstream filters");
            violationHandler.handle(event, result.getViolation(), result.getDetail());
            return result;
        }
        // The audit-class derivation is deterministic; callers do
        // not pass an auditClass on the wire — the publisher
        // resolves it from the action catalogue. This keeps the
        // service code free of an extra mapping.
        Map<String, String> actionMap = AuditClassRegistry.actions();
        String derivedClass = actionMap.get(event.getAction());
        if (derivedClass == null) {
            AuditValidationResult result = AuditValidationResult.fail(
                    AuditValidationResult.Violation.UNKNOWN_AUDIT_CLASS,
                    "action '" + event.getAction() + "' maps to no audit class");
            violationHandler.handle(event, result.getViolation(), result.getDetail());
            return result;
        }
        return AuditValidationResult.ok();
    }

    public static String deriveAuditClass(AuditEvent event) {
        Objects.requireNonNull(event, "event");
        return AuditClassRegistry.classFor(event.getAction());
    }
}
