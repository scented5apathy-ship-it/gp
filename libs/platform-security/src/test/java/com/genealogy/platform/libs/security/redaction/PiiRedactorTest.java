package com.genealogy.platform.libs.security.redaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PiiRedactorTest {

    private final PiiRedactor redactor = new PiiRedactor();

    @Test
    @DisplayName("Deny keys are dropped entirely")
    void denyKeysDropped() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("displayName", "Ada");
        input.put("biography", "long bio");
        input.put("rawDna", "ACGTACGT");
        Map<String, Object> out = redactor.redactMap(input);
        assertEquals("Ada", out.get("displayName"));
        assertFalse(out.containsKey("biography"));
        assertFalse(out.containsKey("rawDna"));
    }

    @Test
    @DisplayName("Mask keys are replaced by [REDACTED:<key>]")
    void maskKeysReplaced() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("email", "ada@example.com");
        input.put("phone", "+84 123 456 789");
        Map<String, Object> out = redactor.redactMap(input);
        assertEquals("[REDACTED:email]", out.get("email"));
        assertEquals("[REDACTED:phone]", out.get("phone"));
    }

    @Test
    @DisplayName("Email inside a free-text field is scrubbed when scrubValues=true")
    void freeTextEmailScrubbed() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("comment", "Email me at ada@example.com please");
        Map<String, Object> out = redactor.redactMap(input);
        assertEquals("Email me at [REDACTED:email] please", out.get("comment"));
    }

    @Test
    @DisplayName("JWT-like token is scrubbed")
    void jwtScrubbed() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("payload", "Authorization: Bearer eyJabc.def.ghi");
        Map<String, Object> out = redactor.redactMap(input);
        assertEquals("Authorization: [REDACTED:token]", out.get("payload"));
    }

    @Test
    @DisplayName("SSN is scrubbed")
    void ssnScrubbed() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("note", "SSN 123-45-6789 attached");
        Map<String, Object> out = redactor.redactMap(input);
        assertEquals("SSN [REDACTED:ssn] attached", out.get("note"));
    }

    @Test
    @DisplayName("Null values are preserved")
    void nullValuesPreserved() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("keepMe", null);
        Map<String, Object> out = redactor.redactMap(input);
        assertTrue(out.containsKey("keepMe"));
        assertNull(out.get("keepMe"));
    }
}
