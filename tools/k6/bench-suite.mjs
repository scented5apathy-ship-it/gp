#!/usr/bin/env node
/**
 * tools/k6/bench-suite.mjs
 *
 * E13.3 k6 benchmark driver. Mirrors
 * `contracts/reliability/performance-capacity-policy.yaml`
 * (`workloadClasses` + `benchmarkScenarios`).
 *
 * This wrapper script is the contract gate: it validates the
 * expected JSON report shape produced by k6 and asserts the
 * p95 / errorRate thresholds declared in the contract. The
 * actual k6 invocation lives in
 * `tools/k6/bench-suite.k6.js` (generated from this file or
 * hand-authored); here we only consume the JSON report.
 *
 * Exit codes:
 *   0 - all workload classes passed
 *   1 - one or more thresholds failed
 *   2 - configuration error
 *
 * The contract linter `scripts/lint-performance-capacity.mjs`
 * asserts the workload closed-set; this script asserts the
 * runtime numbers.
 */
import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.BENCH_ROOT
  ? resolve(process.env.BENCH_ROOT)
  : resolve(HERE, "..", "..");

const REQUIRED_WORKLOAD_CLASSES = [
  "browse_tree", "search", "detail_read",
  "write_proposal", "media_upload", "async_job",
];

const REQUIRED_THRESHOLDS = {
  browse_tree: { p95Ms: 300, errorRate: 0.01 },
  search: { p95Ms: 1000, errorRate: 0.01 },
  detail_read: { p95Ms: 300, errorRate: 0.01 },
  write_proposal: { p95Ms: 600, errorRate: 0.005 },
  media_upload: { p95Ms: 2000, errorRate: 0.005 },
  async_job: { p95Ms: 15000, errorRate: 0.001 },
};

const REPORT_PATH = process.env.BENCH_REPORT
  || join(ROOT, ".kiro", "specs", "genealogy-platform",
    "evidence", "bench-report.json");

function loadReport() {
  if (!existsSync(REPORT_PATH)) {
    console.error(`missing benchmark report at ${REPORT_PATH}`);
    process.exit(2);
  }
  return JSON.parse(readFileSync(REPORT_PATH, "utf8"));
}

function main() {
  const report = loadReport();
  let failures = 0;
  const oks = [];
  for (const wc of REQUIRED_WORKLOAD_CLASSES) {
    const entry = report.workloadClasses?.find?.((e) => e.name === wc);
    if (!entry) {
      console.error(`FAIL: missing workload class ${wc}`);
      failures += 1;
      continue;
    }
    const threshold = REQUIRED_THRESHOLDS[wc];
    if (entry.p95Ms > threshold.p95Ms) {
      console.error(
        `FAIL: ${wc} p95 ${entry.p95Ms}ms > ${threshold.p95Ms}ms`,
      );
      failures += 1;
    } else {
      oks.push(`${wc} p95 ${entry.p95Ms}ms <= ${threshold.p95Ms}ms`);
    }
    if (entry.errorRate > threshold.errorRate) {
      console.error(
        `FAIL: ${wc} errorRate ${entry.errorRate} > ${threshold.errorRate}`,
      );
      failures += 1;
    } else {
      oks.push(`${wc} errorRate ${entry.errorRate} <= ${threshold.errorRate}`);
    }
  }
  if (failures === 0) {
    console.log(`E13.3 bench-suite: OK`);
    for (const line of oks) console.log(`    ✓ ${line}`);
    process.exit(0);
  } else {
    console.error(`E13.3 bench-suite: FAIL (${failures} violations, ${oks.length} passed)`);
    process.exit(1);
  }
}

main();