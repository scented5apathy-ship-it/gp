package com.genealogy.platform.services.notification;

import com.genealogy.platform.services.notification.dispatch.NotificationGuard.DispatchRequest;
import java.util.Map;

/**
 * Test-only builder for {@link DispatchRequest}. Mirrors the E10.x
 * test-helper convention; keeps tests free of constructor-arg churn.
 */
public final class NotificationGuardTestHelper {

  private NotificationGuardTestHelper() {
    throw new UnsupportedOperationException("test helper");
  }

  public static final class RequestBuilder {
    private String tenantPseudoId;
    private String actorPseudoId;
    private String correlationId;
    private String preferenceState;
    private String channelType;
    private String category;
    private String digestCadence;
    private String inboxState;
    private String locale;
    private String quietHoursTimezone;
    private boolean quietHoursTimezoneIana;
    private String providerAdapter;
    private boolean smsAdapterAdrSigned;
    private boolean pushOptIn;
    private boolean marketingDoubleOptIn;
    private boolean transactionalSecurityBypassQuietHours;
    private boolean consentReauthorizedAtRender;
    private boolean consentStateEffective;
    private boolean stepUpAuthOnDownload;
    private boolean complianceCategory;
    private boolean inboxAcknowledgedOrExpired;
    private String taskQueue;
    private boolean localeTemplateVersioned;
    private boolean localeTemplateBcp47Declared;
    private boolean templateVersionBumpDetected;
    private boolean templateVersionBumpApproved;
    private boolean tenantBrandingScoped;
    private boolean brandingIncludesPreferenceCentreHref;
    private boolean crossTenantPreferenceLookup;
    private boolean crossTenantTemplateLookup;
    private boolean crossTenantInboxLookup;
    private boolean retryPolicyBackoffOwnedByTemporalActivity;
    private boolean selfBuiltSmtpServer;
    private int rateLimitPerUserPerMinute;
    private int rateLimitPerTenantPerMinute;
    private Map<String, Object> payload;

    public RequestBuilder(DispatchRequest base) {
      this.tenantPseudoId = base.tenantPseudoId();
      this.actorPseudoId = base.actorPseudoId();
      this.correlationId = base.correlationId();
      this.preferenceState = base.preferenceState();
      this.channelType = base.channelType();
      this.category = base.category();
      this.digestCadence = base.digestCadence();
      this.inboxState = base.inboxState();
      this.locale = base.locale();
      this.quietHoursTimezone = base.quietHoursTimezone();
      this.quietHoursTimezoneIana = base.quietHoursTimezoneIana();
      this.providerAdapter = base.providerAdapter();
      this.smsAdapterAdrSigned = base.smsAdapterAdrSigned();
      this.pushOptIn = base.pushOptIn();
      this.marketingDoubleOptIn = base.marketingDoubleOptIn();
      this.transactionalSecurityBypassQuietHours =
          base.transactionalSecurityBypassQuietHours();
      this.consentReauthorizedAtRender = base.consentReauthorizedAtRender();
      this.consentStateEffective = base.consentStateEffective();
      this.stepUpAuthOnDownload = base.stepUpAuthOnDownload();
      this.complianceCategory = base.complianceCategory();
      this.inboxAcknowledgedOrExpired = base.inboxAcknowledgedOrExpired();
      this.taskQueue = base.taskQueue();
      this.localeTemplateVersioned = base.localeTemplateVersioned();
      this.localeTemplateBcp47Declared = base.localeTemplateBcp47Declared();
      this.templateVersionBumpDetected = base.templateVersionBumpDetected();
      this.templateVersionBumpApproved = base.templateVersionBumpApproved();
      this.tenantBrandingScoped = base.tenantBrandingScoped();
      this.brandingIncludesPreferenceCentreHref =
          base.brandingIncludesPreferenceCentreHref();
      this.crossTenantPreferenceLookup = base.crossTenantPreferenceLookup();
      this.crossTenantTemplateLookup = base.crossTenantTemplateLookup();
      this.crossTenantInboxLookup = base.crossTenantInboxLookup();
      this.retryPolicyBackoffOwnedByTemporalActivity =
          base.retryPolicyBackoffOwnedByTemporalActivity();
      this.selfBuiltSmtpServer = base.selfBuiltSmtpServer();
      this.rateLimitPerUserPerMinute = base.rateLimitPerUserPerMinute();
      this.rateLimitPerTenantPerMinute = base.rateLimitPerTenantPerMinute();
      this.payload = base.payload();
    }

