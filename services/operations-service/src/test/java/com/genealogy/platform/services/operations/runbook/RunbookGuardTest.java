package com.genealogy.platform.services.operations.runbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.operations.runbook.RunbookGuard.Outcome;
import com.genealogy.platform.services.operations.runbook.RunbookGuard.Procedure;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RunbookGuardTest {

  private Procedure canonical(String name) {
    List<String> redactions = name.equals("support_bundle")
        ? List.of("redact_secrets", "redact_pii", "redact_dna",
            "redact_raw_payloads", "redact_jwt",
            "redact_session_cookie", "redact_oauth_client_secret",
            "redact_audit_stream", "redact_consent_receipt",
            "redact_tree_viewer_bypass")
        : List.of("redact_secrets", "redact_pii", "redact_dna");
    return new Procedure(name, "sre_primary", "SEV2",
        "2026-08-01", 10,
        ".kiro/specs/genealogy-platform/evidence/E14.5.md",
        "runbook/index.md",
        redactions);
  }

  @Test
  void allEightProceduresAreAccepted() {
    for (String name : E14RunbookLimits.MANDATORY_PROCEDURES) {
      Outcome out = RunbookGuard.validateProcedure(canonical(name));
      assertEquals(RunbookGuard.STATE_OK, out.state,
          () -> name + " was " + out.violationCode);
    }
  }

  @Test
  void unknownProcedureNameIsRejected() {
    Outcome out = RunbookGuard.validateProcedure(canonical("not_a_real"));
    assertEquals(RunbookGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("UNKNOWN_PROCEDURE"));
  }

  @Test
  void unknownOwnerRoleIsRejected() {
    Procedure p = new Procedure("install", "random_role", "SEV2",
        "2026-08-01", 10,
        ".kiro/specs/genealogy-platform/evidence/E14.5.md",
        "runbook/index.md",
        List.of("redact_secrets", "redact_pii", "redact_dna"));
    Outcome out = RunbookGuard.validateProcedure(p);
    assertEquals(RunbookGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("UNKNOWN_OWNER"));
  }

  @Test
  void unknownSeverityIsRejected() {
    Procedure p = new Procedure("install", "sre_primary", "SEV9",
        "2026-08-01", 10,
        ".kiro/specs/genealogy-platform/evidence/E14.5.md",
        "runbook/index.md",
        List.of("redact_secrets", "redact_pii", "redact_dna"));
    Outcome out = RunbookGuard.validateProcedure(p);
    assertEquals(RunbookGuard.STATE_INVALID, out.state);
  }

  @Test
  void blankLastReviewedAtIsRejected() {
    Procedure p = new Procedure("install", "sre_primary", "SEV2",
        "", 10,
        ".kiro/specs/genealogy-platform/evidence/E14.5.md",
        "runbook/index.md",
        List.of("redact_secrets", "redact_pii", "redact_dna"));
    Outcome out = RunbookGuard.validateProcedure(p);
    assertEquals(RunbookGuard.STATE_INVALID, out.state);
  }

  @Test
  void reviewOverdueIsStale() {
    Procedure p = canonical("install");
    Procedure stale = new Procedure("install", p.owner, p.severity,
        "2024-01-01", 200, p.evidenceAnchor, p.runbookPath,
        p.redactionRequirements);
    Outcome out = RunbookGuard.validateProcedure(stale);
    assertEquals(RunbookGuard.STATE_STALE, out.state);
    assertTrue(out.violationCode.contains("RUNBOOK_REVIEW_OVERDUE"));
  }

  @Test
  void evidenceAnchorOutsideEvidenceDirIsRejected() {
    Procedure p = canonical("install");
    Procedure bad = new Procedure("install", p.owner, p.severity,
        p.lastReviewedAt, p.daysSinceReview,
        "evidence/E14.5.md", p.runbookPath, p.redactionRequirements);
    Outcome out = RunbookGuard.validateProcedure(bad);
    assertEquals(RunbookGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("EVIDENCE_ANCHOR_INVALID"));
  }

  @Test
  void runbookPathOutsideRunbookDirIsRejected() {
    Procedure p = canonical("install");
    Procedure bad = new Procedure("install", p.owner, p.severity,
        p.lastReviewedAt, p.daysSinceReview,
        p.evidenceAnchor, "docs/runbook.md", p.redactionRequirements);
    Outcome out = RunbookGuard.validateProcedure(bad);
    assertEquals(RunbookGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("RUNBOOK_PATH_INVALID"));
  }

  @Test
  void redactionRulesBelowMinimumIsRejected() {
    Procedure p = canonical("install");
    Procedure bad = new Procedure("install", p.owner, p.severity,
        p.lastReviewedAt, p.daysSinceReview,
        p.evidenceAnchor, p.runbookPath,
        List.of("redact_secrets"));
    Outcome out = RunbookGuard.validateProcedure(bad);
    assertEquals(RunbookGuard.STATE_OVER_LIMIT, out.state);
    assertTrue(out.violationCode.contains("REDACTION_RULES_BELOW_MINIMUM"));
  }

  @Test
  void supportBundleWithAllRedactionsIsAccepted() {
    Outcome out = RunbookGuard.validateSupportBundle(
        E14RunbookLimits.REDACTIONS);
    assertEquals(RunbookGuard.STATE_OK, out.state);
  }

  @Test
  void supportBundleMissingRedactionIsForbidden() {
    Set<String> applied = new LinkedHashSet<>(
        E14RunbookLimits.REDACTIONS);
    applied.remove("redact_tree_viewer_bypass");
    Outcome out = RunbookGuard.validateSupportBundle(applied);
    assertEquals(RunbookGuard.STATE_FORBIDDEN, out.state);
    assertTrue(out.violationCode.contains("SUPPORT_BUNDLE_REDACTION_MISSING"));
  }

  @Test
  void sharedResponsibilityCustomerManagedIsAccepted() {
    Outcome out = RunbookGuard.validateSharedResponsibility(
        "kubernetes_cluster", "customer_managed");
    assertEquals(RunbookGuard.STATE_OK, out.state);
  }

  @Test
  void sharedResponsibilityPlatformManagedIsAccepted() {
    Outcome out = RunbookGuard.validateSharedResponsibility(
        "postgres_database", "platform_managed");
    assertEquals(RunbookGuard.STATE_OK, out.state);
  }

  @Test
  void sharedResponsibilityUnknownAreaIsRejected() {
    Outcome out = RunbookGuard.validateSharedResponsibility(
        "kubernetes_service", "platform_managed");
    assertEquals(RunbookGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("UNKNOWN_AREA"));
  }

  @Test
  void supportChannelPortalIsAccepted() {
    Outcome out = RunbookGuard.validateSupportChannel("portal");
    assertEquals(RunbookGuard.STATE_OK, out.state);
  }

  @Test
  void supportChannelAdHocEmailIsRejected() {
    Outcome out = RunbookGuard.validateSupportChannel(
        "personal_email_jane");
    assertEquals(RunbookGuard.STATE_INVALID, out.state);
  }

  @Test
  void bundleSizeWithinLimitIsAccepted() {
    long bytes = 500L * 1024L * 1024L;
    Outcome out = RunbookGuard.validateBundleSize(bytes);
    assertEquals(RunbookGuard.STATE_OK, out.state);
  }

  @Test
  void bundleSizeOverLimitIsRejected() {
    long bytes = 5L * 1024L * 1024L * 1024L;
    Outcome out = RunbookGuard.validateBundleSize(bytes);
    assertEquals(RunbookGuard.STATE_OVER_LIMIT, out.state);
  }

  @Test
  void transitionDraftToReviewIsAccepted() {
    Outcome out = RunbookGuard.validateTransition(
        RunbookGuard.STATUS_DRAFT, RunbookGuard.STATUS_REVIEW);
    assertEquals(RunbookGuard.STATE_OK, out.state);
  }

  @Test
  void transitionSupersededIsTerminal() {
    Outcome ok = RunbookGuard.validateTransition(
        RunbookGuard.STATUS_PUBLISHED, RunbookGuard.STATUS_SUPERSEDED);
    assertEquals(RunbookGuard.STATE_OK, ok.state);
    Outcome bad = RunbookGuard.validateTransition(
        RunbookGuard.STATUS_SUPERSEDED, RunbookGuard.STATUS_DRAFT);
    assertFalse(bad.state.equals(RunbookGuard.STATE_OK));
  }
}