package com.genealogy.platform.services.operations.entitlement;

import com.genealogy.platform.services.operations.shared.E11ForbiddenPayloadKeys;
import com.genealogy.platform.services.operations.shared.E11Limits;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates entitlement / quota / billing
 * decisions against the E11.4 invariants. Mirrors
 * <code>contracts/operations/entitlement-quota-billing-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>billing plan / provider / quota closed-set membership;</li>
 *   <li>domain entitlement source-of-truth lives in
 *       operations-service Postgres, NOT in Kong rate-limit metric
 *       (R1.4 + ADR-E0.5-12);</li>
 *   <li>quota enforced pre-mutation / pre-job-submit /
 *       pre-billing-charge / pre-export-download;</li>
 *   <li>usage events MUST NOT contain raw DNA / raw media / raw
 *       sensitive content, MUST use opaque aggregate ids, MUST
 *       carry an idempotency key and MUST be de-duplicated;</li>
 *   <li>billing webhook signature required (HMAC-SHA256), secret
 *       rotated per ADR;</li>
 *   <li>on-prem license is offline + fingerprint-bound + grace
 *       period;</li>
 *   <li>payload MUST NOT contain any forbidden payload key (incl.
 *       <code>treeViewerBypass</code>, <code>rawStripeApiKey</code>,
 *       <code>rawLicenseFile</code>);</li>
 *   <li>plan tier limits strictly monotonic
 *       (FREE &lt; PRO &lt; ENTERPRISE).</li>
 * </ul>
 */
public final class EntitlementGuard {

  public static final Set<String> BILLING_PLANS = Set.of(
      "FREE", "PRO", "ENTERPRISE", "ON_PREM_COMMUNITY", "ON_PREM_ENTERPRISE", "TRIAL");
  public static final Set<String> PLAN_QUOTAS = Set.of(
      "TREE_COUNT", "PERSON_COUNT", "MEDIA_BYTES", "DNA_KIT_COUNT",
      "API_REQUESTS_PER_DAY", "EXPORT_JOBS_PER_DAY", "REPORT_JOBS_PER_DAY",
      "IMPORT_JOBS_PER_DAY", "WEBHOOK_DELIVERIES_PER_DAY", "ACTIVE_COLLABORATORS");
  public static final Set<String> QUOTA_SCOPES = Set.of(
      "PRE_MUTATION", "PRE_JOB_SUBMIT", "PRE_BILLING_CHARGE", "PRE_EXPORT_DOWNLOAD");
  public static final Set<String> BILLING_PROVIDERS = Set.of(
      "STRIPE_ADAPTER_SAAS", "OFFLINE_LICENSE_ON_PREM");
  public static final Set<String> USAGE_CATEGORIES = Set.of(
      "TREE_CREATED", "PERSON_CREATED", "MEDIA_BYTES_UPLOADED",
      "DNA_KIT_REGISTERED", "API_REQUEST", "EXPORT_JOB_SUBMITTED",
      "REPORT_JOB_SUBMITTED", "IMPORT_JOB_SUBMITTED", "WEBHOOK_DELIVERED",
      "COLLABORATOR_INVITED");
  public static final Set<String> WARNING_LEVELS = Set.of(
      "OK", "WARNING_50", "WARNING_75", "WARNING_90",
      "EXCEEDED_HARD", "EXCEEDED_SOFT");
  public static final Set<String> DECISION_LABELS = Set.of(
      "ALLOW", "ALLOW_WITH_WARNING", "DENY_HARD_QUOTA", "DENY_PLAN_INACTIVE",
      "DENY_TRIAL_EXPIRED", "DENY_FEATURE_FLAG_OFF", "DENY_LICENSE_INVALID");
  public static final Set<String> BILLING_WEBHOOK_EVENTS = Set.of(
      "INVOICE_PAID", "INVOICE_PAYMENT_FAILED",
      "SUBSCRIPTION_CREATED", "SUBSCRIPTION_UPDATED",
      "SUBSCRIPTION_CANCELLED", "SUBSCRIPTION_TRIAL_WILL_END",
      "CUSTOMER_CREATED", "CUSTOMER_UPDATED");
  public static final Set<String> TASK_QUEUES = Set.of(
      "ops.entitlement", "ops.quota", "ops.billingWebhook", "ops.licenseValidation");

