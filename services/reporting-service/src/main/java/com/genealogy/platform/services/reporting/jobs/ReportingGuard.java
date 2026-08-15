package com.genealogy.platform.services.reporting.jobs;

import com.genealogy.platform.services.reporting.shared.E11Limits;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates reporting service requests
 * against the E11.3 invariants. Mirrors
 * <code>contracts/reporting/reporting-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>report kind, output format, template and projection source
 *       closed-set membership;</li>
 *   <li>Gotenberg is the only PDF renderer (self-built PDF renderer
 *       is forbidden);</li>
 *   <li>preview-before-finalization required when the tree contains
 *       living, DNA or minor data;</li>
 *   <li>DNA / raw sensitive content forbidden in analytics product
 *       metrics and report output;</li>
 *   <li>cross-tenant projection lookup / report submission
 *       forbidden;</li>
 *   <li>Gotenberg sandbox egress: allowlist only, no internet, no
 *       internal cluster metadata;</li>
 *   <li>deterministic report version + pinned definition hash
 *       required;</li>
 *   <li>job submission requires step-up auth + signed short-lived
 *       URL;</li>
 *   <li>payload MUST NOT contain any forbidden payload key (incl.
 *       <code>treeViewerBypass</code>).</li>
 * </ul>
 */
public final class ReportingGuard {

  public static final Set<String> REPORT_KINDS = Set.of(
      "COMPLETENESS_DASHBOARD",
      "CONFLICT_DASHBOARD",
      "ORPHAN_DASHBOARD",
      "DUPLICATE_DASHBOARD",
      "DEMOGRAPHICS_SUMMARY",
      "FAMILY_BOOK",
      "TIMELINE_PERSON",
      "TIMELINE_FAMILY",
      "ANNIVERSARY_LIST",
      "SOURCE_COVERAGE",
      "RELATIONSHIP_COVERAGE",
      "TENANT_HEALTH_REPORT");
  public static final Set<String> REPORT_OUTPUT_FORMATS = Set.of("PDF", "CSV", "JSON");
  public static final Set<String> REPORT_TEMPLATES = Set.of(
      "COMPLETENESS_V1", "CONFLICT_V1", "ORPHAN_V1", "DUPLICATE_V1",
      "DEMOGRAPHICS_V1", "FAMILY_BOOK_V1", "TIMELINE_PERSON_V1",
      "TIMELINE_FAMILY_V1", "ANNIVERSARY_LIST_V1", "SOURCE_COVERAGE_V1",
      "RELATIONSHIP_COVERAGE_V1", "TENANT_HEALTH_V1");
  public static final Set<String> PROJECTION_SOURCES = Set.of(
      "PERSON_PROJECTION", "RELATIONSHIP_PROJECTION", "EVENT_PROJECTION",
      "SOURCE_PROJECTION", "MEDIA_PROJECTION", "CONSENT_PROJECTION",
      "DNA_KIT_PROJECTION");
  public static final Set<String> REDACTION_LEVELS = Set.of(
      "FULL", "LIVING_PROTECTED", "MINOR_PROTECTED", "DNA_REDACTED", "PUBLIC");
  public static final Set<String> GOTENBERG_PROFILES = Set.of(
      "PDF_A1B", "PDF_A2B", "PDF_A3B", "PDF_SCREEN");
  public static final Set<String> TASK_QUEUES = Set.of(
      "report.render", "report.pdf", "report.analytics",
      "report.projectionRebuild");

