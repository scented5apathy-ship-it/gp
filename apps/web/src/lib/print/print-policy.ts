/**
 * apps/web/src/lib/print/print-policy.ts
 *
 * Print/export policy surface for E5.6 (R6.4, R12.4, J-SHARE-3).
 *
 * The policy module owns the closed-set enums the renderer / preview
 * rely on:
 *
 *   - `PRINT_SCOPES` — what subset of the graph the user wants
 *     printed/exported. Closed set so the BFF can never accept an
 *     unknown scope and the linter can enforce parity.
 *   - `PRINT_FORMATS` — output format. Closed set; `PDF` is the
 *     dominant path (Gotenberg per ADR-E0.5-11) but `PNG` /
 *     `JPEG` cover single-frame export of one node.
 *   - `WATERMARK_MODES` — watermark obligations the BFF must add
 *     before returning a signed URL. Required for any non-private
 *     scope (R15 + glossary §2.2 obligation table row "W").
 *   - `PAGE_BREAK_BEHAVIOURS` — how the rendered content splits
 *     across pages. Determinism matters: the server must emit the
 *     same break plan given the same input.
 *   - `PRINT_LAYOUTS` — paper size + orientation. Server-side
 *     `@page` rules pin these; UI uses them to choose the preview
 *     CSS class.
 *   - `EXPORT_JOB_STATUSES` — lifecycle of the asynchronous job
 *     (Temporal/Gotenberg) — exposed in the UI live region.
 *
 * All values are plain string literal unions so the BFF contract
 * (E5.x) and the UI catalogues can share the same closed set
 * without `enum` boxing.
 */

export const PRINT_SCOPES = [
  "currentPerson",
  "subtree",
  "ancestors",
  "descendants",
  "family",
] as const;
export type PrintScope = (typeof PRINT_SCOPES)[number];

export function isPrintScope(value: unknown): value is PrintScope {
  return typeof value === "string" && (PRINT_SCOPES as ReadonlyArray<string>).includes(value);
}

export const PRINT_FORMATS = ["PDF", "PNG", "JPEG"] as const;
export type PrintFormat = (typeof PRINT_FORMATS)[number];

export function isPrintFormat(value: unknown): value is PrintFormat {
  return typeof value === "string" && (PRINT_FORMATS as ReadonlyArray<string>).includes(value);
}

export const WATERMARK_MODES = [
  "none",
  "tenant-id",
  "token-hash",
  "tenant-id-and-token-hash",
] as const;
export type WatermarkMode = (typeof WATERMARK_MODES)[number];

export function isWatermarkMode(value: unknown): value is WatermarkMode {
  return typeof value === "string" && (WATERMARK_MODES as ReadonlyArray<string>).includes(value);
}

export const PAGE_BREAK_BEHAVIOURS = ["per-generation", "per-node", "single-page"] as const;
export type PageBreakBehaviour = (typeof PAGE_BREAK_BEHAVIOURS)[number];

export const PRINT_LAYOUTS = [
  "A4-portrait",
  "A4-landscape",
  "Letter-portrait",
  "Letter-landscape",
] as const;
export type PrintLayout = (typeof PRINT_LAYOUTS)[number];

export const EXPORT_JOB_STATUSES = [
  "idle",
  "queued",
  "running",
  "ready",
  "expired",
  "failed",
] as const;
export type ExportJobStatus = (typeof EXPORT_JOB_STATUSES)[number];

export function isExportJobStatus(value: unknown): value is ExportJobStatus {
  return (
    typeof value === "string" && (EXPORT_JOB_STATUSES as ReadonlyArray<string>).includes(value)
  );
}

/**
 * Default watermark for a given scope + privacy mix. `PRIVATE` and
 * `UNLISTED` scopes never carry a watermark (per glossary §2.2 row
 * "W" → only `W` allowed for PUBLIC/SHARED, never PRIVATE). The
 * preview surfaces the chosen mode so the user can verify the
 * obligation before the BFF issues the signed URL.
 */
