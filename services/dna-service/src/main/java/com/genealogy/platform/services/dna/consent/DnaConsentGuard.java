package com.genealogy.platform.services.dna.consent;

import com.genealogy.platform.services.dna.shared.DnaForbiddenPayloadKeys;
import com.genealogy.platform.services.dna.shared.DnaLimits;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates a DNA consent grant against
 * the E10.3 invariants. Domain-service code is the source of truth;
 * this class is the contract-side guardrail.
 */
public final class DnaConsentGuard {

  public static final Set<String> SUBJECTS = Set.of(
      "SELF",
      "GUARDIAN_ON_BEHALF_OF_MINOR",
      "GUARDIAN_ON_BEHALF_OF_INCAPACITATED",
      "COURT_APPOINTED_REPRESENTATIVE",
      "DECEASED_DATA_SUBJECT_NEXT_OF_KIN");

  public static final Set<String> PURPOSES = Set.of(
      "DNA_KIT_REGISTRATION",
      "DNA_RAW_UPLOAD",
      "DNA_MATCHING",
      "DNA_RELATIVE_DISCOVERY",
      "DNA_RESEARCH_OPT_IN",
      "DNA_EXPORT_RAW",
      "DNA_EXPORT_MATCHES",
      "DNA_PORTABILITY_REQUEST",
      "DNA_DELETION_REQUEST",
      "DNA_LEGAL_HOLD_OVERRIDE");

  public static final Set<String> LEGAL_BASES = Set.of(
      "GDPR_ART_9_2_A_EXPLICIT_CONSENT",
      "GDPR_ART_9_2_G_SUBSTANTIAL_PUBLIC_INTEREST",
      "CCPA_SENSITIVE_PI_OPT_IN",
      "GINA_FEDERAL",
      "GINA_FLORIDA",
      "GIPA_ILLINOIS",
      "CCPA_CPRA_SENSITIVE_PI");

  public static final Set<String> POLICY_VERSIONS = Set.of(
      "DNA_POLICY_V1_2026",
      "DNA_POLICY_V1_2026_GUARDIAN",
      "DNA_POLICY_V1_2026_RESEARCH");

  public static final Set<String> STATES = Set.of(
      "DRAFT", "PENDING", "EFFECTIVE", "EXPIRED",
      "REVOKED", "SUPERSEDED", "LEGAL_HOLD", "REJECTED");

  public static final Set<String> MINOR_SUBJECTS = Set.of(
      "GUARDIAN_ON_BEHALF_OF_MINOR",
      "GUARDIAN_ON_BEHALF_OF_INCAPACITATED");

  public static final Set<String> EXPORT_PURPOSES = Set.of(
      "DNA_EXPORT_RAW", "DNA_EXPORT_MATCHES",
      "DNA_PORTABILITY_REQUEST", "DNA_DELETION_REQUEST");

