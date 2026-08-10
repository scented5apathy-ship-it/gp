package com.genealogy.platform.webbff.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Typed projection of the {@code tenant-service} membership
 * resource consumed by the BFF reconciliation layer (E3.5).
 *
 * <p>The BFF calls
 * {@code GET /api/v1/tenants/{tenantId}/memberships} with the
 * Keycloak subject as a service-to-service caller, and
 * reconciles the response against the requested tenant
 * selection. The wire shape mirrors the public OpenAPI under
 * {@code contracts/openapi/public-api/v1/tenant.yaml}; the
 * record is intentionally a read-only DTO so the BFF stays
 * decoupled from the service-side aggregate types.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MembershipView {

    public String membershipId;
    public String tenantId;
    public String userId;
    public String role;
    public String status;
    public OffsetDateTime invitedAt;
    public OffsetDateTime activatedAt;

    public MembershipView() {
        // Jackson
    }

    public MembershipView(
            String membershipId,
            String tenantId,
            String userId,
            String role,
            String status,
            OffsetDateTime invitedAt,
            OffsetDateTime activatedAt) {
        this.membershipId = membershipId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.invitedAt = invitedAt;
        this.activatedAt = activatedAt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Page {
        public List<MembershipView> items;
        public String nextCursor;
    }
}