  private EntitlementGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static DecisionOutcome validate(EntitlementRequest request) {
    if (request == null) {
      return DecisionOutcome.failed("ENTITLEMENT_PLAN_INACTIVE", "request MUST NOT be null");
    }
    if (request.tenantPseudoId() == null || request.tenantPseudoId().isBlank()) {
      return DecisionOutcome.failed("ENTITLEMENT_TENANT_MISMATCH", "tenantPseudoId");
    }
    if (request.billingPlan() == null || !BILLING_PLANS.contains(request.billingPlan())) {
      return DecisionOutcome.failed("ENTITLEMENT_PLAN_INACTIVE",
          "billingPlan MUST be one of " + BILLING_PLANS);
    }
    if (request.quotaDimension() != null
        && !PLAN_QUOTAS.contains(request.quotaDimension())) {
      return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
          "quotaDimension MUST be one of " + PLAN_QUOTAS);
    }
    if (request.quotaEnforcementScope() != null
        && !QUOTA_SCOPES.contains(request.quotaEnforcementScope())) {
      return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
          "quotaEnforcementScope MUST be one of " + QUOTA_SCOPES);
    }
    if (!request.quotaEnforcedPreMutation()) {
      return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
          "quota MUST be enforced pre-mutation");
    }
    if (!request.quotaEnforcedPreJobSubmit()) {
      return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
          "quota MUST be enforced pre-job-submit");
    }
    if (!request.quotaEnforcedPreBillingCharge()) {
      return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
          "quota MUST be enforced pre-billing-charge");
    }
    if (!request.quotaEnforcedPreExportDownload()) {
      return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
          "quota MUST be enforced pre-export-download");
    }
    if (request.billingProvider() == null
        || !BILLING_PROVIDERS.contains(request.billingProvider())) {
      return DecisionOutcome.failed("ENTITLEMENT_PLAN_INACTIVE",
          "billingProvider MUST be one of " + BILLING_PROVIDERS);
    }
    if ("STRIPE_ADAPTER_SAAS".equals(request.billingProvider())
        && !"SaaS".equals(request.deploymentModel())) {
      return DecisionOutcome.failed("ENTITLEMENT_PLAN_INACTIVE",
          "STRIPE_ADAPTER_SAAS requires SaaS deployment per ADR-E0.5-12");
    }
    if ("OFFLINE_LICENSE_ON_PREM".equals(request.billingProvider())
        && !"ON_PREM".equals(request.deploymentModel())) {
      return DecisionOutcome.failed("ENTITLEMENT_LICENSE_INVALID",
          "OFFLINE_LICENSE_ON_PREM requires ON_PREM deployment");
    }
    if (!request.licenseFingerprintMatchesTenant()) {
      return DecisionOutcome.failed("ENTITLEMENT_LICENSE_INVALID",
          "license fingerprint MUST match tenant");
    }
    if (!request.licenseGracePeriodHonored()) {
      return DecisionOutcome.failed("ENTITLEMENT_LICENSE_EXPIRED",
          "license grace period MUST be honored");
    }
    if (!request.billingWebhookHmacSha256()) {
      return DecisionOutcome.failed("ENTITLEMENT_BILLING_WEBHOOK_SIGNATURE_INVALID",
          "billing webhook MUST be HMAC-SHA256");
    }
    if (!request.billingWebhookSecretRotatedPerAdr()) {
      return DecisionOutcome.failed("ENTITLEMENT_BILLING_WEBHOOK_SIGNATURE_INVALID",
          "billing webhook secret MUST be rotated per ADR-E0.5-12");
    }
    if (request.billingWebhookReplayWindowSeconds()
        > E11Limits.BILLING_WEBHOOK_REPLAY_WINDOW_SECONDS) {
      return DecisionOutcome.failed("ENTITLEMENT_BILLING_WEBHOOK_DUPLICATE",
          "billingWebhookReplayWindowSeconds > "
              + E11Limits.BILLING_WEBHOOK_REPLAY_WINDOW_SECONDS);
    }
    if (!request.usageEventIdempotencyKeyRequired()) {
      return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
          "usage events MUST carry an idempotency key");
    }
    if (!request.usageEventsDeDuplicatedByIdempotencyKey()) {
      return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
          "usage events MUST be de-duplicated by idempotency key");
    }
    if (request.usageEventCategories() != null) {
      for (String c : request.usageEventCategories()) {
        if (!USAGE_CATEGORIES.contains(c)) {
          return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
              "usage event category " + c + " MUST be one of " + USAGE_CATEGORIES);
        }
      }
    }
    if (request.warningLevel() != null
        && !WARNING_LEVELS.contains(request.warningLevel())) {
      return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
          "warningLevel MUST be one of " + WARNING_LEVELS);
    }
    if (request.decisionLabel() != null
        && !DECISION_LABELS.contains(request.decisionLabel())) {
      return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
          "decisionLabel MUST be one of " + DECISION_LABELS);
    }
    if (request.featureFlagOverride() && !request.featureFlagOverrideReasonCaptured()) {
      return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
          "feature flag override MUST capture reason");
    }
    if (request.billingWebhookEvents() != null) {
      for (String e : request.billingWebhookEvents()) {
        if (!BILLING_WEBHOOK_EVENTS.contains(e)) {
          return DecisionOutcome.failed("ENTITLEMENT_BILLING_WEBHOOK_SIGNATURE_INVALID",
              "billing webhook event " + e + " MUST be one of " + BILLING_WEBHOOK_EVENTS);
        }
      }
    }
    if (request.taskQueue() == null || !TASK_QUEUES.contains(request.taskQueue())) {
      return DecisionOutcome.failed("ENTITLEMENT_PLAN_INACTIVE",
          "taskQueue MUST be one of " + TASK_QUEUES);
    }
    if (request.usageEventBytes() > E11Limits.USAGE_EVENT_MAX_BYTES) {
      return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
          "usageEventBytes MUST be <= " + E11Limits.USAGE_EVENT_MAX_BYTES);
    }
    if (!request.planTierLimitsMonotonic()) {
      return DecisionOutcome.failed("ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
          "FREE < PRO < ENTERPRISE limits MUST be monotonic");
    }
    String forbidden = E11ForbiddenPayloadKeys.firstViolation(request.payload());
    if (forbidden != null) {
      return DecisionOutcome.failed("ENTITLEMENT_USAGE_EVENT_FORBIDDEN_KEY", forbidden);
    }
    return DecisionOutcome.ok(request);
  }

  public record DecisionOutcome(
      boolean valid, EntitlementRequest request, String failureReason, String detail) {

    public static DecisionOutcome ok(EntitlementRequest request) {
      return new DecisionOutcome(true, request, null, null);
    }

    public static DecisionOutcome failed(String reason, String detail) {
      return new DecisionOutcome(false, null, reason, detail);
    }
  }

  public record EntitlementRequest(
      String tenantPseudoId,
      String actorPseudoId,
      String correlationId,
      String billingPlan,
      String deploymentModel,
      String quotaDimension,
      String quotaEnforcementScope,
      boolean quotaEnforcedPreMutation,
      boolean quotaEnforcedPreJobSubmit,
      boolean quotaEnforcedPreBillingCharge,
      boolean quotaEnforcedPreExportDownload,
      String billingProvider,
      boolean licenseFingerprintMatchesTenant,
      boolean licenseGracePeriodHonored,
      boolean billingWebhookHmacSha256,
      boolean billingWebhookSecretRotatedPerAdr,
      int billingWebhookReplayWindowSeconds,
      boolean usageEventIdempotencyKeyRequired,
      boolean usageEventsDeDuplicatedByIdempotencyKey,
      java.util.Set<String> usageEventCategories,
      String warningLevel,
      String decisionLabel,
      boolean featureFlagOverride,
      boolean featureFlagOverrideReasonCaptured,
      java.util.Set<String> billingWebhookEvents,
      String taskQueue,
      int usageEventBytes,
      boolean planTierLimitsMonotonic,
      Map<String, Object> payload) {

    public EntitlementRequest {
      if (usageEventCategories == null) {
        usageEventCategories = Set.of();
      } else {
        usageEventCategories = Set.copyOf(usageEventCategories);
      }
      if (billingWebhookEvents == null) {
        billingWebhookEvents = Set.of();
      } else {
        billingWebhookEvents = Set.copyOf(billingWebhookEvents);
      }
      payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
  }
}