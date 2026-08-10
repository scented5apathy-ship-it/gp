package com.genealogy.platform.libs.security.abac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultAbacPolicyEngineTest {

    private final DefaultAbacPolicyEngine engine = new DefaultAbacPolicyEngine();

    @Test
    @DisplayName("engineId is stable so dashboards can bucket decisions")
    void engineIdStable() {
        assertEquals("default-abac/v1", engine.engineId());
    }

    @Test
    @DisplayName("suspended resource denies with CONTEXTUAL_DENY regardless of role")
    void suspendedDenies() {
        AbacRequest request = baseRequest()
                .suspended(true)
                .build();
        AbacDecision decision = engine.evaluate(request);
        assertTrue(decision.isDeny());
        assertEquals(ReasonCode.CONTEXTUAL_DENY, decision.reasonCode());
        assertTrue(decision.obligations().hasKind(AbacObligation.Kind.AUDIT));
    }

    @Test
    @DisplayName("soft-deleted resource denies with CONTEXTUAL_DENY")
    void softDeletedDenies() {
        AbacRequest request = baseRequest()
                .softDeleted(true)
                .build();
        AbacDecision decision = engine.evaluate(request);
        assertTrue(decision.isDeny());
        assertEquals(ReasonCode.CONTEXTUAL_DENY, decision.reasonCode());
    }

    @Test
    @DisplayName("impersonation cannot reach GENETIC_RAW")
    void impersonationBlockedOnGeneticRaw() {
        AbacRequest request = baseRequest()
                .impersonated(true)
                .resourcePrivacyClass(PrivacyClass.GENETIC_RAW)
                .build();
        AbacDecision decision = engine.evaluate(request);
        assertTrue(decision.isDeny());
        assertEquals(ReasonCode.CONTEXTUAL_DENY, decision.reasonCode());
    }

    @Test
    @DisplayName("support session is blocked from GENETIC_RAW even with valid consent")
    void supportSessionBlockedOnGeneticRaw() {
        ConsentRecord consent = activeConsent();
        AbacRequest request = baseRequest()
                .supportSession(true)
                .resourcePrivacyClass(PrivacyClass.GENETIC_RAW)
                .consent(consent)
                .build();
        AbacDecision decision = engine.evaluate(request);
        assertTrue(decision.isDeny());
        assertEquals(ReasonCode.JURISDICTION_BLOCKED, decision.reasonCode());
    }

    @Test
    @DisplayName("GENETIC_DERIVED requires an active consent record")
    void geneticDerivedRequiresConsent() {
        AbacRequest request = baseRequest()
                .resourcePrivacyClass(PrivacyClass.GENETIC_DERIVED)
                .build();
        AbacDecision decision = engine.evaluate(request);
        assertTrue(decision.isDeny());
        assertEquals(ReasonCode.CONSENT_MISSING, decision.reasonCode());

        ConsentRecord revoked = activeConsent().withStatus(ConsentRecord.Status.REVOKED);
        AbacRequest revokedRequest = baseRequest()
                .resourcePrivacyClass(PrivacyClass.GENETIC_DERIVED)
                .consent(revoked)
                .build();
        AbacDecision revokedDecision = engine.evaluate(revokedRequest);
        assertTrue(revokedDecision.isDeny());
        assertEquals(ReasonCode.CONSENT_REVOKED, revokedDecision.reasonCode());

        ConsentRecord expired = activeConsent().withEffectiveAt(
                Instant.now().minusSeconds(3600))
                .withExpiresAt(Instant.now().minusSeconds(60));
        AbacRequest expiredRequest = baseRequest()
                .resourcePrivacyClass(PrivacyClass.GENETIC_DERIVED)
                .consent(expired)
                .build();
        AbacDecision expiredDecision = engine.evaluate(expiredRequest);
        assertTrue(expiredDecision.isDeny());
        assertEquals(ReasonCode.CONSENT_MISSING, expiredDecision.reasonCode());
    }

    @Test
    @DisplayName("SENSITIVE class requires consent")
    void sensitiveRequiresConsent() {
        AbacRequest request = baseRequest()
                .resourcePrivacyClass(PrivacyClass.SENSITIVE)
                .build();
        AbacDecision decision = engine.evaluate(request);
        assertTrue(decision.isDeny());
        assertEquals(ReasonCode.CONSENT_MISSING, decision.reasonCode());
    }

    @Test
    @DisplayName("Minor on PUBLIC projection is denied with MINOR_GUARDIAN_REQUIRED")
    void minorOnPublicDenied() {
        AbacRequest request = baseRequest()
                .resourcePrivacyClass(PrivacyClass.PUBLIC)
                .livingStatus(LivingStatus.living(
                        LocalDate.now().minusYears(10), ZoneId.of("UTC")))
                .build();
        AbacDecision decision = engine.evaluate(request);
        assertTrue(decision.isDeny());
        assertEquals(ReasonCode.MINOR_GUARDIAN_REQUIRED, decision.reasonCode());
    }

    @Test
    @DisplayName("Minor on PRIVATE projection is allowed with minor redact fields")
    void minorOnPrivateAllowed() {
        AbacRequest request = baseRequest()
                .resourcePrivacyClass(PrivacyClass.PRIVATE)
                .livingStatus(LivingStatus.living(
                        LocalDate.now().minusYears(10), ZoneId.of("UTC")))
                .build();
        AbacDecision decision = engine.evaluate(request);
        assertTrue(decision.isAllow());
        assertTrue(decision.obligations().redactionProfile().fieldNames()
                .contains("birthDate"));
        assertTrue(decision.obligations().redactionProfile().fieldNames()
                .contains("school"));
    }

    @Test
    @DisplayName("Living on PUBLIC projection is allowed with living redact fields")
    void livingOnPublicAllowed() {
        AbacRequest request = baseRequest()
                .resourcePrivacyClass(PrivacyClass.PUBLIC)
                .livingStatus(LivingStatus.living(
                        LocalDate.now().minusYears(40), ZoneId.of("UTC")))
                .build();
        AbacDecision decision = engine.evaluate(request);
        assertTrue(decision.isAllow());
        assertEquals(ReasonCode.LIVING_REDACTED, decision.reasonCode());
        assertTrue(decision.obligations().redactionProfile().fieldNames()
                .contains("occupation"));
    }

    @Test
    @DisplayName("Default allow always carries an audit obligation")
    void defaultAllowEmitsAudit() {
        AbacRequest request = baseRequest().build();
        AbacDecision decision = engine.evaluate(request);
        assertTrue(decision.isAllow());
        assertTrue(decision.obligations().hasKind(AbacObligation.Kind.AUDIT));
    }

    @Test
    @DisplayName("Deceased person on public projection is allowed without living redact")
    void deceasedPublicAllowed() {
        AbacRequest request = baseRequest()
                .resourcePrivacyClass(PrivacyClass.PUBLIC)
                .livingStatus(LivingStatus.deceased(
                        LocalDate.of(1900, 1, 1),
                        LocalDate.of(1970, 6, 15)))
                .build();
        AbacDecision decision = engine.evaluate(request);
        assertTrue(decision.isAllow());
        assertEquals(ReasonCode.OBLIGATION_AUDIT, decision.reasonCode());
    }

    @Test
    @DisplayName("Death date before birth date throws (fail-fast on invalid input)")
    void deathBeforeBirthThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                LivingStatus.deceased(LocalDate.of(1970, 1, 1), LocalDate.of(1900, 1, 1)));
    }

    @Test
    @DisplayName("Unknown privacy class falls back to PRIVATE semantics on default allow")
    void unknownPrivacyClassThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PrivacyClass.fromString("very-secret"));
    }

    private AbacRequest.Builder baseRequest() {
        return AbacRequest.builder()
                .tenantId("t1")
                .subjectId("u1")
                .role("viewer")
                .resourcePrivacyClass(PrivacyClass.PRIVATE)
                .resourceType("person")
                .resourceId(UUID.randomUUID().toString())
                .jurisdiction(Jurisdiction.EU);
    }

    private ConsentRecord activeConsent() {
        return new ConsentRecord(
                "c1",
                ConsentRecord.Purpose.DNA_MATCH,
                ConsentRecord.Action.READ,
                "u1",
                ConsentRecord.Status.ACTIVE,
                "v1",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                null);
    }
}
