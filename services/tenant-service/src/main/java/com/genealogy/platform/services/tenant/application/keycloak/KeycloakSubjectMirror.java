package com.genealogy.platform.services.tenant.application.keycloak;

import com.genealogy.platform.services.tenant.domain.ids.UserId;

/**
 * Port for the Keycloak ↔ tenant-service subject mirror.
 *
 * <p>Per ADR-E0.5-05 tenant-service is the authoritative source for
 * the {@code Keycloak subject → Tenant membership} mapping. The
 * runtime queries Keycloak through this port to:
 *
 * <ul>
 *   <li>Resolve an email / token to a Keycloak {@code sub}
 *       (opaque, matches {@code ^[A-Za-z0-9_-]{8,64}$}) so the
 *       acceptance flow can write the {@code user_id} on a
 *       membership row.</li>
 *   <li>Mirror group membership / role assertions used by the
 *       audit trail (who actually triggered the mutation).</li>
 * </ul>
 *
 * <p>E3.2c ships the {@link InMemoryKeycloakSubjectMirror}
 * implementation; E3.5 will replace it with the
 * {@code KeycloakAdminClient}-backed implementation. Domain code
 * never references Keycloak directly — it depends only on this
 * interface so the swap is a one-line Spring bean rewire.
 */
public interface KeycloakSubjectMirror {

    /**
     * @param email RFC 5322 email address
     * @return the Keycloak {@code sub} for the user, or {@code null}
     *         if the user is not registered yet. The returned id MUST
     *         match the {@link com.genealogy.platform.services.tenant.domain.ids.OpaqueId#FORMAT}
     *         regex; the {@code null} return is the cue for the caller
     *         to issue an account-recovery / email-verification flow
     *         (E3.5) before re-trying.
     */
    UserId resolveByEmail(String email);

    /**
     * Reverse-lookup: given a raw invite token, return the email
     * address that was originally invited. The runtime hashes the
     * token the same way the writer did so the mirror can answer
     * without holding the raw token in memory.
     *
     * @param rawToken the token the user received by email
     * @return the email if a matching invite exists, else {@code null}
     */
    String findEmailByRawToken(String rawToken);

    /**
     * Provisioning path: resolve an email to a Keycloak subject,
     * auto-creating the user in the mirror if it does not exist.
     * Used by the invite flow when the invite is issued before
     * the user has signed in for the first time (the typical
     * flow per R1 acceptance criterion 2).
     *
     * <p>The in-memory implementation always succeeds; the
     * production implementation calls {@code KeycloakAdminClient.createUser}
     * and stores the returned {@code sub}.
     *
     * @param email RFC 5322 email address
     * @return the Keycloak {@code sub} for the user.
     */
    UserId ensureForEmail(String email);

    /**
     * @return {@code true} if the mirror is currently in a state
     *         where mutations may proceed. The in-memory mirror is
     *         always {@code true}; the Keycloak-backed mirror
     *         returns {@code false} when the admin client cannot
     *         reach the realm.
     */
    boolean isHealthy();
}
