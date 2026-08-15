package com.genealogy.platform.services.dna.rawupload;

import com.genealogy.platform.services.dna.shared.DnaForbiddenPayloadKeys;
import com.genealogy.platform.services.dna.shared.DnaLimits;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates DNA raw upload + matching
 * requests against the E10.4 invariants. Mirrors
 * <code>contracts/dna/dna-raw-upload-matching-policy.yaml</code>.
 */
public final class DnaRawUploadGuard {

  public static final Set<String> PROVIDERS = Set.of(
      "ANCESTRYDNA",
      "TWENTY_THREE_AND_ME",
      "MYHERITAGE",
      "FAMILY_TREE_DNA",
      "LIVING_DNA",
      "SELF_UPLOAD_PROVIDED",
      "RESEARCH_PARTNER");

  public static final Set<String> FORMATS = Set.of(
      "CSV_ANCESTRYDNA",
      "CSV_TWENTY_THREE_AND_ME",
      "CSV_MYHERITAGE",
      "CSV_FAMILY_TREE_DNA",
      "FASTQ_ILLUMINA",
      "BAM_ILLUMINA",
      "VCF_GENEVA",
      "VCF_ANCESTRY");

  public static final Set<String> ALGORITHMS = Set.of(
      "IBD_SEGMENT_V1",
      "IBD_SEGMENT_V2",
      "IBD_SEGMENT_V2_OPAQUE",
      "KINSHIP_ESTIMATE_V1",
      "KINSHIP_ESTIMATE_V2_OPAQUE",
      "RELATIVE_DISCOVERY_V1");

  public static final Set<String> STAGES = Set.of(
      "QUARANTINED", "FORMAT_VALIDATED", "ENVELOPE_ENCRYPTED",
      "DECRYPTED_IN_MEMORY", "SEGMENTED", "ESTIMATED",
      "ANNOTATED", "COMMITTED", "REJECTED");

  public static final Set<String> NODE_POOL_LABELS = Set.of(
      "dna-worker=true",
      "dna-tier=genetic",
      "dna-bucket-bound=true",
      "dna-vault-bound=true");

  public static final Set<String> TASK_QUEUES = Set.of(
      "dna.upload", "dna.match", "dna.revoke", "dna.export");

