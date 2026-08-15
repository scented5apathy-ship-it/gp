package com.genealogy.platform.services.dna.rawupload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.dna.shared.DnaLimits;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DnaRawUploadGuardTest {

  private static DnaRawUploadGuard.RawUploadRequest validRequest() {
    return new DnaRawUploadGuard.RawUploadRequest(
        "tenant-1",
        "actor-1",
        "corr-1",
        "ANCESTRYDNA",
        "CSV_ANCESTRYDNA",
        1_048_576L,
        1,
        true,
        false,
        "dna.upload",
        Set.of(
            "dna-worker=true",
            "dna-tier=genetic",
            "dna-bucket-bound=true",
            "dna-vault-bound=true"),
        "IBD_SEGMENT_V2",
        "v2",
        10,
        100,
        "QUARANTINED",
        true,
        false,
        false,
        false,
        false,
        Map.of("aggregate-id", "agg-1"),
        false);
  }

  @Test
  void validRequestProducesOk() {
    DnaRawUploadGuard.RawUploadOutcome out = DnaRawUploadGuard.validate(validRequest());
    assertTrue(out.valid());
    assertNotNull(out.request());
    assertNull(out.failureReason());
  }

  @Test
  void unsupportedProviderFails() {
    DnaRawUploadGuard.RawUploadRequest req = new DnaRawUploadGuard.RawUploadRequest(
        "tenant-1", "actor-1", "corr-1", "FAKE_LAB", "CSV_ANCESTRYDNA",
        1024L, 1, true, false, "dna.upload",
        Set.of(
            "dna-worker=true",
            "dna-tier=genetic",
            "dna-bucket-bound=true",
            "dna-vault-bound=true"), "IBD_SEGMENT_V2", "v2",
        10, 100, "QUARANTINED",
        true, false, false, false, false, Map.of(), false);
    DnaRawUploadGuard.RawUploadOutcome out = DnaRawUploadGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_FORMAT_UNSUPPORTED", out.failureReason());
  }

  @Test
  void payloadTooLargeFails() {
    DnaRawUploadGuard.RawUploadRequest req = new DnaRawUploadGuard.RawUploadRequest(
        "tenant-1", "actor-1", "corr-1", "ANCESTRYDNA", "CSV_ANCESTRYDNA",
        DnaLimits.RAW_UPLOAD_MAX_BYTES + 1L, 1, true, false, "dna.upload",
        Set.of(
            "dna-worker=true",
            "dna-tier=genetic",
            "dna-bucket-bound=true",
            "dna-vault-bound=true"), "IBD_SEGMENT_V2", "v2",
        10, 100, "QUARANTINED",
        true, false, false, false, false, Map.of(), false);
    DnaRawUploadGuard.RawUploadOutcome out = DnaRawUploadGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_UPLOAD_PAYLOAD_TOO_LARGE", out.failureReason());
  }

  @Test
  void envelopeKeyRevokedFails() {
    DnaRawUploadGuard.RawUploadRequest req = new DnaRawUploadGuard.RawUploadRequest(
        "tenant-1", "actor-1", "corr-1", "ANCESTRYDNA", "CSV_ANCESTRYDNA",
        1024L, 1, true, true, "dna.upload",
        Set.of(
            "dna-worker=true",
            "dna-tier=genetic",
            "dna-bucket-bound=true",
            "dna-vault-bound=true"), "IBD_SEGMENT_V2", "v2",
        10, 100, "QUARANTINED",
        true, false, false, false, false, Map.of(), false);
    DnaRawUploadGuard.RawUploadOutcome out = DnaRawUploadGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_ENVELOPE_KEY_REVOKED", out.failureReason());
  }

  @Test
  void unknownAlgorithmFails() {
    DnaRawUploadGuard.RawUploadRequest req = new DnaRawUploadGuard.RawUploadRequest(
        "tenant-1", "actor-1", "corr-1", "ANCESTRYDNA", "CSV_ANCESTRYDNA",
        1024L, 1, true, false, "dna.upload",
        Set.of(
            "dna-worker=true",
            "dna-tier=genetic",
            "dna-bucket-bound=true",
            "dna-vault-bound=true"),
        "UNKNOWN_V3", "v3",
        10, 100, "QUARANTINED",
        true, false, false, false, false, Map.of(), false);
    DnaRawUploadGuard.RawUploadOutcome out = DnaRawUploadGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_MATCH_ALGORITHM_VERSION_UNKNOWN", out.failureReason());
  }

  @Test
  void rawDnaPayloadFails() {
    DnaRawUploadGuard.RawUploadRequest req = new DnaRawUploadGuard.RawUploadRequest(
        "tenant-1", "actor-1", "corr-1", "ANCESTRYDNA", "CSV_ANCESTRYDNA",
        1024L, 1, true, false, "dna.upload",
        Set.of(
            "dna-worker=true",
            "dna-tier=genetic",
            "dna-bucket-bound=true",
            "dna-vault-bound=true"), "IBD_SEGMENT_V2", "v2",
        10, 100, "QUARANTINED",
        true, false, false, false, false,
        Map.of("rawDnaSequence", "ACGT"), false);
    DnaRawUploadGuard.RawUploadOutcome out = DnaRawUploadGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_RAW_DNA_LEAK_DETECTED", out.failureReason());
  }

  @Test
  void consentRevokedFails() {
    DnaRawUploadGuard.RawUploadRequest req = new DnaRawUploadGuard.RawUploadRequest(
        "tenant-1", "actor-1", "corr-1", "ANCESTRYDNA", "CSV_ANCESTRYDNA",
        1024L, 1, true, false, "dna.upload",
        Set.of(
            "dna-worker=true",
            "dna-tier=genetic",
            "dna-bucket-bound=true",
            "dna-vault-bound=true"), "IBD_SEGMENT_V2", "v2",
        10, 100, "QUARANTINED",
        true, true, false, false, false, Map.of(), false);
    DnaRawUploadGuard.RawUploadOutcome out = DnaRawUploadGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_MATCH_CONSENT_REVOKED", out.failureReason());
  }

  @Test
  void crossRegionWithoutJurisdictionFails() {
    DnaRawUploadGuard.RawUploadRequest req = new DnaRawUploadGuard.RawUploadRequest(
        "tenant-1", "actor-1", "corr-1", "ANCESTRYDNA", "CSV_ANCESTRYDNA",
        1024L, 1, true, false, "dna.upload",
        Set.of(
            "dna-worker=true",
            "dna-tier=genetic",
            "dna-bucket-bound=true",
            "dna-vault-bound=true"), "IBD_SEGMENT_V2", "v2",
        10, 100, "QUARANTINED",
        true, false, false, true, false, Map.of(), false);
    DnaRawUploadGuard.RawUploadOutcome out = DnaRawUploadGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_MATCH_WORKER_POOL_VIOLATED", out.failureReason());
  }

  @Test
  void blankTenantFails() {
    DnaRawUploadGuard.RawUploadRequest req = new DnaRawUploadGuard.RawUploadRequest(
        "", "actor-1", "corr-1", "ANCESTRYDNA", "CSV_ANCESTRYDNA",
        1024L, 1, true, false, "dna.upload",
        Set.of(
            "dna-worker=true",
            "dna-tier=genetic",
            "dna-bucket-bound=true",
            "dna-vault-bound=true"), "IBD_SEGMENT_V2", "v2",
        10, 100, "QUARANTINED",
        true, false, false, false, false, Map.of(), false);
    DnaRawUploadGuard.RawUploadOutcome out = DnaRawUploadGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_MATCH_TENANT_MISMATCH", out.failureReason());
  }

  @Test
  void dnaLimitsMatchContract() {
    assertEquals(26_214_400, DnaLimits.RAW_UPLOAD_MAX_BYTES);
    assertEquals(1_000, DnaLimits.MATCH_MAX_KITS_PER_REQUEST);
    assertEquals(50_000, DnaLimits.MATCH_MAX_SEGMENTS_PER_KIT);
  }

  @Test
  void classCannotBeInstantiated() {
    assertThrows(UnsupportedOperationException.class,
        () -> {
          java.lang.reflect.Constructor<DnaRawUploadGuard> ctor =
              DnaRawUploadGuard.class.getDeclaredConstructor();
          ctor.setAccessible(true);
          try {
            ctor.newInstance();
          } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof UnsupportedOperationException uoe) {
              throw uoe;
            }
            throw e;
          }
        });
  }
}