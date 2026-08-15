package com.genealogy.platform.services.operations.admin;

import com.genealogy.platform.services.operations.shared.E11ForbiddenPayloadKeys;
import com.genealogy.platform.services.operations.shared.E11Limits;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates admin / support / operations
 * requests against the E11.5 invariants. Mirrors
 * <code>contracts/operations/admin-support-operations-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>JIT support access requires step-up auth + scoped expiry +
 *       banner + audit (R16.3);</li>
 *   <li>impersonation is disabled by default and NEVER covers DNA,
 *       consent, export raw bundle or tenant deletion (R16.4 +
 *       TM-06);</li>
 *   <li>DLQ replay requires snapshot + lineage hash; cross-tenant
 *       replay forbidden;</li>
 *   <li>projection rebuild is tenant-scoped, cross-tenant rebuild
 *       forbidden;</li>
 *   <li>feature flag override requires reason + owner + audit and
 *       MUST NEVER touch DNA_FORBIDDEN, CONSENT_FORBIDDEN,
 *       TENANT_ISOLATION_FORBIDDEN or AUDIT_FORBIDDEN categories;</li>
 *   <li>tenant switch in the same browser session requires re-auth;</li>
 *   <li>audit export is one-shot signed URL that respects
 *       retention;</li>
 *   <li>payload MUST NOT contain any forbidden payload key (incl.
 *       <code>treeViewerBypass</code>);</li>
 *   <li>impersonationMaxDurationSeconds MUST be 0 (immutable).</li>
 * </ul>
 */
public final class AdminSupportGuard {

  public static final Set<String> ADMIN_ROLES = Set.of(
      "PLATFORM_ADMIN", "TENANT_ADMIN", "SUPPORT_TIER_1", "SUPPORT_TIER_2",
      "SUPPORT_TIER_3", "SECURITY_ENGINEER", "DPO_DELEGATE", "FINANCE_OPS",
      "READ_ONLY_AUDITOR");
  public static final Set<String> SUPPORT_ACCESS_MODES = Set.of(
      "READ_ONLY", "WRITE_FIXUP", "REPLAY_DLQ", "PROJECTION_REBUILD",
      "BILLING_RECONCILE", "FEATURE_FLAG_OVERRIDE");
  public static final Set<String> ADMIN_OPERATIONS = Set.of(
      "TENANT_SUSPEND", "TENANT_REACTIVATE", "TENANT_DELETE_SCHEDULE",
      "TENANT_PURGE", "JOB_CANCEL", "JOB_RESTART", "WORKFLOW_CANCEL",
      "WORKFLOW_RESTART", "DLQ_REPLAY", "DLQ_PURGE", "PROJECTION_REBUILD",
      "FEATURE_FLAG_OVERRIDE_SET", "FEATURE_FLAG_OVERRIDE_CLEAR",
      "QUOTA_OVERRIDE_SET", "QUOTA_OVERRIDE_CLEAR", "PLAN_OVERRIDE_SET",
      "SUPPORT_JIT_GRANT", "SUPPORT_JIT_REVOKE", "IMPERSONATION_REQUEST",
      "IMPERSONATION_END", "AUDIT_EXPORT_ISSUE", "CONSENT_AUDIT_VIEW",
      "REPORT_DOWNLOAD_VIEW");
  public static final Set<String> IMPERMISSIBLE_SCOPES = Set.of(
      "DNA_RAW_DOWNLOAD", "DNA_MATCH_RAW_DOWNLOAD", "CONSENT_RAW_DOWNLOAD",
      "EXPORT_RAW_BUNDLE_DOWNLOAD", "TENANT_DELETION_FORCE",
      "ADMIN_GRANT_OVERRIDE_SELF", "IMPERSONATION_GRANT_OTHER");
  public static final Set<String> DLQ_REPLAY_MODES = Set.of(
      "SINGLE_EVENT", "TIME_WINDOW", "AGGREGATE_ID", "FULL_TOPIC");
  public static final Set<String> REBUILD_SOURCES = Set.of(
      "PERSON_PROJECTION", "RELATIONSHIP_PROJECTION", "EVENT_PROJECTION",
      "SOURCE_PROJECTION", "MEDIA_PROJECTION", "CONSENT_PROJECTION",
      "DNA_KIT_PROJECTION", "SEARCH_PROJECTION", "AUDIT_PROJECTION");
  public static final Set<String> FLAG_CATEGORIES = Set.of(
      "COMMS_GENERAL", "COMMS_PRIVACY", "UI_GENERAL", "PERFORMANCE",
      "BILLING", "OPS_INTERNAL", "DNA_FORBIDDEN", "CONSENT_FORBIDDEN",
      "TENANT_ISOLATION_FORBIDDEN", "AUDIT_FORBIDDEN");
  public static final Set<String> FORBIDDEN_FLAG_CATEGORIES = Set.of(
      "DNA_FORBIDDEN", "CONSENT_FORBIDDEN",
      "TENANT_ISOLATION_FORBIDDEN", "AUDIT_FORBIDDEN");
  public static final Set<String> JIT_STATES = Set.of(
      "REQUESTED", "PENDING_APPROVAL", "ACTIVE", "EXPIRED", "REVOKED", "DENIED");
  public static final Set<String> TASK_QUEUES = Set.of(
      "ops.admin", "ops.support", "ops.dlqReplay", "ops.projectionRebuild");

