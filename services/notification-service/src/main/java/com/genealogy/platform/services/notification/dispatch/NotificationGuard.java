package com.genealogy.platform.services.notification.dispatch;

import com.genealogy.platform.services.notification.shared.E11ForbiddenPayloadKeys;
import com.genealogy.platform.services.notification.shared.E11Limits;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates notification dispatch decisions
 * against the E11.1 invariants. Mirrors
 * <code>contracts/notifications/notification-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>preference / channel / category closed-set membership;</li>
 *   <li>locale BCP-47 tag belongs to the supported notification
 *       locale catalogue;</li>
 *   <li>quiet hours timezone is IANA (never local clock);</li>
 *   <li>provider adapter is one of the ADR-E0.5-12 sanctioned
 *       adapters and SMS adapter requires an ADR;</li>
 *   <li>temporal task queue belongs to the notify.* set;</li>
 *   <li>in-app inbox acknowledgement required for compliance
 *       categories (TRANSACTIONAL_SECURITY, TRANSACTIONAL_DNA,
 *       TRANSACTIONAL_CONSENT, TRANSACTIONAL_IMPORT);</li>
 *   <li>payload MUST NOT contain any forbidden payload key (incl.
 *       <code>treeViewerBypass</code>);</li>
 *   <li>tenant-branding fields, locale fallback and rate-limit
 *       guards.</li>
 * </ul>
 */
public final class NotificationGuard {

  public static final Set<String> PREFERENCE_STATES = Set.of(
      "OPT_IN", "OPT_OUT", "QUIET", "REQUIRED");
  public static final Set<String> CHANNEL_TYPES = Set.of(
      "IN_APP", "EMAIL", "PUSH", "SMS");
  public static final Set<String> NOTIFICATION_CATEGORIES = Set.of(
      "TRANSACTIONAL_SECURITY",
      "TRANSACTIONAL_PROPOSAL",
      "TRANSACTIONAL_MENTION",
      "TRANSACTIONAL_DNA",
      "TRANSACTIONAL_CONSENT",
      "TRANSACTIONAL_IMPORT",
      "DIGEST_ACTIVITY",
      "DIGEST_ANNIVERSARY",
      "MARKETING");
  public static final Set<String> DIGEST_CADENCES = Set.of(
      "REAL_TIME", "HOURLY", "DAILY", "WEEKLY");
  public static final Set<String> INAPP_INBOX_STATES = Set.of(
      "UNREAD", "READ", "ACKNOWLEDGED", "ARCHIVED", "EXPIRED");
  public static final Set<String> NOTIFICATION_LOCALES = Set.of(
      "en-US", "en-GB", "vi-VN", "fr-FR", "de-DE",
      "es-ES", "ja-JP", "ar-SA", "he-IL");
  public static final Set<String> PROVIDER_ADAPTERS = Set.of(
      "SES_ADAPTER",
      "SENDGRID_ADAPTER",
      "SMTP_RELAY_ADAPTER",
      "FCM_PUSH_ADAPTER",
      "APNS_PUSH_ADAPTER",
      "SMS_ADAPTER_SCAFFOLD");
  public static final Set<String> TEMPORAL_TASK_QUEUES = Set.of(
      "notify.dispatch", "notify.provider", "notify.digest", "notify.inbox");
  public static final Set<String> COMPLIANCE_CATEGORIES = Set.of(
      "TRANSACTIONAL_SECURITY",
      "TRANSACTIONAL_DNA",
      "TRANSACTIONAL_CONSENT",
      "TRANSACTIONAL_IMPORT");

