package com.genealogy.platform.services.importexport.saga;

/**
 * Closed-set dedup outcomes emitted per record candidate.
 * Mirrors <code>contracts/importexport/import-saga-policy.yaml</code>
 * <code>importDedupOutcomes</code>.
 */
public enum ImportDedupOutcome {
  NEW,
  DUPLICATE_AUTO_MERGE,
  DUPLICATE_CANDIDATE,
  DUPLICATE_USER_MERGED,
  DUPLICATE_REJECTED,
  DRY_RUN_REPORT_ONLY;

  public String wire() {
    return name();
  }

  public static ImportDedupOutcome fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("importDedupOutcome MUST NOT be null");
    }
    try {
      return ImportDedupOutcome.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "importDedupOutcome MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}