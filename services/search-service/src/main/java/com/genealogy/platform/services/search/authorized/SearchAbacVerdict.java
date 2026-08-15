package com.genealogy.platform.services.search.authorized;

/**
 * Pure ABAC overlay verdict. Mirrors the closed-set
 * {@link SearchAuthorizationOutcome} (excluding OpenFGA / version
 * outcomes that the ABAC layer does not emit).
 */
public record SearchAbacVerdict(SearchAuthorizationOutcome outcome) {

  public SearchAbacVerdict {
    if (outcome == null) {
      throw new IllegalArgumentException("outcome MUST NOT be null");
    }
    if (outcome == SearchAuthorizationOutcome.OPENFGA_DENY
        || outcome == SearchAuthorizationOutcome.PERMISSION_VERSION_STALE
        || outcome == SearchAuthorizationOutcome.TENANT_MISMATCH
        || outcome == SearchAuthorizationOutcome.REJECTED) {
      throw new IllegalArgumentException(
          "SearchAbacVerdict MUST NOT emit "
              + outcome
              + " (the ABAC layer only emits living / minor / DNA / consent / contextual / ALLOWED)");
    }
  }

  public static SearchAbacVerdict allow() {
    return new SearchAbacVerdict(SearchAuthorizationOutcome.ALLOWED);
  }
}