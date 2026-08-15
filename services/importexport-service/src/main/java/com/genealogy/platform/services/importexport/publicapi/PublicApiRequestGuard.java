package com.genealogy.platform.services.importexport.publicapi;

import com.genealogy.platform.services.importexport.shared.ImportExportLimits;
import java.util.Set;

/**
 * Public API orchestrator. Enforces:
 *  - per-IP per-minute ≤ {@link ImportExportLimits#PUBLIC_API_PER_IP_PER_MINUTE};
 *  - per-IP per-hour ≤ {@link ImportExportLimits#PUBLIC_API_PER_IP_PER_HOUR};
 *  - per-client per-minute / per-hour caps;
 *  - scope → required resource mapping;
 *  - tenant boundary + DNA bucket shield;
 *  - abuse-signal flag propagation.
 */
public final class PublicApiRequestGuard {

  private static final Set<String> LIVING_SCOPES = Set.of(
      "public.read.living");
  private static final Set<String> MEDIA_SCOPES = Set.of(
      "public.read.media");
  private static final Set<String> TREE_SCOPES = Set.of(
      "public.read.tree");
  private static final Set<String> ALBUM_SCOPES = Set.of(
      "public.read.album");

  private PublicApiRequestGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static PublicApiOutcome authorize(PublicApiRequest request) {
    if (request == null) {
      return PublicApiOutcome.deny("PUBLIC_API_RESOURCE_UNKNOWN", "request MUST NOT be null");
    }
    if (request.resource() == null) {
      return PublicApiOutcome.deny("PUBLIC_API_RESOURCE_UNKNOWN", "resource MUST NOT be null");
    }
    if (request.method() == null) {
      return PublicApiOutcome.deny("PUBLIC_API_METHOD_FORBIDDEN", "method MUST be GET/HEAD/OPTIONS");
    }
    if (request.scope() == null) {
      return PublicApiOutcome.deny("PUBLIC_API_SCOPE_MISSING", "scope MUST NOT be null");
    }
    if (!scopeCoversResource(request.resource(), request.scope())) {
      return PublicApiOutcome.deny("PUBLIC_API_SCOPE_INSUFFICIENT",
          "scope=" + request.scope().wire() + " resource=" + request.resource().wire());
    }
    if (request.perIpPerMinute() > ImportExportLimits.PUBLIC_API_PER_IP_PER_MINUTE) {
      return PublicApiOutcome.deny("PUBLIC_API_RATE_LIMIT_EXCEEDED", "perIpPerMinute");
    }
    if (request.perIpPerHour() > ImportExportLimits.PUBLIC_API_PER_IP_PER_HOUR) {
      return PublicApiOutcome.deny("PUBLIC_API_RATE_LIMIT_EXCEEDED", "perIpPerHour");
    }
    if (request.perClientPerMinute() > ImportExportLimits.PUBLIC_API_PER_CLIENT_PER_MINUTE) {
      return PublicApiOutcome.deny("PUBLIC_API_QUOTA_EXCEEDED", "perClientPerMinute");
    }
    if (request.perClientPerHour() > ImportExportLimits.PUBLIC_API_PER_CLIENT_PER_HOUR) {
      return PublicApiOutcome.deny("PUBLIC_API_QUOTA_EXCEEDED", "perClientPerHour");
    }
    if (!request.tenantPseudoId().equals(request.expectedTenantPseudoId())) {
      return PublicApiOutcome.deny("PUBLIC_API_TENANT_MISMATCH", "tenantPseudoId");
    }
    if (request.dnaBucketReference()) {
      return PublicApiOutcome.deny("PUBLIC_API_DNA_BUCKET_FORBIDDEN", "dna bucket reference");
    }
    if (request.abuseSignal()) {
      return PublicApiOutcome.deny("PUBLIC_API_ABUSE_SIGNAL_DETECTED", "abuse signal");
    }
    if (request.idempotencyKeyReuseConflict()) {
      return PublicApiOutcome.deny("PUBLIC_API_IDEMPOTENCY_KEY_REUSED_CONFLICT", "idempotencyKey");
    }
    return PublicApiOutcome.allow(request);
  }

  private static boolean scopeCoversResource(PublicApiResource resource, PublicApiScope scope) {
    if (scope == PublicApiScope.PUBLIC_READ_BASIC) {
      return true;
    }
    if (scope == PublicApiScope.ADMIN_READ_ABUSE) {
      return true;
    }
    String wire = scope.wire();
    return switch (resource) {
      case PUBLIC_PERSON -> LIVING_SCOPES.contains(wire) || wire.equals("public.read.basic");
      case PUBLIC_EVENT, PUBLIC_PLACE, PUBLIC_SOURCE, PUBLIC_CITATION -> wire.equals("public.read.basic");
      case PUBLIC_MEDIA -> MEDIA_SCOPES.contains(wire) || wire.equals("public.read.basic");
      case PUBLIC_TREE -> TREE_SCOPES.contains(wire) || wire.equals("public.read.basic");
      case PUBLIC_ALBUM -> ALBUM_SCOPES.contains(wire) || wire.equals("public.read.basic");
    };
  }

  public enum PublicApiResource {
    PUBLIC_PERSON,
    PUBLIC_EVENT,
    PUBLIC_PLACE,
    PUBLIC_SOURCE,
    PUBLIC_CITATION,
    PUBLIC_MEDIA,
    PUBLIC_TREE,
    PUBLIC_ALBUM;

    public String wire() {
      return name().toLowerCase().replace('_', '.');
    }
  }

  public enum PublicApiMethod {
    GET,
    HEAD,
    OPTIONS
  }

  public record PublicApiRequest(
      PublicApiResource resource,
      PublicApiMethod method,
      PublicApiScope scope,
      long perIpPerMinute,
      long perIpPerHour,
      long perClientPerMinute,
      long perClientPerHour,
      boolean dnaBucketReference,
      boolean abuseSignal,
      boolean idempotencyKeyReuseConflict,
      String tenantPseudoId,
      String expectedTenantPseudoId) {

    public PublicApiRequest {
      if (tenantPseudoId == null) tenantPseudoId = "";
      if (expectedTenantPseudoId == null) expectedTenantPseudoId = tenantPseudoId;
    }
  }

  public record PublicApiOutcome(
      boolean allow,
      String failureReason,
      String detail,
      PublicApiRequest request) {

    public static PublicApiOutcome allow(PublicApiRequest request) {
      return new PublicApiOutcome(true, null, null, request);
    }

    public static PublicApiOutcome deny(String reason, String detail) {
      return new PublicApiOutcome(false, reason, detail, null);
    }
  }
}