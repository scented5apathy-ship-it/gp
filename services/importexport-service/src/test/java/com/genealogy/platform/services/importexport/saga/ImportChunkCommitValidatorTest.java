package com.genealogy.platform.services.importexport.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ImportChunkCommitValidatorTest {

  @Test
  void validChunkProducesOk() {
    ImportChunkCommitValidator.ChunkCommitCommand cmd = new ImportChunkCommitValidator.ChunkCommitCommand(
        100,
        1024L,
        ImportDedupOutcome.NEW,
        "tenant-1",
        false,
        false);
    ImportChunkCommitValidator.ChunkCommitOutcome out = ImportChunkCommitValidator.validate(cmd);
    assertTrue(out.ok());
    assertNotNull(out.command());
  }

  @Test
  void oversizedRecordsFails() {
    ImportChunkCommitValidator.ChunkCommitCommand cmd = new ImportChunkCommitValidator.ChunkCommitCommand(
        50_000,
        1024L,
        ImportDedupOutcome.NEW,
        "tenant-1",
        false,
        false);
    ImportChunkCommitValidator.ChunkCommitOutcome out = ImportChunkCommitValidator.validate(cmd);
    assertFalse(out.ok());
    assertEquals("IMPORT_CHUNK_TOO_LARGE", out.failureReason());
  }

  @Test
  void oversizedBytesFails() {
    ImportChunkCommitValidator.ChunkCommitCommand cmd = new ImportChunkCommitValidator.ChunkCommitCommand(
        100,
        100_000_000L,
        ImportDedupOutcome.NEW,
        "tenant-1",
        false,
        false);
    ImportChunkCommitValidator.ChunkCommitOutcome out = ImportChunkCommitValidator.validate(cmd);
    assertFalse(out.ok());
    assertEquals("IMPORT_CHUNK_TOO_LARGE", out.failureReason());
  }

  @Test
  void dnaBucketReferenceFails() {
    ImportChunkCommitValidator.ChunkCommitCommand cmd = new ImportChunkCommitValidator.ChunkCommitCommand(
        100,
        1024L,
        ImportDedupOutcome.NEW,
        "tenant-1",
        true,
        false);
    ImportChunkCommitValidator.ChunkCommitOutcome out = ImportChunkCommitValidator.validate(cmd);
    assertFalse(out.ok());
    assertEquals("IMPORT_DNA_BUCKET_FORBIDDEN", out.failureReason());
  }

  @Test
  void piiLeakFails() {
    ImportChunkCommitValidator.ChunkCommitCommand cmd = new ImportChunkCommitValidator.ChunkCommitCommand(
        100,
        1024L,
        ImportDedupOutcome.NEW,
        "tenant-1",
        false,
        true);
    ImportChunkCommitValidator.ChunkCommitOutcome out = ImportChunkCommitValidator.validate(cmd);
    assertFalse(out.ok());
    assertEquals("IMPORT_PII_LEAK_DETECTED", out.failureReason());
  }

  @Test
  void tenantMismatchFails() {
    ImportChunkCommitValidator.ChunkCommitCommand cmd = new ImportChunkCommitValidator.ChunkCommitCommand(
        100,
        1024L,
        ImportDedupOutcome.NEW,
        "",
        false,
        false);
    ImportChunkCommitValidator.ChunkCommitOutcome out = ImportChunkCommitValidator.validate(cmd);
    assertFalse(out.ok());
    assertEquals("IMPORT_TENANT_MISMATCH", out.failureReason());
  }

  @Test
  void dedupOutcomeEnumWireRoundTrip() {
    for (ImportDedupOutcome o : ImportDedupOutcome.values()) {
      assertEquals(o, ImportDedupOutcome.fromWire(o.wire()));
    }
    assertThrows(IllegalArgumentException.class,
        () -> ImportDedupOutcome.fromWire("UNKNOWN"));
  }
}