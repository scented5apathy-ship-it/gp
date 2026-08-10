package com.genealogy.platform.services.tenant.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.tenant.application.Commands;
import com.genealogy.platform.services.tenant.application.MembershipCommandService;
import com.genealogy.platform.services.tenant.application.MembershipQueryService;
import com.genealogy.platform.services.tenant.application.Results;
import com.genealogy.platform.services.tenant.application.TenantCommandService;
import com.genealogy.platform.services.tenant.domain.ids.MembershipId;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.ids.UserId;
import com.genealogy.platform.services.tenant.domain.invitation.Email;
import com.genealogy.platform.services.tenant.domain.membership.MembershipRole;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the membership nested resource. Mounted at
 * {@code /api/v1/tenants/{tenantId}/memberships} per the OpenAPI
 * contract.
 *
 * <p>Same headers as {@link TenantController}: idempotency, ETag,
 * correlation id, and the trusted-tenant-id binding from
 * {@link com.genealogy.platform.spring.web.TrustedContextFilter}.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/memberships")
public class MembershipController {

    private final MembershipCommandService membershipCommandService;
    private final MembershipQueryService membershipQueryService;
    private final IdempotencyCache idempotencyCache;
    private final ObjectMapper objectMapper;

    public MembershipController(
            MembershipCommandService membershipCommandService,
            MembershipQueryService membershipQueryService,
            IdempotencyCache idempotencyCache,
            ObjectMapper objectMapper) {
        this.membershipCommandService = Objects.requireNonNull(membershipCommandService,
                "membershipCommandService");
        this.membershipQueryService = Objects.requireNonNull(membershipQueryService,
                "membershipQueryService");
        this.idempotencyCache = Objects.requireNonNull(idempotencyCache, "idempotencyCache");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @PostMapping
    public ResponseEntity<InvitationResponse> invite(
            @PathVariable("tenantId") String tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody InviteMemberRequest body,
            HttpServletRequest request) {
        TenantId trusted = assertTrustedTenantId();
        validateOwnership(trusted, tenantId);
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return replayInvitationResponse(replay.get());
        }
        if (body.email() == null || body.role() == null) {
            throw new IllegalArgumentException("email and role are required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        MembershipRole role = MembershipRole.valueOf(body.role());
        UserId inviter = new UserId(TrustedTenantContext.current().getActorId() == null
                ? "anonymous" : TrustedTenantContext.current().getActorId());
        // The raw invite token is generated server-side so the wire
        // never carries a client-supplied token (the contract body
        // does not include it). A cryptographically random value is
        // stamped on the invitation row; the email-service (E4.x)
        // reads the row and emails the token to the invitee.
        String rawInviteToken = generateRawToken();
        Commands.InviteMember cmd = new Commands.InviteMember(
                trusted,
                new Email(body.email()),
                role,
                inviter,
                idempotencyKey,
                rawInviteToken,
                Duration.ofDays(7));
        Results.InvitationView view = membershipCommandService.invite(cmd);
        InvitationResponse response = InvitationResponse.from(view);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.ACCEPTED, response);
        }
        URI location = URI.create(request.getRequestURI() + "/" + view.id().getValue());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(location)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable("tenantId") String tenantId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "pageSize", required = false, defaultValue = "50") int pageSize) {
        TenantId trusted = assertTrustedTenantId();
        validateOwnership(trusted, tenantId);
        MembershipQueryService.MembershipPage page =
                membershipQueryService.listForCurrentTenant(pageSize, cursor);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.items().stream().map(MembershipResponse::from).toList());
        if (page.nextCursor() != null) {
            body.put("nextCursor", page.nextCursor());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @DeleteMapping("/{membershipId}")
    public ResponseEntity<Void> revoke(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("membershipId") String membershipId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) RevokeMembershipRequest body) {
        TenantId trusted = assertTrustedTenantId();
        validateOwnership(trusted, tenantId);
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return ResponseEntity.status(replay.get().status())
                    .header("X-Idempotent-Replay", "true")
                    .build();
        }
        long expectedVersion = parseETag(ifMatch);
        Commands.RevokeMembership cmd = new Commands.RevokeMembership(
                trusted,
                new MembershipId(membershipId),
                expectedVersion,
                body == null ? null : body.reason());
        membershipCommandService.revoke(cmd);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.ACCEPTED, null);
        }
        return ResponseEntity.accepted().build();
    }

    private static void validateOwnership(TenantId trusted, String pathTenantId) {
        if (pathTenantId == null || !pathTenantId.equals(trusted.getValue())) {
            throw new MembershipCommandService.MembershipNotFoundException(
                    "membership under tenant " + pathTenantId + " not found");
        }
    }

    private static TenantId assertTrustedTenantId() {
        TrustedTenantContext ctx = TrustedTenantContext.current();
        String tenantId = ctx.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantCommandService.TenantNotFoundException(
                    "trusted tenant context is missing");
        }
        return new TenantId(tenantId);
    }

    private static long parseETag(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || "\"\"".equals(ifMatch)) {
            throw new TenantCommandService.OptimisticConcurrencyException(
                    "If-Match header is required for mutations");
        }
        String trimmed = ifMatch.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.startsWith("v")) {
            trimmed = trimmed.substring(1);
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException nfe) {
            throw new TenantCommandService.OptimisticConcurrencyException(
                    "If-Match header is not a recognised version: " + ifMatch);
        }
    }

    private static String generateRawToken() {
        byte[] random = new byte[24];
        new java.security.SecureRandom().nextBytes(random);
        return java.util.HexFormat.of().formatHex(random);
    }

    private void cacheResponse(String key, HttpStatus status, Object body) {
        try {
            String json = body == null ? "" : objectMapper.writeValueAsString(body);
            idempotencyCache.store(key, new IdempotencyCache.CachedResponse(
                    status.value(),
                    MediaType.APPLICATION_JSON_VALUE,
                    json,
                    null));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // best-effort
        }
    }

    private ResponseEntity<InvitationResponse> replayInvitationResponse(
            IdempotencyCache.CachedResponse cached) {
        try {
            InvitationResponse body = cached.body() == null || cached.body().isEmpty()
                    ? null
                    : objectMapper.readValue(cached.body(), InvitationResponse.class);
            return ResponseEntity.status(cached.status())
                    .header("X-Idempotent-Replay", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to replay cached response", e);
        }
    }

    /* ---------------- Request / Response DTOs ---------------- */

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InviteMemberRequest(String email, String role, Map<String, Object> scope) {
    }

    public record RevokeMembershipRequest(String reason) {
    }

    public record InvitationResponse(
            String invitationId,
            String tenantId,
            String email,
            String role,
            String expiresAt,
            @JsonProperty("rawInviteToken") String rawInviteToken) {

        public static InvitationResponse from(Results.InvitationView v) {
            return new InvitationResponse(
                    v.id().getValue(),
                    v.tenantId().getValue(),
                    v.email(),
                    v.role().name(),
                    v.expiresAt() == null ? null : v.expiresAt().toString(),
                    v.rawInviteToken());
        }
    }

    public record MembershipResponse(
            String membershipId,
            String tenantId,
            String userId,
            String role,
            String status,
            long version,
            String invitedAt,
            String joinedAt) {

        public static MembershipResponse from(Results.MembershipView v) {
            return new MembershipResponse(
                    v.id().getValue(),
                    v.tenantId().getValue(),
                    v.userId().getValue(),
                    v.role().name(),
                    v.status(),
                    v.version(),
                    v.invitedAt() == null ? null : v.invitedAt().toString(),
                    v.joinedAt() == null ? null : v.joinedAt().toString());
        }
    }
}