  private ReportingGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static JobOutcome validate(ReportJobRequest request) {
    if (request == null) {
      return JobOutcome.failed("REPORT_REQUESTED", "request MUST NOT be null");
    }
    if (request.tenantPseudoId() == null || request.tenantPseudoId().isBlank()) {
      return JobOutcome.failed("REPORT_TENANT_BOUNDARY_VIOLATION", "tenantPseudoId");
    }
    if (request.reportKind() == null || !REPORT_KINDS.contains(request.reportKind())) {
      return JobOutcome.failed("REPORT_TEMPLATE_NOT_FOUND",
          "reportKind MUST be one of " + REPORT_KINDS);
    }
    if (request.outputFormat() == null
        || !REPORT_OUTPUT_FORMATS.contains(request.outputFormat())) {
      return JobOutcome.failed("REPORT_OUTPUT_TOO_LARGE",
          "outputFormat MUST be one of " + REPORT_OUTPUT_FORMATS);
    }
    if (request.reportTemplate() == null
        || !REPORT_TEMPLATES.contains(request.reportTemplate())) {
      return JobOutcome.failed("REPORT_TEMPLATE_VERSION_STALE",
          "reportTemplate MUST be one of " + REPORT_TEMPLATES);
    }
    if (request.projectionSource() != null
        && !PROJECTION_SOURCES.contains(request.projectionSource())) {
      return JobOutcome.failed("REPORT_PROJECTION_REBUILD_REQUIRED",
          "projectionSource MUST be one of " + PROJECTION_SOURCES);
    }
    if (request.redactionLevel() != null
        && !REDACTION_LEVELS.contains(request.redactionLevel())) {
      return JobOutcome.failed("REPORT_LIVING_PROTECTION_VIOLATION",
          "redactionLevel MUST be one of " + REDACTION_LEVELS);
    }
    if ("PDF".equals(request.outputFormat())) {
      if (!"GOTENBERG".equals(request.pdfRenderer())) {
        return JobOutcome.failed("REPORT_GOTENBERG_SANDBOX_VIOLATION",
            "PDF renderer MUST be GOTENBERG");
      }
      if (request.gotenbergProfile() != null
          && !GOTENBERG_PROFILES.contains(request.gotenbergProfile())) {
        return JobOutcome.failed("REPORT_GOTENBERG_SANDBOX_VIOLATION",
            "gotenbergProfile MUST be one of " + GOTENBERG_PROFILES);
      }
      if (request.selfBuiltPdfRenderer()) {
        return JobOutcome.failed("REPORT_GOTENBERG_SANDBOX_VIOLATION",
            "self-built PDF renderer forbidden");
      }
      if (!request.gotenbergEgressAllowlistOnly()) {
        return JobOutcome.failed("REPORT_GOTENBERG_SANDBOX_VIOLATION",
            "Gotenberg egress MUST be allowlist-only");
      }
      if (request.gotenbergEgressInternet()) {
        return JobOutcome.failed("REPORT_GOTENBERG_SANDBOX_VIOLATION",
            "Gotenberg egress MUST NOT include internet");
      }
      if (request.gotenbergEgressInternalMetadata()) {
        return JobOutcome.failed("REPORT_GOTENBERG_SANDBOX_VIOLATION",
            "Gotenberg egress MUST NOT include cluster metadata");
      }
    }
    if (!request.templateVersionPinRequired()) {
      return JobOutcome.failed("REPORT_TEMPLATE_VERSION_STALE",
          "template version pin REQUIRED");
    }
    if (!request.deterministicReportVersionRequired()) {
      return JobOutcome.failed("REPORT_DETERMINISTIC_HASH_MISMATCH",
          "deterministic report version REQUIRED");
    }
    if (request.outputBytes() > E11Limits.REPORT_MAX_OUTPUT_BYTES) {
      return JobOutcome.failed("REPORT_OUTPUT_TOO_LARGE",
          "outputBytes MUST be <= " + E11Limits.REPORT_MAX_OUTPUT_BYTES);
    }
    if (request.previewBytes() > E11Limits.REPORT_PREVIEW_MAX_BYTES) {
      return JobOutcome.failed("REPORT_OUTPUT_TOO_LARGE",
          "previewBytes MUST be <= " + E11Limits.REPORT_PREVIEW_MAX_BYTES);
    }
    if (request.treeHasLiving() || request.treeHasDna() || request.treeHasMinor()) {
      if (!request.privacyPreviewRendered()) {
        return JobOutcome.failed("REPORT_PRIVACY_PREVIEW_NOT_APPROVED",
            "privacy preview MUST be rendered before finalization");
      }
    }
    if (!request.privacyPreviewApproved()) {
      return JobOutcome.failed("REPORT_PRIVACY_PREVIEW_NOT_APPROVED",
          "privacy preview MUST be approved before finalization");
    }
    if (request.dnaContentDetected() && !request.dnaContentRedacted()) {
      return JobOutcome.failed("REPORT_DNA_CONTENT_FORBIDDEN",
          "DNA content detected and not redacted");
    }
    if (request.analyticsProductMetricsEnabled() && request.analyticsRawPii()) {
      return JobOutcome.failed("REPORT_ANALYTICS_RAW_PII_FORBIDDEN",
          "analytics product metrics MUST NOT contain raw PII");
    }
    if (request.crossTenantProjectionLookup()) {
      return JobOutcome.failed("REPORT_TENANT_BOUNDARY_VIOLATION",
          "cross-tenant projection lookup forbidden");
    }
    if (request.crossTenantReportSubmission()) {
      return JobOutcome.failed("REPORT_TENANT_BOUNDARY_VIOLATION",
          "cross-tenant report submission forbidden");
    }
    if (!request.jobSubmissionRequiresStepUpAuth()) {
      return JobOutcome.failed("REPORT_REQUESTED",
          "job submission requires step-up auth");
    }
    if (!request.signedUrlShortLived()) {
      return JobOutcome.failed("REPORT_REQUESTED",
          "signed URL MUST be short-lived");
    }
    if (request.signedUrlTtlSeconds() > E11Limits.SIGNED_URL_TTL_SECONDS) {
      return JobOutcome.failed("REPORT_REQUESTED",
          "signedUrlTtlSeconds MUST be <= " + E11Limits.SIGNED_URL_TTL_SECONDS);
    }
    if (request.taskQueue() == null || !TASK_QUEUES.contains(request.taskQueue())) {
      return JobOutcome.failed("REPORT_REQUESTED",
          "taskQueue MUST be one of " + TASK_QUEUES);
    }
    String forbidden = com.genealogy.platform.services.reporting.shared.E11ForbiddenPayloadKeys
        .firstViolation(request.payload());
    if (forbidden != null) {
      return JobOutcome.failed("REPORT_DNA_CONTENT_REJECTED", forbidden);
    }
    return JobOutcome.ok(request);
  }

