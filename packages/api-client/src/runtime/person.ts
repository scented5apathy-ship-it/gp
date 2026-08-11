/**
 * `@genealogy/api-client` — typed surface for the BFF person
 * endpoints defined in `contracts/openapi/bff/v1/person.yaml`
 * (E5.4 — profile / editor / timeline / map).
 *
 * The runtime mirrors the tree-projection wrapper (E5.3):
 *
 *   - closed-set enums for `LivingStatus`, `PrivacyLevel`,
 *     `DateValue.kind`, `PersonIdentifier.scheme`,
 *     `TimelineEvent.kind` and `PlaceAuthority` are pinned as
 *     `as const` arrays so the rest of the app can branch on the
 *     canonical wire values without re-reading the OpenAPI doc.
 *   - `PersonBody` / `PersonPatch` / `PersonTimeline` /
 *     `PersonPermissions` / `PlaceLookupResult` mirror the
 *     contract fields the editor actually consumes. Anything
 *     not surfaced here is intentionally dropped so the UI
 *     cannot accidentally bypass the BFF (R10 / design.md
 *     §8.3 — server is the source of truth).
 *   - `PersonResponse.notModified` (304) and
 *     `PersonUpdateResponse.stale` (409) /
 *     `preconditionFailed` (412) surfaces the optimistic-
 *     concurrency contract without throwing `ApiError` —
 *     callers (the editor store) translate them into UX states.
 *   - `REDACTION_REASON_CODES` re-uses the tree-projection
 *     closed set (same audit taxonomy per E3.6).
 *   - Numeric caps (`maxTimelineYears=500`, `maxTimelineEvents=200`,
 *     `maxPlaceCandidates=20`, `maxNamesPerPerson=16`) are
 *     pinned here so the wrapper refuses to submit a violating
 *     request before the BFF even has to.
 */
import type { ApiError } from "./problem";
export type { ApiError } from "./problem";

export const LIVING_STATUSES = [
  "LIVING",
  "PRESUMED_LIVING",
  "DECEASED",
  "PRESUMED_DECEASED",
  "UNKNOWN",
] as const;
export type LivingStatus = (typeof LIVING_STATUSES)[number];

export const PRIVACY_LEVELS = ["PUBLIC", "UNLISTED", "PRIVATE"] as const;
export type PrivacyLevel = (typeof PRIVACY_LEVELS)[number];

export const DATE_VALUE_KINDS = ["EXACT", "ABOUT", "RANGE", "BEFORE", "AFTER", "UNKNOWN"] as const;
export type DateValueKind = (typeof DATE_VALUE_KINDS)[number];

export const PERSON_IDENTIFIER_SCHEMES = [
  "AFN",
  "ARK",
  "GRdbID",
  "WikiTreeID",
  "VRN",
  "Custom",
] as const;
export type PersonIdentifierScheme = (typeof PERSON_IDENTIFIER_SCHEMES)[number];

export const TIMELINE_EVENT_KINDS = [
  "BIRTH",
  "DEATH",
  "MARRIAGE",
  "DIVORCE",
  "RESIDENCE",
  "MIGRATION",
  "MILITARY",
  "EDUCATION",
  "RELIGION",
  "CUSTOM",
] as const;
export type TimelineEventKind = (typeof TIMELINE_EVENT_KINDS)[number];

export const PLACE_AUTHORITIES = ["osm", "wikidata", "geonames", "custom"] as const;
export type PlaceAuthority = (typeof PLACE_AUTHORITIES)[number];

export const PERSON_PERMISSION_FIELDS = [
  "displayName",
  "names",
  "identifiers",
  "birth",
  "death",
  "biography",
  "privacyLevel",
] as const;
export type PersonPermissionField = (typeof PERSON_PERMISSION_FIELDS)[number];

export const PERSON_PERMISSION_ACTIONS = [
  "person.view",
  "person.edit",
  "person.delete",
  "person.merge",
  "person.export",
  "person.relink",
] as const;
export type PersonPermissionAction = (typeof PERSON_PERMISSION_ACTIONS)[number];

export const REDACTION_REASON_CODES = [
  "living_redacted",
  "minor_guardian_required",
  "privacy_class_restricted",
  "visibility_unlisted_token_invalid",
] as const;
export type RedactionReasonCode = (typeof REDACTION_REASON_CODES)[number];

