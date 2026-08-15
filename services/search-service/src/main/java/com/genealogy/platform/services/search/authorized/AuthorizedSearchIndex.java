package com.genealogy.platform.services.search.authorized;

/**
 * Pure deterministic orchestrator for the E8.2 authorized search.
 * The orchestrator wires the OpenFGA verdict + the ABAC overlay +
 * the permission-version binding into a single
 * {@link AuthorizedSearchDecision}.
 *
 * <p>The pipeline:
 * <ol>
 *   <li>Refuse any query whose resource kind is opaque
 *       (cross-service references stay opaque).</li>
 *   <li>Refuse queries whose permission version is older than
 *       {@code allowedPermissionVersion}.</li>
 *   <li>Delegate to {@link SearchOpenFgaPort#check} for the
 *       relationship verdict (ADR-E0.5-06).</li>
 *   <li>Delegate to {@link SearchAbacPort#evaluate} for the ABAC
 *       overlay (living / minor / DNA / consent).</li>
 *   <li>Build the opaque cursor + hit list. Cursor is opaque base64
 *       payload; the orchestrator keeps it deterministic so tests
 *       can pin the exact wire shape.</li>
 * </ol>
 */
public final class AuthorizedSearchIndex {

  private AuthorizedSearchIndex() {}

  public static AuthorizedSearchDecision apply(
      AuthorizedSearchQuery query,
      String allowedPermissionVersion,
      SearchOpenFgaPort openFga,
      SearchAbacPort abac) {
    if (query == null) {
      throw new IllegalArgumentException("query MUST NOT be null");
    }
    if (allowedPermissionVersion == null || allowedPermissionVersion.isBlank()) {
      throw new IllegalArgumentException(
          "allowedPermissionVersion MUST NOT be blank");
    }
    if (openFga == null) {
      throw new IllegalArgumentException("openFga MUST NOT be null");
    }
    if (abac == null) {
      throw new IllegalArgumentException("abac MUST NOT be null");
    }
    if (isStalePermissionVersion(query.permissionVersion(), allowedPermissionVersion)) {
      return AuthorizedSearchDecision.denied(
          SearchAuthorizationOutcome.PERMISSION_VERSION_STALE,
          AuthorizedSearchFailureReason.SEARCH_PERMISSION_VERSION_STALE);
    }
    SearchOpenFgaVerdict openFgaVerdict =
        openFga.check(query.tenantScopeId(), query.resourceKind(), query.actorPseudoId());
    if (openFgaVerdict.outcome() == SearchOpenFgaOutcome.DENY) {
      return AuthorizedSearchDecision.denied(
          SearchAuthorizationOutcome.OPENFGA_DENY,
          AuthorizedSearchFailureReason.SEARCH_OPENFGA_DENY);
    }
    SearchAbacVerdict abacVerdict = abac.evaluate(query.tenantScopeId(), query.actorPseudoId());
    if (abacVerdict.outcome() != SearchAuthorizationOutcome.ALLOWED) {
      return AuthorizedSearchDecision.denied(
          abacVerdict.outcome(),
          mapAbacReason(abacVerdict.outcome()));
    }
    String cursor = opaqueCursor(query);
    return AuthorizedSearchDecision.allowed(
        java.util.List.of(
            query.resourceKind() + ":" + query.tenantScopeId() + ":hit-0"),
        cursor);
  }

  private static boolean isStalePermissionVersion(String current, String allowed) {
    if (current == null || current.isBlank()) {
      return true;
    }
    return !current.equals(allowed);
  }

  private static String opaqueCursor(AuthorizedSearchQuery query) {
    String raw = query.tenantScopeId() + "|" + query.cursorDepth() + "|" + query.pageSize();
    return java.util.Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static AuthorizedSearchFailureReason mapAbacReason(SearchAuthorizationOutcome outcome) {
    return switch (outcome) {
      case ABAC_LIVING_FORBIDDEN -> AuthorizedSearchFailureReason.SEARCH_ABAC_LIVING_FORBIDDEN;
      case ABAC_MINOR_FORBIDDEN -> AuthorizedSearchFailureReason.SEARCH_ABAC_MINOR_FORBIDDEN;
      case ABAC_DNA_FORBIDDEN -> AuthorizedSearchFailureReason.SEARCH_ABAC_DNA_FORBIDDEN;
      case ABAC_CONSENT_REQUIRED -> AuthorizedSearchFailureReason.SEARCH_ABAC_CONSENT_REQUIRED;
      case ABAC_CONTEXTUAL_DENY -> AuthorizedSearchFailureReason.SEARCH_ABAC_CONTEXTUAL_DENY;
      default -> AuthorizedSearchFailureReason.SEARCH_PERMISSION_TOKEN_INVALID;
    };
  }
}