  private NotificationGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static DispatchOutcome validate(DispatchRequest request) {
    if (request == null) {
      return DispatchOutcome.failed("NOTIFICATION_DISPATCH_DECISION", "request MUST NOT be null");
    }
    if (request.tenantPseudoId() == null || request.tenantPseudoId().isBlank()) {
      return DispatchOutcome.failed("NOTIFICATION_PREFERENCE_OPTED_OUT", "tenantPseudoId");
    }
    if (request.actorPseudoId() == null || request.actorPseudoId().isBlank()) {
      return DispatchOutcome.failed("NOTIFICATION_PREFERENCE_OPTED_OUT", "actorPseudoId");
    }
    if (request.preferenceState() == null
        || !PREFERENCE_STATES.contains(request.preferenceState())) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PREFERENCE_OPTED_OUT",
          "preferenceState MUST be one of " + PREFERENCE_STATES);
    }
    if (request.channelType() == null
        || !CHANNEL_TYPES.contains(request.channelType())) {
      return DispatchOutcome.failed(
          "NOTIFICATION_CHANNEL_DISABLED_BY_TENANT",
          "channelType MUST be one of " + CHANNEL_TYPES);
    }
    if (request.category() == null
        || !NOTIFICATION_CATEGORIES.contains(request.category())) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PREFERENCE_OPTED_OUT",
          "category MUST be one of " + NOTIFICATION_CATEGORIES);
    }
    if (request.digestCadence() != null
        && !DIGEST_CADENCES.contains(request.digestCadence())) {
      return DispatchOutcome.failed(
          "NOTIFICATION_TEMPLATE_VERSION_STALE",
          "digestCadence MUST be one of " + DIGEST_CADENCES);
    }
    if (request.inboxState() != null
        && !INAPP_INBOX_STATES.contains(request.inboxState())) {
      return DispatchOutcome.failed(
          "NOTIFICATION_TEMPLATE_VERSION_STALE",
          "inboxState MUST be one of " + INAPP_INBOX_STATES);
    }
    if (request.locale() != null && !NOTIFICATION_LOCALES.contains(request.locale())) {
      return DispatchOutcome.failed(
          "NOTIFICATION_LOCALE_MISSING",
          "locale MUST be one of " + NOTIFICATION_LOCALES);
    }
    if (request.quietHoursTimezone() != null && !request.quietHoursTimezoneIana()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_QUIET_HOURS_ACTIVE",
          "quietHoursTimezone MUST be IANA, never local clock");
    }
    if (request.providerAdapter() == null
        || !PROVIDER_ADAPTERS.contains(request.providerAdapter())) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PROVIDER_AUTH_FAILED",
          "providerAdapter MUST be one of " + PROVIDER_ADAPTERS);
    }
    if ("SMS_ADAPTER_SCAFFOLD".equals(request.providerAdapter())
        && !request.smsAdapterAdrSigned()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PROVIDER_AUTH_FAILED",
          "SMS adapter requires ADR per ADR-E0.5-12");
    }
    if ("PUSH".equals(request.channelType()) && !request.pushOptIn()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_CHANNEL_DISABLED_BY_TENANT",
          "push channel requires opt-in per ADR-E0.5-12");
    }
    if ("MARKETING".equals(request.category()) && !request.marketingDoubleOptIn()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PREFERENCE_OPTED_OUT",
          "marketing category requires double opt-in");
    }
    if ("TRANSACTIONAL_SECURITY".equals(request.category())
        && request.preferenceState().equals("OPT_OUT")
        && !request.transactionalSecurityBypassQuietHours()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PREFERENCE_OPTED_OUT",
          "TRANSACTIONAL_SECURITY requires bypassQuietHours when OPT_OUT");
    }
    if ("TRANSACTIONAL_DNA".equals(request.category())
        && !request.consentReauthorizedAtRender()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PREFERENCE_OPTED_OUT",
          "TRANSACTIONAL_DNA requires consent re-auth at render time per E10.3");
    }
    if ("TRANSACTIONAL_CONSENT".equals(request.category())
        && !request.consentStateEffective()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PREFERENCE_OPTED_OUT",
          "TRANSACTIONAL_CONSENT requires consentStateEffective=true");
    }
    if ("TRANSACTIONAL_IMPORT".equals(request.category())
        && !request.stepUpAuthOnDownload()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PREFERENCE_OPTED_OUT",
          "TRANSACTIONAL_IMPORT requires step-up auth on download");
    }
    if (request.taskQueue() == null
        || !TEMPORAL_TASK_QUEUES.contains(request.taskQueue())) {
      return DispatchOutcome.failed(
          "NOTIFICATION_DISPATCH_DECISION",
          "taskQueue MUST be one of " + TEMPORAL_TASK_QUEUES);
    }
    if (!request.localeTemplateVersioned()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_TEMPLATE_VERSION_STALE",
          "locale template MUST be versioned");
    }
    if (!request.localeTemplateBcp47Declared()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_LOCALE_MISSING",
          "locale template MUST declare BCP-47 tag");
    }
    if (!request.templateVersionBumpApproved()
        && request.templateVersionBumpDetected()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_TEMPLATE_VERSION_STALE",
          "template version bump requires approval");
    }
    if (!request.tenantBrandingScoped()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_BRANDING_INVALID",
          "tenant branding MUST be tenant-scoped");
    }
    if (!request.brandingIncludesPreferenceCentreHref()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_BRANDING_MISSING",
          "branding MUST include preference centre URL");
    }
    if (request.complianceCategory() && !request.inboxAcknowledgedOrExpired()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PREFERENCE_OPTED_OUT",
          "compliance category requires in-app inbox acknowledgement or expiry");
    }
    if (request.crossTenantPreferenceLookup()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PREFERENCE_OPTED_OUT",
          "cross-tenant preference lookup forbidden");
    }
    if (request.crossTenantTemplateLookup()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_TEMPLATE_VERSION_STALE",
          "cross-tenant template lookup forbidden");
    }
    if (request.crossTenantInboxLookup()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PREFERENCE_OPTED_OUT",
          "cross-tenant inbox lookup forbidden");
    }
    if (!request.retryPolicyBackoffOwnedByTemporalActivity()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PROVIDER_AUTH_FAILED",
          "retry backoff MUST be owned by Temporal activity per design.md §11");
    }
    if (request.selfBuiltSmtpServer()) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PROVIDER_AUTH_FAILED",
          "self-built SMTP server is forbidden per ADR-E0.5-12");
    }
    if (request.rateLimitPerUserPerMinute() > E11Limits.RATE_LIMIT_PER_USER_PER_MINUTE) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PROVIDER_RATE_LIMITED",
          "rateLimitPerUserPerMinute > " + E11Limits.RATE_LIMIT_PER_USER_PER_MINUTE);
    }
    if (request.rateLimitPerTenantPerMinute() > E11Limits.RATE_LIMIT_PER_TENANT_PER_MINUTE) {
      return DispatchOutcome.failed(
          "NOTIFICATION_PROVIDER_RATE_LIMITED",
          "rateLimitPerTenantPerMinute > " + E11Limits.RATE_LIMIT_PER_TENANT_PER_MINUTE);
    }
    String forbidden = E11ForbiddenPayloadKeys.firstViolation(request.payload());
    if (forbidden != null) {
      return DispatchOutcome.failed("NOTIFICATION_PAYLOAD_FORBIDDEN_KEY", forbidden);
    }
    return DispatchOutcome.ok(request);
  }

  public record DispatchOutcome(
      boolean valid, DispatchRequest request, String failureReason, String detail) {

    public static DispatchOutcome ok(DispatchRequest request) {
      return new DispatchOutcome(true, request, null, null);
    }

    public static DispatchOutcome failed(String reason, String detail) {
      return new DispatchOutcome(false, null, reason, detail);
    }
  }

  public record DispatchRequest(
      String tenantPseudoId,
      String actorPseudoId,
      String correlationId,
      String preferenceState,
      String channelType,
      String category,
      String digestCadence,
      String inboxState,
      String locale,
      String quietHoursTimezone,
      boolean quietHoursTimezoneIana,
      String providerAdapter,
      boolean smsAdapterAdrSigned,
      boolean pushOptIn,
      boolean marketingDoubleOptIn,
      boolean transactionalSecurityBypassQuietHours,
      boolean consentReauthorizedAtRender,
      boolean consentStateEffective,
      boolean stepUpAuthOnDownload,
      boolean complianceCategory,
      boolean inboxAcknowledgedOrExpired,
      String taskQueue,
      boolean localeTemplateVersioned,
      boolean localeTemplateBcp47Declared,
      boolean templateVersionBumpDetected,
      boolean templateVersionBumpApproved,
      boolean tenantBrandingScoped,
      boolean brandingIncludesPreferenceCentreHref,
      boolean crossTenantPreferenceLookup,
      boolean crossTenantTemplateLookup,
      boolean crossTenantInboxLookup,
      boolean retryPolicyBackoffOwnedByTemporalActivity,
      boolean selfBuiltSmtpServer,
      int rateLimitPerUserPerMinute,
      int rateLimitPerTenantPerMinute,
      Map<String, Object> payload) {

    public DispatchRequest {
      payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
  }
}