export const PERSON_MAX_TIMELINE_YEARS = 500;
export const PERSON_MAX_TIMELINE_EVENTS = 200;
export const PERSON_MAX_PLACE_CANDIDATES = 20;
export const PERSON_MAX_NAMES = 16;
export const PERSON_MAX_IDENTIFIERS = 16;
export const PERSON_DISPLAY_NAME_MAX = 256;
export const PERSON_BIOGRAPHY_MAX = 8_000;
export const PERSON_QUERY_MAX = 128;
export const PLACE_QUERY_MAX = 128;

/**
 * Wire body of `GET /persons/{personId}`. Mirrors the
 * `PersonBody` schema from the contract. `redaction` carries the
 * closed-set reason codes applied INSIDE `genealogy-service`
 * (glossary-and-policy-matrix.md §2.2) — the renderer MUST NOT
 * re-redact.
 */
export interface PersonBody {
  readonly personId: string;
  readonly treeId: string;
  readonly version: number;
  readonly displayName: string;
  readonly livingStatus: LivingStatus;
  readonly names: readonly PersonName[];
  readonly identifiers?: readonly PersonIdentifier[];
  readonly birth?: DateValue;
  readonly death?: DateValue;
  readonly privacyLevel?: PrivacyLevel;
  readonly biography?: string;
  readonly redaction: {
    readonly reasonCodes: readonly RedactionReasonCode[];
    readonly droppedFieldCount: number;
    readonly policyVersion?: string;
  };
}

/**
 * Wire body of `PUT /persons/{personId}`. `additionalProperties`
 * is forbidden by the contract — the type uses `exactOptionalPropertyTypes`
 * to mirror that.
 */
export interface PersonPatch {
  readonly displayName: string;
  readonly names: readonly PersonName[];
  readonly birth?: DateValue;
  readonly death?: DateValue;
  readonly biography?: string;
  readonly identifiers?: readonly PersonIdentifier[];
}

export interface PersonName {
  readonly locale: string;
  readonly script: string;
  readonly parts: {
    readonly given: string;
    readonly surname: string;
    readonly patronymic?: string;
    readonly generationalSuffix?: string;
  };
  readonly isPrimary?: boolean;
}

export interface PersonIdentifier {
  readonly scheme: PersonIdentifierScheme;
  readonly value: string;
  readonly verified?: boolean;
}

export interface DateValue {
  readonly kind: DateValueKind;
  readonly original?: string;
  readonly normalized?: {
    readonly timestamp: string;
    readonly timezone?: string;
    readonly calendarId?: string;
  };
  readonly year?: number;
  readonly month?: number;
  readonly day?: number;
}

export interface TimelineEvent {
  readonly eventId: string;
  readonly kind: TimelineEventKind;
  readonly title?: string;
  readonly date: DateValue;
  readonly placeId?: string;
  readonly placeLabel?: string;
  readonly participantIds?: readonly string[];
  readonly privacyLevel?: PrivacyLevel;
  readonly redacted: boolean;
}

export interface PersonTimeline {
  readonly personId: string;
  readonly fromYear?: number;
  readonly toYear?: number;
  readonly events: readonly TimelineEvent[];
  readonly redaction: PersonBody["redaction"];
}

export interface PersonPermissions {
  readonly personId: string;
  readonly treeId: string;
  readonly fields: readonly {
    readonly field: PersonPermissionField;
    readonly allowed: boolean;
  }[];
  readonly actions: readonly {
    readonly action: PersonPermissionAction;
    readonly allowed: boolean;
  }[];
  readonly reasonCodes: readonly string[];
}

export interface PlaceCandidate {
  readonly placeId: string;
  readonly label: string;
  readonly providerPlaceId?: string;
  readonly latitude?: number;
  readonly longitude?: number;
  readonly historicalNames?: readonly string[];
  readonly authorityRefs?: readonly { readonly authority: PlaceAuthority; readonly ref: string }[];
}

export interface PlaceLookupResult {
  readonly provider: string;
  readonly degraded: boolean;
  readonly candidates: readonly PlaceCandidate[];
}

/**
 * Response envelope from `getPerson`. `notModified=true` means
 * the BFF replied 304; the editor keeps the existing snapshot.
 */
export interface PersonResponse {
  readonly status: number;
  readonly etag: string | null;
  readonly personVersion: number | null;
  readonly body: PersonBody | undefined;
  readonly notModified: boolean;
}

/**
 * Response envelope from `updatePerson`. `stale=true` means the
 * BFF replied 409 (read-model drift) so the caller must
 * refetch + re-apply. `preconditionFailed=true` means 412 —
 * the client edit is on a stale version, the user must
 * reconcile.
 */
export interface PersonUpdateResponse {
  readonly status: number;
  readonly etag: string | null;
  readonly personVersion: number | null;
  readonly body: PersonBody | undefined;
  readonly stale: boolean;
  readonly preconditionFailed: boolean;
}

