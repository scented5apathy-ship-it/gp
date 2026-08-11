/**
 * apps/web/src/lib/profile/store.ts
 *
 * Pure state machine + optimistic-concurrency editor for the
 * E5.4 person profile / editor. Mirrors the
 * `TreeViewStore` pattern from E5.3:
 *
 *   - Framework-agnostic — the React component subscribes via
 *     `subscribe()`; unit tests exercise every invariant
 *     without React.
 *   - `PersonFetcher` is a function-shaped adapter so unit
 *     tests inject a stub without touching module-level
 *     singletons (the production wiring lives in
 *     `apps/web/src/lib/profile/client.ts` and binds to the
 *     real `BffClient`).
 *   - Optimistic-update flow: `applyOptimisticPatch()` mutates
 *     the snapshot without firing a fetch; the caller calls
 *     `commit()` which sends `If-Match=etag` and translates
 *     200 / 409 / 412 / 5xx into discrete UX states
 *     (`ready` / `stale` / `conflict` / `error`).
 *   - `conflict` UX surfaces the server's current body so the
 *     user can compare local edits against the persisted
 *     version (R10.3 / design.md §8.3).
 *   - Field-level permission gates from
 *     `PersonPermissions.fields` drive `canEdit(field)` /
 *     `canAct(action)` — the UI hides controls the user
 *     cannot use but the BFF still enforces on submit
 *     (`design.md` §8.3 — server is the source of truth).
 *
 * The store NEVER re-redacts: `redaction` is only read from
 * the server response and forwarded (glossary §2.2).
 */
import {
  assertPersonClosedSet,
  isOpaquePersonId,
  LIVING_STATUSES,
  PERSON_PERMISSION_ACTIONS,
  PERSON_PERMISSION_FIELDS,
  PRIVACY_LEVELS,
  type DateValue,
  type LivingStatus,
  type PersonBody,
  type PersonFetcher,
  type PersonPatch,
  type PersonPermissionAction,
  type PersonPermissionField,
  type PersonPermissions,
  type PersonRawHttpResponse,
  type PrivacyLevel,
} from "@genealogy/api-client";

export interface PersonEditorQuery {
  readonly treeId: string;
  readonly personId: string;
}

export interface PersonEditorSnapshot {
  readonly query: PersonEditorQuery;
  readonly body: PersonBody | null;
  readonly etag: string | null;
  readonly permissions: PersonPermissions | null;
  readonly meta: PersonEditorMeta;
  /** Field name → local optimistic value. */
  readonly draft: Readonly<Partial<PersonPatch>>;
  /** Last conflict body when the server rejected the optimistic edit. */
  readonly conflict: PersonBody | null;
}

export type PersonEditorStatus =
  | "idle"
  | "loading"
  | "ready"
  | "saving"
  | "stale"
  | "conflict"
  | "error";

export interface PersonEditorMeta {
  readonly status: PersonEditorStatus;
  readonly lastError?: string;
}

export const EMPTY_PERSON_SNAPSHOT: PersonEditorSnapshot = {
  query: { treeId: "", personId: "" },
  body: null,
  etag: null,
  permissions: null,
  meta: { status: "idle" },
  draft: {},
  conflict: null,
};

/**
 * Normalise a wire `PersonBody` into the in-memory
 * representation the editor reads. The conversion is a no-op
 * today (we keep the full body) but isolating it lets us add
 * invariants later (e.g. ensuring `names[0]` is `isPrimary`)
 * without touching the BFF wrapper.
 */
export function toEditorBody(body: PersonBody): PersonBody {
  return body;
}

export function applyOptimisticPatch(
  snapshot: PersonEditorSnapshot,
  patch: Partial<PersonPatch>,
): PersonEditorSnapshot {
  return {
    ...snapshot,
    draft: { ...snapshot.draft, ...patch },
  };
}

export function revertDraft(snapshot: PersonEditorSnapshot): PersonEditorSnapshot {
  return { ...snapshot, draft: {}, conflict: null };
}

