package com.genealogy.platform.services.dna.shared;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Forbidden payload patterns the DNA pipeline MUST refuse.
 * Mirrors <code>forbiddenPayloadPatterns</code> across E10.2 — E10.5.
 * The patterns are matched case-insensitively against keys inside an
 * opaque payload map; the map itself MUST only carry opaque
 * aggregate references (per ADR-E0.5-08 + design.md §5.1). DNA
 * payloads MUST additionally reject <code>treeViewerBypass</code>
 * keys (E10.2 tree-role-isolation guard).
 */
public final class DnaForbiddenPayloadKeys {

  private static final Set<String> KEYS = Set.of(
      "rawDnaSequence",
      "rawFastq",
      "rawBam",
      "rawVcf",
      "exifGps",
      "cameraSerial",
      "passportNumber",
      "socialSecurityNumber",
      "rawSocialSecurityNumber",
      "rawPassport",
      "rawDriverLicense",
      "rawTaxId",
      "nameOnBirth",
      "rawEmail",
      "rawPhone",
      "rawAddress",
      "biometricTemplate",
      "rawFacialEmbedding",
      "rawLivingStatus",
      "rawMinorStatus",
      "rawConsentDocument",
      "rawMedicalRecord",
      "rawPaymentInstrument",
      "productionPii",
      "treeViewerBypass");

  private static final Pattern NON_OPAQUE_AGGREGATE_ID =
      Pattern.compile("^[a-z][a-z0-9-]{0,62}$");

  private DnaForbiddenPayloadKeys() {
    throw new UnsupportedOperationException("constants holder");
  }

  /**
   * Returns the first forbidden key found inside the payload, or
   * <code>null</code> if the payload is clean.
   */
  public static String firstViolation(Map<String, Object> payload) {
    if (payload == null) {
      return null;
    }
    for (String key : payload.keySet()) {
      if (key == null || key.isBlank()) {
        return "blank-key";
      }
      if (KEYS.contains(key)) {
        return key;
      }
      if (!NON_OPAQUE_AGGREGATE_ID.matcher(key).matches()) {
        return "non-opaque-aggregate-id";
      }
    }
    return null;
  }

  public static boolean isClean(Map<String, Object> payload) {
    return firstViolation(payload) == null;
  }
}