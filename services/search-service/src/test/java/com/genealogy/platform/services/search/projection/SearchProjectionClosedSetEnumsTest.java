package com.genealogy.platform.services.search.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SearchProjectionClosedSetEnumsTest {

  @Test
  void searchDocumentKindFromWireCoversAllValues() {
    for (SearchDocumentKind kind : SearchDocumentKind.values()) {
      assertEquals(kind, SearchDocumentKind.fromWire(kind.wire()));
    }
    assertEquals(7, SearchDocumentKind.values().length);
    assertThrows(IllegalArgumentException.class, () -> SearchDocumentKind.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> SearchDocumentKind.fromWire("BOGUS"));
  }

  @Test
  void searchPrivacyClassFromWireCoversAllValues() {
    for (SearchPrivacyClass klass : SearchPrivacyClass.values()) {
      assertEquals(klass, SearchPrivacyClass.fromWire(klass.wire()));
    }
    assertEquals(5, SearchPrivacyClass.values().length);
    assertThrows(IllegalArgumentException.class, () -> SearchPrivacyClass.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> SearchPrivacyClass.fromWire("BOGUS"));
  }

  @Test
  void searchProjectionStatusFromWireCoversAllValues() {
    for (SearchProjectionStatus status : SearchProjectionStatus.values()) {
      assertEquals(status, SearchProjectionStatus.fromWire(status.wire()));
    }
    assertEquals(5, SearchProjectionStatus.values().length);
    assertThrows(IllegalArgumentException.class, () -> SearchProjectionStatus.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> SearchProjectionStatus.fromWire("BOGUS"));
  }

  @Test
  void searchEventTypeFromWireCoversAllValues() {
    for (SearchEventType type : SearchEventType.values()) {
      assertEquals(type, SearchEventType.fromWire(type.wire()));
    }
    assertEquals(20, SearchEventType.values().length);
    assertThrows(IllegalArgumentException.class, () -> SearchEventType.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> SearchEventType.fromWire("BOGUS"));
  }

  @Test
  void searchFailureReasonFromWireCoversAllValues() {
    for (SearchFailureReason reason : SearchFailureReason.values()) {
      assertEquals(reason, SearchFailureReason.fromWire(reason.wire()));
    }
    assertEquals(23, SearchFailureReason.values().length);
    assertThrows(IllegalArgumentException.class, () -> SearchFailureReason.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> SearchFailureReason.fromWire("BOGUS"));
  }

  @Test
  void searchAuditEventFromWireCoversAllValues() {
    for (SearchAuditEvent event : SearchAuditEvent.values()) {
      assertEquals(event, SearchAuditEvent.fromWire(event.wire()));
    }
    assertEquals(18, SearchAuditEvent.values().length);
    assertThrows(IllegalArgumentException.class, () -> SearchAuditEvent.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> SearchAuditEvent.fromWire("BOGUS"));
  }

  @Test
  void searchProjectionRedactionReasonFromWireCoversAllValues() {
    for (SearchProjectionRedactionReason reason : SearchProjectionRedactionReason.values()) {
      assertEquals(reason, SearchProjectionRedactionReason.fromWire(reason.wire()));
    }
    assertEquals(7, SearchProjectionRedactionReason.values().length);
    assertThrows(
        IllegalArgumentException.class, () -> SearchProjectionRedactionReason.fromWire(null));
    assertThrows(
        IllegalArgumentException.class, () -> SearchProjectionRedactionReason.fromWire("BOGUS"));
  }

  @Test
  void numericLimitsPinContractValues() {
    assertEquals(256, SearchProjectionLimits.MAX_NAME_LENGTH);
    assertEquals(256, SearchProjectionLimits.MAX_ALIAS_LENGTH);
    assertEquals(64, SearchProjectionLimits.MAX_ALIAS_PER_DOCUMENT);
    assertEquals(16, SearchProjectionLimits.MAX_LANGUAGES_PER_DOCUMENT);
    assertEquals(64, SearchProjectionLimits.MAX_BCP47_TAG_LENGTH);
    assertEquals(1024, SearchProjectionLimits.MAX_PROJECTION_BATCH_SIZE);
    assertEquals(256, SearchProjectionLimits.MAX_OUTBOX_BATCH_SIZE);
    assertEquals(24, SearchProjectionLimits.PROJECTION_LAG_P95_BUDGET_SECONDS);
    assertEquals(120, SearchProjectionLimits.PROJECTION_LAG_P99_BUDGET_SECONDS);
    assertEquals(300, SearchProjectionLimits.PROJECTION_LAG_BREACH_SECONDS);
    assertEquals(5, SearchProjectionLimits.PROJECTION_LAG_HEARTBEAT_SECONDS);
    assertEquals(4096, SearchProjectionLimits.BACKFILL_BATCH_SIZE);
    assertEquals(1800, SearchProjectionLimits.BACKFILL_TIMEOUT_SECONDS);
    assertEquals(30, SearchProjectionLimits.BACKFILL_HEARTBEAT_SECONDS);
    assertEquals(168, SearchProjectionLimits.BACKFILL_LOOKBACK_HOURS);
    assertEquals(24, SearchProjectionLimits.BACKFILL_CADENCE_HOURS);
    assertEquals(600, SearchProjectionLimits.RECONCILIATION_P95_BUDGET_SECONDS);
    assertEquals(60, SearchProjectionLimits.RECONCILIATION_HEARTBEAT_SECONDS);
  }

  @Test
  void isDnaBucketKeyRecognisesAllClosedSetPrefixes() {
    assertTrue(SearchProjectionIndex.isDnaBucketKey("dna/raw/person-1"));
    assertTrue(SearchProjectionIndex.isDnaBucketKey("dna/match/sample-1"));
    assertTrue(SearchProjectionIndex.isDnaBucketKey("dna/consent/consent-1"));
    assertFalse(SearchProjectionIndex.isDnaBucketKey("public/person-1"));
    assertFalse(SearchProjectionIndex.isDnaBucketKey(""));
    assertFalse(SearchProjectionIndex.isDnaBucketKey(null));
  }

  @Test
  void eventConstructorRejectsBlankActorOrCorrelation() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SearchProjectionEvent(
                SearchEventType.PERSON_CREATED,
                SearchDocumentKind.PERSON,
                "doc-1",
                "tenant-1",
                SearchPrivacyClass.PUBLIC,
                "idem-1",
                List.of(),
                List.of(),
                "",
                "corr-1"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SearchProjectionEvent(
                SearchEventType.PERSON_CREATED,
                SearchDocumentKind.PERSON,
                "doc-1",
                "tenant-1",
                SearchPrivacyClass.PUBLIC,
                "idem-1",
                List.of(),
                List.of(),
                "actor-1",
                ""));
  }

  @Test
  void decisionConstructorRejectsAllowedWithoutVersion() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SearchProjectionDecision(SearchProjectionStatus.INDEXED, null, null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SearchProjectionDecision(
                SearchProjectionStatus.REDACTED,
                null,
                null,
                SearchProjectionRedactionReason.POLICY_DENY,
                null));
  }
}