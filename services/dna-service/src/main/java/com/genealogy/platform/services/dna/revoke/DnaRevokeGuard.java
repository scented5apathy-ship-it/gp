package com.genealogy.platform.services.dna.revoke;

import com.genealogy.platform.services.dna.shared.DnaForbiddenPayloadKeys;
import com.genealogy.platform.services.dna.shared.DnaLimits;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates DNA revoke / export / delete
 * requests against the E10.5 invariants. Mirrors
 * <code>contracts/dna/dna-revoke-export-delete-policy.yaml</code>.
 */
public final class DnaRevokeGuard {

  public static final Set<String> TRIGGERS = Set.of(
      "CONSENT_REVOKED",
      "MEMBERSHIP_REVOKED",
      "DATA_SUBJECT_DELETION_REQUEST",
      "DATA_SUBJECT_PORTABILITY_REQUEST",
      "LEGAL_HOLD_RELEASED",
      "JURISDICTION_BAN_TRIGGERED",
      "PROVIDER_TERMINATION_TRIGGERED",
      "ADMIN_REVOKE_WITH_LEGAL_APPROVAL");

  public static final Set<String> PHASES = Set.of(
      "TRIGGERED",
      "IN_FLIGHT_CANCELLED",
      "SHARING_REVOKED",
      "DERIVED_PURGED",
      "RAW_DELETED",
      "LEGAL_HOLD_OVERRIDE",
      "EVIDENCE_ISSUED",
      "REVOKE_COMPLETED",
      "REVOKE_FAILED");

  public static final Set<String> STATUSES = Set.of(
      "QUEUED", "RUNNING", "AWAITING_STEP_UP", "AWAITING_LEGAL_APPROVAL",
      "COMPLETED", "FAILED", "BLOCKED", "CANCELLED");

  public static final Set<String> COMPENSATION_ACTIONS = Set.of(
      "CANCEL_IN_FLIGHT_MATCHES",
      "REVOKE_KIT_SHARING_LINKS",
      "DELETE_MATCH_SEGMENTS",
      "DELETE_KINSHIP_ESTIMATES",
      "DELETE_RELATIVE_DISCOVERY_ROWS",
      "DELETE_NOTES_FOR_KIT",
      "DELETE_KIT_REGISTRATION",
      "DELETE_RAW_ENCRYPTED_OBJECT",
      "PURGE_DNA_BUCKET_PREFIX",
      "REVOKE_SIGNED_EXPORT_URL",
      "ROTATE_DEK",
      "DEACTIVATE_DATA_KEY");

  public static final Set<String> EXPORT_FORMATS = Set.of(
      "JSON_OPAQUE_AGGREGATES",
      "CSV_OPAQUE_AGGREGATES",
      "PDF_REDACTED_SUMMARY",
      "PDF_LEGAL_HOLD_REPORT",
      "MEDIA_BUNDLE_REDACTED",
      "SELF_PORTABILITY_ZIP");

  public static final Set<String> REDACTION_LEVELS = Set.of(
      "NONE",
      "LIVING_ONLY",
      "MINOR_ONLY",
      "LIVING_AND_MINOR",
      "SENSITIVE_FULL",
      "DNA_DEFAULT_OFF",
      "CONSENT_REQUIRED");

  public static final Set<String> RETENTION_POLICIES = Set.of(
      "SINGLE_DOWNLOAD",
      "TIME_BOXED",
      "LEGAL_HOLD_BLOCKED",
      "IMMEDIATE_REVOKE");

  private static final Set<String> TRIGGERS_REQUIRING_STEP_UP = Set.of(
      "DATA_SUBJECT_PORTABILITY_REQUEST",
      "ADMIN_REVOKE_WITH_LEGAL_APPROVAL");

  private static final Set<String> TRIGGERS_REQUIRING_LEGAL_HOLD = Set.of(
      "JURISDICTION_BAN_TRIGGERED",
      "PROVIDER_TERMINATION_TRIGGERED",
      "ADMIN_REVOKE_WITH_LEGAL_APPROVAL");