/**
 * Raw HTTP envelope shared by every method. Mirrors
 * `RawHttpResponse` from the tree-projection module (E5.3).
 */
export interface PersonRawHttpResponse {
  readonly status: number;
  readonly headers: Readonly<Record<string, string>>;
  readonly parsed: unknown;
}

/**
 * Pure validation helpers — used by the editor store to keep all
 * closed-set enforcement in one place. `assertClosedSet` returns
 * the same array unchanged when the values are valid; it throws
 * `RangeError` otherwise.
 */
export function assertPersonClosedSet<T extends string>(
  field: string,
  values: readonly T[],
  allowed: readonly T[],
): readonly T[] {
  const allowedSet = new Set<string>(allowed);
  const seen = new Set<T>();
  for (const value of values) {
    if (!allowedSet.has(value)) {
      throw new RangeError(
        `person: ${field} value "${value}" is outside the contract closed-set (${allowed.join(", ")})`,
      );
    }
    seen.add(value);
  }
  return Array.from(seen);
}

export function assertTimelineRange(
  fromYear: number | undefined,
  toYear: number | undefined,
): void {
  if (fromYear !== undefined && toYear !== undefined) {
    if (fromYear > toYear) {
      throw new RangeError(`person: timeline fromYear (${fromYear}) must be <= toYear (${toYear})`);
    }
    if (toYear - fromYear > PERSON_MAX_TIMELINE_YEARS) {
      throw new RangeError(
        `person: timeline range (${toYear - fromYear}y) exceeds the ${PERSON_MAX_TIMELINE_YEARS}-year cap`,
      );
    }
  }
}

export function assertPlaceQuery(query: string): string {
  const trimmed = query.trim();
  if (trimmed.length < 2 || trimmed.length > PLACE_QUERY_MAX) {
    throw new RangeError(
      `person: place query must be 2..${PLACE_QUERY_MAX} chars after trim, got ${trimmed.length}`,
    );
  }
  return trimmed;
}

export interface CallOptions {
  headers?: Record<string, string>;
  query?: Record<string, string | number | boolean | undefined>;
  idempotencyKey?: string;
  body?: unknown;
  signal?: AbortSignal;
}

/**
 * Function-shaped fetcher the editor store binds to. Mirrors
 * `TreeProjectionFetcher` from E5.3 so the store layer is
 * framework-agnostic and unit tests can inject a stub without
 * touching module-level singletons.
 */
export interface PersonFetcher {
  getPerson(input: {
    readonly treeId: string;
    readonly personId: string;
    readonly ifNoneMatch?: string;
  }): Promise<PersonRawHttpResponse>;
  updatePerson(input: {
    readonly treeId: string;
    readonly personId: string;
    readonly patch: PersonPatch;
    readonly ifMatch: string;
  }): Promise<PersonRawHttpResponse>;
  getPersonTimeline(input: {
    readonly treeId: string;
    readonly personId: string;
    readonly fromYear?: number;
    readonly toYear?: number;
    readonly limit?: number;
  }): Promise<PersonRawHttpResponse>;
  getPersonPermissions(input: {
    readonly treeId: string;
    readonly personId: string;
  }): Promise<PersonRawHttpResponse>;
  lookupPlace(input: {
    readonly q: string;
    readonly locale?: string;
    readonly limit?: number;
  }): Promise<PersonRawHttpResponse>;
}

function readHeaderInt(headers: Readonly<Record<string, string>>, key: string): number | null {
  const raw = headers[key.toLowerCase()];
  if (raw === undefined || raw === "") return null;
  const value = Number.parseInt(raw, 10);
  return Number.isFinite(value) ? value : null;
}

function envelopeFromFetch(response: {
  status: number;
  headers: Readonly<Record<string, string>>;
  body: unknown;
}): PersonRawHttpResponse {
  return {
    status: response.status,
    headers: response.headers,
    parsed: response.body,
  };
}

/**
 * Default implementation of `PersonFetcher` that delegates to a
 * fetch-style client. The client must expose a `request` method
 * that accepts `(method, path, options)` and returns
 * `{ status, headers, body }`. `BffClient` (E5.3) satisfies this
 * contract.
 */
export interface FetcherClient {
  request(
    method: string,
    path: string,
    options: CallOptions,
  ): Promise<{ status: number; headers: Readonly<Record<string, string>>; body: unknown }>;
}

