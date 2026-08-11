/**
 * apps/web/src/lib/print/export-job.ts
 *
 * Pure state machine for the asynchronous print/export job that
 * E5.6 / R6.4 / J-SHARE-3 ("Print and PDF export") introduces.
 *
 * The job is intentionally framework-agnostic so the React shell
 * (`<ExportRequestPanel>`) and the BFF shim (`createBffExportJob`)
 * can share the same transitions. The state machine is:
 *
 *   idle
 *     └─ submit() ─►  queued   (idempotency key issued)
 *                       │
 *                       │  poll() — server reports progress
 *                       ▼
 *                     running  (Temporal + Gotenberg running)
 *                       │
 *        ┌──────────────┼────────────────────────┐
 *        ▼              ▼                        ▼
 *      ready         failed                   expired
 *   (signed URL)   (no payload)          (URL TTL elapsed)
 *
 * The job stores:
 *   - `idempotencyKey` — UUID v4 the BFF persists so re-clicks do
 *     not enqueue duplicate Temporal workflows (R17.4).
 *   - `resumeToken` — opaque cursor so a multi-page export can be
 *     continued by the server (mirrors E5.2 `TreeProjectionBody`).
 *   - `signedUrl` + `signedUrlExpiresAt` — populated only in
 *     `ready` state; the URL is short-lived (clamped by
 *     `print-policy.clampSignedUrlTtlSeconds`).
 *   - `audit` — the audit metadata the BFF will emit to
 *     `audit-service` (we mirror it locally so the UI can confirm
 *     the obligation was attached before the user clicks the URL).
 *
 * The machine is exercised by `export-job.test.ts`; it never
 * touches the network itself.
 */
import {
  clampSignedUrlTtlSeconds,
  type PrintFormat,
  type PrintScope,
  type WatermarkMode,
  type PrivacyLevel,
  type ExportJobStatus,
  EXPORT_JOB_STATUSES,
  PRINT_FORMATS,
  PRINT_SCOPES,
  WATERMARK_MODES,
  PRIVACY_LEVELS,
  resolveDefaultWatermark,
} from "./print-policy";

export interface RedactionObligation {
  readonly reasonCode: string;
  readonly droppedFieldCount: number;
  readonly appliedAt: string;
}

export interface ExportAuditMetadata {
  readonly correlationId: string;
  readonly tenantId: string;
  readonly treeId: string;
  readonly rootPersonId: string;
  readonly actorPseudoId: string;
  readonly obligations: ReadonlyArray<RedactionObligation>;
  readonly watermark: WatermarkMode;
  readonly issuedAt: string;
}

export interface ExportJobInput {
  readonly tenantId: string;
  readonly treeId: string;
  readonly rootPersonId: string;
  readonly scope: PrintScope;
  readonly format: PrintFormat;
  readonly privacy: PrivacyLevel;
  readonly watermark?: WatermarkMode;
  readonly includeLiving: boolean;
  readonly pageBreakBehaviour: "per-generation" | "per-node" | "single-page";
  readonly layout: "A4-portrait" | "A4-landscape" | "Letter-portrait" | "Letter-landscape";
  readonly ttlSeconds: number;
  readonly hasShareToken: boolean;
  readonly nodeCount: number;
}

export interface ExportJobReady {
  readonly status: "ready";
  readonly jobId: string;
  readonly signedUrl: string;
  readonly signedUrlExpiresAt: string;
  readonly audit: ExportAuditMetadata;
  readonly obligations: ReadonlyArray<RedactionObligation>;
}

export interface ExportJobQueued {
  readonly status: "queued" | "running";
  readonly jobId: string;
  readonly audit: ExportAuditMetadata;
  readonly resumeToken: string | null;
}

export interface ExportJobTerminalFailure {
  readonly status: "failed";
  readonly jobId: string;
  readonly audit: ExportAuditMetadata;
  readonly reasonCode: string;
}

export interface ExportJobExpired {
  readonly status: "expired";
  readonly jobId: string;
  readonly audit: ExportAuditMetadata;
  readonly expiredAt: string;
}

export type ExportJobState =
  | { readonly status: "idle" }
  | ExportJobQueued
  | ExportJobReady
  | ExportJobTerminalFailure
  | ExportJobExpired;

/**
 * The snapshot carries the lifecycle state PLUS the fields the
 * reducer needs to preserve across every transition (audit,
 * idempotency key, input). Flattening them onto a base interface
 * lets the reducer narrow on `status` without losing access to
 * the ambient metadata (TypeScript would otherwise complain that
 * `audit` doesn't exist on `{status: "idle"}`).
 */
