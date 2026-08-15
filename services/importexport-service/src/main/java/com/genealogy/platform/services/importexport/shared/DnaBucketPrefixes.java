package com.genealogy.platform.services.importexport.shared;

/**
 * Closed-set bucket prefixes the import/export pipeline MUST refuse to
 * touch. Mirrors <code>guardRails.dnaBucketPrefixes</code> across all
 * E9.1 — E9.6 contracts.
 */
public final class DnaBucketPrefixes {

  public static final String RAW = "dna/raw";
  public static final String MATCH = "dna/match";
  public static final String CONSENT = "dna/consent";

  public static final java.util.List<String> ALL =
      java.util.List.of(RAW, MATCH, CONSENT);

  private DnaBucketPrefixes() {
    throw new UnsupportedOperationException("constants holder");
  }

  public static boolean isForbidden(String prefix) {
    if (prefix == null) {
      return false;
    }
    for (String p : ALL) {
      if (p.equals(prefix)) {
        return true;
      }
    }
    return false;
  }
}