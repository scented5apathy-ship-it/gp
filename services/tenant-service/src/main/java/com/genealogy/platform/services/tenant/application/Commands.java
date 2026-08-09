package com.genealogy.platform.services.tenant.application;

import com.genealogy.platform.services.tenant.domain.invitation.Email;
import com.genealogy.platform.services.tenant.domain.membership.MembershipRole;
import com.genealogy.platform.services.tenant.domain.tenant.CalendarType;
import com.genealogy.platform.services.tenant.domain.tenant.Locale;
import com.genealogy.platform.services.tenant.domain.tenant.Slug;
import com.genealogy.platform.services.tenant.domain.tenant.TenantDisplayName;
import com.genealogy.platform.services.tenant.domain.tenant.TenantPlan;
import com.genealogy.platform.services.tenant.domain.tenant.Timezone;

/**
 * Application-layer DTOs. These are the inputs to the command
 * services (E3.2c). They are framework-free records so the
 * controllers and gRPC adapters (E3.2d/e) can map their wire
 * types onto these without leaking framework annotations into
 * the domain layer.
 *
 * <p>Validation that is invariant across every wire (e.g. slug
 * regex, display name length) lives in the value objects
 * {@link Slug} / {@link TenantDisplayName} / {@link Email}. This
 * record is just a transport carrier.
 */
public final class Commands {

    private Commands() {
        // utility
    }

    /** Command to create a new tenant. The caller becomes the OWNER. */
    public record CreateTenant(
            Slug slug,
            TenantDisplayName displayName,
            TenantPlan plan,
            Locale locale,
            Timezone timezone,
            CalendarType calendar) {
    }

    /** Command to rename a tenant. Optimistic concurrency via {@code expectedVersion}. */
    public record UpdateTenant(
            com.genealogy.platform.services.tenant.domain.ids.TenantId tenantId,
            long expectedVersion,
            TenantDisplayName displayName) {
    }

    /** Command to change tenant plan. */
    public record ChangePlan(
            com.genealogy.platform.services.tenant.domain.ids.TenantId tenantId,
            long expectedVersion,
            TenantPlan newPlan) {
    }

    /** Command to suspend a tenant. */
    public record SuspendTenant(
            com.genealogy.platform.services.tenant.domain.ids.TenantId tenantId,
            long expectedVersion) {
    }

    /** Command to restore a suspended tenant. */
    public record RestoreTenant(
            com.genealogy.platform.services.tenant.domain.ids.TenantId tenantId,
            long expectedVersion) {
    }

    /** Command to soft-delete a tenant. */
    public record SoftDeleteTenant(
            com.genealogy.platform.services.tenant.domain.ids.TenantId tenantId,
            long expectedVersion) {
    }

    /** Command to invite a user. Idempotency key required (matches REST header). */
    public record InviteMember(
            com.genealogy.platform.services.tenant.domain.ids.TenantId tenantId,
            Email email,
            MembershipRole role,
            com.genealogy.platform.services.tenant.domain.ids.UserId invitedByUserId,
            String idempotencyKey,
            String rawInviteToken,
            java.time.Duration ttl) {
    }

    /** Command to activate a pending invite. */
    public record ActivateMembership(
            com.genealogy.platform.services.tenant.domain.ids.TenantId tenantId,
            com.genealogy.platform.services.tenant.domain.ids.UserId userId,
            String inviteToken) {
    }

    /** Command to revoke a membership. */
    public record RevokeMembership(
            com.genealogy.platform.services.tenant.domain.ids.TenantId tenantId,
            com.genealogy.platform.services.tenant.domain.ids.MembershipId membershipId,
            long expectedVersion,
            String reason) {
    }

    /** Command to change tenant entitlement plan + quotas. */
    public record ChangeEntitlement(
            com.genealogy.platform.services.tenant.domain.ids.TenantId tenantId,
            TenantPlan newPlan,
            Integer newMemberLimit,
            Integer newTreeLimit,
            Integer newStorageLimitMb,
            Integer newRetentionDays,
            String billingExternalId) {
    }
}
