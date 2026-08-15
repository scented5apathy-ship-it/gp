/**
 * apps/web/src/lib/pwa/conflict-resolution.ts
 *
 * E12.2 — Conflict resolution UX hook.
 *
 * When the server rejects an offline-submitted mutation the
 * runtime builds a conflict envelope that distinguishes
 * four user-facing categories:
 *
 *   - VERSION_CONFLICT      (server has a newer version)
 *   - REDACTION_DENIED      (server rejected because redaction
 *                            changed between offline edit and
 *                            online submit)
 *   - TENANT_MISMATCH       (cache served the wrong tenant)
 *   - PERMISSION_REVOKED    (support-jit issued during sync)
 *
 * The UX MUST offer a three-way merge (local copy, server
 * copy, recommended merge) per E12.2 invariant
 * `threeWayMergeOffered`.
 */

export type MutationConflictReason =
  | "VERSION_CONFLICT"
  | "REDACTION_DENIED"
  | "TENANT_MISMATCH"
  | "PERMISSION_REVOKED"
  | "SCHEMA_STALE"
  | "RATE_LIMITED"
  | "LIVING_RULE_CHANGED"
  | "DNA_RULE_CHANGED"
  | "CONSENT_REVOKED"
  | "ENTITY_DELETED"
  | "DUPLICATE_OPERATION_ID"
  | "VALIDATION_FAILED"
  | "ENCRYPTION_KEY_ROTATED";

export type ConflictStrategy =
  | "CONFLICT_PRESERVE_LOCAL"
  | "CONFLICT_PRESERVE_SERVER"
  | "CONFLICT_THREE_WAY_MERGE";

export interface ConflictEnvelope<P> {
  readonly reason: MutationConflictReason;
  readonly operationId: string;
  readonly localPatch: P;
  readonly serverVersion: string;
  readonly serverPatch?: P;
  readonly recommendedStrategy: ConflictStrategy;
}

const THREE_WAY_REASONS: ReadonlyArray<MutationConflictReason> = [
  "VERSION_CONFLICT",
  "REDACTION_DENIED",
  "LIVING_RULE_CHANGED",
  "DNA_RULE_CHANGED",
  "SCHEMA_STALE",
];

const LOCAL_LOCK_REASONS: ReadonlyArray<MutationConflictReason> = [
  "TENANT_MISMATCH",
  "PERMISSION_REVOKED",
  "CONSENT_REVOKED",
  "ENTITY_DELETED",
];

export function recommendedStrategy(reason: MutationConflictReason): ConflictStrategy {
  if (THREE_WAY_REASONS.includes(reason)) {
    return "CONFLICT_THREE_WAY_MERGE";
  }
  if (LOCAL_LOCK_REASONS.includes(reason)) {
    return "CONFLICT_PRESERVE_SERVER";
  }
  return "CONFLICT_PRESERVE_LOCAL";
}

export function buildConflictEnvelope<P>(
  reason: MutationConflictReason,
  operationId: string,
  localPatch: P,
  serverVersion: string,
  serverPatch?: P,
): ConflictEnvelope<P> {
  if (serverPatch !== undefined) {
    return {
      reason,
      operationId,
      localPatch,
      serverVersion,
      serverPatch,
      recommendedStrategy: recommendedStrategy(reason),
    };
  }
  return {
    reason,
    operationId,
    localPatch,
    serverVersion,
    recommendedStrategy: recommendedStrategy(reason),
  };
}

export function supportsThreeWay(reason: MutationConflictReason): boolean {
  return THREE_WAY_REASONS.includes(reason);
}