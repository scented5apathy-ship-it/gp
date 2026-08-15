/**
 * apps/web/src/lib/pwa/mutation-queue.ts
 *
 * E12.2 — Mutation queue engine.
 *
 * The runtime persists queued mutations in IndexedDB. The
 * queue is the only path authorised to enqueue / dequeue /
 * submit / discard mutations — no component may call
 * `indexedDB.open()` directly (enforced by
 * `scripts/lint-mutation-queue.mjs`).
 *
 * Hard invariants (mirrors `contracts/pwa/mutation-queue-policy.yaml`):
 *   - operationId required (UUIDv7 generated client-side)
 *   - baseVersion required (the entity version the user edited)
 *   - tenantId required (never empty)
 *   - LocalStorage is FORBIDDEN
 *   - Background Sync API MUST NOT be assumed available
 *   - Idempotency-Key header MUST be set on submit
 */

export type MutationKind =
  | "PERSON_PATCH"
  | "RELATIONSHIP_PATCH"
  | "NAME_APPEND"
  | "NAME_REPLACE"
  | "EVENT_APPEND"
  | "EVENT_REPLACE"
  | "CITATION_APPEND"
  | "CITATION_REPLACE"
  | "ALBUM_LINK"
  | "ALBUM_UNLINK"
  | "NOTE_APPEND"
  | "NOTE_REPLACE"
  | "COMMENT_POST"
  | "COMMENT_EDIT"
  | "COMMENT_RESOLVE"
  | "COLLABORATION_APPROVE"
  | "COLLABORATION_REJECT"
  | "COLLABORATION_REQUEST"
  | "DRAFT_TREE_CREATE"
  | "DRAFT_PERSON_CREATE";

export type MutationEntityKind =
  | "person"
  | "relationship"
  | "name"
  | "event"
  | "citation"
  | "album"
  | "note"
  | "comment"
  | "collaboration_request"
  | "draft_tree"
  | "draft_person";

export type MutationState =
  | "DRAFT"
  | "QUEUED"
  | "SUBMITTING"
  | "ACCEPTED"
  | "CONFLICTING"
  | "REJECTED"
  | "DLQ"
  | "DISCARDED"
  | "PURGED";

export interface QueuedMutation<P = unknown> {
  readonly operationId: string;
  readonly tenantId: string;
  readonly entityKind: MutationEntityKind;
  readonly entityId: string;
  readonly mutationKind: MutationKind;
  readonly baseVersion: string;
  readonly patch: P;
  readonly submittedAt: number;
  readonly attempts: number;
  readonly status: MutationState;
  readonly permissionVersion: string;
}

export type MutationFailureReason =
  | "QUEUE_FULL"
  | "OPERATION_ID_MISSING"
  | "BASE_VERSION_MISSING"
  | "ENTITY_KIND_UNKNOWN"
  | "MUTATION_KIND_UNKNOWN"
  | "TENANT_ID_MISSING"
  | "INDEXED_DB_UNAVAILABLE"
  | "SERIALIZATION_FAILED"
  | "SYNC_ALREADY_RUNNING"
  | "OFFLINE_BACKOFF_ACTIVE"
  | "PERMISSION_VERSION_STALE";

export type EnqueueOutcome =
  | { readonly outcome: "ENQUEUED"; readonly entry: QueuedMutation }
  | { readonly outcome: "DENIED"; readonly reason: MutationFailureReason };

const VALID_MUTATION_KINDS: ReadonlyArray<MutationKind> = [
  "PERSON_PATCH",
  "RELATIONSHIP_PATCH",
  "NAME_APPEND",
  "NAME_REPLACE",
  "EVENT_APPEND",
  "EVENT_REPLACE",
  "CITATION_APPEND",
  "CITATION_REPLACE",
  "ALBUM_LINK",
  "ALBUM_UNLINK",
  "NOTE_APPEND",
  "NOTE_REPLACE",
  "COMMENT_POST",
  "COMMENT_EDIT",
  "COMMENT_RESOLVE",
  "COLLABORATION_APPROVE",
  "COLLABORATION_REJECT",
  "COLLABORATION_REQUEST",
  "DRAFT_TREE_CREATE",
  "DRAFT_PERSON_CREATE",
];

const VALID_ENTITY_KINDS: ReadonlyArray<MutationEntityKind> = [
  "person",
  "relationship",
  "name",
  "event",
  "citation",
  "album",
  "note",
  "comment",
  "collaboration_request",
  "draft_tree",
  "draft_person",
];

export function isMutationKind(value: string): value is MutationKind {
  return VALID_MUTATION_KINDS.includes(value as MutationKind);
}

export function isEntityKind(value: string): value is MutationEntityKind {
  return VALID_ENTITY_KINDS.includes(value as MutationEntityKind);
}

/**
 * Validate an enqueue request. The runtime MUST call this
 * helper before persisting the mutation; a missing required
 * field MUST be refused.
 */
export function validateEnqueue<P>(req: QueuedMutation<P>): MutationFailureReason | undefined {
  if (!req.operationId) return "OPERATION_ID_MISSING";
  if (!req.baseVersion) return "BASE_VERSION_MISSING";
  if (!req.tenantId) return "TENANT_ID_MISSING";
  if (!isMutationKind(req.mutationKind)) return "MUTATION_KIND_UNKNOWN";
  if (!isEntityKind(req.entityKind)) return "ENTITY_KIND_UNKNOWN";
  if (req.attempts < 0) return "SERIALIZATION_FAILED";
  return undefined;
}

/**
 * Advance a mutation's state. The helper enforces the
 * transitions declared in
 * `mutationQueueStateMatrix` (DRAFT→QUEUED→SUBMITTING→ACCEPTED,
 * plus DLQ / CONFLICTING / DISCARDED side branches).
 */
const TRANSITIONS: Readonly<Record<MutationState, ReadonlyArray<MutationState>>> = {
  DRAFT: ["QUEUED", "DISCARDED"],
  QUEUED: ["SUBMITTING", "DLQ", "PURGED"],
  SUBMITTING: ["ACCEPTED", "CONFLICTING", "REJECTED", "DLQ"],
  ACCEPTED: ["PURGED"],
  CONFLICTING: ["QUEUED", "DISCARDED", "DLQ"],
  REJECTED: ["DISCARDED", "DLQ"],
  DLQ: ["QUEUED", "DISCARDED", "PURGED"],
  DISCARDED: ["PURGED"],
  PURGED: [],
};

export function canTransition(from: MutationState, to: MutationState): boolean {
  return TRANSITIONS[from].includes(to);
}

export function advance<P>(entry: QueuedMutation<P>, next: MutationState): QueuedMutation<P> {
  if (!canTransition(entry.status, next)) {
    throw new Error(`invalid mutation transition ${entry.status} -> ${next}`);
  }
  return { ...entry, status: next, attempts: entry.attempts + (next === "SUBMITTING" ? 1 : 0) };
}