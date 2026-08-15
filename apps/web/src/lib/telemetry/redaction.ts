/**
 * apps/web/src/lib/telemetry/redaction.ts
 *
 * E13.1 browser-side telemetry scrubber. Mirrors
 * `contracts/reliability/telemetry-policy.yaml` (`forbiddenMetricLabels`,
 * `telemetryRedactionPatterns`, `browserTelemetry.eventWhitelist`,
 * `auditPipelineFields.rejected`, `cardinalityCeilings`,
 * `fallbackStrategy`).
 *
 * The SDK is responsible for calling `scrubEvent(...)` BEFORE
 * any OTLP exporter writes. When the OTel Collector is
 * unreachable the runtime MUST fall back to the local ring
 * buffer (`pushFallback`) and trip the circuit breaker.
 */

export type SignalKind = "trace" | "metric" | "log" | "audit";

export interface ScrubbedEvent {
  name: string;
  signalKind: SignalKind;
  attributes: Record<string, unknown>;
  traceparent?: string;
  tracestate?: string;
  correlationId?: string;
  timestamp: number;
}

export type RedactionOutcome =
  | { state: "ALLOWED"; event: ScrubbedEvent }
  | { state: "PSEUDONYMIZED"; event: ScrubbedEvent }
  | { state: "REDACTED"; event: ScrubbedEvent; reason: string }
  | { state: "DROPPED"; reason: string }
  | { state: "ESCALATED"; reason: string };

export const FORBIDDEN_PAYLOAD_KEYS: ReadonlySet<string> = new Set([
  "tenant_id", "user_id", "actor_id",
  "email", "oidc_subject", "oidcSubject",
  "phone", "passport", "ssn",
  "raw_dna", "raw_pii", "rawEmail", "rawPhone", "rawAddress",
  "treeViewerBypass", "rawEventPayload", "rawAuditStream",
  "rawConsentReceipt", "rawSignatureBlob", "rawIdDocument",
  "cameraSerial", "exifGps", "passportNumber",
  "productionPii", "internalVaultToken", "internalSessionCookie",
  "dnaRawBucketKey", "dnaMatchBucketKey",
]);

export const BROWSER_EVENT_WHITELIST: ReadonlySet<string> = new Set([
  "app_loaded",
  "route_changed",
  "flag_exposure",
  "error_boundary_caught",
  "mutation_queue_synced",
  "offline_cache_opt_in",
  "offline_cache_opt_out",
  "offline_cache_purge",
  "permission_version_mismatch",
  "accessibility_preference_changed",
]);

export const REDACTION_PATTERNS: ReadonlyArray<readonly [string, RegExp]> = [
  ["ssn", /\b\d{3}-\d{2}-\d{4}\b/g],
  ["passport", /\b[A-Z]{1,2}[0-9]{6,9}\b/g],
  ["driverLicense", /\b[A-Z]{1,2}\d{6,8}\b/g],
  ["email", /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b/g],
  ["ipv4", /\b(?:25[0-5]|2[0-4]\d|[01]?\d\d?)(?:\.(?:25[0-5]|2[0-4]\d|[01]?\d\d?)){3}\b/g],
  ["jwt", /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b/g],
  ["rawDnaMarker", /\brs\d{2,8}\s+[ACGT]{2,}\b/g],
  ["phone", /\+?\d{1,3}[\s.-]?\d{1,4}[\s.-]?\d{1,4}[\s.-]?\d{1,9}/g],
  ["authorizationHeader", /(?:authorization|cookie|set-cookie):\s*[^\s,;]+/gi],
];

export const CARDINALITY_CEILINGS: Readonly<Record<string, number>> = Object.freeze({
  tenant_pseudo_id: 50000,
  user_pseudo_id: 200000,
  workflow_pseudo_id: 1000,
  consumer_pseudo_id: 1000,
});

export const PSEUDONYM_LABELS: ReadonlySet<string> = new Set([
  "tenant_pseudo_id",
  "user_pseudo_id",
  "actor_pseudo_id",
  "workflow_pseudo_id",
  "consumer_pseudo_id",
]);

export interface CardinalityTracker {
  count(label: string, value: string): number;
  reset(): void;
}

export function createCardinalityTracker(): CardinalityTracker {
  const counts = new Map<string, number>();
  return {
    count(label: string, value: string): number {
      const key = `${label}=${value}`;
      counts.set(key, (counts.get(key) ?? 0) + 1);
      return counts.get(key) ?? 0;
    },
    reset(): void {
      counts.clear();
    },
  };
}

