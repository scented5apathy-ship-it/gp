package com.genealogy.platform.services.importexport.transfer;

import com.genealogy.platform.services.importexport.shared.ForbiddenPayloadKeys;
import com.genealogy.platform.services.importexport.shared.ImportExportLimits;
import java.util.Map;

/**
 * Pure orchestrator that validates a Temporal transfer input
 * against the E9.1 invariants. Domain-service code is the source
 * of truth; this class is the contract-side guardrail.
 *
 * <p>Inputs MUST be opaque aggregate references only; secrets, raw
 * DNA sequences, and PII MUST be absent from the payload map.</p>
 */
public final class TransferRunValidator {

  private TransferRunValidator() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static TransferValidationOutcome validate(TransferRequest request) {
    if (request == null) {
      return TransferValidationOutcome.failed("TRANSFER_NOT_FOUND", "request MUST NOT be null");
    }
    if (request.workflowKind() == null) {
      return TransferValidationOutcome.failed("TRANSFER_KIND_UNKNOWN", "workflowKind MUST NOT be null");
    }
    if (request.runId() == null
        || request.runId().isBlank()
        || request.runId().length() > ImportExportLimits.WORKFLOW_MAX_HISTORY_LENGTH_EVENTS) {
      return TransferValidationOutcome.failed("TRANSFER_NOT_FOUND", "runId MUST be non-blank");
    }
    if (request.idempotencyKey() == null
        || request.idempotencyKey().length() > ImportExportLimits.WORKFLOW_MAX_HISTORY_LENGTH_EVENTS) {
      return TransferValidationOutcome.failed("TRANSFER_USER_PROVIDED_PAYLOAD_TOO_LARGE",
          "idempotencyKey MUST be present");
    }
    String forbidden = ForbiddenPayloadKeys.firstViolation(request.payload());
    if (forbidden != null) {
      if (forbidden.startsWith("rawDna") || forbidden.contains("Dna")) {
        return TransferValidationOutcome.failed("TRANSFER_DNA_BUCKET_FORBIDDEN", forbidden);
      }
      return TransferValidationOutcome.failed("TRANSFER_PII_LEAK_DETECTED", forbidden);
    }
    if (request.containsSecret()) {
      return TransferValidationOutcome.failed("TRANSFER_SECRETS_IN_PAYLOAD", "secret");
    }
    if (request.tenantPseudoId() == null || request.tenantPseudoId().isBlank()) {
      return TransferValidationOutcome.failed("TRANSFER_TENANT_MISMATCH", "tenantPseudoId");
    }
    return TransferValidationOutcome.ok(request);
  }

  /**
   * Result envelope. Either {@link #ok()} or {@link #failed(String, String)}
   * — exactly one of {@link #failureReason()} / {@link #request()} is non-null.
   */
  public record TransferValidationOutcome(
      boolean valid, TransferRequest request, String failureReason, String detail) {

    public static TransferValidationOutcome ok(TransferRequest request) {
      return new TransferValidationOutcome(true, request, null, null);
    }

    public static TransferValidationOutcome failed(String reason, String detail) {
      return new TransferValidationOutcome(false, null, reason, detail);
    }
  }

  /**
   * Pure-data transfer request. {@link #payload()} MUST only carry
   * opaque aggregate references.
   */
  public record TransferRequest(
      String runId,
      TransferWorkflowKind workflowKind,
      String idempotencyKey,
      String tenantPseudoId,
      String actorPseudoId,
      String correlationId,
      Map<String, Object> payload,
      boolean containsSecret) {

    public TransferRequest {
      payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
  }
}