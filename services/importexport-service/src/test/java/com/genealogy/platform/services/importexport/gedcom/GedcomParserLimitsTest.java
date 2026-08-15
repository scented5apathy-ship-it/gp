package com.genealogy.platform.services.importexport.gedcom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GedcomParserLimitsTest {

  private GedcomParserLimits.GedcomPayload goodPayload() {
    return new GedcomParserLimits.GedcomPayload(
        1024,
        256,
        8,
        100,
        GedcomParserLimits.GedcomEncoding.UTF_8,
        false);
  }

  @Test
  void goodPayloadIsAccepted() {
    GedcomParserLimits.GedcomParseOutcome out = GedcomParserLimits.validate(goodPayload());
    assertTrue(out.ok());
    assertNotNull(out.payload());
  }

  @Test
  void oversizedPayloadFails() {
    GedcomParserLimits.GedcomPayload p = new GedcomParserLimits.GedcomPayload(
        60_000_000L,
        256,
        8,
        100,
        GedcomParserLimits.GedcomEncoding.UTF_8,
        false);
    GedcomParserLimits.GedcomParseOutcome out = GedcomParserLimits.validate(p);
    assertFalse(out.ok());
    assertEquals(GedcomFailureReason.GEDCOM_PAYLOAD_TOO_LARGE, out.failureReason());
  }

  @Test
  void lineLengthExceededFails() {
    GedcomParserLimits.GedcomPayload p = new GedcomParserLimits.GedcomPayload(
        1024,
        8_000,
        8,
        100,
        GedcomParserLimits.GedcomEncoding.UTF_8,
        false);
    GedcomParserLimits.GedcomParseOutcome out = GedcomParserLimits.validate(p);
    assertFalse(out.ok());
    assertEquals(GedcomFailureReason.GEDCOM_LINE_LENGTH_EXCEEDED, out.failureReason());
  }

  @Test
  void depthExceededFails() {
    GedcomParserLimits.GedcomPayload p = new GedcomParserLimits.GedcomPayload(
        1024,
        256,
        64,
        100,
        GedcomParserLimits.GedcomEncoding.UTF_8,
        false);
    GedcomParserLimits.GedcomParseOutcome out = GedcomParserLimits.validate(p);
    assertFalse(out.ok());
    assertEquals(GedcomFailureReason.GEDCOM_DEPTH_EXCEEDED, out.failureReason());
  }

  @Test
  void recordCountExceededFails() {
    GedcomParserLimits.GedcomPayload p = new GedcomParserLimits.GedcomPayload(
        1024,
        256,
        8,
        8_000_000,
        GedcomParserLimits.GedcomEncoding.UTF_8,
        false);
    GedcomParserLimits.GedcomParseOutcome out = GedcomParserLimits.validate(p);
    assertFalse(out.ok());
    assertEquals(GedcomFailureReason.GEDCOM_RECORD_COUNT_EXCEEDED, out.failureReason());
  }

  @Test
  void dnaBucketReferenceFails() {
    GedcomParserLimits.GedcomPayload p = new GedcomParserLimits.GedcomPayload(
        1024,
        256,
        8,
        100,
        GedcomParserLimits.GedcomEncoding.UTF_8,
        true);
    GedcomParserLimits.GedcomParseOutcome out = GedcomParserLimits.validate(p);
    assertFalse(out.ok());
    assertEquals(GedcomFailureReason.GEDCOM_DNA_BUCKET_FORBIDDEN, out.failureReason());
  }

  @Test
  void validationStatusEnumWireRoundTrip() {
    for (GedcomValidationStatus s : GedcomValidationStatus.values()) {
      assertEquals(s, GedcomValidationStatus.fromWire(s.wire()));
    }
    assertThrows(IllegalArgumentException.class,
        () -> GedcomValidationStatus.fromWire("UNKNOWN"));
  }

  @Test
  void mappingOutcomeEnumWireRoundTrip() {
    for (GedcomMappingOutcome o : GedcomMappingOutcome.values()) {
      assertEquals(o, GedcomMappingOutcome.fromWire(o.wire()));
    }
    assertThrows(IllegalArgumentException.class,
        () -> GedcomMappingOutcome.fromWire("UNKNOWN_OUTCOME"));
  }

  @Test
  void failureReasonEnumWireRoundTrip() {
    for (GedcomFailureReason r : GedcomFailureReason.values()) {
      assertEquals(r, GedcomFailureReason.fromWire(r.wire()));
    }
    assertThrows(IllegalArgumentException.class,
        () -> GedcomFailureReason.fromWire("UNKNOWN_REASON"));
  }
}