export const PRIVACY_LEVELS = ["PUBLIC", "UNLISTED", "PRIVATE"] as const;
export type PrivacyLevel = (typeof PRIVACY_LEVELS)[number];

export function isPrivacyLevel(value: unknown): value is PrivacyLevel {
  return typeof value === "string" && (PRIVACY_LEVELS as ReadonlyArray<string>).includes(value);
}

export function resolveDefaultWatermark(
  privacy: PrivacyLevel,
  hasShareToken: boolean,
): WatermarkMode {
  if (privacy === "PRIVATE") return "none";
  if (privacy === "UNLISTED") return hasShareToken ? "token-hash" : "none";
  // PUBLIC — always carry at least the tenant id; add token hash
  // when the user is sharing via a token.
  return hasShareToken ? "tenant-id-and-token-hash" : "tenant-id";
}

/**
 * Signed URL TTL bounds. The linter verifies every E5.6 surface
 * clamps the TTL inside this window (R12.4 / `personas-and-
 * journeys.md` J-SHARE-3 "Failure path: … expired signed URL").
 *
 *   - min: 60s — anything shorter makes the round-trip pointless.
 *   - max: 24h — anything longer violates the J-SHARE-3 success
 *     metric window (24h = 86 400s).
 */
export const SIGNED_URL_TTL_MIN_SECONDS = 60;
export const SIGNED_URL_TTL_MAX_SECONDS = 24 * 60 * 60;

export function clampSignedUrlTtlSeconds(value: number): number {
  if (!Number.isFinite(value)) return SIGNED_URL_TTL_MIN_SECONDS;
  if (value < SIGNED_URL_TTL_MIN_SECONDS) return SIGNED_URL_TTL_MIN_SECONDS;
  if (value > SIGNED_URL_TTL_MAX_SECONDS) return SIGNED_URL_TTL_MAX_SECONDS;
  return Math.floor(value);
}

/**
 * Hard cap on how many nodes a single export job may include.
 * R6.3 ("never load the full graph to the browser") mirrors here:
 * a tree larger than the cap must be paginated via `E5.6.exportJob`
 * `resumeToken` rather than re-issued in full.
 */
export const EXPORT_MAX_NODES_PER_JOB = 1_000;

export function clampExportNodeCount(value: number): number {
  if (!Number.isFinite(value) || value < 1) return 1;
  if (value > EXPORT_MAX_NODES_PER_JOB) return EXPORT_MAX_NODES_PER_JOB;
  return Math.floor(value);
}

/**
 * Map a `PrintLayout` onto a CSS `@page` size + margin literal. The
 * print stylesheet imports these constants so the preview matches
 * what Gotenberg will rasterise.
 */
export const PRINT_LAYOUT_CSS: Readonly<Record<PrintLayout, { size: string; margin: string }>> = {
  "A4-portrait": { size: "A4 portrait", margin: "14mm 14mm 18mm 14mm" },
  "A4-landscape": { size: "A4 landscape", margin: "14mm 18mm 14mm 14mm" },
  "Letter-portrait": { size: "Letter portrait", margin: "0.5in 0.5in 0.75in 0.5in" },
  "Letter-landscape": { size: "Letter landscape", margin: "0.5in 0.75in 0.5in 0.5in" },
};

/**
 * Compose a deterministic, audit-friendly label for the preview
 * header. The label is *not* a watermark (the watermark is the
 * obligation added by the BFF); it is a header that the BFF and
 * the preview both stamp so the export is traceable end-to-end.
 */
export function composeExportHeader(args: {
  readonly tenantId: string;
  readonly treeId: string;
  readonly rootPersonId: string;
  readonly scope: PrintScope;
  readonly format: PrintFormat;
  readonly watermark: WatermarkMode;
  readonly issuedAt: Date;
}): string {
  return [
    args.tenantId,
    args.treeId,
    args.rootPersonId,
    args.scope,
    args.format,
    args.watermark,
    args.issuedAt.toISOString(),
  ].join("·");
}
