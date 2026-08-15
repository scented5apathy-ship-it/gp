package com.genealogy.platform.services.dna.consent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.dna.shared.DnaLimits;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DnaConsentGuardTest {

  private static DnaConsentGuard.ConsentRequest validRequest() {
    Instant t = Instant.parse("2026-08-01T00:00:00Z");
    return new DnaConsentGuard.ConsentRequest(
        "tenant-1",
        "actor-1",
        "corr-1",
        "SELF",
        "DNA_MATCHING",
        "GDPR_ART_9_2_A_EXPLICIT_CONSENT",
        "DNA_POLICY_V1_2026",
        t,
        t.plusSeconds(DnaLimits.CONSENT_RETENTION_SECONDS),
        null,
        "EFFECTIVE",
        false,
        false,
        false,
        false,
        Map.of("aggregate-id", "agg-1"),
        false);
  }

  @Test
  void validRequestProducesOk() {
    DnaConsentGuard.ConsentOutcome out = DnaConsentGuard.validate(validRequest());
    assertTrue(out.valid());
    assertNotNull(out.request());
    assertNull(out.failureReason());
  }

  @Test
  void unknownPolicyVersionFails() {
    DnaConsentGuard.ConsentRequest req = new DnaConsentGuard.ConsentRequest(
        "tenant-1", "actor-1", "corr-1", "SELF", "DNA_MATCHING",
        "GDPR_ART_9_2_A_EXPLICIT_CONSENT", "DNA_POLICY_V0_2025",
        Instant.now(), Instant.now().plusSeconds(3600), null, "EFFECTIVE",
        false, false, false, false, Map.of(), false);
    DnaConsentGuard.ConsentOutcome out = DnaConsentGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("CONSENT_POLICY_VERSION_UNKNOWN", out.failureReason());
  }

  @Test
  void unknownSubjectFails() {
    DnaConsentGuard.ConsentRequest req = new DnaConsentGuard.ConsentRequest(
        "tenant-1", "actor-1", "corr-1", "PUBLIC", "DNA_MATCHING",
        "GDPR_ART_9_2_A_EXPLICIT_CONSENT", "DNA_POLICY_V1_2026",
        Instant.now(), Instant.now().plusSeconds(3600), null, "EFFECTIVE",
        false, false, false, false, Map.of(), false);
    DnaConsentGuard.ConsentOutcome out = DnaConsentGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("CONSENT_SUBJECT_MISMATCH", out.failureReason());
  }

  @Test
  void minorWithoutGuardianFails() {
    DnaConsentGuard.ConsentRequest req = new DnaConsentGuard.ConsentRequest(
        "tenant-1", "actor-1", "corr-1", "GUARDIAN_ON_BEHALF_OF_MINOR",
        "DNA_MATCHING", "GDPR_ART_9_2_A_EXPLICIT_CONSENT", "DNA_POLICY_V1_2026_GUARDIAN",
        Instant.now(), Instant.now().plusSeconds(3600), null, "EFFECTIVE",
        true, false, false, false, Map.of(), false);
    DnaConsentGuard.ConsentOutcome out = DnaConsentGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("CONSENT_GUARDIAN_REQUIRED", out.failureReason());
  }

  @Test
  void exportWithoutStepUpFails() {
    DnaConsentGuard.ConsentRequest req = new DnaConsentGuard.ConsentRequest(
        "tenant-1", "actor-1", "corr-1", "SELF", "DNA_EXPORT_RAW",
        "GDPR_ART_9_2_A_EXPLICIT_CONSENT", "DNA_POLICY_V1_2026",
        Instant.now(), Instant.now().plusSeconds(3600), null, "EFFECTIVE",
        false, false, false, false, Map.of(), false);
    DnaConsentGuard.ConsentOutcome out = DnaConsentGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("CONSENT_STEP_UP_AUTH_REQUIRED", out.failureReason());
  }

  @Test
  void legalHoldOverrideRequiresLegalHold() {
    DnaConsentGuard.ConsentRequest req = new DnaConsentGuard.ConsentRequest(
        "tenant-1", "actor-1", "corr-1", "COURT_APPOINTED_REPRESENTATIVE",
        "DNA_LEGAL_HOLD_OVERRIDE",
        "GDPR_ART_9_2_G_SUBSTANTIAL_PUBLIC_INTEREST", "DNA_POLICY_V1_2026",
        Instant.now(), Instant.now().plusSeconds(3600), null, "EFFECTIVE",
        false, false, false, false, Map.of(), false);
    DnaConsentGuard.ConsentOutcome out = DnaConsentGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("CONSENT_LEGAL_HOLD_REQUIRED", out.failureReason());
  }

  @Test
  void expiresBeforeEffectiveFails() {
    Instant t = Instant.now();
    DnaConsentGuard.ConsentRequest req = new DnaConsentGuard.ConsentRequest(
        "tenant-1", "actor-1", "corr-1", "SELF", "DNA_MATCHING",
        "GDPR_ART_9_2_A_EXPLICIT_CONSENT", "DNA_POLICY_V1_2026",
        t, t.minusSeconds(60), null, "EFFECTIVE",
        false, false, false, false, Map.of(), false);
    DnaConsentGuard.ConsentOutcome out = DnaConsentGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("CONSENT_EXPIRED", out.failureReason());
  }

  @Test
  void rawConsentDocumentFails() {
    DnaConsentGuard.ConsentRequest req = new DnaConsentGuard.ConsentRequest(
        "tenant-1", "actor-1", "corr-1", "SELF", "DNA_MATCHING",
        "GDPR_ART_9_2_A_EXPLICIT_CONSENT", "DNA_POLICY_V1_2026",
        Instant.now(), Instant.now().plusSeconds(3600), null, "EFFECTIVE",
        false, false, false, false,
        Map.of("rawConsentDocument", "..."), false);
    DnaConsentGuard.ConsentOutcome out = DnaConsentGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("CONSENT_NOT_FOUND", out.failureReason());
  }

  @Test
  void blankTenantFails() {
    DnaConsentGuard.ConsentRequest req = new DnaConsentGuard.ConsentRequest(
        "", "actor-1", "corr-1", "SELF", "DNA_MATCHING",
        "GDPR_ART_9_2_A_EXPLICIT_CONSENT", "DNA_POLICY_V1_2026",
        Instant.now(), Instant.now().plusSeconds(3600), null, "EFFECTIVE",
        false, false, false, false, Map.of(), false);
    DnaConsentGuard.ConsentOutcome out = DnaConsentGuard.validate(req);
    assertFalse(out.valid());
    assertEquals("CONSENT_TENANT_MISMATCH", out.failureReason());
  }

  @Test
  void dnaLimitsMatchContract() {
    assertEquals(63_072_000, DnaLimits.CONSENT_RETENTION_SECONDS);
    assertEquals(63_072_000, DnaLimits.LEGAL_HOLD_MIN_RETENTION_SECONDS);
    assertEquals(16, DnaLimits.CONSENT_MAX_PURPOSES_PER_SUBJECT);
  }

  @Test
  void classCannotBeInstantiated() {
    assertThrows(UnsupportedOperationException.class,
        () -> {
          java.lang.reflect.Constructor<DnaConsentGuard> ctor =
              DnaConsentGuard.class.getDeclaredConstructor();
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