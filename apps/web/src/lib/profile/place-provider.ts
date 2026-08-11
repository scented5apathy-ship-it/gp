/**
 * apps/web/src/lib/profile/place-provider.ts
 *
 * `PlaceProvider` adapter for ADR-E0.5-14 (calendar / geocoding /
 * place authority providers). The adapter keeps the UI free of
 * vendor lock-in: components consume `PlaceProvider`, the BFF
 * binds the concrete provider via configuration.
 *
 * The default implementation here is **inert by design** —
 * it returns `{ degraded: true, candidates: [] }` so the
 * tree edit never blocks (per ADR-E0.5-14 §Security/privacy:
 * "Provider outages degrade to local placeholder; never block
 * tree edit."). The real OSM Nominatim / Wikidata adapter is
 * loaded by the BFF server-side; the Web app only sees the
 * `PlaceLookupResult` envelope.
 *
 * The adapter interface is intentionally tiny so swapping the
 * concrete implementation later (e.g. an in-memory cache for
 * tests, or a self-hosted Photon adapter) does not touch the
 * components.
 */
import type { PlaceCandidate, PlaceLookupResult } from "@genealogy/api-client";

export interface PlaceProvider {
  /**
   * Resolve a free-text query into a set of place candidates.
   * The adapter MUST never throw on transport errors —
   * provider outages degrade to `{ degraded: true }` so the UI
   * can fall back to manual entry.
   */
  lookup(input: {
    readonly q: string;
    readonly locale?: string;
    readonly limit?: number;
  }): Promise<PlaceLookupResult> | PlaceLookupResult;
}

/**
 * The default `PlaceProvider` delegates to the BFF
 * `lookupPlace` endpoint. The adapter is intentionally a thin
 * wrapper so unit tests can substitute an in-memory
 * implementation.
 */
export interface BffBackedPlaceProviderOptions {
  readonly client: {
    request(
      method: string,
      path: string,
      options: {
        query?: Record<string, string | number | undefined>;
        headers?: Record<string, string>;
        signal?: AbortSignal;
      },
    ): Promise<unknown>;
  };
}

export function createBffBackedPlaceProvider(
  options: BffBackedPlaceProviderOptions,
): PlaceProvider {
  return {
    async lookup(input) {
      try {
        const query: Record<string, string | number | undefined> = { q: input.q };
        if (input.locale !== undefined) query["locale"] = input.locale;
        if (input.limit !== undefined) query["limit"] = input.limit;
        const response = await options.client.request("GET", "/api/v1/place-lookup", { query });
        return normalisePlaceLookup(response);
      } catch {
        return { provider: "degraded", degraded: true, candidates: [] };
      }
    },
  };
}

/**
 * Normalise whatever the BFF returns into a typed
 * `PlaceLookupResult`. The BFF envelope is already typed but
 * the adapter still validates the closed-set so a tampered
 * response never crashes the editor.
 */
export function normalisePlaceLookup(payload: unknown): PlaceLookupResult {
  if (!payload || typeof payload !== "object") {
    return { provider: "unknown", degraded: true, candidates: [] };
  }
  const obj = payload as { provider?: unknown; degraded?: unknown; candidates?: unknown };
  const provider = typeof obj.provider === "string" ? obj.provider : "unknown";
  const degraded = obj.degraded === true;
  const candidates = Array.isArray(obj.candidates)
    ? obj.candidates
        .map((c) => normaliseCandidate(c))
        .filter((c): c is PlaceCandidate => c !== null)
    : [];
  return { provider, degraded, candidates };
}

function normaliseCandidate(candidate: unknown): PlaceCandidate | null {
  if (!candidate || typeof candidate !== "object") return null;
  const obj = candidate as Record<string, unknown>;
  if (typeof obj["placeId"] !== "string" || typeof obj["label"] !== "string") return null;
  if (!/^[A-Za-z0-9._:-]{1,128}$/.test(obj["placeId"])) return null;
  const builder: {
    placeId: string;
    label: string;
    providerPlaceId?: string;
    latitude?: number;
    longitude?: number;
    historicalNames?: readonly string[];
  } = {
    placeId: obj["placeId"],
    label: obj["label"],
  };
  if (typeof obj["providerPlaceId"] === "string") {
    builder.providerPlaceId = obj["providerPlaceId"];
  }
  if (typeof obj["latitude"] === "number") builder.latitude = obj["latitude"];
  if (typeof obj["longitude"] === "number") builder.longitude = obj["longitude"];
  if (Array.isArray(obj["historicalNames"])) {
    builder.historicalNames = obj["historicalNames"].filter(
      (n): n is string => typeof n === "string",
    );
  }
  return builder as PlaceCandidate;
}

/**
 * In-memory `PlaceProvider` for tests / Storybook. The lookup
 * is a case-insensitive substring match against the configured
 * fixtures so component tests can stay deterministic.
 */
export function createFixturePlaceProvider(fixtures: readonly PlaceCandidate[]): PlaceProvider {
  return {
    lookup(input) {
      const needle = input.q.trim().toLowerCase();
      if (!needle) return { provider: "fixture", degraded: false, candidates: [] };
      const matches = fixtures
        .filter((c) => c.label.toLowerCase().includes(needle))
        .slice(0, input.limit ?? 5);
      return { provider: "fixture", degraded: false, candidates: matches };
    },
  };
}
