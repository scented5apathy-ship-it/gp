/**
 * apps/web/src/lib/pwa/idempotency.ts
 *
 * E12.2 — Idempotency binding for the offline mutation queue.
 *
 * Every queued mutation carries a client-generated
 * `operationId` (UUIDv7). When the runtime submits the
 * mutation to the server it MUST set the `Idempotency-Key`
 * header (E1.3 contract) to that `operationId` so a re-submit
 * never duplicates the server side effect.
 *
 * The header is mandatory: an absent Idempotency-Key MUST
 * cause the server to reject the request with 400.
 */
import type { MutationKind } from "./mutation-queue";

export const IDEMPOTENCY_HEADER = "Idempotency-Key";

export interface IdempotencyRequest {
  readonly operationId: string;
  readonly mutationKind: MutationKind;
  readonly entityKind: string;
  readonly entityId: string;
  readonly tenantId: string;
}

/**
 * Build the Idempotency-Key header value for a queued mutation.
 * The server expects the literal `operationId`; we reject
 * empty / malformed values so the runtime never submits a
 * mutation without a real idempotency token.
 */
export function buildIdempotencyHeader(req: IdempotencyRequest): string {
  if (!req.operationId) {
    throw new Error("operationId is required for Idempotency-Key");
  }
  if (!req.tenantId) {
    throw new Error("tenantId is required for Idempotency-Key");
  }
  if (!req.mutationKind) {
    throw new Error("mutationKind is required for Idempotency-Key");
  }
  if (!req.entityKind || !req.entityId) {
    throw new Error("entityKind + entityId are required for Idempotency-Key");
  }
  return req.operationId;
}