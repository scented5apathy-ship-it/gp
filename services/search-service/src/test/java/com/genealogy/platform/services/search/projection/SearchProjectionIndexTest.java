package com.genealogy.platform.services.search.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SearchProjectionIndexTest {

  private SearchProjectionEvent event(String docId, SearchPrivacyClass privacyClass) {
    return new SearchProjectionEvent(
        SearchEventType.PERSON_CREATED,
        SearchDocumentKind.PERSON,
        docId,
        "tenant-1",
        privacyClass,
        "idem-" + docId,
        List.of("Alias One"),
        List.of("en"),
        "actor-1",
        "corr-1");
  }

  @Test
  void happyPathIndexesPublicDocument() {
    SearchProjectionDecision decision =
        SearchProjectionIndex.apply(event("person-1", SearchPrivacyClass.PUBLIC), new HashSet<>());
    assertEquals(SearchProjectionStatus.INDEXED, decision.status());
    assertNotNull(decision.newProjectionVersion());
    assertTrue(decision.newProjectionVersion().startsWith("v"));
  }

  @Test
  void privateDocumentIsRedacted() {
    SearchProjectionDecision decision =
        SearchProjectionIndex.apply(event("person-2", SearchPrivacyClass.PRIVATE), new HashSet<>());
    assertEquals(SearchProjectionStatus.REDACTED, decision.status());
    assertEquals(SearchProjectionRedactionReason.POLICY_DENY, decision.redactionReason());
  }

  @Test
  void dnaBucketDocumentIsRejected() {
    SearchProjectionDecision decision =
        SearchProjectionIndex.apply(event("dna/raw/person-1", SearchPrivacyClass.PUBLIC), new HashSet<>());
    assertEquals(SearchProjectionStatus.PURGED, decision.status());
    assertEquals(SearchFailureReason.PROJECTION_DNA_BUCKET_FORBIDDEN, decision.failureReason());
  }

  @Test
  void duplicateIdempotencyKeyIsRejected() {
    Set<String> seen = new HashSet<>();
    seen.add("idem-person-3");
    SearchProjectionDecision decision =
        SearchProjectionIndex.apply(event("person-3", SearchPrivacyClass.PUBLIC), seen);
    assertEquals(SearchProjectionStatus.PURGED, decision.status());
    assertEquals(SearchFailureReason.PROJECTION_IDEMPOTENCY_KEY_MISSING, decision.failureReason());
  }

  @Test
  void blankIdempotencyKeyRejectedAtConstruction() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SearchProjectionEvent(
                SearchEventType.PERSON_CREATED,
                SearchDocumentKind.PERSON,
                "person-4",
                "tenant-1",
                SearchPrivacyClass.PUBLIC,
                "",
                List.of(),
                List.of(),
                "actor-1",
                "corr-1"));
  }

  @Test
  void tooManyAliasesRejectedAtConstruction() {
    List<String> aliases = new java.util.ArrayList<>();
    for (int i = 0; i < SearchProjectionLimits.MAX_ALIAS_PER_DOCUMENT + 1; i += 1) {
      aliases.add("alias-" + i);
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SearchProjectionEvent(
                SearchEventType.PERSON_CREATED,
                SearchDocumentKind.PERSON,
                "person-5",
                "tenant-1",
                SearchPrivacyClass.PUBLIC,
                "idem-5",
                aliases,
                List.of("en"),
                "actor-1",
                "corr-1"));
  }
}