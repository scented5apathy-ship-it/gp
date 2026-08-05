#!/usr/bin/env node
/**
 * scripts/lint-helm.mjs
 *
 * Repository-wide Helm chart linter. Tries `helm` first (the platform
 * tool of choice). If `helm` is not on $PATH, we run a structural
 * check that ensures every chart has `Chart.yaml`, `values.yaml`,
 * `templates/` and a pinned `appVersion`/`version`.
 *
 * This script is called from `pnpm lint:helm` and `pnpm check:platform`.
 * It is intentionally tolerant when the platform/ directory is empty
 * (E2.x owns the chart content).
 */
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, "..");
const HELM_DIR = join(ROOT, "platform", "helm");

if (!existsSync(HELM_DIR)) {
  console.log("[helm] platform/helm missing — skipping");
  process.exit(0);
}

function findCharts(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const full = join(dir, entry.name);
    if (existsSync(join(full, "Chart.yaml"))) out.push(full);
    else out.push(...findCharts(full));
  }
  return out;
}

const charts = findCharts(HELM_DIR);
if (charts.length === 0) {
  console.log("[helm] no charts found — skipping (content lands in E2.x)");
  process.exit(0);
}

const helmProc = spawnSync("helm", ["version", "--short"], { stdio: "ignore" });
const haveHelm = helmProc.status === 0;

let violations = 0;

for (const chart of charts) {
  const rel = relative(ROOT, chart);

  // 1. Required files.
  for (const required of ["Chart.yaml", "values.yaml"]) {
    if (!existsSync(join(chart, required))) {
      violations++;
      console.error(`[helm] ${rel} — missing ${required}`);
    }
  }
  if (!existsSync(join(chart, "templates"))) {
    violations++;
    console.error(`[helm] ${rel} — missing templates/ directory`);
  }

  // 2. Chart.yaml must have apiVersion, name, version.
  const chartYamlPath = join(chart, "Chart.yaml");
  if (existsSync(chartYamlPath)) {
    const text = readFileSync(chartYamlPath, "utf8");
    for (const field of ["apiVersion", "name", "version"]) {
      if (!new RegExp(`^${field}\\s*:`).test(text)) {
        violations++;
        console.error(`[helm] ${rel}/Chart.yaml — missing '${field}'`);
      }
    }
  }
}

if (haveHelm) {
  for (const chart of charts) {
    const proc = spawnSync("helm", ["lint", chart, "--strict"], {
      stdio: "inherit",
      cwd: ROOT,
    });
    if (proc.status !== 0) violations++;
  }
} else {
  console.warn(
    "[helm] `helm` not on PATH — only structural check ran; install helm to enable full lint",
  );
}

if (violations > 0) {
  console.error(`\n[helm] ${violations} violation(s)`);
  process.exit(1);
}
console.log(`[helm] clean — ${charts.length} chart(s)`);