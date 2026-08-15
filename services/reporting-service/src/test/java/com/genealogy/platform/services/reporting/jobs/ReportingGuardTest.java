package com.genealogy.platform.services.reporting.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.reporting.jobs.ReportingGuard.JobOutcome;
import com.genealogy.platform.services.reporting.jobs.ReportingGuard.ReportJobRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportingGuardTest {

  private static ReportJobRequest happyPath() {
    return new ReportJobRequest(
        "tenant-1", "user-1", "corr-1",
        "FAMILY_BOOK", "PDF", "FAMILY_BOOK_V1",
        "PERSON_PROJECTION", "LIVING_PROTECTED",
        "GOTENBERG", "PDF_A1B",
        false, true, false, false,
        true, true,
        10_485_760L, 5_242_880L,
        true, false, false,
        true, true,
        false, true,
        true, false,
        false, false,
        true, true,
        3600,
        "report.render",
        Map.of("subject", "Hello"));
  }

  @Test
  void happyPathPasses() {
    JobOutcome outcome = ReportingGuard.validate(happyPath());
    assertTrue(outcome.valid(), () -> "unexpected failure: " + outcome.failureReason());
    assertNotNull(outcome.request());
    assertNull(outcome.failureReason());
  }

  @Test
  void nullRequestFails() {
    JobOutcome outcome = ReportingGuard.validate(null);
    assertFalse(outcome.valid());
    assertEquals("REPORT_REQUESTED", outcome.failureReason());
  }

  @Test
  void unknownReportKindFails() {
    ReportJobRequest req = mutate(happyPath()).reportKind("UNKNOWN").build();
    JobOutcome outcome = ReportingGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("REPORT_TEMPLATE_NOT_FOUND", outcome.failureReason());
  }

  @Test
  void pdfWithSelfBuiltRendererFails() {
    ReportJobRequest req = mutate(happyPath())
        .pdfRenderer("libreOffice")
        .selfBuiltPdfRenderer(true).build();
    JobOutcome outcome = ReportingGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("REPORT_GOTENBERG_SANDBOX_VIOLATION", outcome.failureReason());
  }

  @Test
  void gotenbergInternetEgressFails() {
    ReportJobRequest req = mutate(happyPath())
        .gotenbergEgressInternet(true).build();
    JobOutcome outcome = ReportingGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("REPORT_GOTENBERG_SANDBOX_VIOLATION", outcome.failureReason());
  }

  @Test
  void livingWithoutPrivacyPreviewFails() {
    ReportJobRequest req = mutate(happyPath())
        .privacyPreviewRendered(false).build();
    JobOutcome outcome = ReportingGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("REPORT_PRIVACY_PREVIEW_NOT_APPROVED", outcome.failureReason());
  }

  @Test
  void dnaContentNotRedactedFails() {
    ReportJobRequest req = mutate(happyPath())
        .dnaContentDetected(true).dnaContentRedacted(false).build();
    JobOutcome outcome = ReportingGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("REPORT_DNA_CONTENT_FORBIDDEN", outcome.failureReason());
  }

  @Test
  void analyticsRawPiiFails() {
    ReportJobRequest req = mutate(happyPath())
        .analyticsProductMetricsEnabled(true).analyticsRawPii(true).build();
    JobOutcome outcome = ReportingGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("REPORT_ANALYTICS_RAW_PII_FORBIDDEN", outcome.failureReason());
  }

  @Test
  void crossTenantSubmissionFails() {
    ReportJobRequest req = mutate(happyPath())
        .crossTenantReportSubmission(true).build();
    JobOutcome outcome = ReportingGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("REPORT_TENANT_BOUNDARY_VIOLATION", outcome.failureReason());
  }

  @Test
  void forbiddenPayloadKeyFails() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("rawGenotype", "ACGT");
    ReportJobRequest req = mutate(happyPath()).payload(payload).build();
    JobOutcome outcome = ReportingGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("rawGenotype", outcome.detail());
  }

  @Test
  void treeViewerBypassPayloadKeyFails() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("treeViewerBypass", "evil");
    ReportJobRequest req = mutate(happyPath()).payload(payload).build();
    JobOutcome outcome = ReportingGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("treeViewerBypass", outcome.detail());
  }

  @Test
  void signedUrlTooLongFails() {
    ReportJobRequest req = mutate(happyPath()).signedUrlTtlSeconds(86400).build();
    JobOutcome outcome = ReportingGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("REPORT_REQUESTED", outcome.failureReason());
  }

  @Test
  void unknownTaskQueueFails() {
    ReportJobRequest req = mutate(happyPath()).taskQueue("unknown.queue").build();
    JobOutcome outcome = ReportingGuard.validate(req);
    assertFalse(outcome.valid());
    assertEquals("REPORT_REQUESTED", outcome.failureReason());
  }

  private static RequestBuilder mutate(ReportJobRequest base) {
    return new RequestBuilder(base);
  }

  private static final class RequestBuilder {
    private String tenantPseudoId;
    private String actorPseudoId;
    private String correlationId;
    private String reportKind;
    private String outputFormat;
    private String reportTemplate;
    private String projectionSource;
    private String redactionLevel;
    private String pdfRenderer;
    private String gotenbergProfile;
    private boolean selfBuiltPdfRenderer;
    private boolean gotenbergEgressAllowlistOnly;
    private boolean gotenbergEgressInternet;
    private boolean gotenbergEgressInternalMetadata;
    private boolean templateVersionPinRequired;
    private boolean deterministicReportVersionRequired;
    private long outputBytes;
    private long previewBytes;
    private boolean treeHasLiving;
    private boolean treeHasDna;
    private boolean treeHasMinor;
    private boolean privacyPreviewRendered;
    private boolean privacyPreviewApproved;
    private boolean dnaContentDetected;
    private boolean dnaContentRedacted;
    private boolean analyticsProductMetricsEnabled;
    private boolean analyticsRawPii;
    private boolean crossTenantProjectionLookup;
    private boolean crossTenantReportSubmission;
    private boolean jobSubmissionRequiresStepUpAuth;
    private boolean signedUrlShortLived;
    private int signedUrlTtlSeconds;
    private String taskQueue;
    private Map<String, Object> payload;

    RequestBuilder(ReportJobRequest base) {
      this.tenantPseudoId = base.tenantPseudoId();
      this.actorPseudoId = base.actorPseudoId();
      this.correlationId = base.correlationId();
      this.reportKind = base.reportKind();
      this.outputFormat = base.outputFormat();
      this.reportTemplate = base.reportTemplate();
      this.projectionSource = base.projectionSource();
      this.redactionLevel = base.redactionLevel();
      this.pdfRenderer = base.pdfRenderer();
      this.gotenbergProfile = base.gotenbergProfile();
      this.selfBuiltPdfRenderer = base.selfBuiltPdfRenderer();
      this.gotenbergEgressAllowlistOnly = base.gotenbergEgressAllowlistOnly();
      this.gotenbergEgressInternet = base.gotenbergEgressInternet();
      this.gotenbergEgressInternalMetadata = base.gotenbergEgressInternalMetadata();
      this.templateVersionPinRequired = base.templateVersionPinRequired();
      this.deterministicReportVersionRequired = base.deterministicReportVersionRequired();
      this.outputBytes = base.outputBytes();
      this.previewBytes = base.previewBytes();
      this.treeHasLiving = base.treeHasLiving();
      this.treeHasDna = base.treeHasDna();
      this.treeHasMinor = base.treeHasMinor();
      this.privacyPreviewRendered = base.privacyPreviewRendered();
      this.privacyPreviewApproved = base.privacyPreviewApproved();
      this.dnaContentDetected = base.dnaContentDetected();
      this.dnaContentRedacted = base.dnaContentRedacted();
      this.analyticsProductMetricsEnabled = base.analyticsProductMetricsEnabled();
      this.analyticsRawPii = base.analyticsRawPii();
      this.crossTenantProjectionLookup = base.crossTenantProjectionLookup();
      this.crossTenantReportSubmission = base.crossTenantReportSubmission();
      this.jobSubmissionRequiresStepUpAuth = base.jobSubmissionRequiresStepUpAuth();
      this.signedUrlShortLived = base.signedUrlShortLived();
      this.signedUrlTtlSeconds = base.signedUrlTtlSeconds();
      this.taskQueue = base.taskQueue();
      this.payload = base.payload();
    }

    RequestBuilder reportKind(String v) { this.reportKind = v; return this; }
    RequestBuilder pdfRenderer(String v) { this.pdfRenderer = v; return this; }
    RequestBuilder selfBuiltPdfRenderer(boolean v) {
      this.selfBuiltPdfRenderer = v; return this;
    }
    RequestBuilder gotenbergEgressInternet(boolean v) {
      this.gotenbergEgressInternet = v; return this;
    }
    RequestBuilder privacyPreviewRendered(boolean v) {
      this.privacyPreviewRendered = v; return this;
    }
    RequestBuilder dnaContentDetected(boolean v) {
      this.dnaContentDetected = v; return this;
    }
    RequestBuilder dnaContentRedacted(boolean v) {
      this.dnaContentRedacted = v; return this;
    }
    RequestBuilder analyticsProductMetricsEnabled(boolean v) {
      this.analyticsProductMetricsEnabled = v; return this;
    }
    RequestBuilder analyticsRawPii(boolean v) {
      this.analyticsRawPii = v; return this;
    }
    RequestBuilder crossTenantReportSubmission(boolean v) {
      this.crossTenantReportSubmission = v; return this;
    }
    RequestBuilder signedUrlTtlSeconds(int v) {
      this.signedUrlTtlSeconds = v; return this;
    }
    RequestBuilder taskQueue(String v) { this.taskQueue = v; return this; }
    RequestBuilder payload(Map<String, Object> v) { this.payload = v; return this; }

    ReportJobRequest build() {
      return new ReportJobRequest(
          tenantPseudoId, actorPseudoId, correlationId, reportKind, outputFormat,
          reportTemplate, projectionSource, redactionLevel, pdfRenderer,
          gotenbergProfile, selfBuiltPdfRenderer, gotenbergEgressAllowlistOnly,
          gotenbergEgressInternet, gotenbergEgressInternalMetadata,
          templateVersionPinRequired, deterministicReportVersionRequired,
          outputBytes, previewBytes, treeHasLiving, treeHasDna, treeHasMinor,
          privacyPreviewRendered, privacyPreviewApproved, dnaContentDetected,
          dnaContentRedacted, analyticsProductMetricsEnabled, analyticsRawPii,
          crossTenantProjectionLookup, crossTenantReportSubmission,
          jobSubmissionRequiresStepUpAuth, signedUrlShortLived,
          signedUrlTtlSeconds, taskQueue, payload);
    }
  }
}