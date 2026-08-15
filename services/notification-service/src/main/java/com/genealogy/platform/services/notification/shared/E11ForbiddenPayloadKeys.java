package com.genealogy.platform.services.notification.shared;

import java.util.Set;

/**
 * Forbidden payload key catalogue for E11.1 — E11.5. Mirrors the
 * <code>forbiddenPayloadKeys</code> enum declared in every E11.x
 * contract. <code>firstViolation</code> returns the first matched key
 * (in insertion order) or <code>null</code> if the payload is clean.
 */
public final class E11ForbiddenPayloadKeys {

  public static final Set<String> KEYS = Set.of(
      "rawDna",
      "rawGenotype",
      "rawFastq",
      "rawBam",
      "rawVcf",
      "rawEmail",
      "rawPhone",
      "rawAddress",
      "exifGps",
      "cameraSerial",
      "passportNumber",
      "ssn",
      "productionPii",
      "internalVaultToken",
      "internalSessionCookie",
      "rawConsentReceipt",
      "rawSignatureBlob",
      "rawIdDocument",
      "treeViewerBypass",
      "rawEventPayload",
      "rawAuditStream",
      "rawWebhookSecret",
      "rawProviderApiKey",
      "dnaRawBucketKey",
      "dnaMatchBucketKey",
      "rawGuardianReason",
      "rawSupportReason",
      "rawDeletionReason",
      "rawStripeApiKey",
      "rawLicenseFile");

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