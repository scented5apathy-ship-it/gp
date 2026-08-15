package com.genealogy.platform.services.operations.entitlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.operations.entitlement.EntitlementGuard.DecisionOutcome;
import com.genealogy.platform.services.operations.entitlement.EntitlementGuard.EntitlementRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EntitlementGuardTest {

  private static EntitlementRequest happyPath() {
    return new EntitlementRequest(
        "tenant-1", "user-1", "corr-1",
        "PRO", "SaaS",
        "TREE_COUNT", "PRE_MUTATION",
        true, true, true, true,
        "STRIPE_ADAPTER_SAAS",
        true, true,
        true, true,
        300,
        true, true,
        Set.of("TREE_CREATED", "PERSON_CREATED", "MEDIA_BYTES_UPLOADED"),
        "OK",
        "ALLOW",
        false, true,
        Set.of("INVOICE_PAID", "SUBSCRIPTION_UPDATED"),
        "ops.entitlement",
        2048,
        true,
        Map.of("subject", "Hello"));
  }

  @Test
  void happyPathPasses() {
    DecisionOutcome outcome = EntitlementGuard.validate(happyPath());
    assertTrue(outcome.valid(), () -> "unexpected failure: " + outcome.failureReason());
    assertNotNull(outcome.request());
    assertNull(outcome.failureReason());
  }

  @Test
  void nullRequestFails() {
    DecisionOutcome outcome = EntitlementGuard.validate(null);
    assertFalse(outcome.valid());
    assertEquals("ENTITLEMENT_PLAN_INACTIVE", outcome.failureReason());
  }

  @Test
  void unknownBillingPlanFails() {
    EntitlementRequest req = mutate(happyPath()).billingPlan("UNKNOWN").build();
    DecisionOutcome outcome = EntitlementGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("ENTITLEMENT_PLAN_INACTIVE", outcome.failureReason());
  }

  @Test
  void onPremLicenseOnPremDeploymentFails() {
    EntitlementRequest req = mutate(happyPath())
        .billingProvider("OFFLINE_LICENSE_ON_PREM")
        .deploymentModel("SaaS").build();
    DecisionOutcome outcome = EntitlementGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("ENTITLEMENT_LICENSE_INVALID", outcome.failureReason());
  }

  @Test
  void billingWebhookHmacMissingFails() {
    EntitlementRequest req = mutate(happyPath()).billingWebhookHmacSha256(false).build();
    DecisionOutcome outcome = EntitlementGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("ENTITLEMENT_BILLING_WEBHOOK_SIGNATURE_INVALID", outcome.failureReason());
  }

  @Test
  void billingWebhookReplayWindowTooLongFails() {
    EntitlementRequest req = mutate(happyPath())
        .billingWebhookReplayWindowSeconds(3600).build();
    DecisionOutcome outcome = EntitlementGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("ENTITLEMENT_BILLING_WEBHOOK_DUPLICATE", outcome.failureReason());
  }

  @Test
  void quotaNotEnforcedPreMutationFails() {
    EntitlementRequest req = mutate(happyPath()).quotaEnforcedPreMutation(false).build();
    DecisionOutcome outcome = EntitlementGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION", outcome.failureReason());
  }

  @Test
  void featureFlagOverrideWithoutReasonFails() {
    EntitlementRequest req = mutate(happyPath())
        .featureFlagOverride(true).featureFlagOverrideReasonCaptured(false).build();
    DecisionOutcome outcome = EntitlementGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION", outcome.failureReason());
  }

  @Test
  void forbiddenPayloadKeyFails() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("rawStripeApiKey", "sk_live_evil");
    EntitlementRequest req = mutate(happyPath()).payload(payload).build();
    DecisionOutcome outcome = EntitlementGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("rawStripeApiKey", outcome.detail());
  }

  @Test
  void treeViewerBypassFails() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("treeViewerBypass", "evil");
    EntitlementRequest req = mutate(happyPath()).payload(payload).build();
    DecisionOutcome outcome = EntitlementGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("treeViewerBypass", outcome.detail());
  }

  @Test
  void planTierLimitsNotMonotonicFails() {
    EntitlementRequest req = mutate(happyPath()).planTierLimitsMonotonic(false).build();
    DecisionOutcome outcome = EntitlementGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION", outcome.failureReason());
  }

  @Test
  void unknownTaskQueueFails() {
    EntitlementRequest req = mutate(happyPath()).taskQueue("unknown.queue").build();
    DecisionOutcome outcome = EntitlementGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("ENTITLEMENT_PLAN_INACTIVE", outcome.failureReason());
  }

  private static RequestBuilder mutate(EntitlementRequest base) {
    return new RequestBuilder(base);
  }

  private static final class RequestBuilder {
    private String tenantPseudoId;
    private String actorPseudoId;
    private String correlationId;
    private String billingPlan;
    private String deploymentModel;
    private String quotaDimension;
    private String quotaEnforcementScope;
    private boolean quotaEnforcedPreMutation;
    private boolean quotaEnforcedPreJobSubmit;
    private boolean quotaEnforcedPreBillingCharge;
    private boolean quotaEnforcedPreExportDownload;
    private String billingProvider;
    private boolean licenseFingerprintMatchesTenant;
    private boolean licenseGracePeriodHonored;
    private boolean billingWebhookHmacSha256;
    private boolean billingWebhookSecretRotatedPerAdr;
    private int billingWebhookReplayWindowSeconds;
    private boolean usageEventIdempotencyKeyRequired;
    private boolean usageEventsDeDuplicatedByIdempotencyKey;
    private Set<String> usageEventCategories;
    private String warningLevel;
    private String decisionLabel;
    private boolean featureFlagOverride;
    private boolean featureFlagOverrideReasonCaptured;
    private Set<String> billingWebhookEvents;
    private String taskQueue;
    private int usageEventBytes;
    private boolean planTierLimitsMonotonic;
    private Map<String, Object> payload;

    RequestBuilder(EntitlementRequest base) {
      this.tenantPseudoId = base.tenantPseudoId();
      this.actorPseudoId = base.actorPseudoId();
      this.correlationId = base.correlationId();
      this.billingPlan = base.billingPlan();
      this.deploymentModel = base.deploymentModel();
      this.quotaDimension = base.quotaDimension();
      this.quotaEnforcementScope = base.quotaEnforcementScope();
      this.quotaEnforcedPreMutation = base.quotaEnforcedPreMutation();
      this.quotaEnforcedPreJobSubmit = base.quotaEnforcedPreJobSubmit();
      this.quotaEnforcedPreBillingCharge = base.quotaEnforcedPreBillingCharge();
      this.quotaEnforcedPreExportDownload = base.quotaEnforcedPreExportDownload();
      this.billingProvider = base.billingProvider();
      this.licenseFingerprintMatchesTenant = base.licenseFingerprintMatchesTenant();
      this.licenseGracePeriodHonored = base.licenseGracePeriodHonored();
      this.billingWebhookHmacSha256 = base.billingWebhookHmacSha256();
      this.billingWebhookSecretRotatedPerAdr = base.billingWebhookSecretRotatedPerAdr();
      this.billingWebhookReplayWindowSeconds = base.billingWebhookReplayWindowSeconds();
      this.usageEventIdempotencyKeyRequired = base.usageEventIdempotencyKeyRequired();
      this.usageEventsDeDuplicatedByIdempotencyKey =
          base.usageEventsDeDuplicatedByIdempotencyKey();
      this.usageEventCategories = base.usageEventCategories();
      this.warningLevel = base.warningLevel();
      this.decisionLabel = base.decisionLabel();
      this.featureFlagOverride = base.featureFlagOverride();
      this.featureFlagOverrideReasonCaptured = base.featureFlagOverrideReasonCaptured();
      this.billingWebhookEvents = base.billingWebhookEvents();
      this.taskQueue = base.taskQueue();
      this.usageEventBytes = base.usageEventBytes();
      this.planTierLimitsMonotonic = base.planTierLimitsMonotonic();
      this.payload = base.payload();
    }

    RequestBuilder billingPlan(String v) { this.billingPlan = v; return this; }
    RequestBuilder deploymentModel(String v) { this.deploymentModel = v; return this; }
    RequestBuilder billingProvider(String v) { this.billingProvider = v; return this; }
    RequestBuilder billingWebhookHmacSha256(boolean v) {
      this.billingWebhookHmacSha256 = v; return this;
    }
    RequestBuilder billingWebhookReplayWindowSeconds(int v) {
      this.billingWebhookReplayWindowSeconds = v; return this;
    }
    RequestBuilder quotaEnforcedPreMutation(boolean v) {
      this.quotaEnforcedPreMutation = v; return this;
    }
    RequestBuilder featureFlagOverride(boolean v) {
      this.featureFlagOverride = v; return this;
    }
    RequestBuilder featureFlagOverrideReasonCaptured(boolean v) {
      this.featureFlagOverrideReasonCaptured = v; return this;
    }
    RequestBuilder payload(Map<String, Object> v) { this.payload = v; return this; }
    RequestBuilder planTierLimitsMonotonic(boolean v) {
      this.planTierLimitsMonotonic = v; return this;
    }
    RequestBuilder taskQueue(String v) { this.taskQueue = v; return this; }

    EntitlementRequest build() {
      return new EntitlementRequest(
          tenantPseudoId, actorPseudoId, correlationId, billingPlan, deploymentModel,
          quotaDimension, quotaEnforcementScope, quotaEnforcedPreMutation,
          quotaEnforcedPreJobSubmit, quotaEnforcedPreBillingCharge,
          quotaEnforcedPreExportDownload, billingProvider,
          licenseFingerprintMatchesTenant, licenseGracePeriodHonored,
          billingWebhookHmacSha256, billingWebhookSecretRotatedPerAdr,
          billingWebhookReplayWindowSeconds, usageEventIdempotencyKeyRequired,
          usageEventsDeDuplicatedByIdempotencyKey, usageEventCategories,
          warningLevel, decisionLabel, featureFlagOverride,
          featureFlagOverrideReasonCaptured, billingWebhookEvents, taskQueue,
          usageEventBytes, planTierLimitsMonotonic, payload);
    }
  }
}