  private DnaRawUploadGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static RawUploadOutcome validate(RawUploadRequest request) {
    if (request == null) {
      return RawUploadOutcome.failed("DNA_MATCH_KIT_NOT_FOUND", "request MUST NOT be null");
    }
    if (request.tenantPseudoId() == null || request.tenantPseudoId().isBlank()) {
      return RawUploadOutcome.failed("DNA_MATCH_TENANT_MISMATCH", "tenantPseudoId");
    }
    if (request.provider() == null || !PROVIDERS.contains(request.provider())) {
      return RawUploadOutcome.failed("DNA_FORMAT_UNSUPPORTED",
          "provider MUST be one of " + PROVIDERS);
    }
    if (request.format() == null || !FORMATS.contains(request.format())) {
      return RawUploadOutcome.failed("DNA_FORMAT_UNSUPPORTED",
          "format MUST be one of " + FORMATS);
    }
    if (request.payloadBytes() < 0) {
      return RawUploadOutcome.failed("DNA_UPLOAD_PAYLOAD_ENCODING_INVALID",
          "payloadBytes MUST be >= 0");
    }
    if (request.payloadBytes() > DnaLimits.RAW_UPLOAD_MAX_BYTES) {
      return RawUploadOutcome.failed("DNA_UPLOAD_PAYLOAD_TOO_LARGE",
          "payloadBytes MUST be <= " + DnaLimits.RAW_UPLOAD_MAX_BYTES);
    }
    if (request.payloadDepth() < 0 || request.payloadDepth() > 32) {
      return RawUploadOutcome.failed("DNA_UPLOAD_PAYLOAD_DEPTH_EXCEEDED",
          "payloadDepth MUST be in [0,32]");
    }
    if (!request.envelopeEncrypted()) {
      return RawUploadOutcome.failed("DNA_ENVELOPE_DECRYPT_FAILED",
          "envelopeEncrypted MUST be true before any processing");
    }
    if (request.envelopeKeyRevoked()) {
      return RawUploadOutcome.failed("DNA_ENVELOPE_KEY_REVOKED",
          "envelope key has been revoked");
    }
    if (request.taskQueue() == null || !TASK_QUEUES.contains(request.taskQueue())) {
      return RawUploadOutcome.failed("DNA_MATCH_WORKER_POOL_VIOLATED",
          "taskQueue MUST be one of " + TASK_QUEUES);
    }
    if (request.nodePoolLabels() == null
        || !request.nodePoolLabels().containsAll(NODE_POOL_LABELS)) {
      return RawUploadOutcome.failed("DNA_MATCH_WORKER_POOL_VIOLATED",
          "missing required node-pool labels: " + NODE_POOL_LABELS);
    }
    if (request.algorithmVersion() == null
        || !ALGORITHMS.contains(request.algorithmVersion())) {
      return RawUploadOutcome.failed("DNA_MATCH_ALGORITHM_VERSION_UNKNOWN",
          "algorithmVersion MUST be one of " + ALGORITHMS);
    }
    if (request.algorithmVersionLabel() != null
        && request.algorithmVersionLabel().length()
            > DnaLimits.RAW_UPLOAD_MAX_BYTES) {
      return RawUploadOutcome.failed("DNA_MATCH_ALGORITHM_VERSION_UNKNOWN",
          "algorithmVersionLabel MUST be <= 64 bytes");
    }
    if (request.kitCount() < 0
        || request.kitCount() > DnaLimits.MATCH_MAX_KITS_PER_REQUEST) {
      return RawUploadOutcome.failed("DNA_MATCH_KIT_NOT_FOUND",
          "kitCount MUST be in [0," + DnaLimits.MATCH_MAX_KITS_PER_REQUEST + "]");
    }
    if (request.segmentCount() < 0
        || request.segmentCount() > DnaLimits.MATCH_MAX_SEGMENTS_PER_KIT) {
      return RawUploadOutcome.failed("DNA_MATCH_KIT_NOT_FOUND",
          "segmentCount MUST be in [0," + DnaLimits.MATCH_MAX_SEGMENTS_PER_KIT + "]");
    }
    if (request.stage() != null && !STAGES.contains(request.stage())) {
      return RawUploadOutcome.failed("DNA_FORMAT_VALIDATION_FAILED",
          "stage MUST be one of " + STAGES);
    }
    if (!request.consentReauthorizedAtActivityTime()) {
      return RawUploadOutcome.failed("DNA_MATCH_CONSENT_REVOKED",
          "consent MUST be re-authorized at activity time per E10.3");
    }
    if (request.consentRevoked()) {
      return RawUploadOutcome.failed("DNA_MATCH_CONSENT_REVOKED",
          "consent has been revoked");
    }
    if (request.consentExpired()) {
      return RawUploadOutcome.failed("DNA_MATCH_CONSENT_EXPIRED",
          "consent has expired");
    }
    if (request.crossRegionMatch() && !request.jurisdictionResidencyChecked()) {
      return RawUploadOutcome.failed("DNA_MATCH_WORKER_POOL_VIOLATED",
          "cross-region matching requires jurisdiction + residency check");
    }
    String forbidden = DnaForbiddenPayloadKeys.firstViolation(request.payload());
    if (forbidden != null) {
      return RawUploadOutcome.failed("DNA_RAW_DNA_LEAK_DETECTED", forbidden);
    }
    return RawUploadOutcome.ok(request);
  }

  public record RawUploadOutcome(
      boolean valid, RawUploadRequest request, String failureReason, String detail) {

    public static RawUploadOutcome ok(RawUploadRequest request) {
      return new RawUploadOutcome(true, request, null, null);
    }

    public static RawUploadOutcome failed(String reason, String detail) {
      return new RawUploadOutcome(false, null, reason, detail);
    }
  }

  public record RawUploadRequest(
      String tenantPseudoId,
      String actorPseudoId,
      String correlationId,
      String provider,
      String format,
      long payloadBytes,
      int payloadDepth,
      boolean envelopeEncrypted,
      boolean envelopeKeyRevoked,
      String taskQueue,
      Set<String> nodePoolLabels,
      String algorithmVersion,
      String algorithmVersionLabel,
      int kitCount,
      int segmentCount,
      String stage,
      boolean consentReauthorizedAtActivityTime,
      boolean consentRevoked,
      boolean consentExpired,
      boolean crossRegionMatch,
      boolean jurisdictionResidencyChecked,
      Map<String, Object> payload,
      boolean containsSecret) {

    public RawUploadRequest {
      payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
  }
}