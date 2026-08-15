package com.genealogy.platform.services.importexport.gedcom;

/**
 * Closed-set validation statuses emitted per GEDCOM record.
 * Mirrors <code>contracts/importexport/gedcom-parser-validator-policy.yaml</code>
 * <code>gedcomValidationStatuses</code>.
 */
public enum GedcomValidationStatus {
  VALID,
  RECOVERABLE,
  INVALID_STRUCTURE,
  INVALID_ENCODING,
  INVALID_LINE_LENGTH,
  INVALID_DEPTH,
  INVALID_TAG,
  INVALID_REFERENCE,
  INVALID_DATE,
  INVALID_NAME,
  INVALID_PLACE,
  INVALID_PROVENANCE;

  public String wire() {
    return name();
  }

  public static GedcomValidationStatus fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("gedcomValidationStatus MUST NOT be null");
    }
    try {
      return GedcomValidationStatus.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "gedcomValidationStatus MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}