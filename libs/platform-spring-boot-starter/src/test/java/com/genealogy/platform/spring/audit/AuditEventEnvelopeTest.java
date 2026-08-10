package com.genealogy.platform.spring.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditEventEnvelopeTest {

    @Test
    void jsonEncodingIsDeterministic() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("display_name", "Smith");
        metadata.put("reason", "manual");
        AuditEventEnvelope envelope = new AuditEventEnvelope(
                "evt-1",
                "tenant-1",
                "actor-1",
                AuditClassRegistry.CLASS_AUTHORIZATION,
                "tenant.created",
                "tenant",
                "smith",
                null,
                "corr-1",
                Instant.parse("2026-01-01T00:00:00Z"),
                metadata);
        String json = envelope.toJson();
        assertThat(json).startsWith("{\"eventId\":\"evt-1\",\"tenantId\":\"tenant-1\"");
        assertThat(json).contains("\"auditClass\":\"authorization\"");
        assertThat(json).contains("\"metadata\":{\"display_name\":\"Smith\",\"reason\":\"manual\"}}");
    }

    @Test
    void jsonEscapesSpecialCharacters() {
        AuditEventEnvelope envelope = new AuditEventEnvelope(
                "evt-2",
                "tenant-2",
                "actor-2",
                AuditClassRegistry.CLASS_AUTH,
                "auth.login.succeeded",
                "session",
                "sess-1",
                null,
                "corr-2",
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("note", "first\nsecond\t\"quoted\""));
        String json = envelope.toJson();
        assertThat(json).contains("note\":\"first\\nsecond\\t\\\"quoted\\\"\"");
    }

    @Test
    void fromAuditEventCopiesAllFields() {
        AuditEvent event = new AuditEvent(
                "tenant-3",
                "actor-3",
                "tenant.suspended",
                "tenant",
                "smith",
                "corr-3",
                Map.of("reason", "fraud"));
        AuditEventEnvelope envelope = AuditEventEnvelope.from(
                event, AuditClassRegistry.CLASS_AUTHORIZATION, "policy_version_unknown");
        assertThat(envelope.getEventId()).isEqualTo(event.getEventId());
        assertThat(envelope.getTenantId()).isEqualTo("tenant-3");
        assertThat(envelope.getAction()).isEqualTo("tenant.suspended");
        assertThat(envelope.getReasonCode()).isEqualTo("policy_version_unknown");
    }

    @Test
    void knownCatalogueContainsSixClasses() {
        assertThat(AuditClassRegistry.classes())
                .containsExactlyInAnyOrder(
                        "auth",
                        "authorization",
                        "policy",
                        "support",
                        "download",
                        "consent");
    }

    @Test
    void knownActionResolvesToCorrectClass() {
        assertThat(AuditClassRegistry.classFor("tenant.created")).isEqualTo("authorization");
        assertThat(AuditClassRegistry.classFor("auth.login.failed")).isEqualTo("auth");
        assertThat(AuditClassRegistry.classFor("consent.revoked")).isEqualTo("consent");
        assertThat(AuditClassRegistry.classFor("download.signed_url_issued")).isEqualTo("download");
    }

    @Test
    void minRetentionDaysMatchesContract() {
        assertThat(AuditClassRegistry.minRetentionDays("consent")).isEqualTo(1825);
        assertThat(AuditClassRegistry.minRetentionDays("auth")).isEqualTo(365);
        assertThat(AuditClassRegistry.minRetentionDays("policy")).isEqualTo(730);
        assertThat(AuditClassRegistry.minRetentionDays("authorization")).isEqualTo(365);
        assertThat(AuditClassRegistry.minRetentionDays("support")).isEqualTo(730);
        assertThat(AuditClassRegistry.minRetentionDays("download")).isEqualTo(730);
    }

    @Test
    void classForUnknownActionReturnsNull() {
        assertThat(AuditClassRegistry.classFor("not.in.catalogue")).isNull();
    }

    @Test
    void minRetentionDaysUnknownDefaultsTo365() {
        assertThat(AuditClassRegistry.minRetentionDays("not-a-class")).isEqualTo(365);
    }

    @Test
    void everyActionMapsToKnownClass() {
        for (Map.Entry<String, String> entry : AuditClassRegistry.actions().entrySet()) {
            assertThat(AuditClassRegistry.isKnownClass(entry.getValue()))
                    .as("action %s -> class %s", entry.getKey(), entry.getValue())
                    .isTrue();
        }
    }

    @Test
    void actionSetIsClosed() {
        // representative list — adding a new action requires bumping
        // POLICY_ID per the change protocol in contracts/audit/README.md
        List<String> expected = List.of(
                "auth.login.succeeded", "auth.login.failed", "auth.logout",
                "auth.session.revoked", "auth.mfa.challenged", "auth.mfa.succeeded", "auth.mfa.failed",
                "tenant.created", "tenant.updated", "tenant.plan_changed",
                "tenant.suspended", "tenant.restored", "tenant.soft_deleted",
                "membership.invited", "membership.activated",
                "membership.role_changed", "membership.revoked",
                "openfga.tuple_written", "openfga.tuple_revoked",
                "abac.policy_reloaded", "abac.reason_registered", "abac.cache_invalidated",
                "trusted_context.policy_reloaded", "audit.policy_reloaded",
                "support.session.started", "support.session.scope_granted",
                "support.session.ended", "support.read.executed", "support.export.requested",
                "download.signed_url_issued", "download.asset.exported",
                "download.export.bundle_signed", "download.gedcom.exported",
                "download.report.exported",
                "consent.granted", "consent.revoked", "consent.expired",
                "consent.access_logged", "consent.receipt_signed");
        assertThat(AuditClassRegistry.actions().keySet()).containsExactlyInAnyOrderElementsOf(expected);
    }
}
