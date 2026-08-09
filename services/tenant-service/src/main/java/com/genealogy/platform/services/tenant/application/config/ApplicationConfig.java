package com.genealogy.platform.services.tenant.application.config;

import com.genealogy.platform.services.tenant.application.TokenHasher;
import com.genealogy.platform.services.tenant.application.audit.TenantAuditPublisher;
import com.genealogy.platform.services.tenant.application.keycloak.InMemoryKeycloakSubjectMirror;
import com.genealogy.platform.services.tenant.application.keycloak.KeycloakSubjectMirror;
import com.genealogy.platform.services.tenant.application.outbox.JdbcOutboxWriter;
import com.genealogy.platform.services.tenant.application.outbox.OutboxWriter;
import com.genealogy.platform.services.tenant.application.persistence.EntitlementRepository;
import com.genealogy.platform.services.tenant.application.persistence.InvitationRepository;
import com.genealogy.platform.services.tenant.application.persistence.MembershipRepository;
import com.genealogy.platform.services.tenant.application.persistence.TenantRepository;
import com.genealogy.platform.services.tenant.domain.ids.IdGenerator;
import com.genealogy.platform.spring.audit.AuditPublisher;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring wiring for the E3.2c application layer.
 *
 * <p>Every bean in this class is replaceable: the in-memory Keycloak
 * mirror becomes the {@code KeycloakAdminClient}-backed bean in
 * E3.5, the SHA-256 token hasher becomes the Vault-backed HMAC
 * hasher in E3.5, the system {@link Clock} becomes a test-controlled
 * fixed clock in unit tests. The application services themselves
 * never reference concrete types — they take interfaces.
 */
@Configuration
public class ApplicationConfig {

    /**
     * UUID v4 generator. The runtime guarantees uniqueness by
     * delegating to {@link UUID#randomUUID()} which the JVM seeds
     * with a strong entropy source. Unit tests override this bean
     * with a deterministic counter-based implementation.
     */
    @Bean
    public IdGenerator uuidV4IdGenerator() {
        return () -> UUID.randomUUID().toString();
    }

    /**
     * System UTC clock. Unit tests override this bean with
     * {@link Clock#fixed} so the aggregate timestamps are
     * deterministic.
     */
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public TokenHasher sha256TokenHasher() {
        return new TokenHasher.Sha256();
    }

    @Bean
    public InMemoryKeycloakSubjectMirror inMemoryKeycloakSubjectMirror(IdGenerator idGenerator) {
        return new InMemoryKeycloakSubjectMirror(idGenerator);
    }

    /**
     * Promote the in-memory mirror to the {@link KeycloakSubjectMirror}
     * interface so application code depends only on the port.
     * E3.5 swaps this bean for the production mirror.
     */
    @Bean
    public KeycloakSubjectMirror keycloakSubjectMirror(
            InMemoryKeycloakSubjectMirror mirror) {
        return mirror;
    }

    @Bean
    public OutboxWriter outboxWriter(JdbcTemplate jdbc, IdGenerator idGenerator) {
        return new JdbcOutboxWriter(jdbc, idGenerator);
    }

    @Bean
    public TenantAuditPublisher tenantAuditPublisher(AuditPublisher publisher) {
        return new TenantAuditPublisher(publisher);
    }

    @Bean
    public TenantRepository tenantRepository(JdbcTemplate jdbc, Clock clock) {
        return new TenantRepository(jdbc, clock);
    }

    @Bean
    public MembershipRepository membershipRepository(JdbcTemplate jdbc) {
        return new MembershipRepository(jdbc);
    }

    @Bean
    public InvitationRepository invitationRepository(JdbcTemplate jdbc) {
        return new InvitationRepository(jdbc);
    }

    @Bean
    public EntitlementRepository entitlementRepository(JdbcTemplate jdbc, Clock clock) {
        return new EntitlementRepository(jdbc, clock);
    }
}
