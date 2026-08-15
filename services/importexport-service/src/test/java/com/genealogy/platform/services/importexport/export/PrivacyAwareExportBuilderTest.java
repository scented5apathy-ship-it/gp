package com.genealogy.platform.services.importexport.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PrivacyAwareExportBuilderTest {

  private PrivacyAwareExportBuilder.ExportBuildRequest goodRequest() {
    return new PrivacyAwareExportBuilder.ExportBuildRequest(
        PrivacyAwareExportBuilder.ExportScope.BRANCH_SUBGRAPH,
        PrivacyAwareExportBuilder.ExportFormat.GEDCOM_7,
        ExportRedactionLevel.LIVING_ONLY,
        1024L,
        false,
        null,
        false,
        "tenant-1",
        "tenant-1",
        3_600L,
        30L);
  }

  @Test
  void goodRequestIsAccepted() {
    PrivacyAwareExportBuilder.ExportBuildOutcome out = PrivacyAwareExportBuilder.validate(goodRequest());
    assertTrue(out.ok());
    assertNotNull(out.request());
  }

  @Test
  void dnaContentWithoutAllowFails() {
    PrivacyAwareExportBuilder.ExportBuildRequest req = new PrivacyAwareExportBuilder.ExportBuildRequest(
        PrivacyAwareExportBuilder.ExportScope.BRANCH_SUBGRAPH,
        PrivacyAwareExportBuilder.ExportFormat.GEDCOM_7,
        ExportRedactionLevel.LIVING_ONLY,
        1024L,
        true,
        null,
        false,
        "tenant-1",
        "tenant-1",
        3_600L,
        30L);
    PrivacyAwareExportBuilder.ExportBuildOutcome out = PrivacyAwareExportBuilder.validate(req);
    assertFalse(out.ok());
    assertEquals("EXPORT_DNA_DEFAULT_OFF_VIOLATION", out.failureReason());
  }

  @Test
  void dnaContentWithAllowButNoConsentFails() {
    PrivacyAwareExportBuilder.ExportBuildRequest req = new PrivacyAwareExportBuilder.ExportBuildRequest(
        PrivacyAwareExportBuilder.ExportScope.BRANCH_SUBGRAPH,
        PrivacyAwareExportBuilder.ExportFormat.GEDCOM_7,
        ExportRedactionLevel.CONSENT_REQUIRED,
        1024L,
        true,
        null,
        false,
        "tenant-1",
        "tenant-1",
        3_600L,
        30L);
    PrivacyAwareExportBuilder.ExportBuildOutcome out = PrivacyAwareExportBuilder.validate(req);
    assertFalse(out.ok());
    assertEquals("EXPORT_CONSENT_RECEIPT_MISSING", out.failureReason());
  }

  @Test
  void dnaContentWithConsentPasses() {
    PrivacyAwareExportBuilder.ExportBuildRequest req = new PrivacyAwareExportBuilder.ExportBuildRequest(
        PrivacyAwareExportBuilder.ExportScope.BRANCH_SUBGRAPH,
        PrivacyAwareExportBuilder.ExportFormat.GEDCOM_7,
        ExportRedactionLevel.CONSENT_REQUIRED,
        1024L,
        true,
        "DNA_PURPOSE_OWN",
        false,
        "tenant-1",
        "tenant-1",
        3_600L,
        30L);
    PrivacyAwareExportBuilder.ExportBuildOutcome out = PrivacyAwareExportBuilder.validate(req);
    assertTrue(out.ok());
  }

  @Test
  void bundleTooLargeFails() {
    PrivacyAwareExportBuilder.ExportBuildRequest req = new PrivacyAwareExportBuilder.ExportBuildRequest(
        PrivacyAwareExportBuilder.ExportScope.BRANCH_SUBGRAPH,
        PrivacyAwareExportBuilder.ExportFormat.GEDCOM_7,
        ExportRedactionLevel.LIVING_ONLY,
        600_000_000L,
        false,
        null,
        false,
        "tenant-1",
        "tenant-1",
        3_600L,
        30L);
    PrivacyAwareExportBuilder.ExportBuildOutcome out = PrivacyAwareExportBuilder.validate(req);
    assertFalse(out.ok());
    assertEquals("EXPORT_BUNDLE_TOO_LARGE", out.failureReason());
  }

  @Test
  void dnaBucketReferenceFails() {
    PrivacyAwareExportBuilder.ExportBuildRequest req = new PrivacyAwareExportBuilder.ExportBuildRequest(
        PrivacyAwareExportBuilder.ExportScope.BRANCH_SUBGRAPH,
        PrivacyAwareExportBuilder.ExportFormat.GEDCOM_7,
        ExportRedactionLevel.LIVING_ONLY,
        1024L,
        false,
        null,
        true,
        "tenant-1",
        "tenant-1",
        3_600L,
        30L);
    PrivacyAwareExportBuilder.ExportBuildOutcome out = PrivacyAwareExportBuilder.validate(req);
    assertFalse(out.ok());
    assertEquals("EXPORT_DNA_BUCKET_FORBIDDEN", out.failureReason());
  }

  @Test
  void tenantMismatchFails() {
    PrivacyAwareExportBuilder.ExportBuildRequest req = new PrivacyAwareExportBuilder.ExportBuildRequest(
        PrivacyAwareExportBuilder.ExportScope.BRANCH_SUBGRAPH,
        PrivacyAwareExportBuilder.ExportFormat.GEDCOM_7,
        ExportRedactionLevel.LIVING_ONLY,
        1024L,
        false,
        null,
        false,
        "tenant-1",
        "tenant-2",
        3_600L,
        30L);
    PrivacyAwareExportBuilder.ExportBuildOutcome out = PrivacyAwareExportBuilder.validate(req);
    assertFalse(out.ok());
    assertEquals("EXPORT_TENANT_MISMATCH", out.failureReason());
  }

  @Test
  void signUrlTtlTooShortFails() {
    PrivacyAwareExportBuilder.ExportBuildRequest req = new PrivacyAwareExportBuilder.ExportBuildRequest(
        PrivacyAwareExportBuilder.ExportScope.BRANCH_SUBGRAPH,
        PrivacyAwareExportBuilder.ExportFormat.GEDCOM_7,
        ExportRedactionLevel.LIVING_ONLY,
        1024L,
        false,
        null,
        false,
        "tenant-1",
        "tenant-1",
        100L,
        30L);
    PrivacyAwareExportBuilder.ExportBuildOutcome out = PrivacyAwareExportBuilder.validate(req);
    assertFalse(out.ok());
    assertEquals("EXPORT_SIGN_FAILED", out.failureReason());
  }

  @Test
  void redactionLevelEnumWireRoundTrip() {
    for (ExportRedactionLevel l : ExportRedactionLevel.values()) {
      assertEquals(l, ExportRedactionLevel.fromWire(l.wire()));
    }
    assertThrows(IllegalArgumentException.class,
        () -> ExportRedactionLevel.fromWire("UNKNOWN_LEVEL"));
  }

  @Test
  void dnaAllowanceMatrix() {
    assertFalse(ExportRedactionLevel.NONE.isDnaAllowed());
    assertFalse(ExportRedactionLevel.LIVING_ONLY.isDnaAllowed());
    assertFalse(ExportRedactionLevel.MINOR_ONLY.isDnaAllowed());
    assertFalse(ExportRedactionLevel.LIVING_AND_MINOR.isDnaAllowed());
    assertFalse(ExportRedactionLevel.DNA_DEFAULT_OFF.isDnaAllowed());
    assertTrue(ExportRedactionLevel.SENSITIVE_FULL.isDnaAllowed());
    assertTrue(ExportRedactionLevel.CONSENT_REQUIRED.isDnaAllowed());
  }

  @Test
  void scopeMinimumRedaction() {
    assertEquals(ExportRedactionLevel.LIVING_AND_MINOR,
        PrivacyAwareExportBuilder.ExportScope.FULL_TREE.minimumRedaction());
    assertEquals(ExportRedactionLevel.LIVING_ONLY,
        PrivacyAwareExportBuilder.ExportScope.BRANCH_SUBGRAPH.minimumRedaction());
    assertEquals(ExportRedactionLevel.LIVING_ONLY,
        PrivacyAwareExportBuilder.ExportScope.PERSON_CENTRIC.minimumRedaction());
  }
}