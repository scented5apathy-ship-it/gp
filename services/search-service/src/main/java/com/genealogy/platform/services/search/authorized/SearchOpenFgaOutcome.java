package com.genealogy.platform.services.search.authorized;

/**
 * Closed-set OpenFGA verdict (per ADR-E0.5-06). Mirrors the
 * <code>searchOpenFgaOutcome</code> enum in
 * <code>contracts/search/authorized-search-policy.yaml</code>
 * (kept minimal so the orchestrator can stay deterministic).
 */
public enum SearchOpenFgaOutcome {
  ALLOW,
  DENY;

  public String wire() {
    return name();
  }

  public static SearchOpenFgaOutcome fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("searchOpenFgaOutcome MUST NOT be null");
    }
    try {
      return SearchOpenFgaOutcome.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "searchOpenFgaOutcome MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}