package com.genealogy.platform.services.importexport.gedcom;

import com.genealogy.platform.services.importexport.shared.ImportExportLimits;

/**
 * Streaming GEDCOM parser guard. The parser itself lives in the
 * importexport-service sandbox; this orchestrator enforces the
 * size / depth / count / encoding limits from
 * <code>contracts/importexport/gedcom-parser-validator-policy.yaml</code>.
 *
 * <p>The parser MUST refuse:</p>
 * <ul>
 *   <li>payloads over {@link ImportExportLimits#GEDCOM_MAX_PAYLOAD_BYTES};</li>
 *   <li>line lengths over {@link ImportExportLimits#GEDCOM_MAX_LINE_LENGTH};</li>
 *   <li>nesting depth over {@link ImportExportLimits#GEDCOM_MAX_DEPTH};</li>
 *   <li>record counts over {@link ImportExportLimits#GEDCOM_MAX_RECORD_COUNT};</li>
 *   <li>DNA bucket keys;</li>
 *   <li>non-UTF-8 / non-UTF-16 / non-ASCII encodings;</li>
 *   <li>any forbidden payload pattern (delegated).</li>
 * </ul>
 */
public final class GedcomParserLimits {

  private GedcomParserLimits() {
    throw new UnsupportedOperationException("constants holder");
  }

  public static GedcomParseOutcome validate(GedcomPayload payload) {
    if (payload == null) {
      return GedcomParseOutcome.failed(GedcomFailureReason.GEDCOM_PAYLOAD_TOO_LARGE, "payload MUST NOT be null");
    }
    if (payload.byteSize() > ImportExportLimits.GEDCOM_MAX_PAYLOAD_BYTES) {
      return GedcomParseOutcome.failed(GedcomFailureReason.GEDCOM_PAYLOAD_TOO_LARGE, "byteSize=" + payload.byteSize());
    }
    if (payload.maxLineLength() > ImportExportLimits.GEDCOM_MAX_LINE_LENGTH) {
      return GedcomParseOutcome.failed(GedcomFailureReason.GEDCOM_LINE_LENGTH_EXCEEDED,
          "maxLineLength=" + payload.maxLineLength());
    }
    if (payload.maxDepth() > ImportExportLimits.GEDCOM_MAX_DEPTH) {
      return GedcomParseOutcome.failed(GedcomFailureReason.GEDCOM_DEPTH_EXCEEDED,
          "maxDepth=" + payload.maxDepth());
    }
    if (payload.recordCount() > ImportExportLimits.GEDCOM_MAX_RECORD_COUNT) {
      return GedcomParseOutcome.failed(GedcomFailureReason.GEDCOM_RECORD_COUNT_EXCEEDED,
          "recordCount=" + payload.recordCount());
    }
    if (!payload.encoding().equals(GedcomEncoding.UTF_8)
        && !payload.encoding().equals(GedcomEncoding.UTF_16LE)
        && !payload.encoding().equals(GedcomEncoding.UTF_16BE)
        && !payload.encoding().equals(GedcomEncoding.ASCII)) {
      return GedcomParseOutcome.failed(GedcomFailureReason.GEDCOM_ENCODING_INVALID, payload.encoding().wire());
    }
    if (payload.dnaBucketReference()) {
      return GedcomParseOutcome.failed(GedcomFailureReason.GEDCOM_DNA_BUCKET_FORBIDDEN, "dna bucket reference");
    }
    return GedcomParseOutcome.ok(payload);
  }

  public enum GedcomEncoding {
    UTF_8,
    UTF_16LE,
    UTF_16BE,
    ASCII;

    public String wire() {
      return name().replace('_', '-');
    }
  }

  public record GedcomPayload(
      long byteSize,
      int maxLineLength,
      int maxDepth,
      int recordCount,
      GedcomEncoding encoding,
      boolean dnaBucketReference) {

    public GedcomPayload {
      if (byteSize < 0) byteSize = 0;
      if (maxLineLength < 0) maxLineLength = 0;
      if (maxDepth < 0) maxDepth = 0;
      if (recordCount < 0) recordCount = 0;
      if (encoding == null) encoding = GedcomEncoding.UTF_8;
    }
  }

  public record GedcomParseOutcome(
      boolean ok,
      GedcomFailureReason failureReason,
      String detail,
      GedcomPayload payload) {

    public static GedcomParseOutcome ok(GedcomPayload payload) {
      return new GedcomParseOutcome(true, null, null, payload);
    }

    public static GedcomParseOutcome failed(GedcomFailureReason reason, String detail) {
      return new GedcomParseOutcome(false, reason, detail, null);
    }
  }
}