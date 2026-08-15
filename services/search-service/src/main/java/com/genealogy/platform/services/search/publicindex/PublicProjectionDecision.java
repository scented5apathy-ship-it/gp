package com.genealogy.platform.services.search.publicindex;

import java.util.Map;

/**
 * Output of {@link PublicProjectionIndex#apply}. Compact constructor
 * pins the invariant shape:
 * <ul>
 *   <li><code>INDEXED</code> decisions MUST carry a non-blank
 *       <code>publicRowId</code> and <code>canonicalUrl</code>.</li>
 *   <li><code>REDACTED</code> decisions MUST carry a
 *       {@link PublicProjectionRedactionReason}.</li>
 *   <li><code>PURGED</code> decisions MUST carry a
 *       {@link PublicProjectionFailureReason}.</li>
 *   <li><code>PENDING</code> decisions MUST carry no reason.</li>
 * </ul>
 */
public record PublicProjectionDecision(
    PublicProjectionLifecycleStatus status,
    String publicRowId,
    String canonicalUrl,
    PublicProjectionRedactionReason redactionReason,
    PublicProjectionFailureReason failureReason,
    Map<String, String> facts) {

  public PublicProjectionDecision {
    if (status == null) {
      throw new IllegalArgumentException("status MUST NOT be null");
    }
    if (facts == null) {
      facts = Map.of();
    } else {
      facts = Map.copyOf(facts);
    }
    switch (status) {
      case PENDING -> {
        if (redactionReason != null || failureReason != null) {
          throw new IllegalArgumentException(
              "PENDING decision MUST NOT carry a redaction or failure reason");
        }
      }
      case REDACTED -> {
        if (redactionReason == null) {
          throw new IllegalArgumentException(
              "REDACTED decision MUST declare a redactionReason");
        }
      }
      case INDEXED -> {
        if (publicRowId == null || publicRowId.isBlank()) {
          throw new IllegalArgumentException(
              "INDEXED decision MUST carry a non-blank publicRowId");
        }
        if (canonicalUrl == null || canonicalUrl.isBlank()) {
          throw new IllegalArgumentException(
              "INDEXED decision MUST carry a non-blank canonicalUrl");
        }
      }
      case STALE -> {
        // STALE may carry either reason; no constraint.
      }
      case PURGED -> {
        if (failureReason == null) {
          throw new IllegalArgumentException(
              "PURGED decision MUST declare a failureReason");
        }
      }
      default -> {
        // exhaustive switch over the closed-set lifecycle status.
      }
    }
  }

  public static PublicProjectionDecision pending() {
    return new PublicProjectionDecision(
        PublicProjectionLifecycleStatus.PENDING,
        null,
        null,
        null,
        null,
        Map.of());
  }

  public static PublicProjectionDecision redacted(
      PublicProjectionRedactionReason reason) {
    return new PublicProjectionDecision(
        PublicProjectionLifecycleStatus.REDACTED,
        null,
        null,
        reason,
        null,
        Map.of());
  }

  public static PublicProjectionDecision indexed(String publicRowId, String canonicalUrl) {
    return new PublicProjectionDecision(
        PublicProjectionLifecycleStatus.INDEXED,
        publicRowId,
        canonicalUrl,
        null,
        null,
        Map.of());
  }

  public static PublicProjectionDecision purged(PublicProjectionFailureReason reason) {
    return new PublicProjectionDecision(
        PublicProjectionLifecycleStatus.PURGED,
        null,
        null,
        null,
        reason,
        Map.of());
  }
}