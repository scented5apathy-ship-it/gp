package com.genealogy.platform.services.importexport.publicapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PublicApiRequestGuardTest {

  private PublicApiRequestGuard.PublicApiRequest goodRequest() {
    return new PublicApiRequestGuard.PublicApiRequest(
        PublicApiRequestGuard.PublicApiResource.PUBLIC_PERSON,
        PublicApiRequestGuard.PublicApiMethod.GET,
        PublicApiScope.PUBLIC_READ_BASIC,
        10L,
        100L,
        50L,
        500L,
        false,
        false,
        false,
        "tenant-1",
        "tenant-1");
  }

  @Test
  void goodRequestIsAllowed() {
    PublicApiRequestGuard.PublicApiOutcome out = PublicApiRequestGuard.authorize(goodRequest());
    assertTrue(out.allow());
    assertNotNull(out.request());
  }

  @Test
  void postMethodFails() {
    PublicApiRequestGuard.PublicApiRequest req = new PublicApiRequestGuard.PublicApiRequest(
        PublicApiRequestGuard.PublicApiResource.PUBLIC_PERSON,
        null,
        PublicApiScope.PUBLIC_READ_BASIC,
        10L,
        100L,
        50L,
        500L,
        false,
        false,
        false,
        "tenant-1",
        "tenant-1");
    PublicApiRequestGuard.PublicApiOutcome out = PublicApiRequestGuard.authorize(req);
    assertFalse(out.allow());
    assertEquals("PUBLIC_API_METHOD_FORBIDDEN", out.failureReason());
  }

  @Test
  void dnaBucketReferenceFails() {
    PublicApiRequestGuard.PublicApiRequest req = new PublicApiRequestGuard.PublicApiRequest(
        PublicApiRequestGuard.PublicApiResource.PUBLIC_PERSON,
        PublicApiRequestGuard.PublicApiMethod.GET,
        PublicApiScope.PUBLIC_READ_BASIC,
        10L,
        100L,
        50L,
        500L,
        true,
        false,
        false,
        "tenant-1",
        "tenant-1");
    PublicApiRequestGuard.PublicApiOutcome out = PublicApiRequestGuard.authorize(req);
    assertFalse(out.allow());
    assertEquals("PUBLIC_API_DNA_BUCKET_FORBIDDEN", out.failureReason());
  }

  @Test
  void abuseSignalFails() {
    PublicApiRequestGuard.PublicApiRequest req = new PublicApiRequestGuard.PublicApiRequest(
        PublicApiRequestGuard.PublicApiResource.PUBLIC_PERSON,
        PublicApiRequestGuard.PublicApiMethod.GET,
        PublicApiScope.PUBLIC_READ_BASIC,
        10L,
        100L,
        50L,
        500L,
        false,
        true,
        false,
        "tenant-1",
        "tenant-1");
    PublicApiRequestGuard.PublicApiOutcome out = PublicApiRequestGuard.authorize(req);
    assertFalse(out.allow());
    assertEquals("PUBLIC_API_ABUSE_SIGNAL_DETECTED", out.failureReason());
  }

  @Test
  void rateLimitExceededFails() {
    PublicApiRequestGuard.PublicApiRequest req = new PublicApiRequestGuard.PublicApiRequest(
        PublicApiRequestGuard.PublicApiResource.PUBLIC_PERSON,
        PublicApiRequestGuard.PublicApiMethod.GET,
        PublicApiScope.PUBLIC_READ_BASIC,
        200L,
        100L,
        50L,
        500L,
        false,
        false,
        false,
        "tenant-1",
        "tenant-1");
    PublicApiRequestGuard.PublicApiOutcome out = PublicApiRequestGuard.authorize(req);
    assertFalse(out.allow());
    assertEquals("PUBLIC_API_RATE_LIMIT_EXCEEDED", out.failureReason());
  }

  @Test
  void tenantMismatchFails() {
    PublicApiRequestGuard.PublicApiRequest req = new PublicApiRequestGuard.PublicApiRequest(
        PublicApiRequestGuard.PublicApiResource.PUBLIC_PERSON,
        PublicApiRequestGuard.PublicApiMethod.GET,
        PublicApiScope.PUBLIC_READ_BASIC,
        10L,
        100L,
        50L,
        500L,
        false,
        false,
        false,
        "tenant-1",
        "tenant-2");
    PublicApiRequestGuard.PublicApiOutcome out = PublicApiRequestGuard.authorize(req);
    assertFalse(out.allow());
    assertEquals("PUBLIC_API_TENANT_MISMATCH", out.failureReason());
  }

  @Test
  void scopeInsufficientFails() {
    PublicApiRequestGuard.PublicApiRequest req = new PublicApiRequestGuard.PublicApiRequest(
        PublicApiRequestGuard.PublicApiResource.PUBLIC_MEDIA,
        PublicApiRequestGuard.PublicApiMethod.GET,
        PublicApiScope.PUBLIC_READ_TREE,
        10L,
        100L,
        50L,
        500L,
        false,
        false,
        false,
        "tenant-1",
        "tenant-1");
    PublicApiRequestGuard.PublicApiOutcome out = PublicApiRequestGuard.authorize(req);
    assertFalse(out.allow());
    assertEquals("PUBLIC_API_SCOPE_INSUFFICIENT", out.failureReason());
  }

  @Test
  void scopeEnumWireRoundTrip() {
    for (PublicApiScope s : PublicApiScope.values()) {
      assertEquals(s, PublicApiScope.fromWire(s.wire()));
    }
    assertThrows(IllegalArgumentException.class,
        () -> PublicApiScope.fromWire("unknown.scope"));
  }

  @Test
  void idempotencyKeyReuseConflictFails() {
    PublicApiRequestGuard.PublicApiRequest req = new PublicApiRequestGuard.PublicApiRequest(
        PublicApiRequestGuard.PublicApiResource.PUBLIC_PERSON,
        PublicApiRequestGuard.PublicApiMethod.GET,
        PublicApiScope.PUBLIC_READ_BASIC,
        10L,
        100L,
        50L,
        500L,
        false,
        false,
        true,
        "tenant-1",
        "tenant-1");
    PublicApiRequestGuard.PublicApiOutcome out = PublicApiRequestGuard.authorize(req);
    assertFalse(out.allow());
    assertEquals("PUBLIC_API_IDEMPOTENCY_KEY_REUSED_CONFLICT", out.failureReason());
  }
}