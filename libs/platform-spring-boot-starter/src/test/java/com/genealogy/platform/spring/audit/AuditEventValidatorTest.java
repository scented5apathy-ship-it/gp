package com.genealogy.platform.spring.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditEventValidatorTest {

    @Test
    void rejectsUnknownAction() {
        AuditEventValidator validator = new AuditEventValidator();
        AuditEvent event = new AuditEvent(
                "tenant-1", "actor-1", "made_up.action", "tenant", "smith",
                "corr-1", Map.of());
        AuditValidationResult result = validator.validate(event);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolation()).isEqualTo(AuditValidationResult.Violation.UNKNOWN_ACTION);
    }

    @Test
    void rejectsMissingTenantId() {
        AuditEventValidator validator = new AuditEventValidator();
        AuditEvent event = new AuditEvent(
                "", "actor-1", "tenant.created", "tenant", "smith",
                "corr-1", Map.of());
        AuditValidationResult result = validator.validate(event);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolation()).isEqualTo(AuditValidationResult.Violation.MISSING_TENANT_ID);
    }

    @Test
    void rejectsMissingResourceType() {
        AuditEventValidator validator = new AuditEventValidator();
        AuditEvent event = new AuditEvent(
                "tenant-1", "actor-1", "tenant.created", null, "smith",
                "corr-1", Map.of());
        AuditValidationResult result = validator.validate(event);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolation()).isEqualTo(AuditValidationResult.Violation.MISSING_RESOURCE_TYPE);
    }

    @Test
    void rejectsMissingAction() {
        AuditEventValidator validator = new AuditEventValidator();
        AuditEvent event = new AuditEvent(
                "tenant-1", "actor-1", "", "tenant", "smith",
                "corr-1", Map.of());
        AuditValidationResult result = validator.validate(event);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolation()).isEqualTo(AuditValidationResult.Violation.UNKNOWN_ACTION);
    }

    @Test
    void acceptsKnownEvent() {
        AuditEventValidator validator = new AuditEventValidator();
        AuditEvent event = new AuditEvent(
                "tenant-1", "actor-1", "membership.revoked", "membership", "m-1",
                "corr-1", Map.of());
        AuditValidationResult result = validator.validate(event);
        assertThat(result.isValid()).isTrue();
        assertThat(AuditEventValidator.deriveAuditClass(event)).isEqualTo("authorization");
    }

    @Test
    void violationHandlerIsInvoked() {
        boolean[] captured = new boolean[1];
        AuditEventValidator validator = new AuditEventValidator(
                (event, violation, detail) -> captured[0] = true);
        AuditEvent event = new AuditEvent(
                "", "actor-1", "tenant.created", "tenant", "smith",
                "corr-1", Map.of());
        validator.validate(event);
        assertThat(captured[0]).isTrue();
    }
}
