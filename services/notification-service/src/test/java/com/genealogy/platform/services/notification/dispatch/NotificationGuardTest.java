package com.genealogy.platform.services.notification.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.notification.NotificationGuardTestHelper;
import com.genealogy.platform.services.notification.dispatch.NotificationGuard.DispatchOutcome;
import com.genealogy.platform.services.notification.dispatch.NotificationGuard.DispatchRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationGuardTest {

  private static DispatchRequest happyPath() {
    return new DispatchRequest(
        "tenant-1",
        "user-1",
        "corr-1",
        "OPT_IN",
        "EMAIL",
        "DIGEST_ACTIVITY",
        "DAILY",
        "UNREAD",
        "en-US",
        "Europe/Berlin",
        true,
        "SES_ADAPTER",
        false,
        true,
        false,
        true,
        true,
        true,
        true,
        false,
        true,
        "notify.dispatch",
        true,
        true,
        false,
        true,
        true,
        true,
        false,
        false,
        false,
        true,
        false,
        30,
        5000,
        Map.of("subject", "Hello"));
  }

  @Test
  void happyPathPasses() {
    DispatchOutcome outcome = NotificationGuard.validate(happyPath());
    assertTrue(outcome.valid(), () -> "unexpected failure: " + outcome.failureReason());
    assertNotNull(outcome.request());
    assertNull(outcome.failureReason());
  }

  @Test
  void nullRequestFails() {
    DispatchOutcome outcome = NotificationGuard.validate(null);
    assertFalse(outcome.valid());
    assertEquals("NOTIFICATION_DISPATCH_DECISION", outcome.failureReason());
  }

  @Test
  void unknownPreferenceStateFails() {
    DispatchRequest req = new NotificationGuardTestHelper.RequestBuilder(happyPath())
        .preferenceState("MAYBE").build();
    DispatchOutcome outcome = NotificationGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("NOTIFICATION_PREFERENCE_OPTED_OUT", outcome.failureReason());
  }

  @Test
  void unknownChannelFails() {
    DispatchRequest req = new NotificationGuardTestHelper.RequestBuilder(happyPath())
        .channelType("FAX").build();
    DispatchOutcome outcome = NotificationGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("NOTIFICATION_CHANNEL_DISABLED_BY_TENANT", outcome.failureReason());
  }

  @Test
  void smsAdapterWithoutAdrFails() {
    DispatchRequest req = new NotificationGuardTestHelper.RequestBuilder(happyPath())
        .channelType("SMS")
        .providerAdapter("SMS_ADAPTER_SCAFFOLD")
        .smsAdapterAdrSigned(false).build();
    DispatchOutcome outcome = NotificationGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("NOTIFICATION_PROVIDER_AUTH_FAILED", outcome.failureReason());
  }

  @Test
  void pushWithoutOptInFails() {
    DispatchRequest req = new NotificationGuardTestHelper.RequestBuilder(happyPath())
        .channelType("PUSH")
        .providerAdapter("FCM_PUSH_ADAPTER")
        .pushOptIn(false).build();
    DispatchOutcome outcome = NotificationGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("NOTIFICATION_CHANNEL_DISABLED_BY_TENANT", outcome.failureReason());
  }

  @Test
  void marketingWithoutDoubleOptInFails() {
    DispatchRequest req = new NotificationGuardTestHelper.RequestBuilder(happyPath())
        .category("MARKETING")
        .marketingDoubleOptIn(false).build();
    DispatchOutcome outcome = NotificationGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("NOTIFICATION_PREFERENCE_OPTED_OUT", outcome.failureReason());
  }

  @Test
  void dnaCategoryWithoutConsentReauthFails() {
    DispatchRequest req = new NotificationGuardTestHelper.RequestBuilder(happyPath())
        .category("TRANSACTIONAL_DNA")
        .consentReauthorizedAtRender(false).build();
    DispatchOutcome outcome = NotificationGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("NOTIFICATION_PREFERENCE_OPTED_OUT", outcome.failureReason());
  }

  @Test
  void forbiddenPayloadKeyFails() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("treeViewerBypass", "evil");
    DispatchRequest req = new NotificationGuardTestHelper.RequestBuilder(happyPath())
        .payload(payload).build();
    DispatchOutcome outcome = NotificationGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("NOTIFICATION_PAYLOAD_FORBIDDEN_KEY", outcome.failureReason());
    assertEquals("treeViewerBypass", outcome.detail());
  }

  @Test
  void rawDnaPayloadKeyFails() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("rawDna", "ACGT");
    DispatchRequest req = new NotificationGuardTestHelper.RequestBuilder(happyPath())
        .payload(payload).build();
    DispatchOutcome outcome = NotificationGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("rawDna", outcome.detail());
  }

  @Test
  void crossTenantLookupFails() {
    DispatchRequest req = new NotificationGuardTestHelper.RequestBuilder(happyPath())
        .crossTenantPreferenceLookup(true).build();
    DispatchOutcome outcome = NotificationGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("NOTIFICATION_PREFERENCE_OPTED_OUT", outcome.failureReason());
  }

  @Test
  void selfBuiltSmtpFails() {
    DispatchRequest req = new NotificationGuardTestHelper.RequestBuilder(happyPath())
        .selfBuiltSmtpServer(true).build();
    DispatchOutcome outcome = NotificationGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("NOTIFICATION_PROVIDER_AUTH_FAILED", outcome.failureReason());
  }

  @Test
  void rateLimitAboveUserCapFails() {
    DispatchRequest req = new NotificationGuardTestHelper.RequestBuilder(happyPath())
        .rateLimitPerUserPerMinute(120).build();
    DispatchOutcome outcome = NotificationGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("NOTIFICATION_PROVIDER_RATE_LIMITED", outcome.failureReason());
  }

  @Test
  void unknownTaskQueueFails() {
    DispatchRequest req = new NotificationGuardTestHelper.RequestBuilder(happyPath())
        .taskQueue("unknown.queue").build();
    DispatchOutcome outcome = NotificationGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("NOTIFICATION_DISPATCH_DECISION", outcome.failureReason());
  }

  @Test
  void localeNotInCatalogueFails() {
    DispatchRequest req = new NotificationGuardTestHelper.RequestBuilder(happyPath())
        .locale("xx-XX").build();
    DispatchOutcome outcome = NotificationGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("NOTIFICATION_LOCALE_MISSING", outcome.failureReason());
  }
}