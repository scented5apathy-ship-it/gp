package com.genealogy.platform.services.importexport.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.importexport.shared.ImportExportLimits;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransferRunValidatorTest {

  @Test
  void validRequestProducesOk() {
    TransferRunValidator.TransferRequest req = new TransferRunValidator.TransferRequest(
        "run-1",
        TransferWorkflowKind.IMPORT_GEDCOM,
        "idem-1",
        "tenant-1",
        "actor-1",
        "corr-1",
        Map.of("aggregate-id", "agg-1"),
        false);
    TransferRunValidator.TransferValidationOutcome out = TransferRunValidator.validate(req);
    assertTrue(out.valid());
    assertNotNull(out.request());
    assertNull(out.failureReason());
  }

  @Test
  void nullWorkflowKindFails() {
    TransferRunValidator.TransferRequest req = new TransferRunValidator.TransferRequest(
        "run-1",
        null,
        "idem-1",
        "tenant-1",
        "actor-1",
        "corr-1",
        Map.of(),
        false);
    TransferRunValidator.TransferValidationOutcome out = TransferRunValidator.validate(req);
    assertFalse(out.valid());
    assertEquals("TRANSFER_KIND_UNKNOWN", out.failureReason());
  }

  @Test
  void dnaPayloadFails() {
    TransferRunValidator.TransferRequest req = new TransferRunValidator.TransferRequest(
        "run-1",
        TransferWorkflowKind.IMPORT_GEDCOM,
        "idem-1",
        "tenant-1",
        "actor-1",
        "corr-1",
        Map.of("rawDnaSequence", "ACGT"),
        false);
    TransferRunValidator.TransferValidationOutcome out = TransferRunValidator.validate(req);
    assertFalse(out.valid());
    assertEquals("TRANSFER_DNA_BUCKET_FORBIDDEN", out.failureReason());
  }

  @Test
  void piiPayloadFails() {
    TransferRunValidator.TransferRequest req = new TransferRunValidator.TransferRequest(
        "run-1",
        TransferWorkflowKind.IMPORT_GEDCOM,
        "idem-1",
        "tenant-1",
        "actor-1",
        "corr-1",
        Map.of("rawEmail", "user@example.com"),
        false);
    TransferRunValidator.TransferValidationOutcome out = TransferRunValidator.validate(req);
    assertFalse(out.valid());
    assertEquals("TRANSFER_PII_LEAK_DETECTED", out.failureReason());
  }

  @Test
  void secretPayloadFails() {
    TransferRunValidator.TransferRequest req = new TransferRunValidator.TransferRequest(
        "run-1",
        TransferWorkflowKind.IMPORT_GEDCOM,
        "idem-1",
        "tenant-1",
        "actor-1",
        "corr-1",
        Map.of("aggregate-id", "agg-1"),
        true);
    TransferRunValidator.TransferValidationOutcome out = TransferRunValidator.validate(req);
    assertFalse(out.valid());
    assertEquals("TRANSFER_SECRETS_IN_PAYLOAD", out.failureReason());
  }

  @Test
  void blankTenantFails() {
    TransferRunValidator.TransferRequest req = new TransferRunValidator.TransferRequest(
        "run-1",
        TransferWorkflowKind.IMPORT_GEDCOM,
        "idem-1",
        "",
        "actor-1",
        "corr-1",
        Map.of(),
        false);
    TransferRunValidator.TransferValidationOutcome out = TransferRunValidator.validate(req);
    assertFalse(out.valid());
    assertEquals("TRANSFER_TENANT_MISMATCH", out.failureReason());
  }

  @Test
  void transferStatusEnumWireRoundTrip() {
    for (TransferStatus s : TransferStatus.values()) {
      assertEquals(s, TransferStatus.fromWire(s.wire()));
    }
    assertEquals(TransferStatus.CANCELLED, TransferStatus.CANCELLED);
    assertThrows(IllegalArgumentException.class,
        () -> TransferStatus.fromWire("UNKNOWN_STATUS"));
  }

  @Test
  void workflowKindEnumWireRoundTrip() {
    for (TransferWorkflowKind k : TransferWorkflowKind.values()) {
      assertEquals(k, TransferWorkflowKind.fromWire(k.wire()));
    }
    assertThrows(IllegalArgumentException.class,
        () -> TransferWorkflowKind.fromWire("UNKNOWN_KIND"));
  }

  @Test
  void terminalStatusesAreTerminal() {
    assertTrue(TransferStatus.COMPLETED.isTerminal());
    assertTrue(TransferStatus.FAILED.isTerminal());
    assertTrue(TransferStatus.CANCELLED.isTerminal());
    assertFalse(TransferStatus.RUNNING.isTerminal());
    assertFalse(TransferStatus.QUEUED.isTerminal());
  }

  @Test
  void importExportLimitsMatchContract() {
    assertEquals(310, ImportExportLimits.ACTIVITY_START_TO_CLOSE_TIMEOUT_SECONDS);
    assertEquals(30, ImportExportLimits.ACTIVITY_HEARTBEAT_INTERVAL_SECONDS);
    assertEquals(8, ImportExportLimits.WORKFLOW_MAX_ACTIVITY_ATTEMPTS);
    assertEquals(50_000, ImportExportLimits.WORKFLOW_MAX_HISTORY_LENGTH_EVENTS);
  }
}