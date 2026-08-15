/**
 * apps/web/src/lib/pwa/sync-runner.ts
 *
 * E12.2 — Sync runner.
 *
 * The runtime triggers a sync on every event in
 * `syncTriggers` from
 * `contracts/pwa/mutation-queue-policy.yaml`:
 *
 *   - online
 *   - visibilitychange
 *   - pageshow
 *   - periodic-timer
 *   - manual-user
 *   - mutation-queued
 *   - app-foreground
 *
 * Background Sync API is OPT-IN — the runner MUST work without
 * it. The debounce window absorbs rapid event bursts so the
 * runner never double-submits.
 */
export type SyncTrigger =
  | "online"
  | "visibilitychange"
  | "pageshow"
  | "periodic-timer"
  | "manual-user"
  | "mutation-queued"
  | "app-foreground";

export type SyncState =
  | "IDLE"
  | "DEBOUNCING"
  | "RUNNING"
  | "BACKING_OFF"
  | "COOLDOWN"
  | "SUSPENDED"
  | "FAILED";

export interface SyncConfig {
  readonly periodicTimerSeconds: number;
  readonly onlineDebounceMs: number;
  readonly visibilityDebounceMs: number;
  readonly baseBackoffSeconds: number;
  readonly maxBackoffSeconds: number;
  readonly jitterRatio: number;
}

export const DEFAULT_SYNC_CONFIG: SyncConfig = {
  periodicTimerSeconds: 30,
  onlineDebounceMs: 500,
  visibilityDebounceMs: 250,
  baseBackoffSeconds: 2,
  maxBackoffSeconds: 600,
  jitterRatio: 0.2,
};

export const SYNC_TRIGGERS: ReadonlyArray<SyncTrigger> = [
  "online",
  "visibilitychange",
  "pageshow",
  "periodic-timer",
  "manual-user",
  "mutation-queued",
  "app-foreground",
];

export function isSyncTrigger(value: string): value is SyncTrigger {
  return (SYNC_TRIGGERS as ReadonlyArray<string>).includes(value);
}

/**
 * Compute the next backoff seconds given the attempt counter.
 * The runtime MUST apply a 20% jitter window around the
 * nominal value to avoid retry storms (E12.2 invariant:
 * `foregroundRetry` + Exponential backoff per ADR-E0.5-08).
 */
export function computeBackoffSeconds(
  attempts: number,
  config: SyncConfig = DEFAULT_SYNC_CONFIG,
): number {
  const exp = Math.min(config.maxBackoffSeconds, config.baseBackoffSeconds * 2 ** attempts);
  const jitter = exp * config.jitterRatio;
  return Math.max(1, Math.round(exp + jitter));
}

export function debounceWindowFor(trigger: SyncTrigger, config: SyncConfig = DEFAULT_SYNC_CONFIG): number {
  if (trigger === "online") return config.onlineDebounceMs;
  if (trigger === "visibilitychange") return config.visibilityDebounceMs;
  if (trigger === "pageshow") return config.visibilityDebounceMs;
  return 0;
}