package com.genealogy.platform.services.collaboration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the value-object records. The compact
 * constructor rejects blank / oversized / forbidden-character
 * / forbidden-field input.
 */
class ValueObjectTest {

    @Test
    void tenantScopedIdRejectsBlankTenantId() {
        assertThrows(IllegalArgumentException.class,
                () -> TenantScopedId.of("", TenantScopedId.ResourceKind.PROPOSAL, "p-1"));
    }

    @Test
    void tenantScopedIdRejectsBlankResourceId() {
        assertThrows(IllegalArgumentException.class,
                () -> TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.PROPOSAL, ""));
    }

    @Test
    void tenantScopedIdRejectsOversizedResourceId() {
        String huge = "a".repeat(129);
        assertThrows(IllegalArgumentException.class,
                () -> TenantScopedId.of("tenant-1", TenantScopedId.ResourceKind.PROPOSAL, huge));
    }

    @Test
    void tenantScopedIdRejectsForbiddenCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> TenantScopedId.of("tenant-1",
                        TenantScopedId.ResourceKind.PROPOSAL, "p 1"));
    }

    @Test
    void auditAttributesRejectBlankActorPseudoId() {
        assertThrows(IllegalArgumentException.class,
                () -> CollaborationAuditAttributes.of("", "corr-1"));
    }

    @Test
    void auditAttributesRejectBlankCorrelationId() {
        assertThrows(IllegalArgumentException.class,
                () -> CollaborationAuditAttributes.of("actor-1", ""));
    }

    @Test
    void auditAttributesRejectOversizedExtras() {
        Map<String, String> huge = new LinkedHashMap<>();
        for (int i = 0; i < 17; i += 1) {
            huge.put("k-" + i, "v");
        }
        assertThrows(IllegalArgumentException.class,
                () -> new CollaborationAuditAttributes("actor-1", "corr-1", null, huge));
    }

    @Test
    void auditAttributesRejectOversizedExtraKey() {
        Map<String, String> huge = new LinkedHashMap<>();
        huge.put("a".repeat(65), "v");
        assertThrows(IllegalArgumentException.class,
                () -> new CollaborationAuditAttributes("actor-1", "corr-1", null, huge));
    }

    @Test
    void auditAttributesRejectOversizedExtraValue() {
        Map<String, String> huge = new LinkedHashMap<>();
        huge.put("k", "v".repeat(1025));
        assertThrows(IllegalArgumentException.class,
                () -> new CollaborationAuditAttributes("actor-1", "corr-1", null, huge));
    }

    @Test
    void auditAttributesAcceptNormalExtras() {
        CollaborationAuditAttributes a = CollaborationAuditAttributes.of("actor-1", "corr-1")
                .withReason("submit")
                .withExtra("reviewer", "user-1");
        assertEquals("submit", a.correlationReason());
        assertEquals("user-1", a.extras().get("reviewer"));
    }

    @Test
    void domainCommandRequiresPositiveBaseVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainCommand.of(DomainCommandKind.UPDATE_PERSON, "person-1", 0L,
                        Map.of("givenName", "Anne")));
    }

    @Test
    void domainCommandRejectsForbiddenCharactersInResourceId() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainCommand.of(DomainCommandKind.UPDATE_PERSON, "person 1", 1L,
                        Map.of("givenName", "Anne")));
    }

    @Test
    void domainCommandRejectsForbiddenCharactersInFieldKey() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainCommand.of(DomainCommandKind.UPDATE_PERSON, "person-1", 1L,
                        Map.of("given name", "Anne")));
    }

    @Test
    void domainCommandRejectsOversizedFieldValue() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("givenName", "v".repeat(DomainCommand.MAX_FIELD_VALUE_LENGTH + 1));
        assertThrows(IllegalArgumentException.class,
                () -> DomainCommand.of(DomainCommandKind.UPDATE_PERSON, "person-1", 1L, map));
    }

    @Test
    void domainDiffRequiresAtLeastOneCommand() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainDiff.of("person-1", 1L, List.of()));
    }

    @Test
    void domainDiffRequiresPositiveBaseVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainDiff.of("person-1", 0L,
                        List.of(DomainCommand.of(
                                DomainCommandKind.UPDATE_PERSON, "person-1", 1L,
                                Map.of("givenName", "Anne")))));
    }

    @Test
    void reAuthorizationDecisionRequiresNonBlankActorAndCorrelation() {
        assertThrows(IllegalArgumentException.class,
                () -> ReAuthorizationDecision.allow("", "corr", java.time.Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> ReAuthorizationDecision.allow("actor-1", "", java.time.Instant.now()));
    }

    @Test
    void reAuthorizationDecisionIsAllowHelperMatches() {
        ReAuthorizationDecision allow = ReAuthorizationDecision.allow(
                "actor-1", "corr-1", java.time.Instant.now());
        assertTrue(allow.isAllow());
        ReAuthorizationDecision deny = ReAuthorizationDecision.deny(
                "actor-1", "corr-1", "tuple_missing", java.time.Instant.now());
        assertFalse(deny.isAllow());
    }
}