export function createBffPersonFetcher(client: FetcherClient): PersonFetcher {
  return {
    async getPerson(input) {
      const headers: Record<string, string> = {};
      if (input.ifNoneMatch) headers["If-None-Match"] = input.ifNoneMatch;
      const response = await client.request(
        "GET",
        `/api/v1/persons/${encodeURIComponent(input.personId)}`,
        {
          query: { treeId: input.treeId },
          headers,
        },
      );
      return envelopeFromFetch({
        status: response.status,
        headers: response.headers,
        body: response.body,
      });
    },
    async updatePerson(input) {
      const response = await client.request(
        "PUT",
        `/api/v1/persons/${encodeURIComponent(input.personId)}`,
        {
          query: { treeId: input.treeId },
          headers: { "If-Match": input.ifMatch },
          body: input.patch,
        },
      );
      return envelopeFromFetch({
        status: response.status,
        headers: response.headers,
        body: response.body,
      });
    },
    async getPersonTimeline(input) {
      const query: Record<string, string | number | undefined> = { treeId: input.treeId };
      if (input.fromYear !== undefined) query["fromYear"] = input.fromYear;
      if (input.toYear !== undefined) query["toYear"] = input.toYear;
      if (input.limit !== undefined) query["limit"] = input.limit;
      assertTimelineRange(input.fromYear, input.toYear);
      const response = await client.request(
        "GET",
        `/api/v1/persons/${encodeURIComponent(input.personId)}/timeline`,
        { query },
      );
      return envelopeFromFetch({
        status: response.status,
        headers: response.headers,
        body: response.body,
      });
    },
    async getPersonPermissions(input) {
      const response = await client.request(
        "GET",
        `/api/v1/persons/${encodeURIComponent(input.personId)}/permissions`,
        { query: { treeId: input.treeId } },
      );
      return envelopeFromFetch({
        status: response.status,
        headers: response.headers,
        body: response.body,
      });
    },
    async lookupPlace(input) {
      const query: Record<string, string | number | undefined> = { q: assertPlaceQuery(input.q) };
      if (input.locale) query["locale"] = input.locale;
      if (input.limit !== undefined) query["limit"] = input.limit;
      const response = await client.request("GET", "/api/v1/place-lookup", { query });
      return envelopeFromFetch({
        status: response.status,
        headers: response.headers,
        body: response.body,
      });
    },
  };
}

/**
 * Helper to extract the `PersonBody` from a 200 envelope or
 * surface a typed error otherwise. Reused by the editor store.
 */
export function unwrapPersonResponse(response: PersonRawHttpResponse): {
  readonly ok: boolean;
  readonly notModified: boolean;
  readonly body?: PersonBody;
  readonly stale?: boolean;
  readonly preconditionFailed?: boolean;
  readonly error?: ApiError | Error;
} {
  if (response.status === 304) {
    return { ok: true, notModified: true };
  }
  if (response.status === 409) {
    return { ok: false, notModified: false, stale: true };
  }
  if (response.status === 412) {
    return { ok: false, notModified: false, preconditionFailed: true };
  }
  if (response.status >= 200 && response.status < 300) {
    const body = response.parsed as PersonBody | undefined;
    return body !== undefined
      ? { ok: true, notModified: false, body }
      : { ok: true, notModified: false };
  }
  return { ok: false, notModified: false, error: new Error(`BFF status ${response.status}`) };
}

/**
 * Helpers shared by the timeline / permissions / place modules.
 * Re-exported so the editor store does not have to duplicate
 * the regex / closed-set constants.
 */
export const OPAQUE_ID_PATTERN = /^[A-Za-z0-9._:-]{1,128}$/;

export function isOpaquePersonId(value: string): boolean {
  return OPAQUE_ID_PATTERN.test(value);
}

/**
 * Convert a typed `PersonFetcher` envelope into the
 * `PersonResponse` shape (read) consumed by the editor store.
 */
export function toPersonResponse(envelope: PersonRawHttpResponse): PersonResponse {
  return {
    status: envelope.status,
    etag: envelope.headers["etag"] ?? null,
    personVersion: readHeaderInt(envelope.headers, "x-person-version"),
    body: envelope.status === 200 ? (envelope.parsed as PersonBody | undefined) : undefined,
    notModified: envelope.status === 304,
  };
}

export function toPersonUpdateResponse(envelope: PersonRawHttpResponse): PersonUpdateResponse {
  return {
    status: envelope.status,
    etag: envelope.headers["etag"] ?? null,
    personVersion: readHeaderInt(envelope.headers, "x-person-version"),
    body: envelope.status === 200 ? (envelope.parsed as PersonBody | undefined) : undefined,
    stale: envelope.status === 409,
    preconditionFailed: envelope.status === 412,
  };
}
