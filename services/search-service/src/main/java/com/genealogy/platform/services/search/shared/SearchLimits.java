package com.genealogy.platform.services.search.shared;

/**
 * String-length caps shared across the E8 search packages
 * (E8.1 projection, E8.2 authorized, E8.3 public, E8.4 benchmark).
 * Pinned by the linter scripts under <code>scripts/</code>.
 */
public final class SearchLimits {
  private SearchLimits() {}

  public static final int PROJECTION_DOCUMENT_ID_LENGTH = 64;
  public static final int TENANT_SCOPE_ID_LENGTH = 64;
  public static final int ACTOR_PSEUDO_ID_LENGTH = 64;
  public static final int CORRELATION_ID_LENGTH = 128;
  public static final int IDEMPOTENCY_KEY_LENGTH = 128;
  public static final int MAX_QUERY_LENGTH = 512;
  public static final int MAX_BCP47_TAG_LENGTH = 64;

  /** Reject any object key whose first segment matches one of these. */
  public static final String[] DNA_BUCKET_PREFIXES = { "dna/raw", "dna/match", "dna/consent" };

  /** Hard invariant: DNA bucket access is forbidden end-to-end. */
  public static final String DNA_BUCKET_ACCESS = "FORBIDDEN";
}