package com.genealogy.platform.services.search.publicindex;

import com.genealogy.platform.services.search.shared.SearchLimits;

/**
 * Pure deterministic orchestrator for the E8.3 public projection
 * redactor.
 *
 * <p>The pipeline:
 * <ol>
 *   <li>If the document key matches a DNA bucket prefix, refuse
 *       with {@link PublicProjectionFailureReason#PUBLIC_PROJECTION_DNA_BUCKET_FORBIDDEN}.</li>
 *   <li>If visibility is not {@link PublicProjectionVisibility#PUBLIC},
 *       mark as {@code REDACTED} with reason
 *       {@link PublicProjectionRedactionReason#VISIBILITY_NOT_PUBLIC}.</li>
 *   <li>If the subject is living, redact with
 *       {@link PublicProjectionRedactionReason#LIVING}.</li>
 *   <li>If the subject is minor, redact with
 *       {@link PublicProjectionRedactionReason#MINOR}.</li>
 *   <li>If DNA is attached, redact with
 *       {@link PublicProjectionRedactionReason#DNA_ATTACHED}.</li>
 *   <li>If consent is missing / revoked, redact with
 *       {@link PublicProjectionRedactionReason#CONSENT_MISSING} /
 *       {@link PublicProjectionRedactionReason#CONSENT_REVOKED}.</li>
 *   <li>Otherwise index the row with a deterministic public row id
 *       + canonical URL.</li>
 * </ol>
 */
public final class PublicProjectionIndex {

  private PublicProjectionIndex() {}

  public static PublicProjectionDecision apply(PublicProjectionRow row, String canonicalHost) {
    if (row == null) {
      throw new IllegalArgumentException("row MUST NOT be null");
    }
    if (canonicalHost == null || canonicalHost.isBlank()) {
      throw new IllegalArgumentException("canonicalHost MUST NOT be blank");
    }
    if (isDnaBucketKey(row.documentId())) {
      return PublicProjectionDecision.purged(
          PublicProjectionFailureReason.PUBLIC_PROJECTION_DNA_BUCKET_FORBIDDEN);
    }
    if (row.visibility() != PublicProjectionVisibility.PUBLIC) {
      return PublicProjectionDecision.redacted(
          PublicProjectionRedactionReason.VISIBILITY_NOT_PUBLIC);
    }
    if (row.living()) {
      return PublicProjectionDecision.redacted(PublicProjectionRedactionReason.LIVING);
    }
    if (row.minor()) {
      return PublicProjectionDecision.redacted(PublicProjectionRedactionReason.MINOR);
    }
    if (row.dnaAttached()) {
      return PublicProjectionDecision.redacted(PublicProjectionRedactionReason.DNA_ATTACHED);
    }
    if (!row.consentValid()) {
      return PublicProjectionDecision.redacted(PublicProjectionRedactionReason.CONSENT_MISSING);
    }
    String canonicalUrl = canonicalHost + "/public/" + row.tenantScopeId() + "/" + row.documentId();
    return PublicProjectionDecision.indexed(
        "pub-" + Math.abs((row.tenantScopeId() + ":" + row.documentId()).hashCode()),
        canonicalUrl);
  }

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
}