  private AdminSupportGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static OperationOutcome validate(OperationRequest request) {
    if (request == null) {
      return OperationOutcome.failed("OPERATIONS_TENANT_BOUNDARY_VIOLATION",
          "request MUST NOT be null");
    }
    if (request.tenantPseudoId() == null || request.tenantPseudoId().isBlank()) {
      return OperationOutcome.failed("OPERATIONS_TENANT_BOUNDARY_VIOLATION",
          "tenantPseudoId");
    }
    if (request.adminRole() == null || !ADMIN_ROLES.contains(request.adminRole())) {
      return OperationOutcome.failed("OPERATIONS_FEATURE_FLAG_OVERRIDE_REASON_MISSING",
          "adminRole MUST be one of " + ADMIN_ROLES);
    }
    if (request.supportAccessMode() != null
        && !SUPPORT_ACCESS_MODES.contains(request.supportAccessMode())) {
      return OperationOutcome.failed("OPERATIONS_FEATURE_FLAG_OVERRIDE_REASON_MISSING",
          "supportAccessMode MUST be one of " + SUPPORT_ACCESS_MODES);
    }
    if (request.adminOperation() != null
        && !ADMIN_OPERATIONS.contains(request.adminOperation())) {
      return OperationOutcome.failed("OPERATIONS_FEATURE_FLAG_OVERRIDE_REASON_MISSING",
          "adminOperation MUST be one of " + ADMIN_OPERATIONS);
    }
    if (request.impermissibleScopeRequested() != null
        && IMPERMISSIBLE_SCOPES.contains(request.impermissibleScopeRequested())) {
      return OperationOutcome.failed("OPERATIONS_IMPERSONATION_FORBIDDEN_SCOPE",
          "scope " + request.impermissibleScopeRequested() + " is impermissible");
    }
    if (!request.jitRequiresStepUpAuth()) {
      return OperationOutcome.failed("OPERATIONS_JIT_DENIED_BY_POLICY",
          "JIT MUST require step-up auth");
    }
    if (!request.jitRequiresScopedExpiry()) {
      return OperationOutcome.failed("OPERATIONS_JIT_DENIED_BY_POLICY",
          "JIT MUST require scoped expiry");
    }
    if (!request.jitRequiresBannerVisible()) {
      return OperationOutcome.failed("OPERATIONS_JIT_DENIED_BY_POLICY",
          "JIT MUST show banner");
    }
    if (!request.jitRequiresAuditLogEntry()) {
      return OperationOutcome.failed("OPERATIONS_JIT_DENIED_BY_POLICY",
          "JIT MUST emit audit entry");
    }
    if (request.jitMaxDurationMinutes() > E11Limits.JIT_MAX_DURATION_MINUTES) {
      return OperationOutcome.failed("OPERATIONS_JIT_DENIED_BY_POLICY",
          "jitMaxDurationMinutes > " + E11Limits.JIT_MAX_DURATION_MINUTES);
    }
    if (request.jitApprovalTimeoutSeconds() > E11Limits.JIT_APPROVAL_TIMEOUT_SECONDS) {
      return OperationOutcome.failed("OPERATIONS_JIT_APPROVAL_TIMEOUT",
          "jitApprovalTimeoutSeconds > " + E11Limits.JIT_APPROVAL_TIMEOUT_SECONDS);
    }
    if (!request.impersonationDisabledByDefault()) {
      return OperationOutcome.failed("OPERATIONS_IMPERSONATION_DISABLED",
          "impersonation MUST be disabled by default");
    }
    if (request.impersonationMaxDurationSeconds()
        != E11Limits.IMPERSONATION_MAX_DURATION_SECONDS) {
      return OperationOutcome.failed("OPERATIONS_IMPERSONATION_DISABLED",
          "impersonationMaxDurationSeconds MUST be 0");
    }
    if (request.impersonationCoversDna()) {
      return OperationOutcome.failed("OPERATIONS_IMPERSONATION_FORBIDDEN_SCOPE",
          "impersonation NEVER covers DNA");
    }
    if (request.impersonationCoversConsent()) {
      return OperationOutcome.failed("OPERATIONS_IMPERSONATION_FORBIDDEN_SCOPE",
          "impersonation NEVER covers consent");
    }
    if (request.impersonationCoversExportRawBundle()) {
      return OperationOutcome.failed("OPERATIONS_IMPERSONATION_FORBIDDEN_SCOPE",
          "impersonation NEVER covers export raw bundle");
    }
    if (request.impersonationCoversTenantDeletion()) {
      return OperationOutcome.failed("OPERATIONS_IMPERSONATION_FORBIDDEN_SCOPE",
          "impersonation NEVER covers tenant deletion");
    }
    if (request.impersonationSelfGrant()) {
      return OperationOutcome.failed("OPERATIONS_IMPERSONATION_FORBIDDEN_SCOPE",
          "impersonation self-grant forbidden");
    }
    if (request.impersonationGrantOther()) {
      return OperationOutcome.failed("OPERATIONS_IMPERSONATION_FORBIDDEN_SCOPE",
          "impersonation grant-other forbidden");
    }
    if (!request.dlqReplayRequiresSnapshot()) {
      return OperationOutcome.failed("OPERATIONS_DLQ_REPLAY_BLOCKED_NO_SNAPSHOT",
          "DLQ replay MUST require snapshot");
    }
    if (!request.dlqReplaySnapshotLineageHashMatches()) {
      return OperationOutcome.failed("OPERATIONS_DLQ_REPLAY_BLOCKED_NO_SNAPSHOT",
          "DLQ replay snapshot lineage hash MUST match");
    }
    if (request.dlqReplayMaxEventsPerRun() > E11Limits.DLQ_REPLAY_MAX_EVENTS_PER_RUN) {
      return OperationOutcome.failed("OPERATIONS_DLQ_REPLAY_FORBIDDEN_TOPIC",
          "dlqReplayMaxEventsPerRun > " + E11Limits.DLQ_REPLAY_MAX_EVENTS_PER_RUN);
    }
    if (request.dlqReplayMaxWindowHours() > E11Limits.DLQ_REPLAY_MAX_WINDOW_HOURS) {
      return OperationOutcome.failed("OPERATIONS_DLQ_REPLAY_FORBIDDEN_TOPIC",
          "dlqReplayMaxWindowHours > " + E11Limits.DLQ_REPLAY_MAX_WINDOW_HOURS);
    }
    if (request.dlqReplayMode() != null
        && !DLQ_REPLAY_MODES.contains(request.dlqReplayMode())) {
      return OperationOutcome.failed("OPERATIONS_DLQ_REPLAY_FORBIDDEN_TOPIC",
          "dlqReplayMode MUST be one of " + DLQ_REPLAY_MODES);
    }
    if (request.projectionRebuildSource() != null
        && !REBUILD_SOURCES.contains(request.projectionRebuildSource())) {
      return OperationOutcome.failed("OPERATIONS_PROJECTION_REBUILD_CROSS_TENANT_FORBIDDEN",
          "projectionRebuildSource MUST be one of " + REBUILD_SOURCES);
    }
    if (request.crossTenantProjectionRebuild()) {
      return OperationOutcome.failed(
          "OPERATIONS_PROJECTION_REBUILD_CROSS_TENANT_FORBIDDEN",
          "cross-tenant projection rebuild forbidden");
    }
    if (request.featureFlagCategory() != null
        && FORBIDDEN_FLAG_CATEGORIES.contains(request.featureFlagCategory())) {
      return OperationOutcome.failed("OPERATIONS_FEATURE_FLAG_FORBIDDEN_CATEGORY",
          "feature flag category " + request.featureFlagCategory() + " is forbidden");
    }
    if (request.featureFlagCategory() != null
        && !FLAG_CATEGORIES.contains(request.featureFlagCategory())) {
      return OperationOutcome.failed("OPERATIONS_FEATURE_FLAG_FORBIDDEN_CATEGORY",
          "featureFlagCategory MUST be one of " + FLAG_CATEGORIES);
    }
    if (request.featureFlagOverrideRequested()
        && !request.featureFlagOverrideReasonCaptured()) {
      return OperationOutcome.failed("OPERATIONS_FEATURE_FLAG_OVERRIDE_REASON_MISSING",
          "feature flag override MUST capture reason");
    }
    if (request.featureFlagOverrideRequested()
        && !request.featureFlagOverrideOwnerCaptured()) {
      return OperationOutcome.failed("OPERATIONS_FEATURE_FLAG_OVERRIDE_OWNER_MISSING",
          "feature flag override MUST capture owner");
    }
    if (request.planOverrideRequested() && !request.planOverrideReasonCaptured()) {
      return OperationOutcome.failed("OPERATIONS_PLAN_OVERRIDE_NOT_ALLOWED",
          "plan override MUST capture reason");
    }
    if (request.quotaOverrideRequested() && !request.quotaOverrideReasonCaptured()) {
      return OperationOutcome.failed("OPERATIONS_QUOTA_OVERRIDE_REASON_MISSING",
          "quota override MUST capture reason");
    }
    if (request.tenantSwitchRequested() && !request.tenantSwitchReauthCompleted()) {
      return OperationOutcome.failed("OPERATIONS_TENANT_SWITCH_REAUTH_REQUIRED",
          "tenant switch requires re-auth");
    }
    if (request.auditExportBytes() > E11Limits.AUDIT_EXPORT_MAX_BYTES) {
      return OperationOutcome.failed("OPERATIONS_AUDIT_EXPORT_FORBIDDEN_FIELD",
          "auditExportBytes > " + E11Limits.AUDIT_EXPORT_MAX_BYTES);
    }
    if (request.auditExportSignedUrlTtlSeconds()
        > E11Limits.AUDIT_EXPORT_SIGNED_URL_TTL_SECONDS) {
      return OperationOutcome.failed("OPERATIONS_AUDIT_EXPORT_FORBIDDEN_FIELD",
          "auditExportSignedUrlTtlSeconds > "
              + E11Limits.AUDIT_EXPORT_SIGNED_URL_TTL_SECONDS);
    }
    if (request.taskQueue() == null || !TASK_QUEUES.contains(request.taskQueue())) {
      return OperationOutcome.failed("OPERATIONS_TENANT_BOUNDARY_VIOLATION",
          "taskQueue MUST be one of " + TASK_QUEUES);
    }
    String forbidden = E11ForbiddenPayloadKeys.firstViolation(request.payload());
    if (forbidden != null) {
      return OperationOutcome.failed("OPERATIONS_TENANT_BOUNDARY_VIOLATION", forbidden);
    }
    return OperationOutcome.ok(request);
  }

