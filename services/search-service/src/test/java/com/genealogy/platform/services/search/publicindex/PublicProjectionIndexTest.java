package com.genealogy.platform.services.search.publicindex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PublicProjectionIndexTest {

  private static final String CANONICAL = "https://example.com";

  private PublicProjectionRow row(
      String docId,
      PublicProjectionVisibility visibility,
      boolean living,
      boolean minor,
      boolean dna,
      boolean consent) {
    return new PublicProjectionRow(docId, "tenant-1", visibility, living, minor, dna, consent);
  }

  @Test
  void happyPathIndexesPublicRow() {
    PublicProjectionDecision decision =
        PublicProjectionIndex.apply(
            row("person-1", PublicProjectionVisibility.PUBLIC, false, false, false, true),
            CANONICAL);
    assertEquals(PublicProjectionLifecycleStatus.INDEXED, decision.status());
    assertNotNull(decision.publicRowId());
    assertTrue(decision.canonicalUrl().startsWith(CANONICAL));
  }

  @Test
  void unlistedRowRedactedWithVisibilityNotPublic() {
    PublicProjectionDecision decision =
        PublicProjectionIndex.apply(
            row("person-2", PublicProjectionVisibility.UNLISTED, false, false, false, true),
            CANONICAL);
    assertEquals(PublicProjectionLifecycleStatus.REDACTED, decision.status());
    assertEquals(
        PublicProjectionRedactionReason.VISIBILITY_NOT_PUBLIC, decision.redactionReason());
  }

  @Test
  void livingSubjectRedactedWithLivingReason() {
    PublicProjectionDecision decision =
        PublicProjectionIndex.apply(
            row("person-3", PublicProjectionVisibility.PUBLIC, true, false, false, true),
            CANONICAL);
    assertEquals(PublicProjectionLifecycleStatus.REDACTED, decision.status());
    assertEquals(PublicProjectionRedactionReason.LIVING, decision.redactionReason());
  }

  @Test
  void minorSubjectRedactedWithMinorReason() {
    PublicProjectionDecision decision =
        PublicProjectionIndex.apply(
            row("person-4", PublicProjectionVisibility.PUBLIC, false, true, false, true),
            CANONICAL);
    assertEquals(PublicProjectionLifecycleStatus.REDACTED, decision.status());
    assertEquals(PublicProjectionRedactionReason.MINOR, decision.redactionReason());
  }

  @Test
  void dnaAttachedRedactedWithDnaReason() {
    PublicProjectionDecision decision =
        PublicProjectionIndex.apply(
            row("person-5", PublicProjectionVisibility.PUBLIC, false, false, true, true),
            CANONICAL);
    assertEquals(PublicProjectionLifecycleStatus.REDACTED, decision.status());
    assertEquals(PublicProjectionRedactionReason.DNA_ATTACHED, decision.redactionReason());
  }

  @Test
  void missingConsentRedactedWithConsentReason() {
    PublicProjectionDecision decision =
        PublicProjectionIndex.apply(
            row("person-6", PublicProjectionVisibility.PUBLIC, false, false, false, false),
            CANONICAL);
    assertEquals(PublicProjectionLifecycleStatus.REDACTED, decision.status());
    assertEquals(PublicProjectionRedactionReason.CONSENT_MISSING, decision.redactionReason());
  }

  @Test
  void dnaBucketKeyPurged() {
    PublicProjectionDecision decision =
        PublicProjectionIndex.apply(
            row("dna/raw/person-7", PublicProjectionVisibility.PUBLIC, false, false, false, true),
            CANONICAL);
    assertEquals(PublicProjectionLifecycleStatus.PURGED, decision.status());
    assertEquals(
        PublicProjectionFailureReason.PUBLIC_PROJECTION_DNA_BUCKET_FORBIDDEN,
        decision.failureReason());
  }

  @Test
  void blankCanonicalHostRejected() {
    try {
      PublicProjectionIndex.apply(
          row("person-8", PublicProjectionVisibility.PUBLIC, false, false, false, true), "");
      org.junit.jupiter.api.Assertions.fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertNotNull(expected.getMessage());
    }
  }
}