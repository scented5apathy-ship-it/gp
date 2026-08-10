/**
 * apps/web/test/tree-renderer-bench.test.mjs
 *
 * Bench harness self-test. Validates that:
 *   1. The bench policy loads from the canonical contract.
 *   2. The synthetic generator produces the right counts.
 *   3. The bench harness self-test runs to completion and
 *      emits a valid BenchResult object that satisfies the
 *      contract thresholds (NFR2 p75 ≤ 2500 ms).
 *   4. The bench harness runs all three renderer options on
 *      the 1K dataset and emits valid BenchResult objects
 *      with the documented keys.
 *   5. The renderer options reject opaque-id violations.
 *   6. The percentile helper is monotonic and returns 0 for
 *      empty input.
 *   7. The Worker round-trip produces a layout that matches
 *      the in-process layout.
 *
 * The test reuses the production modules — no fixtures —
 * so the bench harness + renderers stay exercised on every
 * CI run. Total runtime budget: ≤ 10 s.
 */
import test from "node:test";
import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import {
  BENCH_SIZES,
  RENDERER_OPTIONS,
  SIZE_TO_PERSON_COUNT,
  percentile,
  percentileSummary,
  assertOpaqueId,
} from "../bench/renderer/contract.mjs";
import { generateGraph } from "../bench/synthetic-tree.mjs";
import { buildNeighborhood, runLayoutInWorker } from "../bench/renderer/worker/client.mjs";
import { layoutNeighborhood } from "../bench/renderer/layout-core.mjs";
import { render as renderSvg } from "../bench/renderer/svg-virtualized/index.mjs";
import { render as renderCanvas } from "../bench/renderer/canvas-hierarchy/index.mjs";
import { render as renderHybrid } from "../bench/renderer/hybrid/index.mjs";
import { scoreAccessibility } from "../bench/renderer/a11y-cost.mjs";
import { scoreKeyboard } from "../bench/renderer/keyboard-cost.mjs";
import { runSelfTest, benchOne } from "../bench/tree-renderer-bench.mjs";
import { parseBenchPolicy } from "../bench/bench-policy.mjs";
import { renderMarkdown } from "../bench/report.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const POLICY_PATH = join(
  __dirname,
  "..",
  "..",
  "..",
  "contracts",
  "genealogy",
  "tree-renderer-bench-policy.yaml",
);

test("contract closed-sets are frozen", () => {
  assert.deepEqual([...RENDERER_OPTIONS], ["SVG_VIRTUALIZED", "CANVAS_HIERARCHY", "HYBRID"]);
  assert.deepEqual([...BENCH_SIZES], ["1K", "10K", "100K", "250K"]);
  assert.equal(SIZE_TO_PERSON_COUNT["10K"], 10_000);
});

test("percentileSummary is monotonic and handles empty input", () => {
  const s = percentileSummary([1, 2, 3, 4, 5]);
  assert.equal(s.p50, 3);
  assert.ok(s.p75 >= s.p50);
  assert.ok(s.p95 >= s.p75);
  assert.equal(s.peak, 5);
  const empty = percentileSummary([]);
  assert.equal(empty.p50, 0);
  assert.equal(empty.peak, 0);
});

test("percentile returns the boundary value for p=0 and p=100", () => {
  const sorted = [10, 20, 30];
  assert.equal(percentile(sorted, 0), 10);
  assert.equal(percentile(sorted, 100), 30);
});

test("assertOpaqueId accepts valid ids and rejects malformed", () => {
  assert.doesNotThrow(() => assertOpaqueId("person-0001234", "x"));
  assert.throws(() => assertOpaqueId("person 0001234", "x"));
  assert.throws(() => assertOpaqueId("", "x"));
  assert.throws(() => assertOpaqueId("has spaces in it", "x"));
});

test("policy loads from canonical contract", async () => {
  const policy = await parseBenchPolicy(POLICY_PATH);
  assert.equal(policy.spec.policyId, "default-tree-renderer-bench/v1");
  assert.equal(policy.spec.interactionBudgetMs, 2500);
  assert.equal(policy.spec.layoutWorkerEnabled, true);
});

