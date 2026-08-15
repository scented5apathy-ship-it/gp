package com.genealogy.platform.services.notification.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.notification.privacy.PrivacySafeDeliveryGuard.DeliveryOutcome;
import com.genealogy.platform.services.notification.privacy.PrivacySafeDeliveryGuard.DeliveryRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PrivacySafeDeliveryGuardTest {

  private static DeliveryRequest happyPath() {
    return new DeliveryRequest(
        "tenant-1",
        "user-1",
        "corr-1",
        "GUARDIAN_ACTION",
        true,
        true,
        "ALLOW_WITH_GENERIC_TEXT",
        true,
        true,
        "GENERIC_GUARDIAN_EVENT",
        true,
        true,
        Set.of("tenantPseudoId", "tenantDisplayName", "preferenceCenterUrl",
            "contactEmail", "logoUrl", "colorScheme", "footerDisclosure", "locale"),
        "https://example.com/preferences",
        Set.of("List-Unsubscribe", "List-Unsubscribe-Post", "List-Id",
            "X-Entity-Ref-ID", "X-Genealogy-Tenant-Pseudo",
            "X-Genealogy-Delivery-Decision"),
        "token123",
        false,
        3600,
        false,
        false,
        true,
        Set.of("PRIVACY_LEVEL_CHANGED", "LIVING_STATUS_CHANGED",
            "CONSENT_REVOKED", "CONSENT_EXPIRED",
            "TENANT_VISIBILITY_CHANGED", "RELATIONSHIP_REMOVED",
            "SCOPE_NARROWED", "SESSION_PRIVILEGE_DEMOTED"),
        false,
        false,
        true,
        2048,
        Map.of("subject", "Hello"));
  }

  @Test
  void happyPathPasses() {
    DeliveryOutcome outcome = PrivacySafeDeliveryGuard.validate(happyPath());
    assertTrue(outcome.valid(), () -> "unexpected failure: " + outcome.failureReason());
    assertNotNull(outcome.request());
    assertNull(outcome.failureReason());
  }

  @Test
  void nullRequestFails() {
    DeliveryOutcome outcome = PrivacySafeDeliveryGuard.validate(null);
    assertFalse(outcome.valid());
    assertEquals("PRIVACY_DELIVERY_ABAC_DENY", outcome.failureReason());
  }

  @Test
  void abacNotRecheckedAtRenderFails() {
    DeliveryRequest req = new DeliveryRequest(
        "tenant-1", "user-1", "corr-1", "GUARDIAN_ACTION",
        false, true, "ALLOW_WITH_GENERIC_TEXT", true, true, "GENERIC_GUARDIAN_EVENT",
        true, true, happyPath().brandingFieldsCovered(),
        "https://example.com/preferences",
        happyPath().providerHeaders(),
        "token", false, 3600, false, false, true,
        happyPath().reauthorizationTriggers(),
        false, false, true, 1024, Map.of());
    DeliveryOutcome outcome = PrivacySafeDeliveryGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("PRIVACY_DELIVERY_REAUTHORIZATION_TRIGGERED", outcome.failureReason());
  }

  @Test
  void abacDecisionDenyFails() {
    DeliveryRequest req = new DeliveryRequest(
        "tenant-1", "user-1", "corr-1", "DNA_MATCH_DISCOVERED",
        true, true, "DENY_DUE_TO_DNA_SCOPE", true, true, "GENERIC_DNA_EVENT",
        true, true, happyPath().brandingFieldsCovered(),
        "https://example.com/preferences",
        happyPath().providerHeaders(),
        "token", false, 3600, false, false, true,
        happyPath().reauthorizationTriggers(),
        false, false, true, 1024, Map.of());
    DeliveryOutcome outcome = PrivacySafeDeliveryGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("PRIVACY_DELIVERY_ABAC_DENY", outcome.failureReason());
  }

  @Test
  void genericTextTemplateMissingFails() {
    DeliveryRequest req = new DeliveryRequest(
        "tenant-1", "user-1", "corr-1", "GUARDIAN_ACTION",
        true, true, "ALLOW_WITH_GENERIC_TEXT", true, false, "GENERIC_GUARDIAN_EVENT",
        true, true, happyPath().brandingFieldsCovered(),
        "https://example.com/preferences",
        happyPath().providerHeaders(),
        "token", false, 3600, false, false, true,
        happyPath().reauthorizationTriggers(),
        false, false, true, 1024, Map.of());
    DeliveryOutcome outcome = PrivacySafeDeliveryGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("PRIVACY_GENERIC_TEXT_MISSING", outcome.failureReason());
  }

  @Test
  void opaquePayloadOnlyMissingForDnaFails() {
    DeliveryRequest req = new DeliveryRequest(
        "tenant-1", "user-1", "corr-1", "DNA_MATCH_DISCOVERED",
        true, true, "ALLOW", false, true, "GENERIC_DNA_EVENT",
        true, true, happyPath().brandingFieldsCovered(),
        "https://example.com/preferences",
        happyPath().providerHeaders(),
        "token", false, 3600, false, false, true,
        happyPath().reauthorizationTriggers(),
        false, false, true, 1024, Map.of());
    DeliveryOutcome outcome = PrivacySafeDeliveryGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("PRIVACY_DELIVERY_DNA_PAYLOAD_REDACTED", outcome.failureReason());
  }

  @Test
  void forbiddenPayloadKeyFails() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("rawDeletionReason", "user asked");
    DeliveryRequest req = new DeliveryRequest(
        "tenant-1", "user-1", "corr-1", "DELETION_REQUESTED",
        true, true, "ALLOW_WITH_GENERIC_TEXT", true, true, "GENERIC_DELETION_EVENT",
        true, true, happyPath().brandingFieldsCovered(),
        "https://example.com/preferences",
        happyPath().providerHeaders(),
        "token", false, 3600, false, false, true,
        happyPath().reauthorizationTriggers(),
        false, false, true, 1024, payload);
    DeliveryOutcome outcome = PrivacySafeDeliveryGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("rawDeletionReason", outcome.detail());
  }

  @Test
  void missingUnsubscribeHeaderFails() {
    DeliveryRequest req = new DeliveryRequest(
        "tenant-1", "user-1", "corr-1", "GUARDIAN_ACTION",
        true, true, "ALLOW_WITH_GENERIC_TEXT", true, true, "GENERIC_GUARDIAN_EVENT",
        true, true, happyPath().brandingFieldsCovered(),
        "https://example.com/preferences",
        Set.of("List-Unsubscribe"),
        "token", false, 3600, false, false, true,
        happyPath().reauthorizationTriggers(),
        false, false, true, 1024, Map.of());
    DeliveryOutcome outcome = PrivacySafeDeliveryGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("PRIVACY_UNSUBSCRIBE_HEADER_MISSING", outcome.failureReason());
  }

  @Test
  void deepLinkTokenReusedFails() {
    DeliveryRequest req = new DeliveryRequest(
        "tenant-1", "user-1", "corr-1", "GUARDIAN_ACTION",
        true, true, "ALLOW_WITH_GENERIC_TEXT", true, true, "GENERIC_GUARDIAN_EVENT",
        true, true, happyPath().brandingFieldsCovered(),
        "https://example.com/preferences",
        happyPath().providerHeaders(),
        "token", true, 3600, false, false, true,
        happyPath().reauthorizationTriggers(),
        false, false, true, 1024, Map.of());
    DeliveryOutcome outcome = PrivacySafeDeliveryGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("PRIVACY_DEEP_LINK_TOKEN_REUSED", outcome.failureReason());
  }

  @Test
  void crossTenantBrandingFails() {
    DeliveryRequest req = new DeliveryRequest(
        "tenant-1", "user-1", "corr-1", "GUARDIAN_ACTION",
        true, true, "ALLOW_WITH_GENERIC_TEXT", true, true, "GENERIC_GUARDIAN_EVENT",
        true, true, happyPath().brandingFieldsCovered(),
        "https://example.com/preferences",
        happyPath().providerHeaders(),
        "token", false, 3600, true, false, true,
        happyPath().reauthorizationTriggers(),
        false, false, true, 1024, Map.of());
    DeliveryOutcome outcome = PrivacySafeDeliveryGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("PRIVACY_BRANDING_INVALID", outcome.failureReason());
  }

  @Test
  void providerSwitchWithoutAdrFails() {
    DeliveryRequest req = new DeliveryRequest(
        "tenant-1", "user-1", "corr-1", "GUARDIAN_ACTION",
        true, true, "ALLOW_WITH_GENERIC_TEXT", true, true, "GENERIC_GUARDIAN_EVENT",
        true, true, happyPath().brandingFieldsCovered(),
        "https://example.com/preferences",
        happyPath().providerHeaders(),
        "token", false, 3600, false, false, false,
        happyPath().reauthorizationTriggers(),
        false, false, true, 1024, Map.of());
    DeliveryOutcome outcome = PrivacySafeDeliveryGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("PRIVACY_PROVIDER_SWITCH_BLOCKED", outcome.failureReason());
  }

  @Test
  void outboundAttachmentFails() {
    DeliveryRequest req = new DeliveryRequest(
        "tenant-1", "user-1", "corr-1", "GUARDIAN_ACTION",
        true, true, "ALLOW_WITH_GENERIC_TEXT", true, true, "GENERIC_GUARDIAN_EVENT",
        true, true, happyPath().brandingFieldsCovered(),
        "https://example.com/preferences",
        happyPath().providerHeaders(),
        "token", false, 3600, false, false, true,
        happyPath().reauthorizationTriggers(),
        true, false, true, 1024, Map.of());
    DeliveryOutcome outcome = PrivacySafeDeliveryGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("PRIVACY_BODY_CONTAINS_FORBIDDEN_KEY", outcome.failureReason());
  }
}