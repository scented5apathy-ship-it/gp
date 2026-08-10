#!/usr/bin/env node
/**
 * scripts/lint-tree-renderer-bench.mjs
 *
 * E5.1 deep validator for the tree renderer benchmark
 * contract under `contracts/genealogy/tree-renderer-bench-policy.yaml`
 * and the platform mirror under
 * `platform/helm/genealogy-platform/files/`.
 *
 * Mirrors the structure of `lint-person-merge-config.mjs`:
 *   - parse + structural assertions on `spec.policyId`,
 *     `spec.options`, `spec.sizes`,
 *     `spec.interactionBudgetMs`, `spec.memoryBudgetMb`,
 *     `spec.bundleBudgetKb`, `spec.layoutWorkerEnabled`,
 *     `spec.hybridThresholdNodes`,
 *     `spec.stableNodeIdentityRequired`,
 *     `spec.neighborhoodOnlyRequired`,
 *     `spec.a11yAcceptableScore`, `spec.keyboardAcceptableScore`,
 *     `spec.seedLocale`, `spec.auditClassOnBenchmark`;
 *   - closed-set checks (options ∈ {SVG_VIRTUALIZED,
 *     CANVAS_HIERARCHY, HYBRID}, sizes ∈ {1K,10K,100K,250K},
 *     locale ∈ supported set, audit class ∈
 *     {operational, consent, security});
 *   - numeric invariants (interactionBudgetMs == 2500,
 *     layoutWorkerEnabled == true, stableNodeIdentityRequired
 *     == true, neighborhoodOnlyRequired == true);
 *   - forbidden-token scan;
 *   - chart mirror byte-equality.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration
 * error. Honours `LINT_ROOT` for monorepo-mounted CI.
 */
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, relative, resolve } from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const CONTRACT = join(ROOT, "contracts/genealogy/tree-renderer-bench-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/tree-renderer-bench-policy.yaml",
);

const REQUIRED_OPTIONS = ["SVG_VIRTUALIZED", "CANVAS_HIERARCHY", "HYBRID"];
const REQUIRED_SIZES = ["10K"];
const ALLOWED_SIZES = ["1K", "10K", "100K", "250K"];
const ALLOWED_LOCALES = ["en-US", "vi-VN", "fr-FR", "ja-JP", "zh-Hans"];
const ALLOWED_AUDIT_CLASS = ["operational", "consent", "security"];

