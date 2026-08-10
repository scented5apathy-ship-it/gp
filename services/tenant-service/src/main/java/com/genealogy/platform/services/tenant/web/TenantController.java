package com.genealogy.platform.services.tenant.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.tenant.application.Commands;
import com.genealogy.platform.services.tenant.application.EntitlementCommandService;
import com.genealogy.platform.services.tenant.application.EntitlementQueryService;
import com.genealogy.platform.services.tenant.application.Results;
import com.genealogy.platform.services.tenant.application.TenantCommandService;
import com.genealogy.platform.services.tenant.application.TenantQueryService;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.tenant.CalendarType;
import com.genealogy.platform.services.tenant.domain.tenant.Locale;
import com.genealogy.platform.services.tenant.domain.tenant.Slug;
import com.genealogy.platform.services.tenant.domain.tenant.TenantDisplayName;
import com.genealogy.platform.services.tenant.domain.tenant.TenantPlan;
import com.genealogy.platform.services.tenant.domain.tenant.Timezone;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the {@code Tenant} aggregate. Mounted at
 * {@code /api/v1/tenants} per the OpenAPI contract
 * ({@code contracts/openapi/public-api/v1/tenant.yaml}).
 *
 * <p>Honours every header documented in
 * {@code contracts/openapi/common/headers.yaml}:
 *
 * <ul>
 *   <li>{@code Idempotency-Key} — replayed via {@link IdempotencyCache}.</li>
 *   <li>{@code If-Match} — optimistic concurrency, returns
 *       {@code 412 Precondition Failed} on mismatch.</li>
 *   <li>{@code X-Correlation-Id} — echoed on every response and
 *       stamped on every outbox row.</li>
 *   <li>{@code X-Tenant-Id} — populated by the
 *       {@code TrustedContextFilter} into {@link TrustedTenantContext}.</li>
 * </ul>
 *
 * <p>Cross-tenant attempts return {@code 404 Not Found} rather than
 * {@code 403 Forbidden} so the wire does not leak the existence of a
 * tenant the caller cannot reach.
 */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantCommandService tenantCommandService;
    private final TenantQueryService tenantQueryService;
    private final EntitlementQueryService entitlementQueryService;
    private final EntitlementCommandService entitlementCommandService;
    private final IdempotencyCache idempotencyCache;
    private final ObjectMapper objectMapper;

    public TenantController(
            TenantCommandService tenantCommandService,
            TenantQueryService tenantQueryService,
            EntitlementQueryService entitlementQueryService,
            EntitlementCommandService entitlementCommandService,
            IdempotencyCache idempotencyCache,
            ObjectMapper objectMapper) {
        this.tenantCommandService =
                Objects.requireNonNull(tenantCommandService, "tenantCommandService");
        this.tenantQueryService =
                Objects.requireNonNull(tenantQueryService, "tenantQueryService");
        this.entitlementQueryService =
                Objects.requireNonNull(entitlementQueryService, "entitlementQueryService");
        this.entitlementCommandService = Objects.requireNonNull(entitlementCommandService,
                "entitlementCommandService");
        this.idempotencyCache = Objects.requireNonNull(idempotencyCache, "idempotencyCache");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @PostMapping
    public ResponseEntity<TenantResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody CreateTenantRequest body,
            HttpServletRequest request) {
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return replayResponse(replay.get(), TenantResponse.class);
        }
        TenantId tenantId = assertTrustedTenantId();
        if (body.slug() == null || body.slug().isBlank()
                || body.displayName() == null || body.displayName().isBlank()) {
            throw new IllegalArgumentException("slug and displayName are required");
        }
        TenantPlan plan = body.plan() == null ? TenantPlan.FREE : TenantPlan.valueOf(body.plan());
        Locale locale = body.defaultLocale() == null ? null : new Locale(body.defaultLocale());
        Timezone timezone = body.defaultTimezone() == null
                ? null : new Timezone(body.defaultTimezone());
        CalendarType calendar = body.defaultCalendar() == null
                ? null : CalendarType.valueOf(body.defaultCalendar());

        Commands.CreateTenant cmd = new Commands.CreateTenant(
                new Slug(body.slug()),
                new TenantDisplayName(body.displayName()),
                plan,
                locale,
                timezone,
                calendar);
        String actorId = TrustedTenantContext.current().getActorId();
        Results.TenantView view = tenantCommandService.create(cmd,
                actorId == null ? "anonymous" : actorId);
        TenantResponse response = TenantResponse.from(view);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.CREATED, response, view.etag());
        }
        URI location = URI.create(request.getRequestURI() + "/" + view.id().getValue());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(location)
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "pageSize", required = false, defaultValue = "50") int pageSize,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        assertTrustedTenantId();
        TenantQueryService.TenantPage page = tenantQueryService.listForCurrentUser(pageSize, cursor);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.items().stream().map(TenantResponse::from).toList());
        if (page.nextCursor() != null) {
            body.put("nextCursor", page.nextCursor());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> get(
            @PathVariable("tenantId") String tenantId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        TenantId trusted = assertTrustedTenantId();
        validateOwnership(trusted, tenantId);
        Results.TenantView view = tenantQueryService.findById(new TenantId(tenantId))
                .orElseThrow(() -> new TenantCommandService.TenantNotFoundException(
                        "tenant " + tenantId + " not found"));
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(TenantResponse.from(view));
    }

    @PatchMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> update(
            @PathVariable("tenantId") String tenantId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody UpdateTenantRequest body,
            HttpServletRequest request) {
        TenantId trusted = assertTrustedTenantId();
        validateOwnership(trusted, tenantId);
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return replayResponse(replay.get(), TenantResponse.class);
        }
        long expectedVersion = parseETag(ifMatch);
        Commands.UpdateTenant cmd = new Commands.UpdateTenant(
                new TenantId(tenantId),
                expectedVersion,
                new TenantDisplayName(body.displayName()));
        Results.TenantView view = tenantCommandService.update(cmd);
        TenantResponse response = TenantResponse.from(view);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.OK, response, view.etag());
        }
        URI location = URI.create(request.getRequestURI());
        return ResponseEntity.ok()
                .location(location)
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<Void> delete(
            @PathVariable("tenantId") String tenantId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TenantId trusted = assertTrustedTenantId();
        validateOwnership(trusted, tenantId);
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            IdempotencyCache.CachedResponse cached = replay.get();
            return ResponseEntity.status(cached.status())
                    .header("X-Idempotent-Replay", "true")
                    .build();
        }
        long expectedVersion = parseETag(ifMatch);
        tenantCommandService.softDelete(new Commands.SoftDeleteTenant(
                new TenantId(tenantId), expectedVersion));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.ACCEPTED, null, null);
        }
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{tenantId}/entitlement")
    public ResponseEntity<EntitlementResponse> entitlement(
            @PathVariable("tenantId") String tenantId) {
        TenantId trusted = assertTrustedTenantId();
        validateOwnership(trusted, tenantId);
        Results.EntitlementView view = entitlementQueryService
                .findForTenant(new TenantId(tenantId))
                .orElseThrow(() -> new EntitlementCommandService.EntitlementNotFoundException(
                        "entitlement for tenant " + tenantId + " not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(EntitlementResponse.from(view));
    }

    @PatchMapping("/{tenantId}/entitlement")
    public ResponseEntity<EntitlementResponse> changeEntitlement(
            @PathVariable("tenantId") String tenantId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ChangeEntitlementRequest body) {
        TenantId trusted = assertTrustedTenantId();
        validateOwnership(trusted, tenantId);
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return replayResponse(replay.get(), EntitlementResponse.class);
        }
        TenantPlan newPlan = body.plan() == null ? null : TenantPlan.valueOf(body.plan());
        Commands.ChangeEntitlement cmd = new Commands.ChangeEntitlement(
                new TenantId(tenantId),
                newPlan,
                body.memberLimit(),
                body.treeLimit(),
                body.storageLimitMb(),
                body.retentionDays(),
                body.billingExternalId());
        String actorId = TrustedTenantContext.current().getActorId();
        Results.EntitlementView view = entitlementCommandService.change(cmd,
                actorId == null ? "anonymous" : actorId);
        EntitlementResponse response = EntitlementResponse.from(view);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.OK, response, null);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Defence in depth: the path {@code tenantId} must match the
     * trusted tenant id from {@link TrustedTenantContext}. A
     * mismatch is answered with {@code 404 Not Found} so the wire
     * does not leak the existence of a tenant the caller cannot see.
     */
    private static void validateOwnership(TenantId trusted, String pathTenantId) {
        if (pathTenantId == null || !pathTenantId.equals(trusted.getValue())) {
            throw new TenantCommandService.TenantNotFoundException(
                    "tenant " + pathTenantId + " not found");
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
        // Accept both `"v3"` and `v3` so curl callers can omit the
        // quotes without surprise. The service stamps `"vN"` on the
        // wire.
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

    private void cacheResponse(String key, HttpStatus status, Object body, String etag) {
        try {
            String json = body == null ? "" : objectMapper.writeValueAsString(body);
            idempotencyCache.store(key, new IdempotencyCache.CachedResponse(
                    status.value(),
                    MediaType.APPLICATION_JSON_VALUE,
                    json,
                    etag));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Idempotency cache is best-effort; a serialisation error
            // never fails the original request.
        }
    }

    private <T> ResponseEntity<T> replayResponse(
            IdempotencyCache.CachedResponse cached, Class<T> type) {
        try {
            T body = cached.body() == null || cached.body().isEmpty()
                    ? null
                    : objectMapper.readValue(cached.body(), type);
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(cached.status());
            if (cached.etag() != null) {
                builder.header(HttpHeaders.ETAG, cached.etag());
            }
            return builder
                    .header("X-Idempotent-Replay", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to replay cached response", e);
        }
    }

    /* ---------------- Request / Response DTOs ---------------- */

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateTenantRequest(
            String slug,
            String displayName,
            String plan,
            @JsonProperty("defaultLocale") String defaultLocale,
            @JsonProperty("defaultTimezone") String defaultTimezone,
            @JsonProperty("defaultCalendar") String defaultCalendar) {
    }

    public record UpdateTenantRequest(String displayName) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChangeEntitlementRequest(
            String plan,
            Integer memberLimit,
            Integer treeLimit,
            Integer storageLimitMb,
            Integer retentionDays,
            String billingExternalId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TenantResponse(
            String tenantId,
            String slug,
            String displayName,
            String plan,
            String status,
            @JsonProperty("defaultLocale") String defaultLocale,
            @JsonProperty("defaultTimezone") String defaultTimezone,
            @JsonProperty("defaultCalendar") String defaultCalendar,
            String etag,
            String createdAt) {

        public static TenantResponse from(Results.TenantView v) {
            return new TenantResponse(
                    v.id().getValue(),
                    v.slug().value(),
                    v.displayName().value(),
                    v.plan().name(),
                    v.status(),
                    v.locale() == null ? null : v.locale().tag(),
                    v.timezone() == null ? null : v.timezone().id(),
                    v.calendar() == null ? null : v.calendar().name(),
                    v.etag(),
                    v.createdAt() == null ? null : v.createdAt().toString());
        }
    }

    public record EntitlementResponse(
            String tenantId,
            String plan,
            int memberLimit,
            int treeLimit,
            int storageLimitMb,
            int retentionDays,
            String billingExternalId,
            String updatedAt) {

        public static EntitlementResponse from(Results.EntitlementView v) {
            return new EntitlementResponse(
                    v.tenantId().getValue(),
                    v.plan().name(),
                    v.memberLimit(),
                    v.treeLimit(),
                    v.storageLimitMb(),
                    v.retentionDays(),
                    v.billingExternalId(),
                    v.updatedAt() == null ? null : v.updatedAt().toString());
        }
    }
}
