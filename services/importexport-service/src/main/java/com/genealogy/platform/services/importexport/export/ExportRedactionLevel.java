package com.genealogy.platform.services.importexport.export;

/**
 * Closed-set redaction levels the privacy-aware export pipeline
 * supports. Mirrors
 * <code>contracts/importexport/privacy-export-policy.yaml</code>
 * <code>exportRedactionLevels</code>.
 */
public enum ExportRedactionLevel {
  NONE,
  LIVING_ONLY,
  MINOR_ONLY,
  LIVING_AND_MINOR,
  SENSITIVE_FULL,
  DNA_DEFAULT_OFF,
  CONSENT_REQUIRED;

  public String wire() {
    return name();
  }

  public static ExportRedactionLevel fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("exportRedactionLevel MUST NOT be null");
    }
    try {
      return ExportRedactionLevel.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "exportRedactionLevel MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }

  public boolean isDnaAllowed() {
    return this == SENSITIVE_FULL || this == CONSENT_REQUIRED;
  }
}