const FORBIDDEN_LITERALS = [
  /password\s*[:=]\s*["']?[A-Za-z0-9!@#$%^&*()_+=\-]{6,}/i,
  /token\s*[:=]\s*["']?[A-Za-z0-9._\-]{20,}/i,
  /secret\s*[:=]\s*["']?[A-Za-z0-9._\-]{12,}/i,
  /jdbc:postgresql:\/\/[^"\s']+:[^"\s']+@/i,
  /AKIA[0-9A-Z]{16}/,
  /-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----/,
];

let violations = 0;
function fail(message) {
  console.error(`[tree-renderer-bench] ${message}`);
  violations += 1;
}

function loadContract(path) {
  if (!existsSync(path)) {
    fail(`missing contract file ${relative(ROOT, path)}`);
    return null;
  }
  try {
    const raw = readFileSync(path, "utf8");
    return { raw, parsed: parseYamlLoose(raw) };
  } catch (err) {
    fail(`cannot read ${relative(ROOT, path)}: ${err.message}`);
    return null;
  }
}

/** Minimal YAML loader: key:value, flow lists, nested objects via indentation.
 *  Reuses the parser shape from lint-person-merge-config.mjs to stay
 *  consistent across platform linters. Quoted scalars preserved. */
function parseYamlLoose(raw) {
  const out = {};
  const stack = [{ indent: -1, value: out }];
  const lines = raw.split(/\r?\n/);
  function parseFlowList(text) {
    const inner = text.trim().slice(1, -1).trim();
    if (!inner) return [];
    return inner.split(",").map((s) => stripQuotesValue(s.trim()));
  }
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    if (!line.trim() || line.trim().startsWith("#")) continue;
    const indent = line.match(/^ */)[0].length;
    while (stack.length > 1 && stack[stack.length - 1].indent >= indent) {
      stack.pop();
    }
    const parent = stack[stack.length - 1].value;
    const trimmed = line.trim();
    if (trimmed.startsWith("- ")) {
      const parentTop = stack[stack.length - 1];
      if (!Array.isArray(parentTop.value)) {
        if (Object.keys(parentTop.value).length === 0) {
          const arr = [];
          const parentOfTop = stack.length >= 2 ? stack[stack.length - 2].value : null;
          if (parentOfTop) {
            for (const k of Object.keys(parentOfTop)) {
              if (parentOfTop[k] === parentTop.value) {
                parentOfTop[k] = arr;
                parentTop.value = arr;
                break;
              }
            }
          } else {
            parentTop.value = arr;
          }
        } else {
          continue;
        }
      }
      const itemRaw = trimmed.slice(2).trim();
      const sub = itemRaw.match(/^([A-Za-z0-9_.\-]+)\s*:\s*(.*)$/);
      if (sub) {
        const obj = {};
        obj[sub[1]] = stripQuotesValue(sub[2]);
        parentTop.value.push(obj);
        stack.push({ indent, value: obj });
      } else {
        parentTop.value.push(stripQuotesValue(itemRaw));
      }
      continue;
    }
    const m = trimmed.match(/^([A-Za-z0-9_.\-]+)\s*:\s*(.*)$/);
    if (!m) continue;
    const key = m[1];
    let rhs = m[2];
    if (rhs === "" || rhs === undefined) {
      const next = {};
      parent[key] = next;
      stack.push({ indent, value: next });
      continue;
    }
    if (rhs.startsWith("[") && rhs.endsWith("]")) {
      parent[key] = parseFlowList(rhs);
      continue;
    }
    if (rhs === "true") {
      parent[key] = true;
      continue;
    }
    if (rhs === "false") {
      parent[key] = false;
      continue;
    }
    const numeric = Number(rhs);
    if (!Number.isNaN(numeric) && rhs.trim() !== "") {
      parent[key] = numeric;
      continue;
    }
    parent[key] = stripQuotesValue(rhs);
  }
  return out;
}

function stripQuotesValue(v) {
  if (v === undefined || v === null) return v;
  v = v.trim();
  if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
    return v.slice(1, -1);
  }
  return v;
}

function requireField(parsed, path, fileName) {
  const parts = path.split(".");
  let cur = parsed;
  for (const p of parts) {
    if (cur === undefined || cur === null) {
      fail(`${fileName}: missing required field ${path}`);
      return undefined;
    }
    cur = cur[p];
  }
  if (cur === undefined || cur === null) {
    fail(`${fileName}: missing required field ${path}`);
    return undefined;
  }
  return cur;
}

function assertString(value, expected, field, fileName) {
  if (value !== expected) {
    fail(
      `${fileName}: ${field} must equal ${JSON.stringify(expected)}, got ${JSON.stringify(value)}`,
    );
  }
}

function assertIncludes(set, required, field, fileName) {
  for (const r of required) {
    if (!set.has(r)) {
      fail(`${fileName}: ${field} missing required value ${r}`);
    }
  }
}

function assertClosedSet(values, allowed, field, fileName) {
  for (const v of values) {
    if (!allowed.includes(v)) {
      fail(
        `${fileName}: ${field} contains value ${v} not in closed set ${JSON.stringify(allowed)}`,
      );
    }
  }
}

function scanForbiddenLiterals(raw, fileName) {
  for (const pattern of FORBIDDEN_LITERALS) {
    if (pattern.test(raw)) {
      fail(`${fileName}: forbidden literal matches ${pattern}`);
    }
  }
}

function checkBenchPolicy() {
  const contract = loadContract(CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = relative(ROOT, CONTRACT);

  assertString(parsed.apiVersion, "v1", "apiVersion", fileName);
  assertString(parsed.kind, "TreeRendererBenchPolicy", "kind", fileName);
  if (parsed.metadata) {
    assertString(
      requireField(parsed, "metadata.name", fileName),
      "default-tree-renderer-bench/v1",
      "metadata.name",
      fileName,
    );
    assertString(
      requireField(parsed, "metadata.namespace", fileName),
      "gp-platform",
      "metadata.namespace",
      fileName,
    );
  } else {
    fail(`${fileName}: metadata block is required`);
  }

  assertString(
    requireField(parsed, "spec.policyId", fileName),
    "default-tree-renderer-bench/v1",
    "spec.policyId",
    fileName,
  );

  // Options — closed set, must include all 3 ADR-E0.5-10 candidates.
  const options = requireField(parsed, "spec.options", fileName);
  if (!Array.isArray(options) || options.length === 0) {
    fail(`${fileName}: spec.options must be a non-empty array`);
  } else {
    assertClosedSet(options, REQUIRED_OPTIONS, "spec.options", fileName);
    assertIncludes(new Set(options), REQUIRED_OPTIONS, "spec.options", fileName);
  }

  // Sizes — closed set, 10K mandatory (ADR-E0.5-10 gating dataset).
  const sizes = requireField(parsed, "spec.sizes", fileName);
  if (!Array.isArray(sizes) || sizes.length === 0) {
    fail(`${fileName}: spec.sizes must be a non-empty array`);
  } else {
    assertClosedSet(sizes, ALLOWED_SIZES, "spec.sizes", fileName);
    assertIncludes(new Set(sizes), REQUIRED_SIZES, "spec.sizes", fileName);
  }

  // Numeric invariants pinned to design.md / NFR2.
  const budget = requireField(parsed, "spec.interactionBudgetMs", fileName);
  if (typeof budget !== "number") {
    fail(`${fileName}: spec.interactionBudgetMs must be a number`);
  } else if (budget !== 2500) {
    fail(`${fileName}: spec.interactionBudgetMs must equal 2500 (NFR2)`);
  }

  const memory = requireField(parsed, "spec.memoryBudgetMb", fileName);
  if (typeof memory !== "number" || memory < 16 || memory > 4096) {
    fail(`${fileName}: spec.memoryBudgetMb must be a number in [16,4096]`);
  }

  const bundle = requireField(parsed, "spec.bundleBudgetKb", fileName);
  if (typeof bundle !== "number" || bundle < 32 || bundle > 2048) {
    fail(`${fileName}: spec.bundleBudgetKb must be a number in [32,2048]`);
  }

  // Boolean invariants pinned to design.md §10.2.
  assertString(
    requireField(parsed, "spec.layoutWorkerEnabled", fileName),
    true,
    "spec.layoutWorkerEnabled",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.stableNodeIdentityRequired", fileName),
    true,
    "spec.stableNodeIdentityRequired",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.neighborhoodOnlyRequired", fileName),
    true,
    "spec.neighborhoodOnlyRequired",
    fileName,
  );

  const threshold = requireField(parsed, "spec.hybridThresholdNodes", fileName);
  if (typeof threshold !== "number" || threshold < 1000 || threshold > 100000) {
    fail(`${fileName}: spec.hybridThresholdNodes must be a number in [1000,100000]`);
  }

  const a11y = requireField(parsed, "spec.a11yAcceptableScore", fileName);
  if (typeof a11y !== "number" || a11y < 0 || a11y > 1) {
    fail(`${fileName}: spec.a11yAcceptableScore must be a number in [0,1]`);
  }
  const keyboard = requireField(parsed, "spec.keyboardAcceptableScore", fileName);
  if (typeof keyboard !== "number" || keyboard < 0 || keyboard > 1) {
    fail(`${fileName}: spec.keyboardAcceptableScore must be a number in [0,1]`);
  }

  // Locale + audit class — closed sets.
  const locale = requireField(parsed, "spec.seedLocale", fileName);
  if (typeof locale !== "string" || !ALLOWED_LOCALES.includes(locale)) {
    fail(`${fileName}: spec.seedLocale must be one of ${JSON.stringify(ALLOWED_LOCALES)}`);
  }
  const auditClass = requireField(parsed, "spec.auditClassOnBenchmark", fileName);
  if (typeof auditClass !== "string" || !ALLOWED_AUDIT_CLASS.includes(auditClass)) {
    fail(
      `${fileName}: spec.auditClassOnBenchmark must be one of ${JSON.stringify(ALLOWED_AUDIT_CLASS)}`,
    );
  }

  scanForbiddenLiterals(raw, fileName);
  // NOTE: the JSON schema is a helper for cross-tool validation and
  // is not enforced as part of the lint contract. The YAML contract
  // is the authoritative source-of-truth per AGENTS.md §1.
}

function checkChartMirror() {
  if (!existsSync(CONTRACT)) {
    fail(`source ${relative(ROOT, CONTRACT)} missing`);
    return;
  }
  if (!existsSync(CHART_FILE)) {
    fail(`chart mirror missing at ${relative(ROOT, CHART_FILE)}`);
    return;
  }
  const srcRaw = readFileSync(CONTRACT, "utf8");
  const destRaw = readFileSync(CHART_FILE, "utf8");
  if (srcRaw !== destRaw) {
    fail(
      `chart mirror ${relative(ROOT, CHART_FILE)} is NOT byte-identical to ${relative(ROOT, CONTRACT)}`,
    );
  }
}

function main() {
  checkBenchPolicy();
  checkChartMirror();
  if (violations === 0) {
    console.log("[tree-renderer-bench] OK");
    process.exit(0);
  } else {
    console.error(`[tree-renderer-bench] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();
