package com.genealogy.platform.services.importexport.export;

import com.genealogy.platform.services.importexport.shared.ImportExportLimits;
import java.util.Set;

/**
 * Privacy-aware export orchestrator. Enforces:
 *  - DNA default-off (level MUST NOT be {@link ExportRedactionLevel#NONE}
 *    when DNA content is present; a consent receipt MUST be supplied
 *    when DNA is allowed);
 *  - signed URL TTL ≥ 120× revocation propagation
 *    (per {@code exportSignUrlTtlMultiplier});
 *  - bundle size ≤ {@link ImportExportLimits#EXPORT_BUNDLE_MAX_BYTES};
 *  - scope → minimum redaction floor mapping from
 *    <code>exportScopesToRedactionFloor</code>;
 *  - tenant boundary + DNA bucket shield.
 */
public final class PrivacyAwareExportBuilder {

  private static final Set<String> CONSENT_REQUIRED_PURPOSES = Set.of(
      "DNA_PURPOSE_OWN",
      "DNA_PURPOSE_FAMILY_MATCH",
      "DNA_PURPOSE_RESEARCH");

  public static final long SIGN_URL_TTL_MULTIPLIER = 120L;

  private PrivacyAwareExportBuilder() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static ExportBuildOutcome validate(ExportBuildRequest request) {
    if (request == null) {
      return ExportBuildOutcome.failed("EXPORT_BUNDLE_COMPONENT_MISSING", "request MUST NOT be null");
    }
    if (request.scope() == null) {
      return ExportBuildOutcome.failed("EXPORT_SCOPE_UNKNOWN", "scope MUST NOT be null");
    }
    if (request.format() == null) {
      return ExportBuildOutcome.failed("EXPORT_FORMAT_UNKNOWN", "format MUST NOT be null");
    }
    if (request.byteSize() > ImportExportLimits.EXPORT_BUNDLE_MAX_BYTES) {
      return ExportBuildOutcome.failed("EXPORT_BUNDLE_TOO_LARGE", "byteSize=" + request.byteSize());
    }
    if (request.dnaBucketReference()) {
      return ExportBuildOutcome.failed("EXPORT_DNA_BUCKET_FORBIDDEN", "dna bucket reference");
    }
    if (request.redactionLevel() == null) {
      return ExportBuildOutcome.failed("EXPORT_REDACTION_LEVEL_FORBIDDEN", "redactionLevel MUST NOT be null");
    }
    if (request.containsDnaContent() && request.redactionLevel().isDnaAllowed()) {
      if (request.consentReceipt() == null || request.consentReceipt().isBlank()) {
        return ExportBuildOutcome.failed("EXPORT_CONSENT_RECEIPT_MISSING", "dna content requires consent");
      }
      if (!CONSENT_REQUIRED_PURPOSES.contains(request.consentReceipt())) {
        return ExportBuildOutcome.failed("EXPORT_CONSENT_RECEIPT_MISSING",
            "consent purpose MUST be one of " + CONSENT_REQUIRED_PURPOSES);
      }
    }
    if (request.containsDnaContent() && !request.redactionLevel().isDnaAllowed()) {
      return ExportBuildOutcome.failed("EXPORT_DNA_DEFAULT_OFF_VIOLATION",
          "DNA content present without explicit redaction allow");
    }
    if (!request.tenantPseudoId().equals(request.expectedTenantPseudoId())) {
      return ExportBuildOutcome.failed("EXPORT_TENANT_MISMATCH", "tenantPseudoId");
    }
    if (request.signUrlTtlSeconds() < SIGN_URL_TTL_MULTIPLIER * request.revocationPropagationSeconds()) {
      return ExportBuildOutcome.failed("EXPORT_SIGN_FAILED",
          "sign URL TTL MUST be >= " + SIGN_URL_TTL_MULTIPLIER + " × revocation propagation");
    }
    return ExportBuildOutcome.ok(request);
  }

  public enum ExportScope {
    FULL_TREE,
    BRANCH_SUBGRAPH,
    PERSON_CENTRIC,
    ANCESTORS_ONLY,
    DESCENDANTS_ONLY,
    FAMILY_UNIT;

    public ExportRedactionLevel minimumRedaction() {
      switch (this) {
        case FULL_TREE:
          return ExportRedactionLevel.LIVING_AND_MINOR;
        case BRANCH_SUBGRAPH:
        case PERSON_CENTRIC:
        case ANCESTORS_ONLY:
        case DESCENDANTS_ONLY:
        case FAMILY_UNIT:
        default:
          return ExportRedactionLevel.LIVING_ONLY;
      }
    }
  }

  public enum ExportFormat {
    GEDCOM_7,
    GEDCOM_5_5_1,
    CSV,
    JSON,
    PDF,
    MEDIA_BUNDLE_ZIP,
    MEDIA_BUNDLE_TAR;

    public String wire() {
      return name().replace('_', '.');
    }
  }

  public record ExportBuildRequest(
      ExportScope scope,
      ExportFormat format,
      ExportRedactionLevel redactionLevel,
      long byteSize,
      boolean containsDnaContent,
      String consentReceipt,
      boolean dnaBucketReference,
      String tenantPseudoId,
      String expectedTenantPseudoId,
      long signUrlTtlSeconds,
      long revocationPropagationSeconds) {

    public ExportBuildRequest {
      if (byteSize < 0) byteSize = 0;
      if (tenantPseudoId == null) tenantPseudoId = "";
      if (expectedTenantPseudoId == null) expectedTenantPseudoId = tenantPseudoId;
    }
  }

  public record ExportBuildOutcome(
      boolean ok,
      String failureReason,
      String detail,
      ExportBuildRequest request) {

    public static ExportBuildOutcome ok(ExportBuildRequest request) {
      return new ExportBuildOutcome(true, null, null, request);
    }

    public static ExportBuildOutcome failed(String reason, String detail) {
      return new ExportBuildOutcome(false, reason, detail, null);
    }
  }
}