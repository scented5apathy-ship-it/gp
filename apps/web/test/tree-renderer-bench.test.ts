/**
 * apps/web/test/tree-renderer-bench.test.ts
 *
 * TypeScript surface for the bench harness self-tests. The
 * authoritative tests live in
 * `apps/web/test/tree-renderer-bench.test.mjs` (run by Node's
 * built-in `node:test` runner). This file exists so the
 * `apps/web` typecheck pipeline picks up the bench harness
 * types when iterating on the renderer surface.
 *
 * It re-exports a few types only — no runtime code — so it
 * cannot drift out of sync with the mjs version. The mjs
 * version remains the source of truth for the actual tests.
 */
export type BenchSize = "1K" | "10K" | "100K" | "250K";
export type RendererOption = "SVG_VIRTUALIZED" | "CANVAS_HIERARCHY" | "HYBRID";

export interface BenchPolicySpec {
  readonly policyId: string;
  readonly options: readonly RendererOption[];
  readonly sizes: readonly BenchSize[];
  readonly interactionBudgetMs: number;
  readonly memoryBudgetMb: number;
  readonly bundleBudgetKb: number;
  readonly layoutWorkerEnabled: true;
  readonly hybridThresholdNodes: number;
  readonly stableNodeIdentityRequired: true;
  readonly neighborhoodOnlyRequired: true;
  readonly a11yAcceptableScore: number;
  readonly keyboardAcceptableScore: number;
  readonly seedLocale: "en-US" | "vi-VN" | "fr-FR" | "ja-JP" | "zh-Hans";
  readonly auditClassOnBenchmark: "operational" | "consent" | "security";
}

export interface BenchSample {
  readonly layoutMs: number;
  readonly renderMs: number;
  readonly heapMb: number;
  readonly bytes: number;
}

export interface BenchResult {
  readonly option: RendererOption;
  readonly size: BenchSize;
  readonly nodes: number;
  readonly samples: number;
  readonly p50LayoutMs: number;
  readonly p75LayoutMs: number;
  readonly p95LayoutMs: number;
  readonly p50RenderMs: number;
  readonly p75RenderMs: number;
  readonly p95RenderMs: number;
  readonly peakHeapMb: number;
  readonly payloadBytes: number;
  readonly a11yScore: number;
  readonly keyboardScore: number;
  readonly meetsInteractionBudget: boolean;
  readonly meetsMemoryBudget: boolean;
  readonly meetsBundleBudget: boolean;
  readonly rawSamples: readonly BenchSample[];
}

export const __BENCH_HARNESS_TYPES_ONLY__: unique symbol = Symbol(
  "apps/web/test/tree-renderer-bench.test.ts: types-only surface",
);
