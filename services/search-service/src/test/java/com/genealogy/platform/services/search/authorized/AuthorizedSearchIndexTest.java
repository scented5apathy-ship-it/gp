package com.genealogy.platform.services.search.authorized;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuthorizedSearchIndexTest {

  private final SearchOpenFgaPort allowOpenFga =
      (tenantScopeId, resourceKind, actorPseudoId) -> SearchOpenFgaVerdict.allow();
  private final SearchAbacPort allowAbac = (tenantScopeId, actorPseudoId) -> SearchAbacVerdict.allow();
  private final SearchAbacPort denyLivingAbac =
      (tenantScopeId, actorPseudoId) -> new SearchAbacVerdict(SearchAuthorizationOutcome.ABAC_LIVING_FORBIDDEN);

  private AuthorizedSearchQuery buildQuery(int pageSize, int cursorDepth) {
    return new AuthorizedSearchQuery(
        "tenant-1",
        "actor-1",
        "corr-1",
        "v1",
        "smith",
        "PERSON",
        List.of("family-1"),
        pageSize,
        cursorDepth);
  }

  @Test
  void happyPathReturnsAllowedWithOpaqueCursor() {
    AuthorizedSearchDecision decision =
        AuthorizedSearchIndex.apply(buildQuery(20, 0), "v1", allowOpenFga, allowAbac);
    assertEquals(SearchAuthorizationOutcome.ALLOWED, decision.outcome());
    assertEquals(1, decision.hits().size());
    assertNotNull(decision.cursorOpaque());
    assertTrue(decision.cursorOpaque().length() > 0);
  }

  @Test
  void stalePermissionVersionDenied() {
    AuthorizedSearchDecision decision =
        AuthorizedSearchIndex.apply(buildQuery(20, 0), "v2", allowOpenFga, allowAbac);
    assertEquals(SearchAuthorizationOutcome.PERMISSION_VERSION_STALE, decision.outcome());
    assertEquals(
        AuthorizedSearchFailureReason.SEARCH_PERMISSION_VERSION_STALE, decision.failureReason());
  }

  @Test
  void livingAbacDenialProducesLivingForbidden() {
    AuthorizedSearchDecision decision =
        AuthorizedSearchIndex.apply(buildQuery(20, 0), "v1", allowOpenFga, denyLivingAbac);
    assertEquals(SearchAuthorizationOutcome.ABAC_LIVING_FORBIDDEN, decision.outcome());
    assertEquals(
        AuthorizedSearchFailureReason.SEARCH_ABAC_LIVING_FORBIDDEN, decision.failureReason());
    assertEquals(0, decision.hits().size());
  }

  @Test
  void openFgaDenyProducesOpenFgaForbidden() {
    SearchOpenFgaPort denyOpenFga =
        (tenantScopeId, resourceKind, actorPseudoId) -> SearchOpenFgaVerdict.deny("no-relation");
    AuthorizedSearchDecision decision =
        AuthorizedSearchIndex.apply(buildQuery(20, 0), "v1", denyOpenFga, allowAbac);
    assertEquals(SearchAuthorizationOutcome.OPENFGA_DENY, decision.outcome());
    assertEquals(AuthorizedSearchFailureReason.SEARCH_OPENFGA_DENY, decision.failureReason());
  }

  @Test
  void pageSizeOutOfBoundsRejectedAtConstruction() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuthorizedSearchQuery(
            "tenant-1", "actor-1", "corr-1", "v1", "smith", "PERSON", List.of(), 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuthorizedSearchQuery(
            "tenant-1",
            "actor-1",
            "corr-1",
            "v1",
            "smith",
            "PERSON",
            List.of(),
            AuthorizedSearchLimits.MAX_PAGE_SIZE + 1,
            0));
  }

  @Test
  void cursorDepthOutOfBoundsRejectedAtConstruction() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuthorizedSearchQuery(
            "tenant-1",
            "actor-1",
            "corr-1",
            "v1",
            "smith",
            "PERSON",
            List.of(),
            20,
            AuthorizedSearchLimits.MAX_CURSOR_DEPTH + 1));
  }
}