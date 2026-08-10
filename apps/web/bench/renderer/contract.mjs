/**
 * apps/web/bench/renderer/contract.mjs
 *
 * Shared data contracts for the tree renderer benchmark
 * (E5.1 / ADR-E0.5-10 closure input). Pure JS, no React, no
 * DOM. Consumed by the three renderer option modules under
 * `svg-virtualized/`, `canvas-hierarchy/`, `hybrid/` and by the
 * synthetic-tree generator under `apps/web/bench/`.
 *
 * Design rules:
 *   - `PersonNode` carries **opaque ids only** (personId,
 *     treeId, tenantId). No biography, name value, DNA or
 *     identifier value leaks through the bench surface.
 *   - `PersonGraph` is an adjacency map keyed by `personId`
 *     (stable identity per `design.md` §10.2). Children are
 *     `[personId, personId]` pairs of (parent, child).
 *   - `Neighborhood` is a depth-bounded slice returned by the
 *     (mock) tree projection API used by the bench.
 *   - `BenchResult` mirrors `contracts/genealogy/
 *     tree-renderer-bench-policy.yaml` thresholds so the
 *     reporter can fail the build when the chosen option
 *     misses NFR2 / design.md §10.2 budgets.
 *
 * Pure ESM module; importable from both Node bench code and
 * browser Worker code (no Node-only globals).
 */

/**
 * @typedef {("SVG_VIRTUALIZED"|"CANVAS_HIERARCHY"|"HYBRID")} RendererOption
 */

/**
 * @typedef {Object} PersonNode
 * @property {string} personId      Stable identity (kept across re-renders).
 * @property {string} treeId        Opaque tree id.
 * @property {string} tenantId      Opaque tenant id (redaction scope).
 * @property {number} generation    0 = root, positive = descendant depth.
 * @property {boolean} rootOfBranch True for the synthetic pivot ancestor.
 */

/**
 * @typedef {Object} PersonEdge
 * @property {string} parentId  Opaque parent personId.
 * @property {string} childId   Opaque child personId.
 */

/**
 * @typedef {Object} PersonGraph
 * @property {string} tenantId      Opaque tenant id.
 * @property {string} treeId        Opaque tree id.
 * @property {number} size          Person count (informational).
 * @property {Map<string, PersonNode>} nodes  Map keyed by personId.
 * @property {PersonEdge[]} edges              All parent → child edges.
 */

/**
 * @typedef {Object} Neighborhood
 * @property {string} rootPersonId          Pivot for the viewport.
 * @property {string} direction             "DESCENDANTS" | "ANCESTORS" | "BOTH".
 * @property {number} depth                 Maximum depth to fetch.
 * @property {PersonNode[]} nodes           Subset of graph nodes.
 * @property {PersonEdge[]} edges           Subset of graph edges.
 */

/**
 * @typedef {Object} BenchSample
 * @property {number} layoutMs     Wall-clock time for the layout pass.
 * @property {number} renderMs     Wall-clock time for the render pass.
 * @property {number} heapMb       process.memoryUsage().heapUsed / 1e6.
 * @property {number} bytes        Serialized render artefact size (bytes).
 */

/**
 * @typedef {Object} BenchResult
 * @property {RendererOption} option
 * @property {string} size                  "1K" | "10K" | "100K" | "250K".
 * @property {number} nodes                 Number of nodes in the neighbourhood.
 * @property {BenchSample[]} samples
 * @property {number} p50LayoutMs
 * @property {number} p75LayoutMs
 * @property {number} p95LayoutMs
 * @property {number} p50RenderMs
 * @property {number} p75RenderMs
 * @property {number} p95RenderMs
 * @property {number} peakHeapMb
 * @property {number} payloadBytes
 * @property {number} a11yScore
 * @property {number} keyboardScore
 * @property {boolean} meetsInteractionBudget
 * @property {boolean} meetsMemoryBudget
 * @property {boolean} meetsBundleBudget
 */

/** Identifier pattern per design.md §10.2 — opaque, no PII. */
const ID_PATTERN = /^[A-Za-z0-9._:-]{1,128}$/;

/**
 * Asserts an opaque id follows the project pattern. Throws a
 * `TypeError` when malformed; used as a guard at every bench
 * boundary so accidental PII (email / phone / DNA) never slips
 * into the renderer surface.
 * @param {string} value
 * @param {string} label
 */
export function assertOpaqueId(value, label) {
  if (typeof value !== "string" || !ID_PATTERN.test(value)) {
    throw new TypeError(
      `${label} must match opaque id pattern ${ID_PATTERN}, got ${JSON.stringify(value)}`,
    );
  }
}

/**
 * Picks the `p` percentile of `samples` (already sorted ascending).
 * Uses the linear-interpolation method (NIST type-7 R6) so the
 * numbers match the production observability stack.
 * @param {readonly number[]} sorted
 * @param {number} p in [0, 100]
 * @returns {number}
 */
export function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  if (p <= 0) return sorted[0];
  if (p >= 100) return sorted[sorted.length - 1];
  const rank = (p / 100) * (sorted.length - 1);
  const lo = Math.floor(rank);
  const hi = Math.ceil(rank);
  if (lo === hi) return sorted[lo];
  const weight = rank - lo;
  return sorted[lo] * (1 - weight) + sorted[hi] * weight;
}

/**
 * Computes the p50/p75/p95 of `values` without mutating caller
 * data. Returns the trio + the peak absolute value (used for
 * memory peak aggregation across samples).
 * @param {readonly number[]} values
 */
export function percentileSummary(values) {
  const sorted = [...values].sort((a, b) => a - b);
  return {
    p50: percentile(sorted, 50),
    p75: percentile(sorted, 75),
    p95: percentile(sorted, 95),
    peak: sorted.length === 0 ? 0 : sorted[sorted.length - 1],
  };
}

/** Closed-set of renderer options mirrored from the contract. */
export const RENDERER_OPTIONS = Object.freeze(["SVG_VIRTUALIZED", "CANVAS_HIERARCHY", "HYBRID"]);
/** Closed-set of supported dataset sizes. */
export const BENCH_SIZES = Object.freeze(["1K", "10K", "100K", "250K"]);
/** Numeric sizes — person count per dataset. 1K is reserved for the
 *  placeholder canvas cap documented in ADR-E0.5-10 §Consequences. */
export const SIZE_TO_PERSON_COUNT = Object.freeze({
  "1K": 1_000,
  "10K": 10_000,
  "100K": 100_000,
  "250K": 250_000,
});