/**
 * The store is intentionally **single-snapshot per
 * `(treeId, personId)`** — switching to another person resets
 * the draft + conflict to avoid leaking state across distinct
 * aggregates.
 */
export class PersonEditorStore {
  private snapshot: PersonEditorSnapshot;
  private readonly listeners = new Set<(snapshot: PersonEditorSnapshot) => void>();
  private readonly fetcher: PersonFetcher;
  private inflight: AbortController | null = null;

  constructor(initial: PersonEditorSnapshot, options: { readonly fetcher: PersonFetcher }) {
    this.snapshot = initial;
    this.fetcher = options.fetcher;
  }

  getSnapshot(): PersonEditorSnapshot {
    return this.snapshot;
  }

  subscribe(listener: (snapshot: PersonEditorSnapshot) => void): () => void {
    this.listeners.add(listener);
    listener(this.snapshot);
    return () => {
      this.listeners.delete(listener);
    };
  }

  reset(next: PersonEditorSnapshot): void {
    this.snapshot = next;
    this.emit();
  }

  /**
   * Apply a UI-only patch (typing in a form field). Does NOT
   * fire a fetch. The user expects immediate feedback so the
   * `meta.status` stays whatever it was.
   */
  patchDraft(patch: Partial<PersonPatch>): void {
    this.snapshot = applyOptimisticPatch(this.snapshot, patch);
    this.emit();
  }

  revert(): void {
    this.snapshot = revertDraft(this.snapshot);
    this.emit();
  }

