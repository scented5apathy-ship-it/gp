package com.genealogy.platform.services.importexport.gedcom;

/**
 * Closed-set mapping outcomes for the 5.5.1 → 7.0 mapper.
 * Mirrors <code>contracts/importexport/gedcom-parser-validator-policy.yaml</code>
 * <code>gedcomMappingOutcomes</code>.
 */
public enum GedcomMappingOutcome {
  MAPPED_NATIVE,
  MAPPED_TRANSFORMED,
  MAPPED_WITH_NOTES,
  MAPPED_WITH_PROVENANCE_PRESERVED,
  DROPPED_UNSUPPORTED,
  DROPPED_DNA_BUCKET,
  DROPPED_PII_PAYLOAD,
  FAILED_MAPPING;

  public String wire() {
    return name();
  }

  public static GedcomMappingOutcome fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("gedcomMappingOutcome MUST NOT be null");
    }
    try {
      return GedcomMappingOutcome.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "gedcomMappingOutcome MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}