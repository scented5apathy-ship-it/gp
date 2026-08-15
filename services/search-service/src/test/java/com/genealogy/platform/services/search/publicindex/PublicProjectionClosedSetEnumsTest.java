package com.genealogy.platform.services.search.publicindex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PublicProjectionClosedSetEnumsTest {

  @Test
  void publicProjectionVisibilityFromWireCoversAllValues() {
    for (PublicProjectionVisibility visibility : PublicProjectionVisibility.values()) {
      assertEquals(visibility, PublicProjectionVisibility.fromWire(visibility.wire()));
    }
    assertEquals(2, PublicProjectionVisibility.values().length);
    assertThrows(IllegalArgumentException.class, () -> PublicProjectionVisibility.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> PublicProjectionVisibility.fromWire("BOGUS"));
  }

  @Test
  void publicProjectionRedactionReasonFromWireCoversAllValues() {
    for (PublicProjectionRedactionReason reason : PublicProjectionRedactionReason.values()) {
      assertEquals(reason, PublicProjectionRedactionReason.fromWire(reason.wire()));
    }
    assertEquals(10, PublicProjectionRedactionReason.values().length);
    assertThrows(
        IllegalArgumentException.class, () -> PublicProjectionRedactionReason.fromWire(null));
    assertThrows(
        IllegalArgumentException.class, () -> PublicProjectionRedactionReason.fromWire("BOGUS"));
  }

  @Test
  void publicProjectionFailureReasonFromWireCoversAllValues() {
    for (PublicProjectionFailureReason reason : PublicProjectionFailureReason.values()) {
      assertEquals(reason, PublicProjectionFailureReason.fromWire(reason.wire()));
    }
    assertEquals(21, PublicProjectionFailureReason.values().length);
    assertThrows(
        IllegalArgumentException.class, () -> PublicProjectionFailureReason.fromWire(null));
    assertThrows(
        IllegalArgumentException.class, () -> PublicProjectionFailureReason.fromWire("BOGUS"));
  }

  @Test
  void publicProjectionLifecycleStatusFromWireCoversAllValues() {
    for (PublicProjectionLifecycleStatus status : PublicProjectionLifecycleStatus.values()) {
      assertEquals(status, PublicProjectionLifecycleStatus.fromWire(status.wire()));
    }
    assertEquals(5, PublicProjectionLifecycleStatus.values().length);
    assertThrows(
        IllegalArgumentException.class, () -> PublicProjectionLifecycleStatus.fromWire(null));
    assertThrows(
        IllegalArgumentException.class, () -> PublicProjectionLifecycleStatus.fromWire("BOGUS"));
  }

  @Test
  void decisionConstructorRejectsIndexedWithoutRowId() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicProjectionDecision(
                PublicProjectionLifecycleStatus.INDEXED,
                null,
                "https://example.com/public/tenant-1/doc-1",
                null,
                null,
                null));
  }

  @Test
  void decisionConstructorRejectsRedactedWithoutReason() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicProjectionDecision(
                PublicProjectionLifecycleStatus.REDACTED, null, null, null, null, null));
  }

  @Test
  void decisionConstructorRejectsPurgedWithoutReason() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicProjectionDecision(
                PublicProjectionLifecycleStatus.PURGED, null, null, null, null, null));
  }
}