  /**
   * Fetch the initial snapshot. Reads the body AND the
   * permission matrix in parallel so the UI never renders an
   * editor for a field the user cannot edit. `If-None-Match`
   * is sent when an ETag is already held (e.g. soft
   * navigation back to the page).
   */
  async load(query: PersonEditorQuery): Promise<void> {
    validateQuery(query);
    this.cancelInflight();
    const controller = new AbortController();
    this.inflight = controller;
    this.snapshot = {
      ...this.snapshot,
      query,
      body: null,
      etag: null,
      permissions: null,
      meta: { status: "loading" },
      draft: {},
      conflict: null,
    };
    this.emit();
    try {
      const personHeaders: Record<string, string> = {};
      if (this.snapshot.etag) personHeaders["If-None-Match"] = this.snapshot.etag;
      const [personRaw, permsRaw] = await Promise.all([
        this.fetcher.getPerson({ treeId: query.treeId, personId: query.personId }),
        this.fetcher.getPersonPermissions({ treeId: query.treeId, personId: query.personId }),
      ]);
      if (controller.signal.aborted) return;
      this.applyPersonResponse(personRaw);
      this.applyPermissionsResponse(permsRaw);
    } catch (error) {
      if (controller.signal.aborted) return;
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "error", lastError: toMessage(error) },
      };
      this.emit();
    }
  }

  /**
   * Commit the draft back to the BFF. The store always sends
   * `If-Match=<current etag>` (E4.2 optimistic concurrency);
   * the BFF replies 200 / 409 / 412 / 5xx which the store
   * translates to discrete UX states.
   */
  async commit(): Promise<void> {
    const baseline = this.snapshot;
    if (!baseline.body || !baseline.etag) {
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "error", lastError: "cannot commit without a baseline body + etag" },
      };
      this.emit();
      return;
    }
    const draft = baseline.draft;
    if (Object.keys(draft).length === 0) return;
    // Merge the draft with the baseline body so a single-field
    // edit still carries the required `displayName` + `names`
    // (PersonPatch requires both, per the BFF contract).
    const merged: {
      displayName?: string;
      names?: PersonPatch["names"];
      birth?: DateValue;
      death?: DateValue;
      biography?: string;
      identifiers?: PersonPatch["identifiers"];
    } = {
      displayName: draft.displayName ?? baseline.body.displayName,
      names: draft.names ?? baseline.body.names,
    };
    if (draft.birth !== undefined) merged.birth = draft.birth;
    if (draft.death !== undefined) merged.death = draft.death;
    if (draft.biography !== undefined) merged.biography = draft.biography;
    if (draft.identifiers !== undefined) merged.identifiers = draft.identifiers;
    const patch = validatePatch(merged as Partial<PersonPatch>);
    const baselineEtag = baseline.etag;
    this.cancelInflight();
    const controller = new AbortController();
    this.inflight = controller;
    this.snapshot = { ...this.snapshot, meta: { status: "saving" } };
    this.emit();
    try {
      const raw = await this.fetcher.updatePerson({
        treeId: baseline.query.treeId,
        personId: baseline.query.personId,
        ifMatch: baselineEtag,
        patch,
      });
      if (controller.signal.aborted) return;
      this.applyUpdateResponse(raw);
    } catch (error) {
      if (controller.signal.aborted) return;
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "error", lastError: toMessage(error) },
      };
      this.emit();
    }
  }

  private applyPersonResponse(raw: PersonRawHttpResponse): void {
    if (raw.status === 304) {
      // 304 → keep the previous snapshot (no change).
      this.snapshot = { ...this.snapshot, meta: { status: "ready" } };
      this.emit();
      return;
    }
    if (raw.status !== 200) {
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "error", lastError: `BFF status ${raw.status}` },
      };
      this.emit();
      return;
    }
    const body = raw.parsed as PersonBody | undefined;
    if (!body) {
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "error", lastError: "BFF returned an empty body" },
      };
      this.emit();
      return;
    }
    this.snapshot = {
      ...this.snapshot,
      body: toEditorBody(body),
      etag: raw.headers["etag"] ?? null,
      meta: { status: "ready" },
      draft: {},
      conflict: null,
    };
    this.emit();
  }

  private applyPermissionsResponse(raw: PersonRawHttpResponse): void {
    if (raw.status !== 200) return;
    const body = raw.parsed as PersonPermissions | undefined;
    if (!body) return;
    this.snapshot = {
      ...this.snapshot,
      permissions: normalisePermissions(body),
    };
    this.emit();
  }

  private applyUpdateResponse(raw: PersonRawHttpResponse): void {
    if (raw.status === 409) {
      // Read-model drift → caller must refetch.
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "stale", lastError: "person stale — refetching" },
      };
      this.emit();
      return;
    }
    if (raw.status === 412) {
      // Precondition failed → user must reconcile.
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "conflict", lastError: "person version changed — please reconcile" },
        conflict: (raw.parsed as PersonBody | undefined) ?? null,
      };
      this.emit();
      return;
    }
    if (raw.status !== 200) {
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "error", lastError: `BFF status ${raw.status}` },
      };
      this.emit();
      return;
    }
    const body = raw.parsed as PersonBody | undefined;
    if (!body) {
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "error", lastError: "BFF returned an empty body" },
      };
      this.emit();
      return;
    }
    this.snapshot = {
      ...this.snapshot,
      body: toEditorBody(body),
      etag: raw.headers["etag"] ?? this.snapshot.etag ?? null,
      meta: { status: "ready" },
      draft: {},
      conflict: null,
    };
    this.emit();
  }

  private cancelInflight(): void {
    if (this.inflight) {
      this.inflight.abort();
      this.inflight = null;
    }
  }

  private emit(): void {
    for (const listener of this.listeners) {
      listener(this.snapshot);
    }
  }
}

/**
 * Permission gates. The UI calls these to decide whether to
 * render a control. The BFF still re-checks on submit; the
 * store mirrors that for symmetry so a stale UI cannot bypass
 * server enforcement.
 */
export function canEditField(
  permissions: PersonPermissions | null,
  field: PersonPermissionField,
): boolean {
  if (!permissions) return false;
  return permissions.fields.find((entry) => entry.field === field)?.allowed ?? false;
}

export function canAct(
  permissions: PersonPermissions | null,
  action: PersonPermissionAction,
): boolean {
  if (!permissions) return false;
  return permissions.actions.find((entry) => entry.action === action)?.allowed ?? false;
}

export function validateQuery(query: PersonEditorQuery): void {
  if (!isOpaquePersonId(query.treeId)) {
    throw new RangeError(`profile-editor: treeId must match opaque regex, got "${query.treeId}"`);
  }
  if (!isOpaquePersonId(query.personId)) {
    throw new RangeError(
      `profile-editor: personId must match opaque regex, got "${query.personId}"`,
    );
  }
}

