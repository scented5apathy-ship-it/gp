package com.genealogy.platform.services.notification.privacy;

import com.genealogy.platform.services.notification.shared.E11ForbiddenPayloadKeys;
import com.genealogy.platform.services.notification.shared.E11Limits;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates privacy-safe delivery decisions
 * against the E11.2 invariants. Mirrors
 * <code>contracts/notifications/notification-privacy-delivery-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>ABAC re-check at render and delivery time (R14.3, design.md
 *       §11.2);</li>
 *   <li>generic-text required for sensitive event categories
 *       (DNA, consent, guardian, support JIT, deletion, portability,
 *       impersonation, tenant suspension);</li>
 *   <li>tenant branding is tenant-scoped, includes preference centre
 *       URL and never global;</li>
 *   <li>deep-link token is single-use + short-lived;</li>
 *   <li>unsubscribe MUST be List-Unsubscribe +
 *       List-Unsubscribe-Post (RFC 8058);</li>
 *   <li>bounce / suppression / complaint lists are tenant-scoped;</li>
 *   <li>provider switch requires ADR;</li>
 *   <li>payload MUST NOT contain any forbidden payload key (incl.
 *       <code>treeViewerBypass</code>, rawGuardianReason,
 *       rawSupportReason, rawDeletionReason);</li>
 *   <li>DNA / consent / guardian / deletion categories use
 *       opaque-payload-only mode for third-party providers.</li>
 * </ul>
 */
public final class PrivacySafeDeliveryGuard {

  public static final Set<String> SENSITIVE_EVENT_CATEGORIES = Set.of(
      "DNA_KIT_REGISTERED",
      "DNA_MATCH_DISCOVERED",
      "DNA_RELATIVE_DISCOVERY",
      "CONSENT_REVOKED",
      "CONSENT_LEGAL_HOLD",
      "GUARDIAN_ACTION",
      "SUPPORT_JIT_ACCESS",
      "DELETION_REQUESTED",
      "DELETION_COMPLETED",
      "PORTABILITY_EXPORT_READY",
      "IMPERSONATION_STARTED",
      "ADMIN_TENANT_SUSPENDED",
      "TENANT_DELETION_SCHEDULED");
  public static final Set<String> GENERIC_TEMPLATES = Set.of(
      "GENERIC_DNA_EVENT",
      "GENERIC_CONSENT_EVENT",
      "GENERIC_GUARDIAN_EVENT",
      "GENERIC_SUPPORT_EVENT",
      "GENERIC_DELETION_EVENT",
      "GENERIC_PORTABILITY_EVENT",
      "GENERIC_IMPERSONATION_EVENT",
      "GENERIC_TENANT_EVENT");
  public static final Set<String> REAUTHORIZATION_TRIGGERS = Set.of(
      "PRIVACY_LEVEL_CHANGED",
      "LIVING_STATUS_CHANGED",
      "CONSENT_REVOKED",
      "CONSENT_EXPIRED",
      "TENANT_VISIBILITY_CHANGED",
      "RELATIONSHIP_REMOVED",
      "SCOPE_NARROWED",
      "SESSION_PRIVILEGE_DEMOTED");
  public static final Set<String> ABAC_DECISION_LABELS = Set.of(
      "ALLOW",
      "ALLOW_WITH_GENERIC_TEXT",
      "DENY",
      "DENY_DUE_TO_DNA_SCOPE",
      "DENY_DUE_TO_LIVING_PROTECTION",
      "DENY_DUE_TO_CONSENT_REVOKED",
      "DENY_DUE_TO_SCOPE_NARROWED");
  public static final Set<String> PROVIDER_HEADERS = Set.of(
      "List-Unsubscribe",
      "List-Unsubscribe-Post",
      "List-Id",
      "X-Entity-Ref-ID",
      "X-Genealogy-Tenant-Pseudo",
      "X-Genealogy-Delivery-Decision");
  public static final Set<String> TENANT_BRANDING_FIELDS = Set.of(
      "tenantPseudoId",
      "tenantDisplayName",
      "preferenceCenterUrl",
      "contactEmail",
      "logoUrl",
      "colorScheme",
      "footerDisclosure",
      "locale");
  public static final Set<String> OPAQUE_PAYLOAD_ONLY_CATEGORIES = Set.of(
      "DNA_KIT_REGISTERED",
      "DNA_MATCH_DISCOVERED",
      "DNA_RELATIVE_DISCOVERY",
      "CONSENT_REVOKED",
      "CONSENT_LEGAL_HOLD",
      "GUARDIAN_ACTION",
      "DELETION_REQUESTED",
      "DELETION_COMPLETED");

  private PrivacySafeDeliveryGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static DeliveryOutcome validate(DeliveryRequest request) {
    if (request == null) {
      return DeliveryOutcome.failed("PRIVACY_DELIVERY_ABAC_DENY", "request MUST NOT be null");
    }
    if (request.tenantPseudoId() == null || request.tenantPseudoId().isBlank()) {
      return DeliveryOutcome.failed("PRIVACY_DELIVERY_ABAC_DENY", "tenantPseudoId");
    }
    if (request.sensitiveEventCategory() == null
        || !SENSITIVE_EVENT_CATEGORIES.contains(request.sensitiveEventCategory())) {
      return DeliveryOutcome.failed(
          "PRIVACY_DELIVERY_ABAC_DENY",
          "sensitiveEventCategory MUST be one of " + SENSITIVE_EVENT_CATEGORIES);
    }
    if (!request.abacRecheckedAtRender()) {
      return DeliveryOutcome.failed(
          "PRIVACY_DELIVERY_REAUTHORIZATION_TRIGGERED",
          "ABAC MUST be re-checked at render time");
    }
    if (!request.abacRecheckedAtDelivery()) {
      return DeliveryOutcome.failed(
          "PRIVACY_DELIVERY_REAUTHORIZATION_TRIGGERED",
          "ABAC MUST be re-checked at delivery time");
    }
    if (request.abacDecision() == null
        || !ABAC_DECISION_LABELS.contains(request.abacDecision())) {
      return DeliveryOutcome.failed(
          "PRIVACY_DELIVERY_ABAC_DENY",
          "abacDecision MUST be one of " + ABAC_DECISION_LABELS);
    }
    if (request.abacDecision().startsWith("DENY")) {
      return DeliveryOutcome.failed(
          "PRIVACY_DELIVERY_ABAC_DENY",
          "ABAC decision is " + request.abacDecision());
    }
    if (OPAQUE_PAYLOAD_ONLY_CATEGORIES.contains(request.sensitiveEventCategory())
        && !request.opaquePayloadOnly()) {
      return DeliveryOutcome.failed(
          "PRIVACY_DELIVERY_DNA_PAYLOAD_REDACTED",
          "sensitive category " + request.sensitiveEventCategory()
              + " MUST use opaque-payload-only mode");
    }
    if (request.abacDecision().equals("ALLOW_WITH_GENERIC_TEXT")
        && !request.genericTextApplied()) {
      return DeliveryOutcome.failed(
          "PRIVACY_GENERIC_TEXT_MISSING",
          "genericTextApplied MUST be true when ABAC decision is ALLOW_WITH_GENERIC_TEXT");
    }
    if (request.genericTextTemplate() != null
        && !GENERIC_TEMPLATES.contains(request.genericTextTemplate())) {
      return DeliveryOutcome.failed(
          "PRIVACY_GENERIC_TEXT_MISSING",
          "genericTextTemplate MUST be one of " + GENERIC_TEMPLATES);
    }
    if (!request.tenantBrandingScoped()) {
      return DeliveryOutcome.failed(
          "PRIVACY_BRANDING_INVALID",
          "tenant branding MUST be tenant-scoped");
    }
    if (!request.globalBrandingForbidden()) {
      return DeliveryOutcome.failed(
          "PRIVACY_BRANDING_INVALID",
          "global branding MUST be forbidden");
    }
    if (!request.brandingFieldsCovered().containsAll(TENANT_BRANDING_FIELDS)) {
      return DeliveryOutcome.failed(
          "PRIVACY_BRANDING_MISSING",
          "branding fields MUST cover " + TENANT_BRANDING_FIELDS);
    }
    if (request.preferenceCentreUrl() == null
        || !request.preferenceCentreUrl().startsWith("https://")) {
      return DeliveryOutcome.failed(
          "PRIVACY_BRANDING_INVALID",
          "preferenceCentreUrl MUST be https://");
    }
    if (!request.providerHeaders().containsAll(PROVIDER_HEADERS)) {
      return DeliveryOutcome.failed(
          "PRIVACY_UNSUBSCRIBE_HEADER_MISSING",
          "provider headers MUST cover " + PROVIDER_HEADERS);
    }
    if (request.deepLinkToken() == null
        || request.deepLinkToken().isBlank()
        || request.deepLinkToken().length() > E11Limits.DEEP_LINK_TOKEN_MAX_BYTES) {
      return DeliveryOutcome.failed(
          "PRIVACY_DEEP_LINK_TOKEN_EXPIRED",
          "deepLinkToken MUST be present and <= "
              + E11Limits.DEEP_LINK_TOKEN_MAX_BYTES + " bytes");
    }
    if (request.deepLinkTokenReused()) {
      return DeliveryOutcome.failed(
          "PRIVACY_DEEP_LINK_TOKEN_REUSED",
          "deepLinkToken MUST be single-use");
    }
    if (request.deepLinkTokenTtlSeconds() > E11Limits.DEEP_LINK_TOKEN_TTL_SECONDS) {
      return DeliveryOutcome.failed(
          "PRIVACY_DEEP_LINK_TOKEN_EXPIRED",
          "deepLinkTokenTtlSeconds MUST be <= " + E11Limits.DEEP_LINK_TOKEN_TTL_SECONDS);
    }
    if (request.crossTenantBranding()) {
      return DeliveryOutcome.failed(
          "PRIVACY_BRANDING_INVALID",
          "cross-tenant branding forbidden");
    }
    if (request.crossTenantSuppression()) {
      return DeliveryOutcome.failed(
          "PRIVACY_DELIVERY_SUPPRESSION_LIST_HIT",
          "cross-tenant suppression lookup forbidden");
    }
    if (!request.providerSwitchHasAdr()) {
      return DeliveryOutcome.failed(
          "PRIVACY_PROVIDER_SWITCH_BLOCKED",
          "provider switch requires ADR-E0.5-12 amendment");
    }
    if (!request.reauthorizationTriggers().containsAll(REAUTHORIZATION_TRIGGERS)) {
      return DeliveryOutcome.failed(
          "PRIVACY_DELIVERY_REAUTHORIZATION_TRIGGERED",
          "reauthorizationTriggers MUST cover all required triggers");
    }
    if (request.outboundAttachment()) {
      return DeliveryOutcome.failed(
          "PRIVACY_BODY_CONTAINS_FORBIDDEN_KEY",
          "outbound attachments are forbidden");
    }
    if (request.outboundInlineImage() && !request.outboundInlineImageSignedUrl()) {
      return DeliveryOutcome.failed(
          "PRIVACY_BODY_CONTAINS_FORBIDDEN_KEY",
          "outbound inline image MUST use signed URL");
    }
    if (request.outboundBodyBytes() > E11Limits.OUTBOUND_BODY_MAX_BYTES) {
      return DeliveryOutcome.failed(
          "PRIVACY_BODY_CONTAINS_FORBIDDEN_KEY",
          "outbound body MUST be <= " + E11Limits.OUTBOUND_BODY_MAX_BYTES);
    }
    String forbidden = E11ForbiddenPayloadKeys.firstViolation(request.payload());
    if (forbidden != null) {
      return DeliveryOutcome.failed("PRIVACY_BODY_CONTAINS_FORBIDDEN_KEY", forbidden);
    }
    return DeliveryOutcome.ok(request);
  }

