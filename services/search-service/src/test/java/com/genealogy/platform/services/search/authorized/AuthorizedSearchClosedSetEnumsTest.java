package com.genealogy.platform.services.search.authorized;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuthorizedSearchClosedSetEnumsTest {

  @Test
  void searchAuthorizationOutcomeFromWireCoversAllValues() {
    for (SearchAuthorizationOutcome outcome : SearchAuthorizationOutcome.values()) {
      assertEquals(outcome, SearchAuthorizationOutcome.fromWire(outcome.wire()));
    }
    assertEquals(10, SearchAuthorizationOutcome.values().length);
    assertThrows(IllegalArgumentException.class, () -> SearchAuthorizationOutcome.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> SearchAuthorizationOutcome.fromWire("BOGUS"));
  }

  @Test
  void authorizedSearchFailureReasonFromWireCoversAllValues() {
    for (AuthorizedSearchFailureReason reason : AuthorizedSearchFailureReason.values()) {
      assertEquals(reason, AuthorizedSearchFailureReason.fromWire(reason.wire()));
    }
    assertEquals(24, AuthorizedSearchFailureReason.values().length);
    assertThrows(
        IllegalArgumentException.class, () -> AuthorizedSearchFailureReason.fromWire(null));
    assertThrows(
        IllegalArgumentException.class, () -> AuthorizedSearchFailureReason.fromWire("BOGUS"));
  }

  @Test
  void numericLimitsPinContractValues() {
    assertEquals(100, AuthorizedSearchLimits.MAX_PAGE_SIZE);
    assertEquals(1, AuthorizedSearchLimits.MIN_PAGE_SIZE);
    assertEquals(20, AuthorizedSearchLimits.DEFAULT_PAGE_SIZE);
    assertEquals(1024, AuthorizedSearchLimits.MAX_CURSOR_DEPTH);
    assertEquals(256, AuthorizedSearchLimits.MAX_FACET_COUNT_PER_AXIS);
    assertEquals(32, AuthorizedSearchLimits.MAX_FILTERS_PER_QUERY);
    assertEquals(64, AuthorizedSearchLimits.MAX_SAVED_SEARCH_ALERTS_PER_USER);
    assertEquals(256, AuthorizedSearchLimits.MAX_SAVED_SEARCH_ALERT_SUBSCRIBERS);
    assertEquals(30, AuthorizedSearchLimits.PERMISSION_CACHE_MAX_AGE_SECONDS);
    assertEquals(5, AuthorizedSearchLimits.PERMISSION_CACHE_MAX_VERSION_WINDOW);
    assertEquals(90, AuthorizedSearchLimits.PERMISSION_CACHE_TOMBSTONE_SECONDS);
    assertEquals(5, AuthorizedSearchLimits.PERMISSION_CACHE_P95_BUDGET_MILLISECONDS);
    assertEquals(15, AuthorizedSearchLimits.PERMISSION_CACHE_STALE_BUDGET_SECONDS);
    assertEquals(1000, AuthorizedSearchLimits.SEARCH_P95_BUDGET_MILLISECONDS);
    assertEquals(2500, AuthorizedSearchLimits.SEARCH_P99_BUDGET_MILLISECONDS);
  }

  @Test
  void openFgaVerdictAllowsWithoutReasonDeniesWithReason() {
    SearchOpenFgaVerdict allow = SearchOpenFgaVerdict.allow();
    assertEquals(SearchOpenFgaOutcome.ALLOW, allow.outcome());
    assertThrows(
        IllegalArgumentException.class,
        () -> SearchOpenFgaVerdict.deny(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> SearchOpenFgaVerdict.deny(""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SearchOpenFgaVerdict(SearchOpenFgaOutcome.ALLOW, "reason-not-allowed", null));
  }

  @Test
  void decisionConstructorRejectsDeniedWithoutReason() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AuthorizedSearchDecision(
                SearchAuthorizationOutcome.OPENFGA_DENY, List.of(), null, null, null));
  }

  @Test
  void decisionConstructorRejectsAllowedWithoutCursor() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AuthorizedSearchDecision(
                SearchAuthorizationOutcome.ALLOWED, List.of(), null, null, null));
  }

  @Test
  void abacVerdictRefusesOpenFgaOutcomes() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SearchAbacVerdict(SearchAuthorizationOutcome.OPENFGA_DENY));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SearchAbacVerdict(SearchAuthorizationOutcome.PERMISSION_VERSION_STALE));
  }
}