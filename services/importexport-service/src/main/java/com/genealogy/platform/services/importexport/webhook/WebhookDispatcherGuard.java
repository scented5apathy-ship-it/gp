package com.genealogy.platform.services.importexport.webhook;

import com.genealogy.platform.services.importexport.shared.ImportExportLimits;

/**
 * Webhook delivery orchestrator. Enforces:
 *  - attempts ≤ {@link ImportExportLimits#WEBHOOK_MAX_DELIVERY_ATTEMPTS};
 *  - max backoff ≥ 720 × initial backoff;
 *  - signed payload + opaque aggregate reference;
 *  - DNA bucket shield;
 *  - tenant boundary + revoke on tenant revocation.
 */
public final class WebhookDispatcherGuard {

  private WebhookDispatcherGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static WebhookDeliveryOutcome dispatch(WebhookDeliveryRequest request) {
    if (request == null) {
      return WebhookDeliveryOutcome.deny("WEBHOOK_DELIVERY_FAILED", "request MUST NOT be null");
    }
    if (request.targetUrl() == null || request.targetUrl().isBlank()) {
      return WebhookDeliveryOutcome.deny("WEBHOOK_TARGET_URL_INVALID", "targetUrl");
    }
    if (!request.targetUrl().startsWith("https://")) {
      return WebhookDeliveryOutcome.deny("WEBHOOK_TARGET_TLS_INVALID", "scheme MUST be https");
    }
    if (request.attempts() > ImportExportLimits.WEBHOOK_MAX_DELIVERY_ATTEMPTS) {
      return WebhookDeliveryOutcome.deny("WEBHOOK_RETRY_EXHAUSTED", "attempts=" + request.attempts());
    }
    if (request.maxBackoffSeconds() < 720L * request.initialBackoffSeconds()) {
      return WebhookDeliveryOutcome.deny("WEBHOOK_RETRY_EXHAUSTED", "maxBackoff MUST be >= 720 × initial");
    }
    if (request.retryPolicy() == null) {
      return WebhookDeliveryOutcome.deny("WEBHOOK_RETRY_EXHAUSTED", "retryPolicy MUST NOT be null");
    }
    if (request.signatureAlgorithm() == null) {
      return WebhookDeliveryOutcome.deny("WEBHOOK_SIGNATURE_FAILED", "signatureAlgorithm");
    }
    if (!request.signed()) {
      return WebhookDeliveryOutcome.deny("WEBHOOK_SIGNATURE_FAILED", "signed MUST be true");
    }
    if (request.dnaBucketReference()) {
      return WebhookDeliveryOutcome.deny("WEBHOOK_DNA_BUCKET_FORBIDDEN", "dna bucket reference");
    }
    if (request.piiLeakDetected()) {
      return WebhookDeliveryOutcome.deny("WEBHOOK_PII_LEAK_DETECTED", "pii leak");
    }
    if (!request.tenantPseudoId().equals(request.expectedTenantPseudoId())) {
      return WebhookDeliveryOutcome.deny("WEBHOOK_TENANT_MISMATCH", "tenantPseudoId");
    }
    if (request.tenantRevoked()) {
      return WebhookDeliveryOutcome.deny("WEBHOOK_SUBSCRIPTION_REVOKED", "tenant revoked");
    }
    if (request.idempotencyKeyReuseConflict()) {
      return WebhookDeliveryOutcome.deny("WEBHOOK_IDEMPOTENCY_KEY_REUSED_CONFLICT", "idempotencyKey");
    }
    return WebhookDeliveryOutcome.allow(request);
  }

  public enum WebhookSignatureAlgorithm {
    HMAC_SHA_256,
    HMAC_SHA_512,
    ED25519;

    public String wire() {
      return name().replace('_', '-').toLowerCase();
    }
  }

  public record WebhookDeliveryRequest(
      String targetUrl,
      int attempts,
      long initialBackoffSeconds,
      long maxBackoffSeconds,
      WebhookRetryPolicy retryPolicy,
      WebhookSignatureAlgorithm signatureAlgorithm,
      boolean signed,
      boolean dnaBucketReference,
      boolean piiLeakDetected,
      boolean tenantRevoked,
      boolean idempotencyKeyReuseConflict,
      String tenantPseudoId,
      String expectedTenantPseudoId) {

    public WebhookDeliveryRequest {
      if (attempts < 0) attempts = 0;
      if (initialBackoffSeconds < 0) initialBackoffSeconds = 0;
      if (maxBackoffSeconds < 0) maxBackoffSeconds = 0;
      if (tenantPseudoId == null) tenantPseudoId = "";
      if (expectedTenantPseudoId == null) expectedTenantPseudoId = tenantPseudoId;
    }
  }

  public record WebhookDeliveryOutcome(
      boolean allow,
      String failureReason,
      String detail,
      WebhookDeliveryRequest request) {

    public static WebhookDeliveryOutcome allow(WebhookDeliveryRequest request) {
      return new WebhookDeliveryOutcome(true, null, null, request);
    }

    public static WebhookDeliveryOutcome deny(String reason, String detail) {
      return new WebhookDeliveryOutcome(false, reason, detail, null);
    }
  }
}