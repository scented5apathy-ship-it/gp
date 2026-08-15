package com.genealogy.platform.services.search.authorized;

import com.genealogy.platform.services.search.shared.SearchLimits;
import java.util.Map;

/**
 * Pure verdict returned by {@link SearchOpenFgaPort}. Compact
 * constructor pins the invariant shape:
 * <ul>
 *   <li><code>DENY</code> MUST carry a non-blank reason.</li>
 *   <li><code>ALLOW</code> MUST NOT carry a reason.</li>
 * </ul>
 */
public record SearchOpenFgaVerdict(
    SearchOpenFgaOutcome outcome, String reason, Map<String, String> facts) {

  public SearchOpenFgaVerdict {
    if (outcome == null) {
      throw new IllegalArgumentException("outcome MUST NOT be null");
    }
    if (facts == null) {
      facts = Map.of();
    } else {
      facts = Map.copyOf(facts);
    }
    if (outcome == SearchOpenFgaOutcome.DENY) {
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("DENY verdict MUST carry a reason");
      }
      if (reason.length() > SearchLimits.CORRELATION_ID_LENGTH) {
        throw new IllegalArgumentException(
            "reason length MUST be <= "
                + SearchLimits.CORRELATION_ID_LENGTH
                + " (got "
                + reason.length()
                + ")");
      }
    } else if (reason != null && !reason.isBlank()) {
      throw new IllegalArgumentException("ALLOW verdict MUST NOT carry a reason");
    }
  }

  public static SearchOpenFgaVerdict allow() {
    return new SearchOpenFgaVerdict(SearchOpenFgaOutcome.ALLOW, null, Map.of());
  }

  public static SearchOpenFgaVerdict deny(String reason) {
    return new SearchOpenFgaVerdict(SearchOpenFgaOutcome.DENY, reason, Map.of());
  }
}