    public RequestBuilder preferenceState(String v) { this.preferenceState = v; return this; }
    public RequestBuilder channelType(String v) { this.channelType = v; return this; }
    public RequestBuilder category(String v) { this.category = v; return this; }
    public RequestBuilder digestCadence(String v) { this.digestCadence = v; return this; }
    public RequestBuilder inboxState(String v) { this.inboxState = v; return this; }
    public RequestBuilder locale(String v) { this.locale = v; return this; }
    public RequestBuilder quietHoursTimezone(String v) {
      this.quietHoursTimezone = v; return this;
    }
    public RequestBuilder quietHoursTimezoneIana(boolean v) {
      this.quietHoursTimezoneIana = v; return this;
    }
    public RequestBuilder providerAdapter(String v) { this.providerAdapter = v; return this; }
    public RequestBuilder smsAdapterAdrSigned(boolean v) {
      this.smsAdapterAdrSigned = v; return this;
    }
    public RequestBuilder pushOptIn(boolean v) { this.pushOptIn = v; return this; }
    public RequestBuilder marketingDoubleOptIn(boolean v) {
      this.marketingDoubleOptIn = v; return this;
    }
    public RequestBuilder transactionalSecurityBypassQuietHours(boolean v) {
      this.transactionalSecurityBypassQuietHours = v; return this;
    }
    public RequestBuilder consentReauthorizedAtRender(boolean v) {
      this.consentReauthorizedAtRender = v; return this;
    }
    public RequestBuilder consentStateEffective(boolean v) {
      this.consentStateEffective = v; return this;
    }
    public RequestBuilder stepUpAuthOnDownload(boolean v) {
      this.stepUpAuthOnDownload = v; return this;
    }
    public RequestBuilder complianceCategory(boolean v) {
      this.complianceCategory = v; return this;
    }
    public RequestBuilder inboxAcknowledgedOrExpired(boolean v) {
      this.inboxAcknowledgedOrExpired = v; return this;
    }
    public RequestBuilder taskQueue(String v) { this.taskQueue = v; return this; }
    public RequestBuilder localeTemplateVersioned(boolean v) {
      this.localeTemplateVersioned = v; return this;
    }
    public RequestBuilder localeTemplateBcp47Declared(boolean v) {
      this.localeTemplateBcp47Declared = v; return this;
    }
    public RequestBuilder templateVersionBumpDetected(boolean v) {
      this.templateVersionBumpDetected = v; return this;
    }
    public RequestBuilder templateVersionBumpApproved(boolean v) {
      this.templateVersionBumpApproved = v; return this;
    }
    public RequestBuilder tenantBrandingScoped(boolean v) {
      this.tenantBrandingScoped = v; return this;
    }
    public RequestBuilder brandingIncludesPreferenceCentreHref(boolean v) {
      this.brandingIncludesPreferenceCentreHref = v; return this;
    }
    public RequestBuilder crossTenantPreferenceLookup(boolean v) {
      this.crossTenantPreferenceLookup = v; return this;
    }
    public RequestBuilder crossTenantTemplateLookup(boolean v) {
      this.crossTenantTemplateLookup = v; return this;
    }
    public RequestBuilder crossTenantInboxLookup(boolean v) {
      this.crossTenantInboxLookup = v; return this;
    }
    public RequestBuilder retryPolicyBackoffOwnedByTemporalActivity(boolean v) {
      this.retryPolicyBackoffOwnedByTemporalActivity = v; return this;
    }
    public RequestBuilder selfBuiltSmtpServer(boolean v) {
      this.selfBuiltSmtpServer = v; return this;
    }
    public RequestBuilder rateLimitPerUserPerMinute(int v) {
      this.rateLimitPerUserPerMinute = v; return this;
    }
    public RequestBuilder rateLimitPerTenantPerMinute(int v) {
      this.rateLimitPerTenantPerMinute = v; return this;
    }
    public RequestBuilder payload(Map<String, Object> v) { this.payload = v; return this; }

    public DispatchRequest build() {
      return new DispatchRequest(
          tenantPseudoId, actorPseudoId, correlationId,
          preferenceState, channelType, category, digestCadence, inboxState,
          locale, quietHoursTimezone, quietHoursTimezoneIana,
          providerAdapter, smsAdapterAdrSigned, pushOptIn, marketingDoubleOptIn,
          transactionalSecurityBypassQuietHours, consentReauthorizedAtRender,
          consentStateEffective, stepUpAuthOnDownload, complianceCategory,
          inboxAcknowledgedOrExpired, taskQueue, localeTemplateVersioned,
          localeTemplateBcp47Declared, templateVersionBumpDetected,
          templateVersionBumpApproved, tenantBrandingScoped,
          brandingIncludesPreferenceCentreHref, crossTenantPreferenceLookup,
          crossTenantTemplateLookup, crossTenantInboxLookup,
          retryPolicyBackoffOwnedByTemporalActivity, selfBuiltSmtpServer,
          rateLimitPerUserPerMinute, rateLimitPerTenantPerMinute, payload);
    }
  }
}