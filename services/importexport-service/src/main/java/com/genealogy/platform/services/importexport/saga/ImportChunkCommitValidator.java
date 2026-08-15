package com.genealogy.platform.services.importexport.saga;

import com.genealogy.platform.services.importexport.shared.ImportExportLimits;

/**
 * Closed-set import saga orchestrator that enforces the chunk
 * commit + dedup invariants from
 * <code>contracts/importexport/import-saga-policy.yaml</code>.
 */
public final class ImportChunkCommitValidator {

  private ImportChunkCommitValidator() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static ChunkCommitOutcome validate(ChunkCommitCommand command) {
    if (command == null) {
      return ChunkCommitOutcome.failed("IMPORT_CHUNK_COMMIT_FAILED", "command MUST NOT be null");
    }
    if (command.records() > ImportExportLimits.IMPORT_CHUNK_MAX_RECORDS) {
      return ChunkCommitOutcome.failed("IMPORT_CHUNK_TOO_LARGE", "records=" + command.records());
    }
    if (command.byteSize() > ImportExportLimits.IMPORT_CHUNK_MAX_BYTES) {
      return ChunkCommitOutcome.failed("IMPORT_CHUNK_TOO_LARGE", "byteSize=" + command.byteSize());
    }
    if (command.dedupOutcome() == null) {
      return ChunkCommitOutcome.failed("IMPORT_DEDUP_STRATEGY_UNKNOWN", "dedupOutcome");
    }
    if (command.tenantPseudoId() == null || command.tenantPseudoId().isBlank()) {
      return ChunkCommitOutcome.failed("IMPORT_TENANT_MISMATCH", "tenantPseudoId");
    }
    if (command.dnaBucketReference()) {
      return ChunkCommitOutcome.failed("IMPORT_DNA_BUCKET_FORBIDDEN", "dna bucket reference");
    }
    if (command.piiLeakDetected()) {
      return ChunkCommitOutcome.failed("IMPORT_PII_LEAK_DETECTED", "pii leak");
    }
    return ChunkCommitOutcome.ok(command);
  }

  public record ChunkCommitCommand(
      int records,
      long byteSize,
      ImportDedupOutcome dedupOutcome,
      String tenantPseudoId,
      boolean dnaBucketReference,
      boolean piiLeakDetected) {

    public ChunkCommitCommand {
      if (records < 0) records = 0;
      if (byteSize < 0) byteSize = 0;
      if (tenantPseudoId == null) tenantPseudoId = "";
    }
  }

  public record ChunkCommitOutcome(
      boolean ok,
      String failureReason,
      String detail,
      ChunkCommitCommand command) {

    public static ChunkCommitOutcome ok(ChunkCommitCommand command) {
      return new ChunkCommitOutcome(true, null, null, command);
    }

    public static ChunkCommitOutcome failed(String reason, String detail) {
      return new ChunkCommitOutcome(false, reason, detail, null);
    }
  }
}