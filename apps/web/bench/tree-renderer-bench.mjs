#!/usr/bin/env node
/**
 * apps/web/bench/tree-renderer-bench.mjs
 *
 * E5.1 tree renderer benchmark harness. Compares the three
 * ADR-E0.5-10 candidate options (SVG_VIRTUALIZED,
 * CANVAS_HIERARCHY, HYBRID) on the synthetic 10K / 100K
 * datasets (per `scale-and-slo.md` §3), records layout time
 * (inside a real `node:worker_threads` worker), render time,
 * heap peak, serialized payload size, keyboard and
 * accessibility heuristic scores, and emits both JSON and
 * Markdown reports.
 *
 * The harness is intentionally **not** a Playwright run —
 * Playwright + on-device mid-tier mobile profiling lands in
 * E5.3 with the editor milestone (per E1.5 evidence line 268).
 * The Node bench measures the layout / worker-boundary /
 * render cost; the recommendation memo inside the Markdown
 * report flags the limitations explicitly.
 *
 * Usage:
 *   node apps/web/bench/tree-renderer-bench.mjs \
 *     --size 10K --options SVG_VIRTUALIZED,CANVAS_HIERARCHY,HYBRID \
 *     --report /tmp/report.md
 *
 *   node apps/web/bench/tree-renderer-bench.mjs \
 *     --size 10K --self-test
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { generateGraph } from "./synthetic-tree.mjs";
import {
  BENCH_SIZES,
  RENDERER_OPTIONS,
  SIZE_TO_PERSON_COUNT,
  percentileSummary,
} from "./renderer/contract.mjs";
import { layoutNeighborhood } from "./renderer/layout-core.mjs";
import { render as renderSvg } from "./renderer/svg-virtualized/index.mjs";
import { render as renderCanvas } from "./renderer/canvas-hierarchy/index.mjs";
import { render as renderHybrid } from "./renderer/hybrid/index.mjs";
import { scoreAccessibility } from "./renderer/a11y-cost.mjs";
import { scoreKeyboard } from "./renderer/keyboard-cost.mjs";
import { buildNeighborhood, runLayoutInWorker } from "./renderer/worker/client.mjs";
import { parseBenchPolicy } from "./bench-policy.mjs";
import { renderMarkdown } from "./report.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const DEFAULT_POLICY = join(
  __dirname,
  "..",
  "..",
  "..",
  "contracts",
  "genealogy",
  "tree-renderer-bench-policy.yaml",
);

const RENDERERS = {
  SVG_VIRTUALIZED: { layout: layoutNeighborhood, render: renderSvg },
  CANVAS_HIERARCHY: { layout: layoutNeighborhood, render: renderCanvas },
  HYBRID: { layout: layoutNeighborhood, render: renderHybrid },
};

/**
 * Run one (option, size) combination `repeats` times and
 * collect every metric into a `BenchResult`.
 *
 * @param {{size:string, option:string, repeats:number, policy:any}} args
 */
