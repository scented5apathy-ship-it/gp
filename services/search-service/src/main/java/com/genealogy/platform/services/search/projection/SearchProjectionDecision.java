package com.genealogy.platform.services.search.projection;

import com.genealogy.platform.services.search.shared.SearchLimits;
import java.util.Map;

/**
 * Immutable decision returned by
 * {@link SearchProjectionIndex#apply(SearchProjectionEvent)}.
 * Compact constructor pins the invariant shape:
 * <ul>
 *   <li><code>ALLOWED</code> requires non-blank newProjectionVersion.</li>
 *   <li><code>REDACTED</code> requires both newProjectionVersion and a redactionReason.</li>
 *   <li><code>REJECTED</code> requires a non-null failureReason.</li>
 * </ul>
 */
public record SearchProjectionDecision(
    SearchProjectionStatus status,
    String newProjectionVersion,
    SearchFailureReason failureReason,
    SearchProjectionRedactionReason redactionReason,
    Map<String, String> facts) {

  public SearchProjectionDecision {
    if (status == null) {
      throw new IllegalArgumentException("status MUST NOT be null");
    }
    if (facts == null) {
      facts = Map.of();
    } else {
      facts = Map.copyOf(facts);
    }
    switch (status) {
      case INDEXED -> requireNonBlank(newProjectionVersion, "newProjectionVersion");
      case REDACTED -> {
        requireNonBlank(newProjectionVersion, "newProjectionVersion");
        if (redactionReason == null) {
          throw new IllegalArgumentException(
              "REDACTED decision MUST declare a redactionReason");
        }
      }
      case PURGED -> {
        if (failureReason == null) {
          throw new IllegalArgumentException(
              "PURGED decision MUST declare a failureReason");
        }
      }
      default -> {
        if (failureReason == null) {
          throw new IllegalArgumentException(
              status + " decision MUST declare a failureReason");
        }
      }
    }
  }

  public static SearchProjectionDecision indexed(String version) {
    return new SearchProjectionDecision(
        SearchProjectionStatus.INDEXED, version, null, null, Map.of());
  }

  public static SearchProjectionDecision redacted(
      String version, SearchProjectionRedactionReason reason) {
    return new SearchProjectionDecision(
        SearchProjectionStatus.REDACTED, version, null, reason, Map.of());
  }

  public static SearchProjectionDecision rejected(SearchFailureReason reason) {
    return new SearchProjectionDecision(
        SearchProjectionStatus.PURGED, null, reason, null, Map.of());
  }

  private static void requireNonBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " MUST NOT be blank");
    }
    if (value.length() > SearchLimits.PROJECTION_DOCUMENT_ID_LENGTH) {
      throw new IllegalArgumentException(
          field
              + " length MUST be <= "
              + SearchLimits.PROJECTION_DOCUMENT_ID_LENGTH
              + " (got "
              + value.length()
              + ")");
    }
  }
}