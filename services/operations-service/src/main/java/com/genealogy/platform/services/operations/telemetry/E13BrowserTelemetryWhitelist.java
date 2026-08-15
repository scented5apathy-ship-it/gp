package com.genealogy.platform.services.operations.telemetry;

import java.util.Set;

/**
 * Closed-set of browser-side telemetry events the runtime is
 * allowed to emit. Mirrors the
 * <code>browserTelemetry.eventWhitelist</code> block of
 * <code>contracts/reliability/telemetry-policy.yaml</code>.
 */
public final class E13BrowserTelemetryWhitelist {

  public static final Set<String> EVENTS = Set.of(
      "app_loaded",
      "route_changed",
      "flag_exposure",
      "error_boundary_caught",
      "mutation_queue_synced",
      "offline_cache_opt_in",
      "offline_cache_opt_out",
      "offline_cache_purge",
      "permission_version_mismatch",
      "accessibility_preference_changed");

  private E13BrowserTelemetryWhitelist() {
    throw new UnsupportedOperationException("constants holder");
  }

  public static boolean isWhitelisted(String eventName) {
    return eventName != null && EVENTS.contains(eventName);
  }
}