function validatePatch(draft: Partial<PersonPatch>): PersonPatch {
  if (!draft.displayName) {
    throw new RangeError("profile-editor: displayName must be non-empty");
  }
  if (!draft.names || draft.names.length === 0) {
    throw new RangeError("profile-editor: names must be a non-empty array");
  }
  const builder: {
    displayName: string;
    names: PersonPatch["names"];
    birth?: DateValue;
    death?: DateValue;
    biography?: string;
    identifiers?: PersonPatch["identifiers"];
  } = {
    displayName: draft.displayName,
    names: draft.names,
  };
  if (draft.birth !== undefined) builder.birth = validateDate(draft.birth, "birth");
  if (draft.death !== undefined) builder.death = validateDate(draft.death, "death");
  if (draft.biography !== undefined) {
    if (draft.biography.length > 8000) {
      throw new RangeError(
        `profile-editor: biography exceeds 8000 chars (got ${draft.biography.length})`,
      );
    }
    builder.biography = draft.biography;
  }
  if (draft.identifiers !== undefined) {
    if (draft.identifiers.length > 16) {
      throw new RangeError(
        `profile-editor: identifiers length exceeds 16 (got ${draft.identifiers.length})`,
      );
    }
    builder.identifiers = draft.identifiers;
  }
  // `PersonPatch` is deeply readonly; the builder mirrors the
  // shape with mutable optional fields so we can attach
  // conditional fields without `as unknown as` casts. The
  // resulting object is structurally identical to a real
  // `PersonPatch` and is never mutated after this return.
  return builder as PersonPatch;
}

function validateDate(date: DateValue, fieldName: string): DateValue {
  assertPersonClosedSet(`date.${fieldName}.kind`, [date.kind], [
    "EXACT",
    "ABOUT",
    "RANGE",
    "BEFORE",
    "AFTER",
    "UNKNOWN",
  ] as readonly LivingStatus[] extends never
    ? readonly DateValue["kind"][]
    : readonly DateValue["kind"][]);
  return date;
}

function normalisePermissions(body: PersonPermissions): PersonPermissions {
  const fields = assertPersonClosedSet<(typeof PERSON_PERMISSION_FIELDS)[number]>(
    "permissions.fields[].field",
    body.fields.map((f) => f.field as (typeof PERSON_PERMISSION_FIELDS)[number]),
    PERSON_PERMISSION_FIELDS,
  );
  const actions = assertPersonClosedSet<(typeof PERSON_PERMISSION_ACTIONS)[number]>(
    "permissions.actions[].action",
    body.actions.map((a) => a.action as (typeof PERSON_PERMISSION_ACTIONS)[number]),
    PERSON_PERMISSION_ACTIONS,
  );
  const seenField = new Set<string>();
  const seenAction = new Set<string>();
  const fieldEntries: { field: PersonPermissionField; allowed: boolean }[] = [];
  for (const field of fields) {
    if (seenField.has(field)) continue;
    seenField.add(field);
    const found = body.fields.find((f) => f.field === field);
    fieldEntries.push({ field, allowed: found?.allowed ?? false });
  }
  const actionEntries: { action: PersonPermissionAction; allowed: boolean }[] = [];
  for (const action of actions) {
    if (seenAction.has(action)) continue;
    seenAction.add(action);
    const found = body.actions.find((a) => a.action === action);
    actionEntries.push({ action, allowed: found?.allowed ?? false });
  }
  return {
    personId: body.personId,
    treeId: body.treeId,
    fields: fieldEntries,
    actions: actionEntries,
    reasonCodes: body.reasonCodes,
  };
}

function toMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  return String(error);
}

// Re-exported so React components do not have to import
// `@genealogy/api-client` directly for the closed sets they
// need at render time.
export type { DateValue, LivingStatus, PersonBody, PersonPatch, PersonPermissions, PrivacyLevel };
export { LIVING_STATUSES, PRIVACY_LEVELS };
