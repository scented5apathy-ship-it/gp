/**
 * `apps/web/src/lib/profile/place-provider.test.ts`
 *
 * Validates the E5.4 `PlaceProvider` adapter
 * (ADR-E0.5-14):
 *
 *   - BFF-backed provider returns `{ degraded: true }` when
 *     the BFF call throws (provider outage → UI must fall back
 *     to manual entry per ADR-E0.5-14 §Security/privacy);
 *   - Fixture provider does a case-insensitive substring
 *     match and respects the `limit` knob;
 *   - `normalisePlaceLookup` strips candidates with malformed
 *     placeId / label so the UI never crashes on a tampered
 *     response.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  createBffBackedPlaceProvider,
  createFixturePlaceProvider,
  normalisePlaceLookup,
} from "./place-provider";

function makeClient(impl: () => Promise<unknown> | unknown) {
  return {
    async request() {
      return impl();
    },
  };
}

test("place-provider: BFF-backed provider returns degraded on thrown error", async () => {
  const provider = createBffBackedPlaceProvider({
    client: makeClient(() => {
      throw new Error("network down");
    }),
  });
  const result = await provider.lookup({ q: "Paris" });
  assert.equal(result.degraded, true);
  assert.equal(result.candidates.length, 0);
  assert.equal(result.provider, "degraded");
});

test("place-provider: BFF-backed provider forwards a successful response", async () => {
  const provider = createBffBackedPlaceProvider({
    client: makeClient(() => ({
      provider: "osm-nominatim",
      degraded: false,
      candidates: [
        {
          placeId: "00000000-0000-4000-8000-000000000001",
          label: "Paris, France",
          providerPlaceId: "N123",
          latitude: 48.8566,
          longitude: 2.3522,
        },
      ],
    })),
  });
  const result = await provider.lookup({ q: "Paris" });
  assert.equal(result.provider, "osm-nominatim");
  assert.equal(result.degraded, false);
  assert.equal(result.candidates.length, 1);
  assert.equal(result.candidates[0]?.label, "Paris, France");
});

test("place-provider: fixture provider does a case-insensitive substring match", async () => {
  const provider = createFixturePlaceProvider([
    {
      placeId: "00000000-0000-4000-8000-000000000001",
      label: "Paris, France",
    },
    {
      placeId: "00000000-0000-4000-8000-000000000002",
      label: "Lyon, France",
    },
  ]);
  const result = await provider.lookup({ q: "paris" });
  assert.equal(result.candidates.length, 1);
  assert.equal(result.candidates[0]?.label, "Paris, France");
});

test("place-provider: fixture provider respects the limit", async () => {
  const provider = createFixturePlaceProvider([
    { placeId: "p1", label: "Paris" },
    { placeId: "p2", label: "Paris 2" },
    { placeId: "p3", label: "Paris 3" },
  ]);
  const result = await provider.lookup({ q: "paris", limit: 2 });
  assert.equal(result.candidates.length, 2);
});

test("place-provider: normalisePlaceLookup strips malformed candidates", () => {
  const normalised = normalisePlaceLookup({
    provider: "wikidata",
    degraded: false,
    candidates: [
      { placeId: "ok", label: "OK" },
      { placeId: "has space", label: "Bad" },
      { placeId: "ok", label: 123 },
      null,
      "string",
    ],
  });
  assert.equal(normalised.candidates.length, 1);
  assert.equal(normalised.candidates[0]?.label, "OK");
});

test("place-provider: normalisePlaceLookup falls back to degraded on garbage payload", () => {
  assert.deepEqual(normalisePlaceLookup(null), {
    provider: "unknown",
    degraded: true,
    candidates: [],
  });
  assert.deepEqual(normalisePlaceLookup("garbage"), {
    provider: "unknown",
    degraded: true,
    candidates: [],
  });
});
