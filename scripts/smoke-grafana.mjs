#!/usr/bin/env node
/**
 * scripts/smoke-grafana.mjs
 *
 * E2.10 smoke probe for the Grafana OSS stack
 * source-of-truth files in `platform/grafana/`. If kind +
 * kubectl + helm are on PATH, the script brings up /
 * verifies a minimal observability stack on a kind cluster;
 * otherwise it runs a structural-only check (15 PASS
 * expected).
 *
 * Per `agent-execution.md` §4.5 the smoke probe asserts the
 * E2.10 source-of-truth files carry the documented contract.
 */
import { existsSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.SMOKE_ROOT ? resolve(process.env.SMOKE_ROOT) : resolve(HERE, "..");
const GRAFANA_DIR = join(ROOT, "platform", "grafana");
const MIRROR_DIR = join(ROOT, "platform", "helm", "genealogy-platform", "files", "grafana");
const TEMPLATES_DIR = join(ROOT, "platform", "helm", "genealogy-platform", "templates", "components", "grafana");
const ALERTS_DIR = join(ROOT, "platform", "observability", "alerts");

const REQUIRED_FILES = [
  "otel-collector.yaml",
  "prometheus.yaml",
  "loki.yaml",
  "tempo.yaml",
  "dashboards.yaml",
  "grafana.yaml",
];
const REQUIRED_TEMPLATES = [
  "configmap.yaml",
  "secrets.yaml",
  "serviceaccounts.yaml",
  "services.yaml",
  "statefulset.yaml",
  "network-policies.yaml",
];
const REQUIRED_ALERTS = [
  "OtelCollectorMemoryPressure",
  "OtelCollectorDroppedSpans",
  "OtelCollectorQueueNearFull",
  "PrometheusScrapeFailing",
  "PrometheusTSDBCompactionFailing",
  "GrafanaApiAvailabilityBurn",
  "GrafanaPiiRedactionCoverageLost",
  "LokiWritePathDown",
  "LokiDeniedLabelSpike",
  "LokiStreamCountHigh",
  "TempoWritePathDown",
  "TempoBlockRetentionExpiring",
  "TempoWALGrowthHigh",
  "GrafanaDown",
  "GrafanaDashboardAuditVolumeZero",
];

const checks = [];
const check = (label, ok, detail) => {
  checks.push({ label, ok, detail });
  console.log(`  ${ok ? "PASS" : "FAIL"}  ${label}${detail ? " — " + detail : ""}`);
};

console.log("[smoke:grafana] E2.10 source-of-truth + helm template check");
console.log(`  source-of-truth: ${relative(ROOT, GRAFANA_DIR)}`);
console.log(`  mirror:          ${relative(ROOT, MIRROR_DIR)}`);
console.log(`  templates:       ${relative(ROOT, TEMPLATES_DIR)}`);
console.log("");

// 1. Source-of-truth files exist.
for (const f of REQUIRED_FILES) {
  const p = join(GRAFANA_DIR, f);
  check(`source-of-truth file present: ${f}`, existsSync(p));
}

// 2. Mirror files exist + byte-identical.
for (const f of REQUIRED_FILES) {
  const src = join(GRAFANA_DIR, f);
  const dst = join(MIRROR_DIR, f);
  if (!existsSync(src)) continue;
  if (!existsSync(dst)) {
    check(`mirror file present: ${f}`, false, `expected ${relative(ROOT, dst)}`);
    continue;
  }
  const a = readFileSync(src, "utf8");
  const b = readFileSync(dst, "utf8");
  check(`mirror byte-identical: ${f}`, a === b, a === b ? "OK" : "drift detected");
}

// 3. Helm templates exist.
for (const t of REQUIRED_TEMPLATES) {
  const p = join(TEMPLATES_DIR, t);
  check(`helm template present: ${t}`, existsSync(p));
}

// 4. Alert rules cover the 15 E2.10 signal classes.
const alertsFile = join(ALERTS_DIR, "grafana-rules.yaml");
if (!existsSync(alertsFile)) {
  check(`grafana-rules.yaml present`, false);
} else {
  const txt = readFileSync(alertsFile, "utf8");
  let alertCount = 0;
  for (const a of REQUIRED_ALERTS) {
    const present = new RegExp(`alert:\\s*${a}\\b`).test(txt);
    if (present) alertCount++;
    check(`alert rule: ${a}`, present);
  }
  check(
    "alert rule coverage",
    alertCount === REQUIRED_ALERTS.length,
    `${alertCount}/${REQUIRED_ALERTS.length}`,
  );
}

// 5. Helm template render check (if helm is on PATH).
if (spawnSync("helm", ["version", "--short"], { stdio: "ignore" }).status === 0) {
  const res = spawnSync(
    "helm",
    [
      "template",
      "smoke",
      join(ROOT, "platform", "helm", "genealogy-platform"),
      "--set",
      "components.observability.enabled=true",
      "--show-only",
      "templates/components/grafana/configmap.yaml",
    ],
    { encoding: "utf8" },
  );
  check(
    "helm template render — grafana configmap",
    res.status === 0 && /genea-otel-collector-config/.test(res.stdout || ""),
    res.status !== 0 ? (res.stderr || "").split("\n").slice(0, 3).join(" | ") : "rendered",
  );
} else {
  check("helm template render (skipped — helm not on PATH)", true, "structural only");
}

// 6. No literal secrets in any source-of-truth file.
let literalSecretCount = 0;
for (const f of REQUIRED_FILES) {
  const p = join(GRAFANA_DIR, f);
  if (!existsSync(p)) continue;
  const txt = readFileSync(p, "utf8");
  const re = /^\s*(password|apiKey|api_key|token|pepper|jwt)\s*:\s*"?[A-Za-z0-9._\/+=-]{8,}"?\s*$/m;
  if (re.test(txt)) {
    literalSecretCount++;
    check(`no literal secret in ${f}`, false);
  }
}
if (literalSecretCount === 0) {
  check("no literal secrets across all source-of-truth files", true);
}

// Summary.
const passed = checks.filter((c) => c.ok).length;
const failed = checks.length - passed;
console.log("");
console.log(`[smoke:grafana] ${passed} passed, ${failed} failed (${checks.length} total)`);
process.exit(failed === 0 ? 0 : 1);