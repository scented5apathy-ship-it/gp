package com.genealogy.platform.services.tenant.application;

import com.genealogy.platform.services.tenant.domain.ids.InvitationId;
import com.genealogy.platform.services.tenant.domain.ids.MembershipId;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.membership.MembershipRole;
import com.genealogy.platform.services.tenant.domain.tenant.CalendarType;
import com.genealogy.platform.services.tenant.domain.tenant.Locale;
import com.genealogy.platform.services.tenant.domain.tenant.Slug;
import com.genealogy.platform.services.tenant.domain.tenant.TenantDisplayName;
import com.genealogy.platform.services.tenant.domain.tenant.TenantPlan;
import com.genealogy.platform.services.tenant.domain.tenant.Timezone;

/**
 * Application-layer result DTOs. Returned by the command services
 * so the controllers (E3.2d) and gRPC adapters (E3.2e) do not have
 * to re-construct view models from the framework-free aggregates.
 *
 * <p>All timestamps are {@link java.time.Instant}; the wire layer
 * (REST / gRPC) is responsible for RFC 3339 serialisation.
 */
public final class Results {

    private Results() {
        // utility
    }

    public record TenantView(
            TenantId id,
            Slug slug,
            TenantDisplayName displayName,
            TenantPlan plan,
            String status,
            Locale locale,
            Timezone timezone,
            CalendarType calendar,
            long version,
            String etag,
            java.time.Instant createdAt,
            java.time.Instant updatedAt,
            java.time.Instant suspendedAt,
            java.time.Instant deletedAt) {
    }

    public record MembershipView(
            MembershipId id,
            TenantId tenantId,
            com.genealogy.platform.services.tenant.domain.ids.UserId userId,
            MembershipRole role,
            String status,
            long version,
            java.time.Instant invitedAt,
            java.time.Instant joinedAt,
            java.time.Instant suspendedAt,
            java.time.Instant revokedAt) {
    }

    public record InvitationView(
            InvitationId id,
            TenantId tenantId,
            String email,
            MembershipRole role,
            java.time.Instant expiresAt,
            java.time.Instant acceptedAt,
            java.time.Instant revokedAt,
            String rawInviteToken) {
    }

    public record EntitlementView(
            TenantId tenantId,
            TenantPlan plan,
            int memberLimit,
            int treeLimit,
            int storageLimitMb,
            int retentionDays,
            String billingExternalId,
            java.time.Instant updatedAt) {
    }
}
