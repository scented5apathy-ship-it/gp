package com.genealogy.platform.services.operations.telemetry;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Forbidden payload key catalogue for the E13.1 telemetry pipeline.
 * Mirrors the <code>forbiddenMetricLabels</code> + browser-side
 * <code>redaction.ts</code> closed-set declared in
 * <code>contracts/reliability/telemetry-policy.yaml</code>.
 */
public final class E13ForbiddenTelemetryKeys {

  public static final Set<String> KEYS = Set.of(
      "tenant_id", "user_id", "actor_id",
      "email", "oidc_subject", "oidcSubject",
      "phone", "passport", "ssn",
      "raw_dna", "raw_pii", "rawEmail", "rawPhone", "rawAddress",
      "treeViewerBypass", "rawEventPayload", "rawAuditStream",
      "rawConsentReceipt", "rawSignatureBlob", "rawIdDocument",
      "cameraSerial", "exifGps", "passportNumber",
      "productionPii", "internalVaultToken", "internalSessionCookie",
      "dnaRawBucketKey", "dnaMatchBucketKey");

  public static final List<String> PSEUDONYM_LABELS = List.of(
      "tenant_pseudo_id", "user_pseudo_id", "actor_pseudo_id",
      "workflow_pseudo_id", "consumer_pseudo_id");

  public static final List<String> REDACTION_PATTERNS = List.of(
      "\\b\\d{3}-\\d{2}-\\d{4}\\b",
      "\\b[A-Z]{1,2}[0-9]{6,9}\\b",
      "\\b[A-Z]{1,2}\\d{6,8}\\b",
      "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b",
      "\\+?\\d{1,3}[\\s.-]?\\(?\\d{1,4}\\)?[\\s.-]?\\d{1,4}[\\s.-]?\\d{1,9}",
      "\\b(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)(?:\\.(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)){3}\\b",
      "\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b",
      "\\brs\\d{2,8}\\s+[ACGT]{2,}\\b",
      "(?i)(authorization|cookie|set-cookie):\\s*[^\\s,;]+");

  private E13ForbiddenTelemetryKeys() {
    throw new UnsupportedOperationException("constants holder");
  }

  public static String firstViolation(Map<String, Object> payload) {
    if (payload == null) {
      return null;
    }
    for (String key : payload.keySet()) {
      if (key == null || key.isBlank()) {
        return "blank key";
      }
      if (KEYS.contains(key)) {
        return key;
      }
    }
    return null;
  }
}