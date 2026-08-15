package com.genealogy.platform.services.operations.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.operations.admin.AdminSupportGuard.OperationOutcome;
import com.genealogy.platform.services.operations.admin.AdminSupportGuard.OperationRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminSupportGuardTest {

  private static OperationRequest happyPath() {
    return new OperationRequest(
        "tenant-1", "user-1", "corr-1",
        "SUPPORT_TIER_2",
        "READ_ONLY",
        "DLQ_REPLAY",
        null,
        true, true, true, true,
        120,
        600,
        true,
        0,
        false, false, false, false,
        false, false,
        true, true,
        5000,
        72,
        "TIME_WINDOW",
        "PERSON_PROJECTION",
        false,
        "COMMS_GENERAL",
        false, true, true,
        false, true,
        false, true,
        false, true,
        10_485_760L,
        3600,
        "ops.support",
        Map.of("subject", "Hello"));
  }

  @Test
  void happyPathPasses() {
    OperationOutcome outcome = AdminSupportGuard.validate(happyPath());
    assertTrue(outcome.valid(), () -> "unexpected failure: " + outcome.failureReason());
    assertNotNull(outcome.request());
    assertNull(outcome.failureReason());
  }

  @Test
  void nullRequestFails() {
    OperationOutcome outcome = AdminSupportGuard.validate(null);
    assertFalse(outcome.valid());
    assertEquals("OPERATIONS_TENANT_BOUNDARY_VIOLATION", outcome.failureReason());
  }

  @Test
  void unknownAdminRoleFails() {
    OperationRequest req = mutate(happyPath()).adminRole("UNKNOWN").build();
    OperationOutcome outcome = AdminSupportGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("OPERATIONS_FEATURE_FLAG_OVERRIDE_REASON_MISSING", outcome.failureReason());
  }

  @Test
  void impersonationNotDisabledByDefaultFails() {
    OperationRequest req = mutate(happyPath()).impersonationDisabledByDefault(false).build();
    OperationOutcome outcome = AdminSupportGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("OPERATIONS_IMPERSONATION_DISABLED", outcome.failureReason());
  }

  @Test
  void impersonationCoversDnaFails() {
    OperationRequest req = mutate(happyPath()).impersonationCoversDna(true).build();
    OperationOutcome outcome = AdminSupportGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("OPERATIONS_IMPERSONATION_FORBIDDEN_SCOPE", outcome.failureReason());
  }

  @Test
  void dlqReplayWithoutSnapshotFails() {
    OperationRequest req = mutate(happyPath()).dlqReplayRequiresSnapshot(false).build();
    OperationOutcome outcome = AdminSupportGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("OPERATIONS_DLQ_REPLAY_BLOCKED_NO_SNAPSHOT", outcome.failureReason());
  }

  @Test
  void crossTenantProjectionRebuildFails() {
    OperationRequest req = mutate(happyPath()).crossTenantProjectionRebuild(true).build();
    OperationOutcome outcome = AdminSupportGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("OPERATIONS_PROJECTION_REBUILD_CROSS_TENANT_FORBIDDEN",
        outcome.failureReason());
  }

  @Test
  void dnaForbiddenFlagCategoryFails() {
    OperationRequest req = mutate(happyPath())
        .featureFlagOverrideRequested(true)
        .featureFlagCategory("DNA_FORBIDDEN").build();
    OperationOutcome outcome = AdminSupportGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("OPERATIONS_FEATURE_FLAG_FORBIDDEN_CATEGORY", outcome.failureReason());
  }

  @Test
  void consentForbiddenFlagCategoryFails() {
    OperationRequest req = mutate(happyPath())
        .featureFlagOverrideRequested(true)
        .featureFlagCategory("CONSENT_FORBIDDEN").build();
    OperationOutcome outcome = AdminSupportGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("OPERATIONS_FEATURE_FLAG_FORBIDDEN_CATEGORY", outcome.failureReason());
  }

  @Test
  void featureFlagOverrideWithoutReasonFails() {
    OperationRequest req = mutate(happyPath())
        .featureFlagOverrideRequested(true)
        .featureFlagOverrideReasonCaptured(false).build();
    OperationOutcome outcome = AdminSupportGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("OPERATIONS_FEATURE_FLAG_OVERRIDE_REASON_MISSING", outcome.failureReason());
  }

  @Test
  void tenantSwitchWithoutReauthFails() {
    OperationRequest req = mutate(happyPath())
        .tenantSwitchRequested(true).tenantSwitchReauthCompleted(false).build();
    OperationOutcome outcome = AdminSupportGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("OPERATIONS_TENANT_SWITCH_REAUTH_REQUIRED", outcome.failureReason());
  }

  @Test
  void forbiddenPayloadKeyFails() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("treeViewerBypass", "evil");
    OperationRequest req = mutate(happyPath()).payload(payload).build();
    OperationOutcome outcome = AdminSupportGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("treeViewerBypass", outcome.detail());
  }

  @Test
  void auditExportTooLargeFails() {
    OperationRequest req = mutate(happyPath())
        .auditExportBytes(1_073_741_824L).build();
    OperationOutcome outcome = AdminSupportGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("OPERATIONS_AUDIT_EXPORT_FORBIDDEN_FIELD", outcome.failureReason());
  }

  @Test
  void jitMaxDurationTooLongFails() {
    OperationRequest req = mutate(happyPath()).jitMaxDurationMinutes(500).build();
    OperationOutcome outcome = AdminSupportGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("OPERATIONS_JIT_DENIED_BY_POLICY", outcome.failureReason());
  }

  @Test
  void impermissibleScopeDnaFails() {
    OperationRequest req = mutate(happyPath())
        .impermissibleScopeRequested("DNA_RAW_DOWNLOAD").build();
    OperationOutcome outcome = AdminSupportGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("OPERATIONS_IMPERSONATION_FORBIDDEN_SCOPE", outcome.failureReason());
  }

  private static RequestBuilder mutate(OperationRequest base) {
    return new RequestBuilder(base);
  }

  private static final class RequestBuilder {
    private String tenantPseudoId;
    private String actorPseudoId;
    private String correlationId;
    private String adminRole;
    private String supportAccessMode;
    private String adminOperation;
    private String impermissibleScopeRequested;
    private boolean jitRequiresStepUpAuth;
    private boolean jitRequiresScopedExpiry;
    private boolean jitRequiresBannerVisible;
    private boolean jitRequiresAuditLogEntry;
    private int jitMaxDurationMinutes;
    private int jitApprovalTimeoutSeconds;
    private boolean impersonationDisabledByDefault;
    private int impersonationMaxDurationSeconds;
    private boolean impersonationCoversDna;
    private boolean impersonationCoversConsent;
    private boolean impersonationCoversExportRawBundle;
    private boolean impersonationCoversTenantDeletion;
    private boolean impersonationSelfGrant;
    private boolean impersonationGrantOther;
    private boolean dlqReplayRequiresSnapshot;
    private boolean dlqReplaySnapshotLineageHashMatches;
    private int dlqReplayMaxEventsPerRun;
    private int dlqReplayMaxWindowHours;
    private String dlqReplayMode;
    private String projectionRebuildSource;
    private boolean crossTenantProjectionRebuild;
    private String featureFlagCategory;
    private boolean featureFlagOverrideRequested;
    private boolean featureFlagOverrideReasonCaptured;
    private boolean featureFlagOverrideOwnerCaptured;
    private boolean planOverrideRequested;
    private boolean planOverrideReasonCaptured;
    private boolean quotaOverrideRequested;
    private boolean quotaOverrideReasonCaptured;
    private boolean tenantSwitchRequested;
    private boolean tenantSwitchReauthCompleted;
    private long auditExportBytes;
    private int auditExportSignedUrlTtlSeconds;
    private String taskQueue;
    private Map<String, Object> payload;

    RequestBuilder(OperationRequest base) {
      this.tenantPseudoId = base.tenantPseudoId();
      this.actorPseudoId = base.actorPseudoId();
      this.correlationId = base.correlationId();
      this.adminRole = base.adminRole();
      this.supportAccessMode = base.supportAccessMode();
      this.adminOperation = base.adminOperation();
      this.impermissibleScopeRequested = base.impermissibleScopeRequested();
      this.jitRequiresStepUpAuth = base.jitRequiresStepUpAuth();
      this.jitRequiresScopedExpiry = base.jitRequiresScopedExpiry();
      this.jitRequiresBannerVisible = base.jitRequiresBannerVisible();
      this.jitRequiresAuditLogEntry = base.jitRequiresAuditLogEntry();
      this.jitMaxDurationMinutes = base.jitMaxDurationMinutes();
      this.jitApprovalTimeoutSeconds = base.jitApprovalTimeoutSeconds();
      this.impersonationDisabledByDefault = base.impersonationDisabledByDefault();
      this.impersonationMaxDurationSeconds = base.impersonationMaxDurationSeconds();
      this.impersonationCoversDna = base.impersonationCoversDna();
      this.impersonationCoversConsent = base.impersonationCoversConsent();
      this.impersonationCoversExportRawBundle = base.impersonationCoversExportRawBundle();
      this.impersonationCoversTenantDeletion = base.impersonationCoversTenantDeletion();
      this.impersonationSelfGrant = base.impersonationSelfGrant();
      this.impersonationGrantOther = base.impersonationGrantOther();
      this.dlqReplayRequiresSnapshot = base.dlqReplayRequiresSnapshot();
      this.dlqReplaySnapshotLineageHashMatches = base.dlqReplaySnapshotLineageHashMatches();
      this.dlqReplayMaxEventsPerRun = base.dlqReplayMaxEventsPerRun();
      this.dlqReplayMaxWindowHours = base.dlqReplayMaxWindowHours();
      this.dlqReplayMode = base.dlqReplayMode();
      this.projectionRebuildSource = base.projectionRebuildSource();
      this.crossTenantProjectionRebuild = base.crossTenantProjectionRebuild();
      this.featureFlagCategory = base.featureFlagCategory();
      this.featureFlagOverrideRequested = base.featureFlagOverrideRequested();
      this.featureFlagOverrideReasonCaptured = base.featureFlagOverrideReasonCaptured();
      this.featureFlagOverrideOwnerCaptured = base.featureFlagOverrideOwnerCaptured();
      this.planOverrideRequested = base.planOverrideRequested();
      this.planOverrideReasonCaptured = base.planOverrideReasonCaptured();
      this.quotaOverrideRequested = base.quotaOverrideRequested();
      this.quotaOverrideReasonCaptured = base.quotaOverrideReasonCaptured();
      this.tenantSwitchRequested = base.tenantSwitchRequested();
      this.tenantSwitchReauthCompleted = base.tenantSwitchReauthCompleted();
      this.auditExportBytes = base.auditExportBytes();
      this.auditExportSignedUrlTtlSeconds = base.auditExportSignedUrlTtlSeconds();
      this.taskQueue = base.taskQueue();
      this.payload = base.payload();
    }

    RequestBuilder adminRole(String v) { this.adminRole = v; return this; }
    RequestBuilder impersonationDisabledByDefault(boolean v) {
      this.impersonationDisabledByDefault = v; return this;
    }
    RequestBuilder impersonationCoversDna(boolean v) {
      this.impersonationCoversDna = v; return this;
    }
    RequestBuilder dlqReplayRequiresSnapshot(boolean v) {
      this.dlqReplayRequiresSnapshot = v; return this;
    }
    RequestBuilder crossTenantProjectionRebuild(boolean v) {
      this.crossTenantProjectionRebuild = v; return this;
    }
    RequestBuilder featureFlagCategory(String v) {
      this.featureFlagCategory = v; return this;
    }
    RequestBuilder featureFlagOverrideRequested(boolean v) {
      this.featureFlagOverrideRequested = v; return this;
    }
    RequestBuilder featureFlagOverrideReasonCaptured(boolean v) {
      this.featureFlagOverrideReasonCaptured = v; return this;
    }
    RequestBuilder tenantSwitchRequested(boolean v) {
      this.tenantSwitchRequested = v; return this;
    }
    RequestBuilder tenantSwitchReauthCompleted(boolean v) {
      this.tenantSwitchReauthCompleted = v; return this;
    }
    RequestBuilder auditExportBytes(long v) {
      this.auditExportBytes = v; return this;
    }
    RequestBuilder jitMaxDurationMinutes(int v) {
      this.jitMaxDurationMinutes = v; return this;
    }
    RequestBuilder impermissibleScopeRequested(String v) {
      this.impermissibleScopeRequested = v; return this;
    }
    RequestBuilder payload(Map<String, Object> v) { this.payload = v; return this; }

    OperationRequest build() {
      return new OperationRequest(
          tenantPseudoId, actorPseudoId, correlationId, adminRole,
          supportAccessMode, adminOperation, impermissibleScopeRequested,
          jitRequiresStepUpAuth, jitRequiresScopedExpiry, jitRequiresBannerVisible,
          jitRequiresAuditLogEntry, jitMaxDurationMinutes, jitApprovalTimeoutSeconds,
          impersonationDisabledByDefault, impersonationMaxDurationSeconds,
          impersonationCoversDna, impersonationCoversConsent,
          impersonationCoversExportRawBundle, impersonationCoversTenantDeletion,
          impersonationSelfGrant, impersonationGrantOther, dlqReplayRequiresSnapshot,
          dlqReplaySnapshotLineageHashMatches, dlqReplayMaxEventsPerRun,
          dlqReplayMaxWindowHours, dlqReplayMode, projectionRebuildSource,
          crossTenantProjectionRebuild, featureFlagCategory,
          featureFlagOverrideRequested, featureFlagOverrideReasonCaptured,
          featureFlagOverrideOwnerCaptured, planOverrideRequested,
          planOverrideReasonCaptured, quotaOverrideRequested,
          quotaOverrideReasonCaptured, tenantSwitchRequested,
          tenantSwitchReauthCompleted, auditExportBytes,
          auditExportSignedUrlTtlSeconds, taskQueue, payload);
    }
  }
}