  private DnaRevokeGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static RevokeOutcome validate(RevokeRequest request) {
    if (request == null) {
      return RevokeOutcome.failed("DNA_REVOKE_NOT_FOUND", "request MUST NOT be null");
    }
    if (request.tenantPseudoId() == null || request.tenantPseudoId().isBlank()) {
      return RevokeOutcome.failed("DNA_REVOKE_TENANT_MISMATCH", "tenantPseudoId");
    }
    if (request.trigger() == null || !TRIGGERS.contains(request.trigger())) {
      return RevokeOutcome.failed("DNA_REVOKE_TRIGGER_UNKNOWN",
          "trigger MUST be one of " + TRIGGERS);
    }
    if (request.kitAggregateId() == null || request.kitAggregateId().isBlank()) {
      return RevokeOutcome.failed("DNA_REVOKE_KIT_NOT_FOUND", "kitAggregateId");
    }
    if (TRIGGERS_REQUIRING_STEP_UP.contains(request.trigger())
        && !request.stepUpAuthCompleted()) {
      return RevokeOutcome.failed("DNA_REVOKE_STEP_UP_AUTH_REQUIRED",
          "step-up auth required for trigger: " + request.trigger());
    }
    if (TRIGGERS_REQUIRING_LEGAL_HOLD.contains(request.trigger())
        && !request.legalHoldOverride()) {
      return RevokeOutcome.failed("DNA_REVOKE_LEGAL_HOLD_OVERRIDE_INVALID",
          "legal-hold override required for trigger: " + request.trigger());
    }
    if (request.legalHoldActive() && !request.legalHoldOverride()) {
      return RevokeOutcome.failed("DNA_REVOKE_LEGAL_HOLD_BLOCKED",
          "legal hold active; override required");
    }
    if (request.exportRequested() && request.exportFormat() != null
        && !EXPORT_FORMATS.contains(request.exportFormat())) {
      return RevokeOutcome.failed("DNA_REVOKE_NOT_FOUND",
          "exportFormat MUST be one of " + EXPORT_FORMATS);
    }
    if (request.exportRequested() && !request.stepUpAuthCompleted()) {
      return RevokeOutcome.failed("DNA_REVOKE_STEP_UP_AUTH_REQUIRED",
          "export requires step-up auth");
    }
    if (request.exportRequested() && request.redactionLevel() != null
        && !REDACTION_LEVELS.contains(request.redactionLevel())) {
      return RevokeOutcome.failed("DNA_REVOKE_NOT_FOUND",
          "redactionLevel MUST be one of " + REDACTION_LEVELS);
    }
    if (request.exportRequested() && request.exportBundleBytes() > DnaLimits.EXPORT_BUNDLE_MAX_BYTES) {
      return RevokeOutcome.failed("DNA_REVOKE_NOT_FOUND",
          "exportBundleBytes MUST be <= " + DnaLimits.EXPORT_BUNDLE_MAX_BYTES);
    }
    if (request.exportRequested() && request.signedUrlTtlSeconds() > 0
        && request.signedUrlTtlSeconds() < DnaLimits.EXPORT_REVOCATION_PROPAGATION_SECONDS * 120) {
      return RevokeOutcome.failed("DNA_REVOKE_SIGNED_URL_REVOKE_FAILED",
          "signedUrlTtl MUST be >= 120 × revocationPropagation");
    }
    if (request.evidenceExcludesDeletedContent() == null) {
      return RevokeOutcome.failed("DNA_REVOKE_EVIDENCE_INCOMPLETE",
          "evidenceExcludesDeletedContent MUST be set explicitly");
    }
    if (request.compensationActions() == null
        || !request.compensationActions().stream()
            .allMatch(COMPENSATION_ACTIONS::contains)) {
      return RevokeOutcome.failed("DNA_REVOKE_DERIVED_PURGE_FAILED",
          "compensationActions MUST be subset of " + COMPENSATION_ACTIONS);
    }
    if (request.phase() != null && !PHASES.contains(request.phase())) {
      return RevokeOutcome.failed("DNA_REVOKE_NOT_FOUND",
          "phase MUST be one of " + PHASES);
    }
    if (request.status() != null && !STATUSES.contains(request.status())) {
      return RevokeOutcome.failed("DNA_REVOKE_NOT_FOUND",
          "status MUST be one of " + STATUSES);
    }
    String forbidden = DnaForbiddenPayloadKeys.firstViolation(request.payload());
    if (forbidden != null) {
      return RevokeOutcome.failed("DNA_REVOKE_EVIDENCE_INCOMPLETE", forbidden);
    }
    if (request.containsSecret()) {
      return RevokeOutcome.failed("DNA_REVOKE_EVIDENCE_INCOMPLETE", "secret");
    }
    return RevokeOutcome.ok(request);
  }

  public record RevokeOutcome(
      boolean valid, RevokeRequest request, String failureReason, String detail) {

    public static RevokeOutcome ok(RevokeRequest request) {
      return new RevokeOutcome(true, request, null, null);
    }

    public static RevokeOutcome failed(String reason, String detail) {
      return new RevokeOutcome(false, null, reason, detail);
    }
  }

  public record RevokeRequest(
      String tenantPseudoId,
      String actorPseudoId,
      String correlationId,
      String trigger,
      String kitAggregateId,
      boolean stepUpAuthCompleted,
      boolean legalHoldActive,
      Boolean legalHoldOverride,
      boolean exportRequested,
      String exportFormat,
      String redactionLevel,
      long exportBundleBytes,
      int signedUrlTtlSeconds,
      Boolean evidenceExcludesDeletedContent,
      Set<String> compensationActions,
      String phase,
      String status,
      Map<String, Object> payload,
      boolean containsSecret) {

    public RevokeRequest {
      payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
  }
}