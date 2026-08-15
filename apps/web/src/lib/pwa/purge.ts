/**
 * apps/web/src/lib/pwa/purge.ts
 *
 * E12.1 — Purge orchestrator.
 *
 * The runtime registers a handler for every entry in
 * `purgeTriggers` from `contracts/pwa/offline-classification-policy.yaml`:
 *
 *   - LOGOUT
 *   - SESSION_REVOKE
 *   - TENANT_SWITCH
 *   - PERMISSION_VERSION_BUMP
 *   - SUPPORT_JIT_REAUTH
 *   - PLAN_DOWNGRADE
 *   - DNA_REVOKE
 *   - EXPORT_DELETE_REQUESTED
 *
 * Each handler MUST clear the appropriate Cache Storage buckets
 * AND the IndexedDB stores owned by the PWA. The function is
 * intentionally pure (returns a `PurgePlan`) so the runtime can
 * decide whether to actually invoke the cache API.
 */
import type { CacheTier } from "./storage-classifier";

export type PurgeTrigger =
  | "LOGOUT"
  | "SESSION_REVOKE"
  | "TENANT_SWITCH"
  | "PERMISSION_VERSION_BUMP"
  | "SUPPORT_JIT_REAUTH"
  | "PLAN_DOWNGRADE"
  | "DNA_REVOKE"
  | "EXPORT_DELETE_REQUESTED";

export interface PurgePlan {
  readonly trigger: PurgeTrigger;
  readonly tiers: ReadonlyArray<CacheTier>;
  readonly storeNames: ReadonlyArray<string>;
  readonly debounceMs: number;
}

const ALL_TIERS: ReadonlyArray<CacheTier> = ["SHELL", "LOCALE", "PROJECTION", "MEDIA_THUMB"];

const TABLE: Readonly<Record<PurgeTrigger, PurgePlan>> = {
  LOGOUT: { trigger: "LOGOUT", tiers: ALL_TIERS, storeNames: ["mutations", "dlq"], debounceMs: 0 },
  SESSION_REVOKE: { trigger: "SESSION_REVOKE", tiers: ALL_TIERS, storeNames: ["mutations", "dlq"], debounceMs: 0 },
  TENANT_SWITCH: { trigger: "TENANT_SWITCH", tiers: ["PROJECTION", "MEDIA_THUMB"], storeNames: ["mutations"], debounceMs: 0 },
  PERMISSION_VERSION_BUMP: {
    trigger: "PERMISSION_VERSION_BUMP",
    tiers: ["PROJECTION", "MEDIA_THUMB"],
    storeNames: [],
    debounceMs: 250,
  },
  SUPPORT_JIT_REAUTH: { trigger: "SUPPORT_JIT_REAUTH", tiers: ["PROJECTION"], storeNames: ["mutations"], debounceMs: 0 },
  PLAN_DOWNGRADE: {
    trigger: "PLAN_DOWNGRADE",
    tiers: ["PROJECTION", "MEDIA_THUMB"],
    storeNames: ["mutations", "dlq"],
    debounceMs: 0,
  },
  DNA_REVOKE: { trigger: "DNA_REVOKE", tiers: ["PROJECTION"], storeNames: [], debounceMs: 0 },
  EXPORT_DELETE_REQUESTED: {
    trigger: "EXPORT_DELETE_REQUESTED",
    tiers: ["PROJECTION"],
    storeNames: ["dlq"],
    debounceMs: 0,
  },
};

export function planForTrigger(trigger: PurgeTrigger): PurgePlan {
  const plan = TABLE[trigger];
  if (!plan) {
    throw new Error(`Unknown purge trigger: ${trigger}`);
  }
  return plan;
}

export function shouldPurgeTiers(trigger: PurgeTrigger, tiers: ReadonlyArray<CacheTier>): boolean {
  const plan = planForTrigger(trigger);
  return plan.tiers.some((t) => tiers.includes(t));
}