  private DnaConsentGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  /**
   * Validates a consent grant / revoke / re-authorize request
   * against the E10.3 invariants.
   */
  public static ConsentOutcome validate(ConsentRequest request) {
    if (request == null) {
      return ConsentOutcome.failed("CONSENT_NOT_FOUND", "request MUST NOT be null");
    }
    if (request.tenantPseudoId() == null || request.tenantPseudoId().isBlank()) {
      return ConsentOutcome.failed("CONSENT_TENANT_MISMATCH", "tenantPseudoId");
    }
    if (request.policyVersion() == null
        || !POLICY_VERSIONS.contains(request.policyVersion())) {
      return ConsentOutcome.failed("CONSENT_POLICY_VERSION_UNKNOWN",
          "policyVersion MUST be one of " + POLICY_VERSIONS);
    }
    if (request.subject() == null || !SUBJECTS.contains(request.subject())) {
      return ConsentOutcome.failed("CONSENT_SUBJECT_MISMATCH",
          "subject MUST be one of " + SUBJECTS);
    }
    if (request.legalBasis() == null
        || !LEGAL_BASES.contains(request.legalBasis())) {
      return ConsentOutcome.failed("CONSENT_LEGAL_HOLD_REQUIRED",
          "legalBasis MUST be one of " + LEGAL_BASES);
    }
    if (request.purpose() == null || !PURPOSES.contains(request.purpose())) {
      return ConsentOutcome.failed("CONSENT_PURPOSE_DENIED",
          "purpose MUST be one of " + PURPOSES);
    }
    if (request.effectiveAt() == null) {
      return ConsentOutcome.failed("CONSENT_NOT_FOUND", "effectiveAt MUST be set");
    }
    if (request.expiresAt() == null) {
      return ConsentOutcome.failed("CONSENT_NOT_FOUND", "expiresAt MUST be set");
    }
    if (request.expiresAt().isBefore(request.effectiveAt())) {
      return ConsentOutcome.failed("CONSENT_EXPIRED",
          "expiresAt MUST be >= effectiveAt");
    }
    long lifetime = request.expiresAt().getEpochSecond() - request.effectiveAt().getEpochSecond();
    if (lifetime < 0 || lifetime > DnaLimits.CONSENT_RETENTION_SECONDS) {
      return ConsentOutcome.failed("CONSENT_EXPIRED",
          "lifetime MUST be <= CONSENT_RETENTION_SECONDS");
    }
    if (request.policyVersion().length() < DnaLimits.CONSENT_MIN_VERSION_LENGTH
        || request.policyVersion().length() > DnaLimits.CONSENT_MAX_VERSION_LENGTH) {
      return ConsentOutcome.failed("CONSENT_POLICY_VERSION_UNKNOWN",
          "policyVersion length MUST be in ["
              + DnaLimits.CONSENT_MIN_VERSION_LENGTH
              + ","
              + DnaLimits.CONSENT_MAX_VERSION_LENGTH + "]");
    }
    if (MINOR_SUBJECTS.contains(request.subject())
        && !request.guardianConsentIdPresent()) {
      return ConsentOutcome.failed("CONSENT_GUARDIAN_REQUIRED",
          "guardianConsentId MUST be present for minor / incapacitated subjects");
    }
    if (request.minorFlag()
        && !MINOR_SUBJECTS.contains(request.subject())) {
      return ConsentOutcome.failed("CONSENT_MINOR_WORKFLOW_MISSING",
          "minor flag requires GUARDIAN_ON_BEHALF_OF_MINOR subject");
    }
    if (EXPORT_PURPOSES.contains(request.purpose())
        && !request.stepUpAuthCompleted()) {
      return ConsentOutcome.failed("CONSENT_STEP_UP_AUTH_REQUIRED",
          "step-up auth required for export / portability / deletion");
    }
    if (request.purpose().equals("DNA_LEGAL_HOLD_OVERRIDE")
        && !request.legalHoldPresent()) {
      return ConsentOutcome.failed("CONSENT_LEGAL_HOLD_REQUIRED",
          "DNA_LEGAL_HOLD_OVERRIDE requires legal-hold record");
    }
    if (request.state() != null && !STATES.contains(request.state())) {
      return ConsentOutcome.failed("CONSENT_NOT_FOUND",
          "state MUST be one of " + STATES);
    }
    if (request.revokedAt() != null && request.state() != null
        && !request.state().equals("REVOKED")) {
      return ConsentOutcome.failed("CONSENT_REVOKED",
          "revokedAt set MUST coexist with state=REVOKED");
    }
    String forbidden = DnaForbiddenPayloadKeys.firstViolation(request.payload());
    if (forbidden != null) {
      if (forbidden.startsWith("rawConsent") || forbidden.equals("rawSignatureImage")
          || forbidden.equals("rawIdDocument") || forbidden.equals("rawFacePhoto")) {
        return ConsentOutcome.failed("CONSENT_NOT_FOUND", forbidden);
      }
      return ConsentOutcome.failed("CONSENT_NOT_FOUND", forbidden);
    }
    if (request.containsSecret()) {
      return ConsentOutcome.failed("CONSENT_NOT_FOUND", "secret");
    }
    return ConsentOutcome.ok(request);
  }

  /**
   * Result envelope.
   */
  public record ConsentOutcome(
      boolean valid, ConsentRequest request, String failureReason, String detail) {

    public static ConsentOutcome ok(ConsentRequest request) {
      return new ConsentOutcome(true, request, null, null);
    }

    public static ConsentOutcome failed(String reason, String detail) {
      return new ConsentOutcome(false, null, reason, detail);
    }
  }

  /**
   * Pure-data consent grant / revoke / re-authorize request.
   */
  public record ConsentRequest(
      String tenantPseudoId,
      String actorPseudoId,
      String correlationId,
      String subject,
      String purpose,
      String legalBasis,
      String policyVersion,
      Instant effectiveAt,
      Instant expiresAt,
      Instant revokedAt,
      String state,
      boolean minorFlag,
      boolean guardianConsentIdPresent,
      boolean stepUpAuthCompleted,
      boolean legalHoldPresent,
      Map<String, Object> payload,
      boolean containsSecret) {

    public ConsentRequest {
      payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
  }
}