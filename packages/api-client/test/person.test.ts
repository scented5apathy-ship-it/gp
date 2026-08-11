/**
 * `packages/api-client/test/person.test.ts`
 *
 * Validates the typed person / timeline / permissions /
 * place-lookup surface (E5.4). The fetcher is exercised
 * end-to-end with a stub `FetcherClient` so we cover:
 *
 *   - 304 short-circuit on `getPerson`
 *   - If-Match propagation on `updatePerson`
 *   - 409 (stale) and 412 (precondition failed) surfaces
 *   - Timeline range validation (`PERSON_MAX_TIMELINE_YEARS=500`)
 *   - Place query length validation (`PLACE_QUERY_MAX=128`,
 *     minimum 2 chars)
 *   - Closed-set assertions for `LIVING_STATUSES`,
 *     `PRIVACY_LEVELS`, `TIMELINE_EVENT_KINDS`,
 *     `PERSON_PERMISSION_FIELDS`, `PERSON_PERMISSION_ACTIONS`,
 *     `REDACTION_REASON_CODES`
 *   - `isOpaquePersonId` regex (matches the BFF contract)
 *   - `toPersonResponse` / `toPersonUpdateResponse` header
 *     extraction (ETag + X-Person-Version)
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  DATE_VALUE_KINDS,
  LIVING_STATUSES,
  PERSON_MAX_TIMELINE_YEARS,
  PERSON_PERMISSION_ACTIONS,
  PERSON_PERMISSION_FIELDS,
  PLACE_AUTHORITIES,
  PLACE_QUERY_MAX,
  PRIVACY_LEVELS,
  REDACTION_REASON_CODES,
  TIMELINE_EVENT_KINDS,
  assertPersonClosedSet,
  assertPlaceQuery,
  assertTimelineRange,
  createBffPersonFetcher,
  isOpaquePersonId,
  toPersonResponse,
  toPersonUpdateResponse,
  unwrapPersonResponse,
  type FetcherClient,
  type PersonBody,
  type PersonRawHttpResponse,
} from "../src/runtime/person";

interface CapturedCall {
  method: string;
  path: string;
  query?: Record<string, string | number | undefined>;
  headers?: Record<string, string>;
  body?: unknown;
}

function makeStubClient(impl: (call: CapturedCall) => PersonRawHttpResponse): {
  client: FetcherClient;
  calls: CapturedCall[];
} {
  const calls: CapturedCall[] = [];
  return {
    calls,
    client: {
      async request(method, path, options) {
        const call: CapturedCall = { method, path };
        if (options.query) call.query = options.query as Record<string, string | number | undefined>;
        if (options.headers) call.headers = options.headers;
        if (options.body !== undefined) call.body = options.body;
        calls.push(call);
        const response = impl(call);
        const headers = response.headers;
        return { status: response.status, headers, body: response.parsed };
      },
    },
  };
}

function envelope(
  status: number,
  parsed: unknown,
  headers: Record<string, string> = {},
): PersonRawHttpResponse {
  return { status, headers, parsed };
}

const SAMPLE_PERSON: PersonBody = {
  personId: "00000000-0000-4000-8000-000000000000",
  treeId: "00000000-0000-4000-8000-000000000001",
  version: 7,
  displayName: "Ada Lovelace",
  livingStatus: "DECEASED",
  names: [
    {
      locale: "en",
      script: "Latn",
      parts: { given: "Ada", surname: "Lovelace" },
      isPrimary: true,
    },
  ],
  redaction: { reasonCodes: [], droppedFieldCount: 0 },
};

test("person: closed-set enums are sorted exactly as the contract", () => {
  assert.deepEqual([...LIVING_STATUSES], ["LIVING", "PRESUMED_LIVING", "DECEASED", "PRESUMED_DECEASED", "UNKNOWN"]);
  assert.deepEqual([...PRIVACY_LEVELS], ["PUBLIC", "UNLISTED", "PRIVATE"]);
  assert.deepEqual(
    [...TIMELINE_EVENT_KINDS],
    ["BIRTH", "DEATH", "MARRIAGE", "DIVORCE", "RESIDENCE", "MIGRATION", "MILITARY", "EDUCATION", "RELIGION", "CUSTOM"],
  );
  assert.deepEqual([...PLACE_AUTHORITIES], ["osm", "wikidata", "geonames", "custom"]);
  assert.deepEqual(
    [...PERSON_PERMISSION_FIELDS],
    ["displayName", "names", "identifiers", "birth", "death", "biography", "privacyLevel"],
  );
  assert.deepEqual(
    [...PERSON_PERMISSION_ACTIONS],
    ["person.view", "person.edit", "person.delete", "person.merge", "person.export", "person.relink"],
  );
});

test("person: redaction reason codes match the audit taxonomy", () => {
  assert.deepEqual(
    [...REDACTION_REASON_CODES],
    [
      "living_redacted",
      "minor_guardian_required",
      "privacy_class_restricted",
      "visibility_unlisted_token_invalid",
    ],
  );
});

test("person: DATE_VALUE_KINDS matches the contract closed-set", () => {
  assert.deepEqual(
    [...DATE_VALUE_KINDS],
    ["EXACT", "ABOUT", "RANGE", "BEFORE", "AFTER", "UNKNOWN"],
  );
});

test("person: assertPersonClosedSet accepts canonical values", () => {
  const result = assertPersonClosedSet("test", ["PUBLIC", "PRIVATE"], [...PRIVACY_LEVELS]);
  assert.deepEqual(result, ["PUBLIC", "PRIVATE"]);
});

test("person: assertPersonClosedSet rejects non-canonical values", () => {
  assert.throws(
    () => assertPersonClosedSet("test", ["PUBLIC", "GARBAGE"], [...PRIVACY_LEVELS]),
    /outside the contract closed-set/,
  );
});

test("person: assertPersonClosedSet deduplicates values", () => {
  const result = assertPersonClosedSet("test", ["PUBLIC", "PUBLIC"], [...PRIVACY_LEVELS]);
  assert.deepEqual(result, ["PUBLIC"]);
});

test("person: assertTimelineRange accepts the 500-year boundary", () => {
  assertTimelineRange(1500, 2000);
  assert.doesNotThrow(() => assertTimelineRange(1500, 2000));
});

test("person: assertTimelineRange refuses ranges above the 500-year cap", () => {
  assert.throws(() => assertTimelineRange(1500, 2001), /500-year cap/);
});

test("person: assertTimelineRange refuses fromYear > toYear", () => {
  assert.throws(() => assertTimelineRange(2000, 1500), /fromYear/);
});

test("person: assertTimelineRange allows undefined endpoints", () => {
  assertTimelineRange(undefined, undefined);
  assert.doesNotThrow(() => assertTimelineRange(undefined, 2000));
});

test("person: assertPlaceQuery trims and rejects too-short input", () => {
  assert.throws(() => assertPlaceQuery("a"), /2\.\.128 chars/);
  assert.throws(() => assertPlaceQuery(""), /2\.\.128 chars/);
});

test("person: assertPlaceQuery accepts canonical input", () => {
  assert.equal(assertPlaceQuery("  Paris  "), "Paris");
});

test("person: assertPlaceQuery refuses inputs longer than PLACE_QUERY_MAX", () => {
  const long = "x".repeat(PLACE_QUERY_MAX + 1);
  assert.throws(() => assertPlaceQuery(long), /2\.\.128 chars/);
});

test("person: isOpaquePersonId accepts canonical opaque ids", () => {
  assert.equal(isOpaquePersonId("00000000-0000-4000-8000-000000000000"), true);
  assert.equal(isOpaquePersonId("tree-1.person-2"), true);
});

test("person: isOpaquePersonId rejects empty / too-long / non-conforming strings", () => {
  assert.equal(isOpaquePersonId(""), false);
  assert.equal(isOpaquePersonId("contains spaces"), false);
  assert.equal(isOpaquePersonId("x".repeat(129)), false);
});

test("person: getPerson sends If-None-Match + receives 304 short-circuit", async () => {
  const stub = makeStubClient(() => envelope(304, undefined, { etag: '"7f3a9b21"' }));
  const fetcher = createBffPersonFetcher(stub.client);
  const raw = await fetcher.getPerson({
    treeId: "tree-1",
    personId: "00000000-0000-4000-8000-000000000000",
    ifNoneMatch: '"old-etag"',
  });
  assert.equal(stub.calls[0]?.method, "GET");
  assert.equal(stub.calls[0]?.headers?.["If-None-Match"], '"old-etag"');
  assert.equal(raw.status, 304);
  const response = toPersonResponse(raw);
  assert.equal(response.status, 304);
  assert.equal(response.notModified, true);
});

test("person: getPerson without If-None-Match returns the body", async () => {
  const stub = makeStubClient(() =>
    envelope(200, SAMPLE_PERSON, { etag: '"new"', "x-person-version": "7" }),
  );
  const fetcher = createBffPersonFetcher(stub.client);
  const raw = await fetcher.getPerson({
    treeId: "tree-1",
    personId: SAMPLE_PERSON.personId,
  });
  const response = toPersonResponse(raw);
  assert.equal(response.status, 200);
  assert.equal(response.notModified, false);
  assert.equal(response.etag, '"new"');
  assert.equal(response.personVersion, 7);
  assert.deepEqual(response.body, SAMPLE_PERSON);
});

test("person: updatePerson sends If-Match + body", async () => {
  const stub = makeStubClient(() =>
    envelope(200, { ...SAMPLE_PERSON, version: 8 }, { etag: '"new"', "x-person-version": "8" }),
  );
  const fetcher = createBffPersonFetcher(stub.client);
  const raw = await fetcher.updatePerson({
    treeId: "tree-1",
    personId: SAMPLE_PERSON.personId,
    ifMatch: '"old"',
    patch: {
      displayName: "Augusta Ada King",
      names: [
        {
          locale: "en",
          script: "Latn",
          parts: { given: "Augusta Ada", surname: "King" },
          isPrimary: true,
        },
      ],
    },
  });
  assert.equal(stub.calls[0]?.method, "PUT");
  assert.equal(stub.calls[0]?.headers?.["If-Match"], '"old"');
  const response = toPersonUpdateResponse(raw);
  assert.equal(response.status, 200);
  assert.equal(response.stale, false);
  assert.equal(response.preconditionFailed, false);
  assert.equal(response.personVersion, 8);
});

test("person: updatePerson surfaces 409 stale and 412 precondition separately", async () => {
  const stub409 = makeStubClient(() => envelope(409, undefined, {}));
  const fetcher409 = createBffPersonFetcher(stub409.client);
  const e409 = await fetcher409.updatePerson({
    treeId: "tree-1",
    personId: SAMPLE_PERSON.personId,
    ifMatch: '"old"',
    patch: { displayName: "x", names: [] },
  });
  const r409 = toPersonUpdateResponse(e409);
  assert.equal(r409.status, 409);
  assert.equal(r409.stale, true);
  assert.equal(r409.preconditionFailed, false);

  const stub412 = makeStubClient(() => envelope(412, undefined, {}));
  const fetcher412 = createBffPersonFetcher(stub412.client);
  const e412 = await fetcher412.updatePerson({
    treeId: "tree-1",
    personId: SAMPLE_PERSON.personId,
    ifMatch: '"old"',
    patch: { displayName: "x", names: [] },
  });
  const r412 = toPersonUpdateResponse(e412);
  assert.equal(r412.status, 412);
  assert.equal(r412.preconditionFailed, true);
  assert.equal(r412.stale, false);
});

test("person: getPersonTimeline passes fromYear/toYear/limit + refuses oversize range", async () => {
  const stub = makeStubClient(() => envelope(200, { personId: "p1", events: [] }));
  const fetcher = createBffPersonFetcher(stub.client);
  await fetcher.getPersonTimeline({
    treeId: "tree-1",
    personId: "p1",
    fromYear: 1500,
    toYear: 2000,
    limit: 50,
  });
  assert.deepEqual(stub.calls[0]?.query, { treeId: "tree-1", fromYear: 1500, toYear: 2000, limit: 50 });

  await assert.rejects(
    () =>
      fetcher.getPersonTimeline({
        treeId: "tree-1",
        personId: "p1",
        fromYear: 1500,
        toYear: 2000 + PERSON_MAX_TIMELINE_YEARS,
      }),
    /500-year cap/,
  );
});

test("person: lookupPlace sends q + locale + limit + validates query length", async () => {
  const stub = makeStubClient(() => envelope(200, { provider: "osm-nominatim", degraded: false, candidates: [] }));
  const fetcher = createBffPersonFetcher(stub.client);
  await fetcher.lookupPlace({ q: "Paris", locale: "vi", limit: 5 });
  assert.deepEqual(stub.calls[0]?.query, { q: "Paris", locale: "vi", limit: 5 });
  await assert.rejects(() => fetcher.lookupPlace({ q: "a" }), /2\.\.128 chars/);
});

test("person: toPersonResponse extracts ETag + version headers", () => {
  const envelope200 = envelope(200, SAMPLE_PERSON, { etag: '"abc"', "x-person-version": "11" });
  assert.deepEqual(toPersonResponse(envelope200), {
    status: 200,
    etag: '"abc"',
    personVersion: 11,
    body: SAMPLE_PERSON,
    notModified: false,
  });
});

test("person: toPersonUpdateResponse flags stale + precondition failed", () => {
  const env409 = envelope(409, undefined, {});
  const r409 = toPersonUpdateResponse(env409);
  assert.equal(r409.status, 409);
  assert.equal(r409.stale, true);

  const env412 = envelope(412, undefined, {});
  const r412 = toPersonUpdateResponse(env412);
  assert.equal(r412.status, 412);
  assert.equal(r412.preconditionFailed, true);
});

test("person: unwrapPersonResponse covers 304 / 409 / 412 / 200 / 5xx", () => {
  assert.deepEqual(unwrapPersonResponse(envelope(304, undefined)), { ok: true, notModified: true });
  const stale = unwrapPersonResponse(envelope(409, undefined));
  assert.equal(stale.ok, false);
  assert.equal(stale.stale, true);
  const pc = unwrapPersonResponse(envelope(412, undefined));
  assert.equal(pc.ok, false);
  assert.equal(pc.preconditionFailed, true);
  const ok = unwrapPersonResponse(envelope(200, SAMPLE_PERSON));
  assert.equal(ok.ok, true);
  assert.deepEqual(ok.body, SAMPLE_PERSON);
  const err = unwrapPersonResponse(envelope(500, undefined));
  assert.equal(err.ok, false);
});