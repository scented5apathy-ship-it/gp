package com.genealogy.platform.services.dna.revoke;

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

class DnaRevokeGuardTest {

  private static DnaRevokeGuard.RevokeRequest validRevokeRequest() {
    return new DnaRevokeGuard.RevokeRequest(
        "tenant-1",
        "actor-1",
        "corr-1",
        "CONSENT_REVOKED",
        "kit-agg-1",
        false,
        false,
        false,
        false,
        null,
        null,
        0L,
        0,
        Boolean.TRUE,
        Set.of("DELETE_MATCH_SEGMENTS", "REVOKE_KIT_SHARING_LINKS"),
        "TRIGGERED",
        "QUEUED",
        Map.of("aggregate-id", "agg-1"),
        false);
  }

  @Test
  void validRevokeProducesOk() {
    DnaRevokeGuard.RevokeOutcome out = DnaRevokeGuard.validate(validRevokeRequest());
    assertTrue(out.valid());
    assertNotNull(out.request());
    assertNull(out.failureReason());
  }

  @Test
  void unknownTriggerFails() {
    DnaRevokeGuard.RevokeRequest req = new DnaRevokeGuard.RevokeRequest(
        "tenant-1", "actor-1", "corr-1", "INVALID_TRIGGER", "kit-agg-1",
        false, false, false, false, null, null, 0L, 0, Boolean.TRUE,
        Set.of(), "TRIGGERED", "QUEUED", Map.of(), false);
    DnaRevokeGuard.RevokeOutcome out = DnaRevokeGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_REVOKE_TRIGGER_UNKNOWN", out.failureReason());
  }

  @Test
  void portabilityRequiresStepUp() {
    DnaRevokeGuard.RevokeRequest req = new DnaRevokeGuard.RevokeRequest(
        "tenant-1", "actor-1", "corr-1", "DATA_SUBJECT_PORTABILITY_REQUEST",
        "kit-agg-1", false, false, false, false, null, null, 0L, 0, Boolean.TRUE,
        Set.of(), "TRIGGERED", "QUEUED", Map.of(), false);
    DnaRevokeGuard.RevokeOutcome out = DnaRevokeGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_REVOKE_STEP_UP_AUTH_REQUIRED", out.failureReason());
  }

  @Test
  void jurisdictionBanRequiresLegalHold() {
    DnaRevokeGuard.RevokeRequest req = new DnaRevokeGuard.RevokeRequest(
        "tenant-1", "actor-1", "corr-1", "JURISDICTION_BAN_TRIGGERED",
        "kit-agg-1", false, false, false, false, null, null, 0L, 0, Boolean.TRUE,
        Set.of(), "TRIGGERED", "QUEUED", Map.of(), false);
    DnaRevokeGuard.RevokeOutcome out = DnaRevokeGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_REVOKE_LEGAL_HOLD_OVERRIDE_INVALID", out.failureReason());
  }

  @Test
  void legalHoldActiveBlocks() {
    DnaRevokeGuard.RevokeRequest req = new DnaRevokeGuard.RevokeRequest(
        "tenant-1", "actor-1", "corr-1", "CONSENT_REVOKED",
        "kit-agg-1", false, true, false, false, null, null, 0L, 0, Boolean.TRUE,
        Set.of(), "TRIGGERED", "QUEUED", Map.of(), false);
    DnaRevokeGuard.RevokeOutcome out = DnaRevokeGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_REVOKE_LEGAL_HOLD_BLOCKED", out.failureReason());
  }

  @Test
  void exportWithoutStepUpFails() {
    DnaRevokeGuard.RevokeRequest req = new DnaRevokeGuard.RevokeRequest(
        "tenant-1", "actor-1", "corr-1", "CONSENT_REVOKED",
        "kit-agg-1", false, false, false, true, "SELF_PORTABILITY_ZIP",
        "DNA_DEFAULT_OFF", 1024L, 3600, Boolean.TRUE,
        Set.of(), "TRIGGERED", "QUEUED", Map.of(), false);
    DnaRevokeGuard.RevokeOutcome out = DnaRevokeGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_REVOKE_STEP_UP_AUTH_REQUIRED", out.failureReason());
  }

  @Test
  void signedUrlTtlTooShortFails() {
    DnaRevokeGuard.RevokeRequest req = new DnaRevokeGuard.RevokeRequest(
        "tenant-1", "actor-1", "corr-1", "DATA_SUBJECT_PORTABILITY_REQUEST",
        "kit-agg-1", true, false, false, true, "SELF_PORTABILITY_ZIP",
        "DNA_DEFAULT_OFF", 1024L, 60, Boolean.TRUE,
        Set.of(), "TRIGGERED", "QUEUED", Map.of(), false);
    DnaRevokeGuard.RevokeOutcome out = DnaRevokeGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_REVOKE_SIGNED_URL_REVOKE_FAILED", out.failureReason());
  }

  @Test
  void evidenceExcludesDeletedContentMustBeSet() {
    DnaRevokeGuard.RevokeRequest req = new DnaRevokeGuard.RevokeRequest(
        "tenant-1", "actor-1", "corr-1", "CONSENT_REVOKED",
        "kit-agg-1", false, false, false, false, null, null, 0L, 0, null,
        Set.of(), "TRIGGERED", "QUEUED", Map.of(), false);
    DnaRevokeGuard.RevokeOutcome out = DnaRevokeGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_REVOKE_EVIDENCE_INCOMPLETE", out.failureReason());
  }

  @Test
  void rawDnaPayloadFails() {
    DnaRevokeGuard.RevokeRequest req = new DnaRevokeGuard.RevokeRequest(
        "tenant-1", "actor-1", "corr-1", "CONSENT_REVOKED",
        "kit-agg-1", false, false, false, false, null, null, 0L, 0, Boolean.TRUE,
        Set.of(), "TRIGGERED", "QUEUED",
        Map.of("rawDnaSequence", "ACGT"), false);
    DnaRevokeGuard.RevokeOutcome out = DnaRevokeGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_REVOKE_EVIDENCE_INCOMPLETE", out.failureReason());
  }

  @Test
  void blankTenantFails() {
    DnaRevokeGuard.RevokeRequest req = new DnaRevokeGuard.RevokeRequest(
        "", "actor-1", "corr-1", "CONSENT_REVOKED",
        "kit-agg-1", false, false, false, false, null, null, 0L, 0, Boolean.TRUE,
        Set.of(), "TRIGGERED", "QUEUED", Map.of(), false);
    DnaRevokeGuard.RevokeOutcome out = DnaRevokeGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("DNA_REVOKE_TENANT_MISMATCH", out.failureReason());
  }

  @Test
  void dnaLimitsMatchContract() {
    assertEquals(3_600, DnaLimits.EXPORT_SIGNED_URL_TTL_SECONDS);
    assertEquals(30, DnaLimits.EXPORT_REVOCATION_PROPAGATION_SECONDS);
    assertEquals(60, DnaLimits.REVOKE_TERMINATION_GRACE_SECONDS);
  }

  @Test
  void classCannotBeInstantiated() {
    assertThrows(UnsupportedOperationException.class,
        () -> {
          java.lang.reflect.Constructor<DnaRevokeGuard> ctor =
              DnaRevokeGuard.class.getDeclaredConstructor();
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