  public record DeliveryOutcome(
      boolean valid, DeliveryRequest request, String failureReason, String detail) {

    public static DeliveryOutcome ok(DeliveryRequest request) {
      return new DeliveryOutcome(true, request, null, null);
    }

    public static DeliveryOutcome failed(String reason, String detail) {
      return new DeliveryOutcome(false, null, reason, detail);
    }
  }

  public record DeliveryRequest(
      String tenantPseudoId,
      String actorPseudoId,
      String correlationId,
      String sensitiveEventCategory,
      boolean abacRecheckedAtRender,
      boolean abacRecheckedAtDelivery,
      String abacDecision,
      boolean opaquePayloadOnly,
      boolean genericTextApplied,
      String genericTextTemplate,
      boolean tenantBrandingScoped,
      boolean globalBrandingForbidden,
      java.util.Set<String> brandingFieldsCovered,
      String preferenceCentreUrl,
      java.util.Set<String> providerHeaders,
      String deepLinkToken,
      boolean deepLinkTokenReused,
      int deepLinkTokenTtlSeconds,
      boolean crossTenantBranding,
      boolean crossTenantSuppression,
      boolean providerSwitchHasAdr,
      java.util.Set<String> reauthorizationTriggers,
      boolean outboundAttachment,
      boolean outboundInlineImage,
      boolean outboundInlineImageSignedUrl,
      int outboundBodyBytes,
      Map<String, Object> payload) {

    public DeliveryRequest {
      brandingFieldsCovered =
          brandingFieldsCovered == null ? java.util.Set.of() : java.util.Set.copyOf(brandingFieldsCovered);
      providerHeaders =
          providerHeaders == null ? java.util.Set.of() : java.util.Set.copyOf(providerHeaders);
      reauthorizationTriggers =
          reauthorizationTriggers == null
              ? java.util.Set.of()
              : java.util.Set.copyOf(reauthorizationTriggers);
      payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
  }
}