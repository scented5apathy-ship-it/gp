package com.genealogy.platform.services.operations.shared;

import java.util.Set;

/**
 * Forbidden payload key catalogue for the E11.4 + E11.5 operations
 * pipelines. Mirrors the
 * <code>forbiddenPayloadKeys</code> enum declared in
 * <code>contracts/operations/entitlement-quota-billing-policy.yaml</code>
 * and
 * <code>contracts/operations/admin-support-operations-policy.yaml</code>.
 */
public final class E11ForbiddenPayloadKeys {

  public static final Set<String> KEYS = Set.of(
      "rawDna", "rawGenotype", "rawFastq", "rawBam", "rawVcf",
      "rawEmail", "rawPhone", "rawAddress", "exifGps", "cameraSerial",
      "passportNumber", "ssn", "productionPii", "internalVaultToken",
      "internalSessionCookie", "rawConsentReceipt", "rawSignatureBlob",
      "rawIdDocument", "treeViewerBypass", "rawEventPayload",
      "rawAuditStream", "rawWebhookSecret", "rawProviderApiKey",
      "dnaRawBucketKey", "dnaMatchBucketKey",
      "rawStripeApiKey", "rawLicenseFile");

  private E11ForbiddenPayloadKeys() {
    throw new UnsupportedOperationException("constants holder");
  }

  public static String firstViolation(java.util.Map<String, Object> payload) {
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