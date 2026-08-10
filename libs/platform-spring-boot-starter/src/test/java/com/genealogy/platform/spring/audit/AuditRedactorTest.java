package com.genealogy.platform.spring.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditRedactorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void dropsDenyKeys() {
        AuditRedactor redactor = AuditRedactor.defaultRedactor();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("rawDna", "ACGTACGTACGTACGT");
        metadata.put("biography", "secret");
        metadata.put("display_name", "Smith");
        AuditEventEnvelope envelope = makeEnvelope(metadata);
        AuditEventEnvelope redacted = redactor.redact(envelope);
        assertThat(redacted.getMetadata()).doesNotContainKeys("rawDna", "biography");
        assertThat(redacted.getMetadata()).containsEntry("display_name", "Smith");
    }

    @Test
    void masksMaskKeys() {
        AuditRedactor redactor = AuditRedactor.defaultRedactor();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("email", "alice@example.com");
        metadata.put("phone", "+1234567890");
        AuditEventEnvelope envelope = makeEnvelope(metadata);
        AuditEventEnvelope redacted = redactor.redact(envelope);
        assertThat(redacted.getMetadata()).containsEntry("email", "[REDACTED:email]");
        assertThat(redacted.getMetadata()).containsEntry("phone", "[REDACTED:phone]");
    }

    @Test
    void scrubsFreeTextPatterns() {
        AuditRedactor redactor = AuditRedactor.defaultRedactor();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("note", "token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig");
        metadata.put("source", "bearer abc.def.ghi");
        metadata.put("ip", "10.0.0.42");
        AuditEventEnvelope envelope = makeEnvelope(metadata);
        AuditEventEnvelope redacted = redactor.redact(envelope);
        assertThat(redacted.getMetadata().get("note")).contains("[REDACTED:jwt]");
        assertThat(redacted.getMetadata().get("source")).contains("[REDACTED:bearer]");
        assertThat(redacted.getMetadata().get("ip")).isEqualTo("[REDACTED:ipv4]");
    }

    @Test
    void scrubsDnaSequenceInAnyKey() {
        AuditRedactor redactor = AuditRedactor.defaultRedactor();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("note", "kit ACGTACGTACGTACGTACGT");
        AuditEventEnvelope envelope = makeEnvelope(metadata);
        AuditEventEnvelope redacted = redactor.redact(envelope);
        assertThat(redacted.getMetadata().get("note")).contains("[REDACTED:dnaSequence]");
    }

    @Test
    void overflowIsTruncatedWithMarker() {
        AuditRedactor redactor = AuditRedactor.defaultRedactor();
        Map<String, String> metadata = new LinkedHashMap<>();
        for (int i = 0; i < 1000; i++) {
            metadata.put("k" + i, "v".repeat(40));
        }
        AuditEventEnvelope envelope = makeEnvelope(metadata);
        AuditEventEnvelope redacted = redactor.redact(envelope);
        assertThat(redacted.getMetadata()).containsKey("__overflow__");
        assertThat(redacted.getMetadata().get("__overflow__")).isEqualTo("[REDACTED:overflow]");
    }

    @Test
    void redactionIsCaseInsensitive() {
        AuditRedactor redactor = AuditRedactor.defaultRedactor();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("Email", "alice@example.com");
        AuditEventEnvelope envelope = makeEnvelope(metadata);
        AuditEventEnvelope redacted = redactor.redact(envelope);
        assertThat(redacted.getMetadata().get("Email")).isEqualTo("[REDACTED:Email]");
    }

    private static AuditEventEnvelope makeEnvelope(Map<String, String> metadata) {
        return new AuditEventEnvelope(
                "evt-1",
                "tenant-1",
                "actor-1",
                AuditClassRegistry.CLASS_AUTHORIZATION,
                "tenant.created",
                "tenant",
                "smith",
                null,
                "corr-1",
                NOW,
                metadata);
    }
}