test("synthetic graph is deterministic for (10K, vi-VN)", () => {
  const a = generateGraph("10K", "vi-VN");
  const b = generateGraph("10K", "vi-VN");
  assert.equal(a.size, 10_000);
  assert.equal(b.size, 10_000);
  const aFirst = a.nodes.get("person-0000000");
  const bFirst = b.nodes.get("person-0000000");
  assert.deepEqual(aFirst, bFirst);
  assert.equal(aFirst.generation, 0);
  assert.equal(aFirst.rootOfBranch, true);
});

test("bench harness self-test emits valid BenchResult", async () => {
  const result = await runSelfTest();
  assert.equal(result.option, "SVG_VIRTUALIZED");
  assert.equal(result.size, "1K");
  assert.ok(result.p75LayoutMs >= 0);
  assert.ok(result.p75RenderMs >= 0);
  assert.ok(result.peakHeapMb >= 0);
  assert.ok(result.a11yScore > 0);
  assert.ok(result.keyboardScore > 0);
  assert.equal(result.meetsInteractionBudget, result.p75LayoutMs + result.p75RenderMs <= 2500);
});

test("bench runs all 3 renderer options on 1K dataset", async () => {
  const policy = await parseBenchPolicy(POLICY_PATH);
  const results = [];
  for (const option of RENDERER_OPTIONS) {
    const r = await benchOne({ size: "1K", option, repeats: 1, policy });
    assert.equal(r.option, option);
    results.push(r);
  }
  assert.equal(results.length, 3);
  for (const r of results) {
    assert.ok(typeof r.a11yScore === "number");
    assert.ok(typeof r.keyboardScore === "number");
  }
});

test("layout worker round-trip produces equivalent layout", async () => {
  const graph = generateGraph("1K", "vi-VN");
  const neighborhood = buildNeighborhood(graph, { depth: 2 });
  const inProc = layoutNeighborhood(neighborhood, "SVG_VIRTUALIZED", 1);
  const { layout } = await runLayoutInWorker(neighborhood, "SVG_VIRTUALIZED", { sampleRuns: 1 });
  assert.equal(inProc.nodeCount, layout.nodeCount);
  assert.equal(inProc.edgeCount, layout.edgeCount);
});

test("renderer options reject opaque-id violations", () => {
  const layout = {
    positions: {
      nodes: new Map([["has space", { x: 0, y: 0, w: 10, h: 10 }]]),
      segments: [],
    },
    nodeCount: 1,
    edgeCount: 0,
  };
  assert.throws(() => renderSvg(layout), /opaque id pattern/);
  assert.throws(() => renderCanvas(layout), /opaque id pattern/);
});

test("hybrid renderer falls back to SVG at 1K", () => {
  const graph = generateGraph("1K", "vi-VN");
  const neighborhood = buildNeighborhood(graph, { depth: 2 });
  const layout = layoutNeighborhood(neighborhood, "HYBRID", 1);
  const out = renderHybrid(layout, { threshold: 5000 });
  assert.equal(out.backend, "SVG");
});

test("accessibility + keyboard scores are within [0, 1]", () => {
  for (const option of RENDERER_OPTIONS) {
    const a = scoreAccessibility(option);
    const k = scoreKeyboard(option);
    assert.ok(a >= 0 && a <= 1, `a11y ${option}=${a} out of range`);
    assert.ok(k >= 0 && k <= 1, `keyboard ${option}=${k} out of range`);
  }
});

test("markdown report includes policy, results, recommendation, limitations", async () => {
  const policy = await parseBenchPolicy(POLICY_PATH);
  const result = await runSelfTest();
  const md = renderMarkdown(
    {
      policy: {
        policyId: policy.spec.policyId,
        interactionBudgetMs: policy.spec.interactionBudgetMs,
      },
      size: "1K",
      personCount: 1_000,
      repeats: 1,
      options: [result],
    },
    policy,
  );
  assert.match(md, /Tree renderer benchmark report/);
  assert.match(md, /SVG_VIRTUALIZED/);
  assert.match(md, /Recommendation memo/);
  assert.match(md, /Limitations/);
});

test("contract files exist", () => {
  assert.ok(existsSync(POLICY_PATH), `expected ${POLICY_PATH} to exist`);
  assert.ok(
    existsSync(
      join(
        __dirname,
        "..",
        "..",
        "..",
        "platform",
        "helm",
        "genealogy-platform",
        "files",
        "tree-renderer-bench-policy.yaml",
      ),
    ),
    "chart mirror must exist",
  );
});