export async function benchOne({ size, option, repeats, policy }) {
  if (!BENCH_SIZES.includes(size)) {
    throw new Error(`unsupported size ${size}`);
  }
  if (!RENDERER_OPTIONS.includes(option)) {
    throw new Error(`unsupported option ${option}`);
  }
  const graph = generateGraph(size, policy.spec.seedLocale);
  const neighborhood = buildNeighborhood(graph, { depth: 4 });

  const layoutSamples = [];
  const renderSamples = [];
  const heapSamples = [];
  const payloadBytes = [];
  let payload = 0;

  for (let i = 0; i < repeats; i += 1) {
    const layoutStart = process.hrtime.bigint();
    const { layout } = await runLayoutInWorker(neighborhood, option, {
      sampleRuns: 1,
      timeoutMs: 60_000,
    });
    const layoutEnd = process.hrtime.bigint();

    const renderStart = process.hrtime.bigint();
    const renderer = RENDERERS[option];
    const output = renderer.render(layout, {
      threshold: policy.spec.hybridThresholdNodes,
    });
    const renderEnd = process.hrtime.bigint();

    const mem = process.memoryUsage();
    layoutSamples.push(Number(layoutEnd - layoutStart) / 1e6);
    renderSamples.push(Number(renderEnd - renderStart) / 1e6);
    heapSamples.push(mem.heapUsed / 1e6);
    payload = output.bytes ?? Buffer.byteLength(output.svg ?? output.commands ?? "", "utf8");
    payloadBytes.push(payload);
  }

  const layoutSummary = percentileSummary(layoutSamples);
  const renderSummary = percentileSummary(renderSamples);
  const heapSummary = percentileSummary(heapSamples);
  const payloadSummary = percentileSummary(payloadBytes);

  const p75LayoutMs = layoutSummary.p75 + renderSummary.p75;
  const meetsInteractionBudget = p75LayoutMs <= policy.spec.interactionBudgetMs;
  const meetsMemoryBudget = heapSummary.peak <= policy.spec.memoryBudgetMb;
  const meetsBundleBudget = payloadSummary.peak / 1024 <= policy.spec.bundleBudgetKb;

  return {
    option,
    size,
    nodes: neighborhood.nodes.length,
    samples: layoutSamples.length,
    p50LayoutMs: layoutSummary.p50,
    p75LayoutMs: layoutSummary.p75,
    p95LayoutMs: layoutSummary.p95,
    p50RenderMs: renderSummary.p50,
    p75RenderMs: renderSummary.p75,
    p95RenderMs: renderSummary.p95,
    peakHeapMb: heapSummary.peak,
    payloadBytes: payloadSummary.peak,
    a11yScore: scoreAccessibility(option),
    keyboardScore: scoreKeyboard(option),
    meetsInteractionBudget,
    meetsMemoryBudget,
    meetsBundleBudget,
    rawSamples: layoutSamples.map((layout, i) => ({
      layoutMs: layout,
      renderMs: renderSamples[i],
      heapMb: heapSamples[i],
      bytes: payloadBytes[i],
    })),
  };
}

function parseArgs(argv) {
  const args = {
    size: "10K",
    options: ["SVG_VIRTUALIZED", "CANVAS_HIERARCHY", "HYBRID"],
    repeats: 5,
    report: null,
    selfTest: false,
    policyPath: DEFAULT_POLICY,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (token === "--size") {
      args.size = argv[++i];
    } else if (token === "--options") {
      args.options = argv[++i]
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
    } else if (token === "--repeats") {
      args.repeats = Number(argv[++i]);
    } else if (token === "--report") {
      args.report = argv[++i];
    } else if (token === "--self-test") {
      args.selfTest = true;
    } else if (token === "--policy") {
      args.policyPath = argv[++i];
    } else if (token === "--format") {
      args.format = argv[++i];
    }
  }
  return args;
}

/**
 * Quick self-test used by `apps/web/test/tree-renderer-bench.test.mjs`
 * and CI smoke runs. Runs `repeats=1` for one (size=1K,
 * option=SVG_VIRTUALIZED) combination, asserts the bench
 * machinery works, and returns the `BenchResult` to the
 * caller without writing any file.
 */
export async function runSelfTest() {
  const policy = await parseBenchPolicy(DEFAULT_POLICY);
  return benchOne({ size: "1K", option: "SVG_VIRTUALIZED", repeats: 1, policy });
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.selfTest) {
    const result = await runSelfTest();
    console.log(JSON.stringify(result, null, 2));
    return;
  }
  const policy = await parseBenchPolicy(args.policyPath);
  /** @type {Array<any>} */
  const results = [];
  for (const option of args.options) {
    process.stderr.write(`[tree-renderer-bench] ${args.size} ${option} …\n`);
    const result = await benchOne({
      size: args.size,
      option,
      repeats: args.repeats,
      policy,
    });
    results.push(result);
  }
  const payload = {
    policy: {
      policyId: policy.spec.policyId,
      interactionBudgetMs: policy.spec.interactionBudgetMs,
    },
    size: args.size,
    personCount: SIZE_TO_PERSON_COUNT[args.size],
    repeats: args.repeats,
    options: results,
  };
  if (args.report) {
    const outPath = resolve(__dirname, args.report);
    mkdirSync(dirname(outPath), { recursive: true });
    const md = renderMarkdown(payload, policy);
    writeFileSync(outPath, md);
    process.stderr.write(`[tree-renderer-bench] wrote ${outPath}\n`);
  }
  console.log(JSON.stringify(payload, null, 2));
}

const isMain = process.argv[1] && resolve(process.argv[1]) === resolve(__filename);
if (isMain) {
  main().catch((err) => {
    console.error("[tree-renderer-bench] failed:", err);
    process.exit(1);
  });
}
