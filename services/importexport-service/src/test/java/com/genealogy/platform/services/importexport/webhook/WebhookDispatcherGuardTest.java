package com.genealogy.platform.services.importexport.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebhookDispatcherGuardTest {

  private WebhookDispatcherGuard.WebhookDeliveryRequest goodRequest() {
    return new WebhookDispatcherGuard.WebhookDeliveryRequest(
        "https://example.com/hook",
        1,
        5L,
        3_600L,
        WebhookRetryPolicy.EXPONENTIAL_BACKOFF,
        WebhookDispatcherGuard.WebhookSignatureAlgorithm.HMAC_SHA_256,
        true,
        false,
        false,
        false,
        false,
        "tenant-1",
        "tenant-1");
  }

  @Test
  void goodRequestIsAllowed() {
    WebhookDispatcherGuard.WebhookDeliveryOutcome out = WebhookDispatcherGuard.dispatch(goodRequest());
    assertTrue(out.allow());
    assertNotNull(out.request());
  }

  @Test
  void nonHttpsFails() {
    WebhookDispatcherGuard.WebhookDeliveryRequest req = new WebhookDispatcherGuard.WebhookDeliveryRequest(
        "http://example.com/hook",
        1,
        5L,
        3_600L,
        WebhookRetryPolicy.EXPONENTIAL_BACKOFF,
        WebhookDispatcherGuard.WebhookSignatureAlgorithm.HMAC_SHA_256,
        true,
        false,
        false,
        false,
        false,
        "tenant-1",
        "tenant-1");
    WebhookDispatcherGuard.WebhookDeliveryOutcome out = WebhookDispatcherGuard.dispatch(req);
    assertFalse(out.allow());
    assertEquals("WEBHOOK_TARGET_TLS_INVALID", out.failureReason());
  }

  @Test
  void attemptsExceededFails() {
    WebhookDispatcherGuard.WebhookDeliveryRequest req = new WebhookDispatcherGuard.WebhookDeliveryRequest(
        "https://example.com/hook",
        16,
        5L,
        3_600L,
        WebhookRetryPolicy.EXPONENTIAL_BACKOFF,
        WebhookDispatcherGuard.WebhookSignatureAlgorithm.HMAC_SHA_256,
        true,
        false,
        false,
        false,
        false,
        "tenant-1",
        "tenant-1");
    WebhookDispatcherGuard.WebhookDeliveryOutcome out = WebhookDispatcherGuard.dispatch(req);
    assertFalse(out.allow());
    assertEquals("WEBHOOK_RETRY_EXHAUSTED", out.failureReason());
  }

  @Test
  void maxBackoffTooSmallFails() {
    WebhookDispatcherGuard.WebhookDeliveryRequest req = new WebhookDispatcherGuard.WebhookDeliveryRequest(
        "https://example.com/hook",
        1,
        5L,
        100L,
        WebhookRetryPolicy.EXPONENTIAL_BACKOFF,
        WebhookDispatcherGuard.WebhookSignatureAlgorithm.HMAC_SHA_256,
        true,
        false,
        false,
        false,
        false,
        "tenant-1",
        "tenant-1");
    WebhookDispatcherGuard.WebhookDeliveryOutcome out = WebhookDispatcherGuard.dispatch(req);
    assertFalse(out.allow());
    assertEquals("WEBHOOK_RETRY_EXHAUSTED", out.failureReason());
  }

  @Test
  void notSignedFails() {
    WebhookDispatcherGuard.WebhookDeliveryRequest req = new WebhookDispatcherGuard.WebhookDeliveryRequest(
        "https://example.com/hook",
        1,
        5L,
        3_600L,
        WebhookRetryPolicy.EXPONENTIAL_BACKOFF,
        WebhookDispatcherGuard.WebhookSignatureAlgorithm.HMAC_SHA_256,
        false,
        false,
        false,
        false,
        false,
        "tenant-1",
        "tenant-1");
    WebhookDispatcherGuard.WebhookDeliveryOutcome out = WebhookDispatcherGuard.dispatch(req);
    assertFalse(out.allow());
    assertEquals("WEBHOOK_SIGNATURE_FAILED", out.failureReason());
  }

  @Test
  void dnaBucketReferenceFails() {
    WebhookDispatcherGuard.WebhookDeliveryRequest req = new WebhookDispatcherGuard.WebhookDeliveryRequest(
        "https://example.com/hook",
        1,
        5L,
        3_600L,
        WebhookRetryPolicy.EXPONENTIAL_BACKOFF,
        WebhookDispatcherGuard.WebhookSignatureAlgorithm.HMAC_SHA_256,
        true,
        true,
        false,
        false,
        false,
        "tenant-1",
        "tenant-1");
    WebhookDispatcherGuard.WebhookDeliveryOutcome out = WebhookDispatcherGuard.dispatch(req);
    assertFalse(out.allow());
    assertEquals("WEBHOOK_DNA_BUCKET_FORBIDDEN", out.failureReason());
  }

  @Test
  void tenantRevokedFails() {
    WebhookDispatcherGuard.WebhookDeliveryRequest req = new WebhookDispatcherGuard.WebhookDeliveryRequest(
        "https://example.com/hook",
        1,
        5L,
        3_600L,
        WebhookRetryPolicy.EXPONENTIAL_BACKOFF,
        WebhookDispatcherGuard.WebhookSignatureAlgorithm.HMAC_SHA_256,
        true,
        false,
        false,
        true,
        false,
        "tenant-1",
        "tenant-1");
    WebhookDispatcherGuard.WebhookDeliveryOutcome out = WebhookDispatcherGuard.dispatch(req);
    assertFalse(out.allow());
    assertEquals("WEBHOOK_SUBSCRIPTION_REVOKED", out.failureReason());
  }

  @Test
  void tenantMismatchFails() {
    WebhookDispatcherGuard.WebhookDeliveryRequest req = new WebhookDispatcherGuard.WebhookDeliveryRequest(
        "https://example.com/hook",
        1,
        5L,
        3_600L,
        WebhookRetryPolicy.EXPONENTIAL_BACKOFF,
        WebhookDispatcherGuard.WebhookSignatureAlgorithm.HMAC_SHA_256,
        true,
        false,
        false,
        false,
        false,
        "tenant-1",
        "tenant-2");
    WebhookDispatcherGuard.WebhookDeliveryOutcome out = WebhookDispatcherGuard.dispatch(req);
    assertFalse(out.allow());
    assertEquals("WEBHOOK_TENANT_MISMATCH", out.failureReason());
  }

  @Test
  void retryPolicyEnumWireRoundTrip() {
    for (WebhookRetryPolicy p : WebhookRetryPolicy.values()) {
      assertEquals(p, WebhookRetryPolicy.fromWire(p.wire()));
    }
    assertThrows(IllegalArgumentException.class,
        () -> WebhookRetryPolicy.fromWire("UNKNOWN"));
  }
}