export function pseudonymize(
  value: string,
  pepper: string,
  truncateBytes = 16,
): string {
  // Subset of WebCrypto / Node `crypto` based on the runtime.
  // We keep this synchronous and rely on a tiny FNV-1a + pepper
  // mixing for unit-test determinism; production code MUST
  // replace it with HMAC-SHA256 keyed by the Vault pepper.
  if (!value) return "";
  const mix = `${pepper}::${value}`;
  let h1 = 0x811c9dc5;
  for (let i = 0; i < mix.length; i += 1) {
    h1 ^= mix.charCodeAt(i);
    h1 = Math.imul(h1, 0x01000193);
  }
  let h2 = 0xdeadbeef;
  for (let i = mix.length - 1; i >= 0; i -= 1) {
    h2 ^= mix.charCodeAt(i);
    h2 = Math.imul(h2, 0x85ebca6b);
  }
  const raw = ((h1 >>> 0).toString(16).padStart(8, "0")
    + (h2 >>> 0).toString(16).padStart(8, "0"));
  return raw.slice(0, Math.min(truncateBytes * 2, raw.length));
}

export function scrubEvent(input: ScrubbedEvent): RedactionOutcome {
  if (!input || !input.name) {
    return { state: "DROPPED", reason: "blank_event" };
  }
  if (!BROWSER_EVENT_WHITELIST.has(input.name)) {
    return { state: "DROPPED", reason: `event_not_whitelisted:${input.name}` };
  }
  const attributes = { ...input.attributes };
  for (const key of Object.keys(attributes)) {
    if (FORBIDDEN_PAYLOAD_KEYS.has(key)) {
      if (key === "raw_dna" || key === "raw_pii"
          || key === "oidc_subject" || key === "rawConsentReceipt"
          || key === "rawSignatureBlob") {
        return { state: "ESCALATED", reason: `browser_forbidden:${key}` };
      }
      delete attributes[key];
    }
  }
  if (attributes["content"] !== undefined
      || attributes["inputValue"] !== undefined
      || attributes["rawDna"] !== undefined
      || attributes["rawMedia"] !== undefined) {
    return { state: "ESCALATED", reason: "browser_capture_forbidden" };
  }
  const isAudit = input.signalKind === "audit";
  if (input.signalKind === "audit" || input.signalKind === "metric") {
    if (!attributes["tenant_pseudo_id"]) {
      return { state: "DROPPED", reason: "tenant_pseudo_id_missing" };
    }
  }
  if (isAudit && !attributes["actor_pseudo_id"]) {
    return { state: "ESCALATED", reason: "actor_pseudo_id_missing" };
  }
  return { state: "ALLOWED", event: { ...input, attributes } };
}

export function applyRedactionToString(
  body: string,
): { body: string; hits: Record<string, number> } {
  const hits: Record<string, number> = {};
  let scrubbed = body;
  for (const [name, regex] of REDACTION_PATTERNS) {
    const matches = scrubbed.match(regex);
    if (matches && matches.length > 0) {
      hits[name] = matches.length;
      scrubbed = scrubbed.replace(regex, `[REDACTED:${name}]`);
    }
  }
  return { body: scrubbed, hits };
}

export interface FallbackRingBufferOptions {
  maxBytes: number;
  collectorUnreachable: () => boolean;
  flush: (events: ScrubbedEvent[]) => Promise<void>;
}

export class FallbackRingBuffer {
  private bytes = 0;
  private readonly buffer: ScrubbedEvent[] = [];
  private failures = 0;
  private circuitOpen = false;
  private readonly opts: FallbackRingBufferOptions;

  constructor(opts: FallbackRingBufferOptions) {
    this.opts = opts;
  }

  size(): number {
    return this.buffer.length;
  }

  totalBytes(): number {
    return this.bytes;
  }

  isCircuitOpen(): boolean {
    return this.circuitOpen;
  }

  push(event: ScrubbedEvent): "queued" | "dropped_oldest" | "circuit_open" {
    const ev = JSON.stringify(event);
    const size = ev.length;
    if (this.circuitOpen) {
      return "circuit_open";
    }
    while (this.bytes + size > this.opts.maxBytes && this.buffer.length > 0) {
      const removed = this.buffer.shift();
      if (removed) {
        this.bytes -= JSON.stringify(removed).length;
      }
    }
    this.buffer.push(event);
    this.bytes += size;
    return "queued";
  }

  async drain(): Promise<number> {
    if (this.opts.collectorUnreachable()) {
      this.failures += 1;
      if (this.failures >= 5) {
        this.circuitOpen = true;
      }
      return 0;
    }
    if (this.buffer.length === 0) return 0;
    const events = this.buffer.slice();
    try {
      await this.opts.flush(events);
      this.buffer.length = 0;
      this.bytes = 0;
      this.failures = 0;
      this.circuitOpen = false;
      return events.length;
    } catch {
      this.failures += 1;
      if (this.failures >= 5) {
        this.circuitOpen = true;
      }
      return 0;
    }
  }
}

export const DEFAULT_EGRESS_ALLOWLIST: ReadonlyArray<string> = [
  "otel-collector.gp-observability.svc.cluster.local",
  "otel-collector.gp-observability",
  "localhost_4318_dev_only",
];

export function isEgressAllowed(endpoint: string): boolean {
  return DEFAULT_EGRESS_ALLOWLIST.includes(endpoint);
}

export function hasTraceparent(headers: Record<string, string>): boolean {
  return /^[\da-f]{2}-[\da-f]{32}-[\da-f]{16}-[\da-f]{2}$/.test(
    headers["traceparent"] ?? "",
  );
}