export interface ExportJobBaseFields {
  readonly idempotencyKey: string;
  readonly input: ExportJobInput;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export type ExportJobSnapshot = {
  readonly status: ExportJobState["status"];
  readonly jobId: string;
  readonly audit: ExportAuditMetadata;
  readonly obligations: ReadonlyArray<RedactionObligation>;
  readonly signedUrl: string | null;
  readonly signedUrlExpiresAt: string | null;
  readonly reasonCode: string | null;
  readonly resumeToken: string | null;
  readonly expiredAt: string | null;
} & ExportJobBaseFields;

export const TERMINAL_EXPORT_STATUSES: ReadonlyArray<ExportJobStatus> = [
  "ready",
  "failed",
  "expired",
];

/**
 * Assertion helpers the BFF shim and the UI both call before they
 * build an `ExportJob`. They return `null` on success and a
 * machine-readable `reasonCode` on failure so the UI can render
 * the right key from `print.scopeRejected` / `print.formatRejected`
 * etc.
 */
export function assertExportInput(
  input: ExportJobInput,
): { readonly ok: true } | { readonly ok: false; readonly reasonCode: string } {
  if (!input.tenantId) return { ok: false, reasonCode: "missing-tenant" };
  if (!input.treeId) return { ok: false, reasonCode: "missing-tree" };
  if (!input.rootPersonId) return { ok: false, reasonCode: "missing-root-person" };
  if (!PRINT_SCOPES.includes(input.scope)) {
    return { ok: false, reasonCode: "unknown-scope" };
  }
  if (!PRINT_FORMATS.includes(input.format)) {
    return { ok: false, reasonCode: "unknown-format" };
  }
  if (!PRIVACY_LEVELS.includes(input.privacy)) {
    return { ok: false, reasonCode: "unknown-privacy" };
  }
  if (input.watermark && !WATERMARK_MODES.includes(input.watermark)) {
    return { ok: false, reasonCode: "unknown-watermark" };
  }
  if (!Number.isFinite(input.ttlSeconds) || input.ttlSeconds <= 0) {
    return { ok: false, reasonCode: "invalid-ttl" };
  }
  if (!Number.isFinite(input.nodeCount) || input.nodeCount <= 0) {
    return { ok: false, reasonCode: "invalid-node-count" };
  }
  if (input.privacy === "PRIVATE" && input.scope !== "currentPerson") {
    return { ok: false, reasonCode: "private-scope-too-broad" };
  }
  return { ok: true };
}

/**
 * Decide the watermark mode for a given export. The UI uses this
 * to preview what the BFF will stamp; the BFF may still override
 * (e.g. when ABAC adds a `watermark` obligation) but the preview
 * must surface what the user is consenting to.
 */
export function resolveWatermarkMode(input: ExportJobInput): WatermarkMode {
  if (input.watermark) return input.watermark;
  return resolveDefaultWatermark(input.privacy, input.hasShareToken);
}

/**
 * Build the audit metadata the BFF will persist. The fields here
 * come from R15 / glossary §2.2 obligations row — every signed URL
 * MUST carry a `tenant_pseudo_id`/`user_pseudo_id` trace, never a
 * raw identifier.
 */
export function buildAuditMetadata(input: {
  readonly input: ExportJobInput;
  readonly correlationId: string;
  readonly actorPseudoId: string;
  readonly issuedAt: Date;
}): ExportAuditMetadata {
  return {
    correlationId: input.correlationId,
    tenantId: input.input.tenantId,
    treeId: input.input.treeId,
    rootPersonId: input.input.rootPersonId,
    actorPseudoId: input.actorPseudoId,
    obligations: [],
    watermark: resolveWatermarkMode(input.input),
    issuedAt: input.issuedAt.toISOString(),
  };
}

/**
 * Reduce an incoming server snapshot into the previous state. The
 * reducer is *strict*: an unknown status throws so the caller can
 * fail the live region announcement rather than render a corrupt
 * panel.
 */
export function reduceExportJob(
  prev: ExportJobSnapshot,
  patch:
    | { readonly kind: "submit"; readonly jobId: string; readonly at: Date }
    | {
        readonly kind: "poll";
        readonly status: "queued" | "running";
        readonly resumeToken: string | null;
        readonly at: Date;
      }
    | {
        readonly kind: "ready";
        readonly signedUrl: string;
        readonly ttlSeconds: number;
        readonly obligations: ReadonlyArray<RedactionObligation>;
        readonly at: Date;
      }
    | { readonly kind: "failed"; readonly reasonCode: string; readonly at: Date }
    | { readonly kind: "expired"; readonly at: Date }
    | { readonly kind: "reset"; readonly at: Date },
): ExportJobSnapshot {
  const base: ExportJobBaseFields = {
    idempotencyKey: prev.idempotencyKey,
    input: prev.input,
    createdAt: prev.createdAt,
    updatedAt: patch.at.toISOString(),
  };
  const preservedJobId = (kind: "queued" | "running" | "ready" | "failed" | "expired"): string => {
    if (kind === "queued" || kind === "running") {
      if (prev.status === "queued" || prev.status === "running" || prev.status === "ready") {
        return prev.jobId;
      }
    }
    if (kind === "ready" || kind === "failed" || kind === "expired") {
      if (
        prev.status === "queued" ||
        prev.status === "running" ||
        prev.status === "ready" ||
        prev.status === "failed" ||
        prev.status === "expired"
      ) {
        return prev.jobId;
      }
    }
    return "";
  };
  const preservedAudit = (): ExportAuditMetadata => prev.audit;
  switch (patch.kind) {
    case "reset":
      return {
        status: "idle",
        jobId: "",
        audit: prev.audit,
        obligations: prev.obligations,
        signedUrl: null,
        signedUrlExpiresAt: null,
        reasonCode: null,
        resumeToken: null,
        expiredAt: null,
        ...base,
      };
    case "submit": {
      if (prev.status !== "idle" && prev.status !== "failed") {
        throw new Error("export-job: submit only allowed from idle/failed");
      }
      return {
        status: "queued",
        jobId: patch.jobId,
        audit: preservedAudit(),
        obligations: prev.obligations,
        signedUrl: null,
        signedUrlExpiresAt: null,
        reasonCode: null,
        resumeToken: null,
        expiredAt: null,
        ...base,
      };
    }
    case "poll": {
      if (prev.status !== "queued" && prev.status !== "running") {
        throw new Error("export-job: poll only allowed from queued/running");
      }
      return {
        status: patch.status,
        jobId: preservedJobId(patch.status),
        audit: preservedAudit(),
        obligations: prev.obligations,
        signedUrl: null,
        signedUrlExpiresAt: null,
        reasonCode: null,
        resumeToken: patch.resumeToken,
        expiredAt: null,
        ...base,
      };
    }
    case "ready": {
      const ttl = clampSignedUrlTtlSeconds(patch.ttlSeconds);
      const expiresAt = new Date(patch.at.getTime() + ttl * 1000);
      return {
        status: "ready",
        jobId: preservedJobId("ready"),
        audit: { ...preservedAudit(), obligations: patch.obligations },
        obligations: patch.obligations,
        signedUrl: patch.signedUrl,
        signedUrlExpiresAt: expiresAt.toISOString(),
        reasonCode: null,
        resumeToken: null,
        expiredAt: null,
        ...base,
      };
    }
    case "failed": {
      return {
        status: "failed",
        jobId: preservedJobId("failed"),
        audit: preservedAudit(),
        obligations: prev.obligations,
        signedUrl: null,
        signedUrlExpiresAt: null,
        reasonCode: patch.reasonCode,
        resumeToken: null,
        expiredAt: null,
        ...base,
      };
    }
    case "expired": {
      return {
        status: "expired",
        jobId: preservedJobId("expired"),
        audit: preservedAudit(),
        obligations: prev.obligations,
        signedUrl: prev.signedUrl,
        signedUrlExpiresAt: prev.signedUrlExpiresAt,
        reasonCode: null,
        resumeToken: null,
        expiredAt: patch.at.toISOString(),
        ...base,
      };
    }
  }
}

/**
 * Build a deterministic, audit-friendly `jobId` placeholder used
 * by the BFF shim when the server is unreachable. The real jobId
 * arrives from the server; this helper exists so the React shell
 * can render a stable identity for the live region announcement.
 */
export function previewJobId(correlationId: string, at: Date): string {
  const bucket = Math.floor(at.getTime() / 1000);
  return `preview-${correlationId}-${bucket}`;
}

/**
 * Helper for tests / BFF shims: re-export the closed-set status
 * list with a runtime guard so unknown statuses cannot slip into
 * the panel.
 */
export function assertStatus(value: unknown): ExportJobStatus {
  if (typeof value === "string" && (EXPORT_JOB_STATUSES as ReadonlyArray<string>).includes(value)) {
    return value as ExportJobStatus;
  }
  throw new Error(`export-job: unknown status "${String(value)}"`);
}
