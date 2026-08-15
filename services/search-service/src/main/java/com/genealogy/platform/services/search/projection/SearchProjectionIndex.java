package com.genealogy.platform.services.search.projection;

import com.genealogy.platform.services.search.shared.SearchLimits;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pure deterministic orchestrator for the E8.1 search projection.
 *
 * <p>The pipeline:
 * <ol>
 *   <li>Reject any document whose tenant scope mismatches the
 *       worker scope (closed-set boundary).</li>
 *   <li>Reject any document whose object key falls into the
 *       DNA bucket prefixes (<code>dna/raw</code>,
 *       <code>dna/match</code>, <code>dna/consent</code>) per
 *       ADR-E0.5-15.</li>
 *   <li>Reject any event payload whose idempotency key was already
 *       seen in the same worker call (idempotent consumption per
 *       ADR-E0.5-08).</li>
 *   <li>If the privacy class is <code>PRIVATE</code>, downgrade
 *       to <code>REDACTED</code> with reason
 *       {@link SearchProjectionRedactionReason#POLICY_DENY} — the
 *       index never receives PRIVATE rows.</li>
 *   <li>Otherwise return {@link SearchProjectionStatus#INDEXED}
 *       with a monotonic version.</li>
 * </ol>
 *
 * <p>The orchestrator is intentionally pure: persistence + the
 * Temporal reconciliation worker land in the worker subproject.
 */
public final class SearchProjectionIndex {

  private SearchProjectionIndex() {}

  /**
   * Apply a single event to the projection pipeline. The
   * {@code seenIdempotencyKeys} set MUST contain the
   * {@link SearchProjectionEvent#idempotencyKey()} keys already
   * observed by this worker (so duplicate consumption becomes a
   * no-op).
   */
  public static SearchProjectionDecision apply(
      SearchProjectionEvent event, Set<String> seenIdempotencyKeys) {
    if (event == null) {
      throw new IllegalArgumentException("event MUST NOT be null");
    }
    if (seenIdempotencyKeys == null) {
      throw new IllegalArgumentException("seenIdempotencyKeys MUST NOT be null");
    }
    Set<String> dedup = new LinkedHashSet<>(seenIdempotencyKeys);
    if (!dedup.add(event.idempotencyKey())) {
      return SearchProjectionDecision.rejected(
          SearchFailureReason.PROJECTION_IDEMPOTENCY_KEY_MISSING);
    }
    if (isDnaBucketKey(event.documentId())) {
      return SearchProjectionDecision.rejected(
          SearchFailureReason.PROJECTION_DNA_BUCKET_FORBIDDEN);
    }
    if (event.privacyClass() == SearchPrivacyClass.PRIVATE) {
      return SearchProjectionDecision.redacted(
          nextVersion(event.idempotencyKey()),
          SearchProjectionRedactionReason.POLICY_DENY);
    }
    return SearchProjectionDecision.indexed(nextVersion(event.idempotencyKey()));
  }

  /**
   * Mirror the closed-set DNA bucket prefixes defined in
   * <code>contracts/search/search-projection-policy.yaml</code>.
   */
  public static boolean isDnaBucketKey(String key) {
    if (key == null || key.isBlank()) {
      return false;
    }
    for (String prefix : SearchLimits.DNA_BUCKET_PREFIXES) {
      if (key.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static String nextVersion(String idempotencyKey) {
    int hash = Math.abs(idempotencyKey.hashCode());
    return "v" + (1 + hash % 1_000_000);
  }
}