  public record OperationOutcome(
      boolean valid, OperationRequest request, String failureReason, String detail) {

    public static OperationOutcome ok(OperationRequest request) {
      return new OperationOutcome(true, request, null, null);
    }

    public static OperationOutcome failed(String reason, String detail) {
      return new OperationOutcome(false, null, reason, detail);
    }
  }

  public record OperationRequest(
      String tenantPseudoId,
      String actorPseudoId,
      String correlationId,
      String adminRole,
      String supportAccessMode,
      String adminOperation,
      String impermissibleScopeRequested,
      boolean jitRequiresStepUpAuth,
      boolean jitRequiresScopedExpiry,
      boolean jitRequiresBannerVisible,
      boolean jitRequiresAuditLogEntry,
      int jitMaxDurationMinutes,
      int jitApprovalTimeoutSeconds,
      boolean impersonationDisabledByDefault,
      int impersonationMaxDurationSeconds,
      boolean impersonationCoversDna,
      boolean impersonationCoversConsent,
      boolean impersonationCoversExportRawBundle,
      boolean impersonationCoversTenantDeletion,
      boolean impersonationSelfGrant,
      boolean impersonationGrantOther,
      boolean dlqReplayRequiresSnapshot,
      boolean dlqReplaySnapshotLineageHashMatches,
      int dlqReplayMaxEventsPerRun,
      int dlqReplayMaxWindowHours,
      String dlqReplayMode,
      String projectionRebuildSource,
      boolean crossTenantProjectionRebuild,
      String featureFlagCategory,
      boolean featureFlagOverrideRequested,
      boolean featureFlagOverrideReasonCaptured,
      boolean featureFlagOverrideOwnerCaptured,
      boolean planOverrideRequested,
      boolean planOverrideReasonCaptured,
      boolean quotaOverrideRequested,
      boolean quotaOverrideReasonCaptured,
      boolean tenantSwitchRequested,
      boolean tenantSwitchReauthCompleted,
      long auditExportBytes,
      int auditExportSignedUrlTtlSeconds,
      String taskQueue,
      Map<String, Object> payload) {

    public OperationRequest {
      payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
  }
}