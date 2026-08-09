package com.genealogy.platform.services.tenant.domain.ids;

/**
 * Keycloak subject pseudonym (the {@code sub} claim after pseudonym
 * rotation). The runtime role Keycloak assignment is the
 * {@code identity-primary} team's responsibility per
 * {@code ownership-catalog.md} §3; {@code tenant-service} consumes
 * the pseudonym, never the raw email or the cleartext Keycloak id.
 */
public final class UserId extends OpaqueId {

    public UserId(String value) {
        super(value);
    }
}