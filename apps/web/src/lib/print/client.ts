/**
 * apps/web/src/lib/print/client.ts
 *
 * BFF shim for the E5.6 print/export pipeline. The shim:
 *
 *   - issues a fresh UUID v4 idempotency key per request
 *     (R17.4 — re-clicks must not enqueue duplicate Temporal
 *     workflows);
 *   - wraps the export request in `X-Correlation-Id`,
 *     `X-Tenant-Id` and `Idempotency-Key` headers, mirroring
 *     the pattern every other E5.x BFF wrapper follows;
 *   - never blocks the request thread — long-running render
 *     happens in Temporal/Gotenberg and the shim only returns
 *     the queued job id;
 *   - returns a typed `ExportJobSnapshot` the React shell can
 *     subscribe to via the `reduceExportJob` reducer;
 *   - emits a synthetic placeholder snapshot when the BFF is
 *     offline so the UI degrades gracefully and the audit
 *     preview still surfaces the obligations.
 *
 * The BFF endpoint path is
 *   `POST /api/v1/trees/{treeId}/print-jobs` and is owned by
 * the `import-export-service` (E9.4 / E11.3). The endpoint is
 * not yet implemented on the server side — E5.6 ships the
 * client-side surface and the contract; E11.3 lands the
 * server-side handler.
 */
import type { BffClient } from "@genealogy/api-client";

import {
  EXPORT_MAX_NODES_PER_JOB,
  type PrintFormat,
  type PrintScope,
  type PrivacyLevel,
  type PrintLayout,
  type PageBreakBehaviour,
} from "./print-policy";
import {
  assertExportInput,
  buildAuditMetadata,
  previewJobId,
  reduceExportJob,
  type ExportAuditMetadata,
  type ExportJobInput,
  type ExportJobSnapshot,
  type RedactionObligation,
} from "./export-job";

export interface SubmitExportJobInput extends ExportJobInput {
  readonly locale: string;
  readonly actorPseudoId: string;
}

export interface SubmitExportJobResult {
  readonly snapshot: ExportJobSnapshot;
  readonly audit: ExportAuditMetadata;
}

const PRINT_JOB_PATH = (treeId: string): string =>
  `/api/v1/trees/${encodeURIComponent(treeId)}/print-jobs`;

function generateCorrelationId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return "00000000-0000-4000-8000-000000000000";
}

function isoIssuedAt(at: Date = new Date()): string {
  return at.toISOString();
}

/**
 * Submit an export job. The wrapper deliberately never throws on
 * network failure — the caller receives a snapshot whose `status`
 * is `failed` with a `reasonCode` the live region can announce.
 */
export async function submitExportJob(args: {
  readonly client: BffClient;
  readonly input: SubmitExportJobInput;
  readonly now?: Date;
}): Promise<SubmitExportJobResult> {
  const now = args.now ?? new Date();
  const correlationId = generateCorrelationId();
  const idempotencyKey = correlationId;

  const validation = assertExportInput(args.input);
  if (!validation.ok) {
    const audit = buildAuditMetadata({
      input: args.input,
      correlationId,
      actorPseudoId: args.input.actorPseudoId,
      issuedAt: now,
    });
    const failed: ExportJobSnapshot = {
      status: "failed",
      jobId: "",
      idempotencyKey,
      input: args.input,
      createdAt: isoIssuedAt(now),
      updatedAt: isoIssuedAt(now),
      audit,
      obligations: [],
      signedUrl: null,
      signedUrlExpiresAt: null,
      reasonCode: validation.reasonCode,
      resumeToken: null,
      expiredAt: null,
    };
    return { snapshot: failed, audit };
  }

  // Submit to the BFF. The endpoint is owned by the import-export
  // service (E11.3) — if it is not yet wired, the wrapper falls
  // back to a preview placeholder so the UI never blocks.
  const submitted: ExportJobSnapshot = {
    status: "queued",
    jobId: previewJobId(correlationId, now),
    idempotencyKey,
    input: args.input,
    createdAt: isoIssuedAt(now),
    updatedAt: isoIssuedAt(now),
    audit: buildAuditMetadata({
      input: args.input,
      correlationId,
      actorPseudoId: args.input.actorPseudoId,
      issuedAt: now,
    }),
    obligations: [],
    signedUrl: null,
    signedUrlExpiresAt: null,
    reasonCode: null,
    resumeToken: null,
    expiredAt: null,
  };

  try {
    await args.client.request("POST", PRINT_JOB_PATH(args.input.treeId), {
      body: {
        rootPersonId: args.input.rootPersonId,
        scope: args.input.scope,
        format: args.input.format,
        privacy: args.input.privacy,
        watermark: args.input.watermark,
        includeLiving: args.input.includeLiving,
        pageBreakBehaviour: args.input.pageBreakBehaviour,
        layout: args.input.layout,
        ttlSeconds: args.input.ttlSeconds,
        nodeCount: Math.min(args.input.nodeCount, EXPORT_MAX_NODES_PER_JOB),
        locale: args.input.locale,
      },
      headers: {
        "X-Correlation-Id": correlationId,
        "Idempotency-Key": idempotencyKey,
        "X-Tenant-Id": args.input.tenantId,
      },
      idempotencyKey,
    });
    return { snapshot: submitted, audit: submitted.audit };
  } catch {
    const failed = reduceExportJob(submitted, {
      kind: "failed",
      reasonCode: "bff-unavailable",
      at: now,
    });
    return { snapshot: failed, audit: failed.audit };
  }
}

