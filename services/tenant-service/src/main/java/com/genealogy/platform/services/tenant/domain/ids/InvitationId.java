package com.genealogy.platform.services.tenant.domain.ids;

/**
 * Invitation row identifier. The invitation links an email + role +
 * idempotency-key to a future {@link MembershipId}; on acceptance
 * E3.2c materialises the membership row and the
 * {@code MembershipInvited} -> {@code MembershipActivated} event pair.
 */
public final class InvitationId extends OpaqueId {

    public InvitationId(String value) {
        super(value);
    }
}