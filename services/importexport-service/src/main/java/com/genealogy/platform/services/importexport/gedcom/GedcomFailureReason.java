package com.genealogy.platform.services.importexport.gedcom;

/**
 * Closed-set failure reasons emitted by the GEDCOM parser.
 * Mirrors <code>contracts/importexport/gedcom-parser-validator-policy.yaml</code>
 * <code>gedcomFailureReasons</code>.
 */
public enum GedcomFailureReason {
  GEDCOM_PAYLOAD_TOO_LARGE,
  GEDCOM_LINE_LENGTH_EXCEEDED,
  GEDCOM_DEPTH_EXCEEDED,
  GEDCOM_RECORD_COUNT_EXCEEDED,
  GEDCOM_TAG_COUNT_EXCEEDED,
  GEDCOM_ENCODING_INVALID,
  GEDCOM_LINE_ENDING_INVALID,
  GEDCOM_BOM_INVALID,
  GEDCOM_EXTENSION_BLOCKED,
  GEDCOM_REFERENCE_UNRESOLVED,
  GEDCOM_DATE_INVALID,
  GEDCOM_PLACE_INVALID,
  GEDCOM_NAME_INVALID,
  GEDCOM_PROVENANCE_MISSING,
  GEDCOM_MAPPING_FAILED,
  GEDCOM_DNA_BUCKET_FORBIDDEN,
  GEDCOM_PII_LEAK_DETECTED,
  GEDCOM_DRY_RUN_ONLY_OK,
  GEDCOM_PAYLOAD_ENCRYPTED_UNSUPPORTED,
  GEDCOM_COMPRESSION_UNSUPPORTED;

  public String wire() {
    return name();
  }

  public static GedcomFailureReason fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("gedcomFailureReason MUST NOT be null");
    }
    try {
      return GedcomFailureReason.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "gedcomFailureReason MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}