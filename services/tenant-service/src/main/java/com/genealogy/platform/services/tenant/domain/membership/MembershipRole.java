package com.genealogy.platform.services.tenant.domain.membership;

/**
 * Per-tenant membership role. Mirrors the gRPC {@code MembershipRole}
 * enum and the Avro {@code MembershipRole} enum. Per ADR-E0.5-05 the
 * federated Keycloak {@code groups} MUST NOT force-sync into a role;
 * only an OWNER can invite a user with role OTHER than the
 * default {@link #MEMBER}. Self-service promotion to OWNER is not
 * supported.
 */
public enum MembershipRole {

    /** Sole owner of a tenant; the only role that can delete a tenant. */
    OWNER,

    /** Operational admin; can manage memberships and tree ACLs. */
    ADMIN,

    /** Default role for invited collaborators. */
    MEMBER,

    /** Read-only audit access; required by some enterprise contracts. */
    AUDITOR,

    /** Can manage billing / entitlement; cannot manage memberships. */
    BILLING_ADMIN
}