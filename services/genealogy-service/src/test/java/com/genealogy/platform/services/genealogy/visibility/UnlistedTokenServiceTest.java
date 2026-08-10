package com.genealogy.platform.services.genealogy.visibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.genealogy.domain.CollaborationMode;
import com.genealogy.platform.services.genealogy.domain.LifecycleState;
import com.genealogy.platform.services.genealogy.domain.Tree;
import com.genealogy.platform.services.genealogy.domain.UnlistedToken;
import com.genealogy.platform.services.genealogy.domain.Visibility;
import com.genealogy.platform.services.genealogy.outbox.InMemoryOutboxWriter;
import com.genealogy.platform.services.genealogy.outbox.JdbcTreeOutboxWriter;
import com.genealogy.platform.services.genealogy.outbox.TreeEventPayloads;
import com.genealogy.platform.services.genealogy.persistence.InMemoryTreeRepository;
import com.genealogy.platform.services.genealogy.persistence.InMemoryUnlistedTokenRepository;
import com.genealogy.platform.services.genealogy.persistence.TreeRepository;
import com.genealogy.platform.services.genealogy.persistence.UnlistedTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class UnlistedTokenServiceTest {

    private InMemoryTreeRepository trees;
    private InMemoryUnlistedTokenRepository tokens;
    private InMemoryOutboxWriter outbox;
    private UnlistedTokenService service;
    private Tree tree;

    @BeforeEach
    void setup() {
        trees = new InMemoryTreeRepository();
        tokens = new InMemoryUnlistedTokenRepository();
        outbox = new InMemoryOutboxWriter();
        JdbcTreeOutboxWriter shim = new JdbcTreeOutboxWriter(mock(DataSource.class), new ObjectMapper()) {
            @Override
            public String enqueue(String aggregateId, String tenantId, String eventType,
                                  Object payload, Instant occurredAt, String correlationId) {
                return outbox.enqueue(aggregateId, tenantId, eventType, payload,
                        occurredAt, correlationId);
            }
        };
        service = new UnlistedTokenService(trees, tokens, shim, new SecureRandom());
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        tree = new Tree(
                "tree-1", "tenant-1", "smith", "Smith Family", Visibility.UNLISTED,
                CollaborationMode.DIRECT_EDIT, LifecycleState.ACTIVE,
                "en-US", "UTC", "GREGORIAN", Map.of(), "user-1", 1L, now, now);
        trees.insert(tree);
    }

    @Test
    void issueReturnsPlaintextAndPersistsFingerprint() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        UnlistedTokenService.IssuedToken issued = service.issue(
                new UnlistedTokenService.IssueCommand(
                        "tenant-1", "tree-1", UnlistedToken.Scope.FULL_TREE, null,
                        Duration.ofDays(7), "user-1", "corr-1", now));
        assertNotNull(issued.plaintext());
        assertEquals(43, issued.plaintext().length()); // 32-byte URL-safe base64url
        assertEquals(64, issued.persisted().fingerprint().length());
        assertEquals(UnlistedToken.sha256HexLower(issued.plaintext()),
                issued.persisted().fingerprint());
        assertEquals(1, outbox.countsByEventType()
                .getOrDefault(TreeEventPayloads.EVENT_UNLISTED_TOKEN_ISSUED, 0L));
    }

    @Test
    void issueRequiresUnlistedVisibility() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        Tree privateTree = new Tree(
                "tree-2", "tenant-1", "jones", "Jones Family", Visibility.PRIVATE,
                CollaborationMode.DIRECT_EDIT, LifecycleState.ACTIVE,
                "en-US", "UTC", "GREGORIAN", Map.of(), "user-1", 1L, now, now);
        trees.insert(privateTree);
        assertThrows(IllegalStateException.class, () -> service.issue(
                new UnlistedTokenService.IssueCommand(
                        "tenant-1", "tree-2", UnlistedToken.Scope.FULL_TREE, null,
                        Duration.ofDays(7), "user-1", "corr-1", now)));
    }

    @Test
    void issueRejectsLifetimeOverMax() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> service.issue(
                new UnlistedTokenService.IssueCommand(
                        "tenant-1", "tree-1", UnlistedToken.Scope.FULL_TREE, null,
                        Duration.ofDays(60), "user-1", "corr-1", now)));
    }

    @Test
    void branchScopeRequiresBranchId() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> service.issue(
                new UnlistedTokenService.IssueCommand(
                        "tenant-1", "tree-1", UnlistedToken.Scope.BRANCH, null,
                        Duration.ofDays(7), "user-1", "corr-1", now)));
    }

    @Test
    void verifyAcceptsPlaintext() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        UnlistedTokenService.IssuedToken issued = service.issue(
                new UnlistedTokenService.IssueCommand(
                        "tenant-1", "tree-1", UnlistedToken.Scope.FULL_TREE, null,
                        Duration.ofDays(7), "user-1", "corr-1", now));
        UnlistedToken verified = service.verify("tenant-1", issued.plaintext(),
                now.plusSeconds(60));
        assertTrue(verified.isActive(now.plusSeconds(60)));
    }

    @Test
    void verifyRejectsExpiredToken() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        UnlistedTokenService.IssuedToken issued = service.issue(
                new UnlistedTokenService.IssueCommand(
                        "tenant-1", "tree-1", UnlistedToken.Scope.FULL_TREE, null,
                        Duration.ofSeconds(60), "user-1", "corr-1", t0));
        assertThrows(IllegalStateException.class,
                () -> service.verify("tenant-1", issued.plaintext(), t0.plusSeconds(120)));
    }

    @Test
    void revokeEmitsEventAndMakesTokenInactive() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        UnlistedTokenService.IssuedToken issued = service.issue(
                new UnlistedTokenService.IssueCommand(
                        "tenant-1", "tree-1", UnlistedToken.Scope.FULL_TREE, null,
                        Duration.ofDays(7), "user-1", "corr-1", t0));
        service.revoke(new UnlistedTokenService.RevokeCommand(
                "tenant-1", issued.persisted().fingerprint(),
                "user-2", "user requested", "corr-2", t0.plusSeconds(60)));
        assertEquals(1, outbox.countsByEventType()
                .getOrDefault(TreeEventPayloads.EVENT_UNLISTED_TOKEN_REVOKED, 0L));
        // Verify throws because the token is now revoked.
        assertThrows(IllegalStateException.class,
                () -> service.verify("tenant-1", issued.plaintext(), t0.plusSeconds(120)));
        // The persisted row carries the revocation timestamp.
        UnlistedToken persisted = tokens.findByFingerprint("tenant-1",
                issued.persisted().fingerprint()).orElseThrow();
        assertFalse(persisted.isActive(t0.plusSeconds(120)));
    }

    @Test
    void plaintextIsNeverPersisted() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        UnlistedTokenService.IssuedToken issued = service.issue(
                new UnlistedTokenService.IssueCommand(
                        "tenant-1", "tree-1", UnlistedToken.Scope.FULL_TREE, null,
                        Duration.ofDays(7), "user-1", "corr-1", t0));
        UnlistedToken persisted = tokens.findByFingerprint("tenant-1",
                issued.persisted().fingerprint()).orElseThrow();
        assertNotEquals(issued.plaintext(), persisted.fingerprint());
        // The persisted row should expose the fingerprint only — never the plaintext.
        assertFalse(persisted.toString().contains(issued.plaintext()));
    }
}