  public record JobOutcome(
      boolean valid, ReportJobRequest request, String failureReason, String detail) {

    public static JobOutcome ok(ReportJobRequest request) {
      return new JobOutcome(true, request, null, null);
    }

    public static JobOutcome failed(String reason, String detail) {
      return new JobOutcome(false, null, reason, detail);
    }
  }

  public record ReportJobRequest(
      String tenantPseudoId,
      String actorPseudoId,
      String correlationId,
      String reportKind,
      String outputFormat,
      String reportTemplate,
      String projectionSource,
      String redactionLevel,
      String pdfRenderer,
      String gotenbergProfile,
      boolean selfBuiltPdfRenderer,
      boolean gotenbergEgressAllowlistOnly,
      boolean gotenbergEgressInternet,
      boolean gotenbergEgressInternalMetadata,
      boolean templateVersionPinRequired,
      boolean deterministicReportVersionRequired,
      long outputBytes,
      long previewBytes,
      boolean treeHasLiving,
      boolean treeHasDna,
      boolean treeHasMinor,
      boolean privacyPreviewRendered,
      boolean privacyPreviewApproved,
      boolean dnaContentDetected,
      boolean dnaContentRedacted,
      boolean analyticsProductMetricsEnabled,
      boolean analyticsRawPii,
      boolean crossTenantProjectionLookup,
      boolean crossTenantReportSubmission,
      boolean jobSubmissionRequiresStepUpAuth,
      boolean signedUrlShortLived,
      int signedUrlTtlSeconds,
      String taskQueue,
      Map<String, Object> payload) {

    public ReportJobRequest {
      payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
  }
}