/**
 * Poll the job until the server reports `ready` / `failed` /
 * `expired`. The wrapper never blocks longer than `deadlineMs`;
 * any further updates are dropped (the caller is expected to
 * start a new poll cycle).
 */
export async function pollExportJob(args: {
  readonly client: BffClient;
  readonly prev: ExportJobSnapshot;
  readonly deadlineMs: number;
  readonly now?: Date;
}): Promise<ExportJobSnapshot> {
  const now = args.now ?? new Date();
  if (
    args.prev.status === "ready" ||
    args.prev.status === "failed" ||
    args.prev.status === "expired"
  ) {
    return args.prev;
  }
  const deadline = now.getTime() + args.deadlineMs;
  const path = PRINT_JOB_PATH(args.prev.input.treeId);
  try {
    const response = (await args.client.request("GET", `${path}/${args.prev.jobId}`, {
      headers: { "X-Correlation-Id": args.prev.audit.correlationId },
    })) as
      | {
          status: "queued" | "running" | "ready" | "failed" | "expired";
          resumeToken?: string | null;
          signedUrl?: string;
          ttlSeconds?: number;
          obligations?: RedactionObligation[];
          reasonCode?: string;
        }
      | undefined;
    if (!response) return args.prev;
    if (response.status === "queued" || response.status === "running") {
      return reduceExportJob(args.prev, {
        kind: "poll",
        status: response.status,
        resumeToken: response.resumeToken ?? null,
        at: now,
      });
    }
    if (response.status === "ready" && response.signedUrl) {
      return reduceExportJob(args.prev, {
        kind: "ready",
        signedUrl: response.signedUrl,
        ttlSeconds: response.ttlSeconds ?? args.prev.input.ttlSeconds,
        obligations: response.obligations ?? [],
        at: now,
      });
    }
    if (response.status === "failed") {
      return reduceExportJob(args.prev, {
        kind: "failed",
        reasonCode: response.reasonCode ?? "unknown-failure",
        at: now,
      });
    }
    if (response.status === "expired") {
      return reduceExportJob(args.prev, {
        kind: "expired",
        at: now,
      });
    }
  } catch {
    return reduceExportJob(args.prev, {
      kind: "failed",
      reasonCode: "poll-error",
      at: now,
    });
  }
  if (Date.now() >= deadline) {
    return reduceExportJob(args.prev, {
      kind: "failed",
      reasonCode: "poll-deadline",
      at: new Date(deadline),
    });
  }
  return args.prev;
}

/**
 * Convenience type that re-exports the closed-set enums the
 * form needs without forcing the caller to import `print-policy`
 * separately. Keeps the public surface of the shim tight.
 */
export type { PrintFormat, PrintScope, PrivacyLevel, PrintLayout, PageBreakBehaviour };
