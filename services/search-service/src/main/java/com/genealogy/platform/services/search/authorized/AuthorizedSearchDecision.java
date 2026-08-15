package com.genealogy.platform.services.search.authorized;

import java.util.List;
import java.util.Map;

/**
 * Output of the authorized-search orchestrator. Compact constructor
 * pins the invariant shape:
 * <ul>
 *   <li><code>ALLOWED</code> decisions MUST carry a non-empty
 *       {@code cursorOpaque} token + a list of hits.</li>
 *   <li>Any other outcome MUST carry a non-null failure reason and
 *       an empty hit list.</li>
 * </ul>
 */
public record AuthorizedSearchDecision(
    SearchAuthorizationOutcome outcome,
    List<String> hits,
    String cursorOpaque,
    AuthorizedSearchFailureReason failureReason,
    Map<String, String> facts) {

  public AuthorizedSearchDecision {
    if (outcome == null) {
      throw new IllegalArgumentException("outcome MUST NOT be null");
    }
    if (hits == null) {
      throw new IllegalArgumentException("hits MUST NOT be null");
    }
    if (facts == null) {
      facts = Map.of();
    } else {
      facts = Map.copyOf(facts);
    }
    if (outcome == SearchAuthorizationOutcome.ALLOWED) {
      if (cursorOpaque == null || cursorOpaque.isBlank()) {
        throw new IllegalArgumentException(
            "ALLOWED decision MUST carry a non-blank cursorOpaque token");
      }
    } else {
      if (failureReason == null) {
        throw new IllegalArgumentException(
            outcome + " decision MUST declare a failureReason");
      }
      if (!hits.isEmpty()) {
        throw new IllegalArgumentException(
            outcome + " decision MUST have an empty hit list");
      }
    }
  }

  public static AuthorizedSearchDecision allowed(
      List<String> hits, String cursorOpaque) {
    return new AuthorizedSearchDecision(
        SearchAuthorizationOutcome.ALLOWED,
        List.copyOf(hits),
        cursorOpaque,
        null,
        Map.of());
  }

  public static AuthorizedSearchDecision denied(
      SearchAuthorizationOutcome outcome, AuthorizedSearchFailureReason reason) {
    if (outcome == SearchAuthorizationOutcome.ALLOWED) {
      throw new IllegalArgumentException(
          "denied() MUST NOT produce an ALLOWED outcome; use allowed() instead");
    }
    return new AuthorizedSearchDecision(outcome, List.of(), null, reason, Map.of());
  }
}