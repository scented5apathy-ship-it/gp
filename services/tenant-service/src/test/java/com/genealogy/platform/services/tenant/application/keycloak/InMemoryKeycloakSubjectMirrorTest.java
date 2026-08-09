package com.genealogy.platform.services.tenant.application.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.platform.services.tenant.domain.ids.IdGenerator;
import com.genealogy.platform.services.tenant.domain.ids.UserId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InMemoryKeycloakSubjectMirror}.
 */
class InMemoryKeycloakSubjectMirrorTest {

    private final AtomicInteger counter = new AtomicInteger();
    private final IdGenerator ids = () -> "kc-user-" + counter.incrementAndGet() + "-zzzzz";
    private InMemoryKeycloakSubjectMirror mirror;

    @BeforeEach
    void setUp() {
        mirror = new InMemoryKeycloakSubjectMirror(ids);
    }

    @Nested
    @DisplayName("ensureForEmail")
    class EnsureForEmail {

        @Test
        @DisplayName("allocates a fresh opaque id for a new email")
        void newEmailAllocatesId() {
            UserId first = mirror.ensureForEmail("alice@example.com");
            assertThat(first).isNotNull();
            assertThat(first.getValue()).matches("^[A-Za-z0-9_-]{8,64}$");

            // A second call returns the SAME id (idempotency).
            UserId second = mirror.ensureForEmail("alice@example.com");
            assertThat(second).isEqualTo(first);
        }

        @Test
        @DisplayName("email lookup is case-insensitive and trims whitespace")
        void caseInsensitive() {
            UserId first = mirror.ensureForEmail("ALICE@example.com");
            UserId second = mirror.ensureForEmail("  alice@example.com  ");
            assertThat(second).isEqualTo(first);
        }
    }

    @Nested
    @DisplayName("invite token mirror")
    class InviteTokenMirror {

        @Test
        @DisplayName("findEmailByRawToken returns the email that was registered")
        void roundTrip() {
            mirror.rememberInviteToken("bob@example.com", "raw-token-1");
            assertThat(mirror.findEmailByRawToken("raw-token-1"))
                    .isEqualTo("bob@example.com");
            assertThat(mirror.findEmailByRawToken("unknown")).isNull();
        }

        @Test
        @DisplayName("SHA-256 hashing is stable (same input → same hash)")
        void stableHash() {
            mirror.rememberInviteToken("carol@example.com", "raw-token-2");
            // Recompute and verify the lookup hits.
            String expected = InMemoryKeycloakSubjectMirror.sha256Hex("raw-token-2");
            assertThat(mirror.findEmailByRawToken("raw-token-2")).isNotNull();
            // sanity: hashing is deterministic
            assertThat(InMemoryKeycloakSubjectMirror.sha256Hex("raw-token-2"))
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("isHealthy() returns true (no external dependency)")
    void isHealthy() {
        assertThat(mirror.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("register + resolveByEmail round-trip")
    void registerAndResolve() {
        UserId user = new UserId("kc-user-stable-9999");
        mirror.register("dave@example.com", user);
        assertThat(mirror.resolveByEmail("dave@example.com")).isEqualTo(user);
    }
}
