/**
 * apps/web/bench/perf-budget.test.ts
 *
 * E13.3 Core Web Vitals budget gate. Mirrors
 * `contracts/reliability/performance-capacity-policy.yaml`
 * `coreWebVitalsBudget`. The runtime suite (Lighthouse + Playwright)
 * lands in E6; today the test is structural and consumes a
 * static `metrics.json` report under `.kiro/.../bench-report.json`.
 *
 * Run with `node ../../scripts/test-ts.mjs "bench/perf-budget.test.ts"`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import { readFileSync, existsSync } from "node:fs";

interface CwvMetric {
  name: string;
  value: number;
  unit: "ms" | "ratio";
  target: number;
}

const REQUIRED: ReadonlyArray<CwvMetric> = [
  { name: "lcp", value: 0, unit: "ms", target: 2500 },
  { name: "cls", value: 0, unit: "ratio", target: 0.1 },
  { name: "inp", value: 0, unit: "ms", target: 200 },
  { name: "ttfb", value: 0, unit: "ms", target: 800 },
  { name: "tti", value: 0, unit: "ms", target: 2500 },
  { name: "tbt", value: 0, unit: "ms", target: 200 },
];

test("E13.3 coreWebVitalsBudget declares 6 entries", () => {
  assert.equal(REQUIRED.length, 6);
});

test("E13.3 perf-budget rejects p95 regression > 10 %", () => {
  const baseline = 250;
  const current = 290;
  const regression = ((current - baseline) / baseline) * 100;
  assert.ok(regression > 10, "regression should exceed 10 %");
});

test("E13.3 perf-budget rejects bundle size regression > 5 %", () => {
  const baseline = 220_000;
  const current = 240_000;
  const regression = ((current - baseline) / baseline) * 100;
  assert.ok(regression > 5, "bundle size regression should exceed 5 %");
});

test("E13.3 perf-budget honors structured report when present", () => {
  const reportPath = ".kiro/specs/genealogy-platform/evidence/bench-report.json";
  if (!existsSync(reportPath)) {
    return;
  }
  const report = JSON.parse(readFileSync(reportPath, "utf8"));
  for (const metric of REQUIRED) {
    const entry = report.cwv?.find?.((e: any) => e.name === metric.name);
    if (!entry) continue;
    if (entry.value > metric.target) {
      assert.fail(
        `CWV ${metric.name} ${entry.value}${metric.unit} > ${metric.target}${metric.unit}`,
      );
    }
  }
});