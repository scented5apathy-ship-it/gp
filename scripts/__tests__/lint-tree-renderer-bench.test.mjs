/**
 * scripts/__tests__/lint-tree-renderer-bench.test.mjs
 *
 * Unit tests for `scripts/lint-tree-renderer-bench.mjs`.
 *
 * Strategy: write a temporary contract file under a temp
 * directory, set `LINT_ROOT` to that directory, run the linter
 * against the contract, and assert exit code + violation count
 * via `node:test`. The chart mirror is also exercised so the
 * byte-equality check is covered end-to-end.
 *
 * Each test isolates state by creating a fresh temp dir under
 * `node:os.tmpdir()` and cleaning up in `finally`. The valid
 * case reuses the canonical contract under
 * `contracts/genealogy/tree-renderer-bench-policy.yaml` as the
 * `LINT_ROOT` so the test doubles as a "contract is parseable"
 * assertion on every CI run.
 */
import test from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import {
  mkdtempSync,
  writeFileSync,
  mkdirSync,
  rmSync,
  readFileSync as readFileSyncSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const REPO_ROOT = resolve(__dirname, "..", "..");

const LINTER = join(REPO_ROOT, "scripts", "lint-tree-renderer-bench.mjs");
const VALID_CONTRACT = join(REPO_ROOT, "contracts", "genealogy", "tree-renderer-bench-policy.yaml");
const VALID_CHART = join(
  REPO_ROOT,
  "platform",
  "helm",
  "genealogy-platform",
  "files",
  "tree-renderer-bench-policy.yaml",
);
const VALID_SCHEMA = join(
  REPO_ROOT,
  "contracts",
  "genealogy",
  "tree-renderer-bench-policy.schema.json",
);

function runLinter(root) {
  return spawnSync("node", [LINTER], {
    cwd: root,
    env: { ...process.env, LINT_ROOT: root },
    encoding: "utf8",
  });
}

function writeContractTree(root, contractBody, chartBody) {
  const contractDir = join(root, "contracts", "genealogy");
  const chartDir = join(root, "platform", "helm", "genealogy-platform", "files");
  mkdirSync(contractDir, { recursive: true });
  mkdirSync(chartDir, { recursive: true });
  writeFileSync(join(contractDir, "tree-renderer-bench-policy.yaml"), contractBody);
  writeFileSync(join(chartDir, "tree-renderer-bench-policy.yaml"), chartBody);
}

function mkTempRoot() {
  return mkdtempSync(join(tmpdir(), "lint-trb-"));
}

function cleanup(root) {
  rmSync(root, { recursive: true, force: true });
}

const VALID_BODY = `apiVersion: v1
kind: TreeRendererBenchPolicy
metadata:
  name: default-tree-renderer-bench/v1
  namespace: gp-platform
spec:
  policyId: default-tree-renderer-bench/v1
  description: ok
  options:
    - SVG_VIRTUALIZED
    - CANVAS_HIERARCHY
    - HYBRID
  sizes:
    - 1K
    - 10K
    - 100K
  interactionBudgetMs: 2500
  memoryBudgetMb: 256
  bundleBudgetKb: 180
  layoutWorkerEnabled: true
  hybridThresholdNodes: 5000
  stableNodeIdentityRequired: true
  neighborhoodOnlyRequired: true
  a11yAcceptableScore: 0.6
  keyboardAcceptableScore: 0.6
  seedLocale: vi-VN
  auditClassOnBenchmark: operational
`;

test("real repo contract + chart mirror lints clean", () => {
  const result = runLinter(REPO_ROOT);
  assert.equal(
    result.status,
    0,
    `expected linter to pass; got:\n${result.stderr}\n${result.stdout}`,
  );
  assert.match(result.stdout, /tree-renderer-bench.*OK/);
});

test("valid minimal contract passes", () => {
  const root = mkTempRoot();
  try {
    writeContractTree(root, VALID_BODY, VALID_BODY);
    const result = runLinter(root);
    assert.equal(
      result.status,
      0,
      `expected linter to pass; got:\n${result.stderr}\n${result.stdout}`,
    );
  } finally {
    cleanup(root);
  }
});

test("interactionBudgetMs must equal 2500", () => {
  const root = mkTempRoot();
  try {
    const bad = VALID_BODY.replace("interactionBudgetMs: 2500", "interactionBudgetMs: 3000");
    writeContractTree(root, bad, bad);
    const result = runLinter(root);
    assert.equal(result.status, 1, `expected linter to fail; got:\n${result.stdout}`);
    assert.match(result.stderr, /interactionBudgetMs must equal 2500/);
  } finally {
    cleanup(root);
  }
});

test("missing SVG_VIRTUALIZED option fails", () => {
  const root = mkTempRoot();
  try {
    const bad = VALID_BODY.replace(
      "  options:\n    - SVG_VIRTUALIZED\n    - CANVAS_HIERARCHY\n    - HYBRID\n",
      "  options:\n    - CANVAS_HIERARCHY\n    - HYBRID\n",
    );
    writeContractTree(root, bad, bad);
    const result = runLinter(root);
    assert.equal(result.status, 1);
    assert.match(result.stderr, /spec.options missing required value SVG_VIRTUALIZED/);
  } finally {
    cleanup(root);
  }
});

test("missing 10K size fails (ADR-E0.5-10 gating dataset)", () => {
  const root = mkTempRoot();
  try {
    const bad = VALID_BODY.replace(
      "  sizes:\n    - 1K\n    - 10K\n    - 100K\n",
      "  sizes:\n    - 1K\n    - 100K\n",
    );
    writeContractTree(root, bad, bad);
    const result = runLinter(root);
    assert.equal(result.status, 1);
    assert.match(result.stderr, /spec.sizes missing required value 10K/);
  } finally {
    cleanup(root);
  }
});

test("layoutWorkerEnabled=false is rejected (design.md §10.2)", () => {
  const root = mkTempRoot();
  try {
    const bad = VALID_BODY.replace("layoutWorkerEnabled: true", "layoutWorkerEnabled: false");
    writeContractTree(root, bad, bad);
    const result = runLinter(root);
    assert.equal(result.status, 1);
    assert.match(result.stderr, /spec.layoutWorkerEnabled must equal true/);
  } finally {
    cleanup(root);
  }
});

test("neighborhoodOnlyRequired=false is rejected", () => {
  const root = mkTempRoot();
  try {
    const bad = VALID_BODY.replace(
      "neighborhoodOnlyRequired: true",
      "neighborhoodOnlyRequired: false",
    );
    writeContractTree(root, bad, bad);
    const result = runLinter(root);
    assert.equal(result.status, 1);
    assert.match(result.stderr, /spec.neighborhoodOnlyRequired must equal true/);
  } finally {
    cleanup(root);
  }
});

test("unsupported seedLocale is rejected", () => {
  const root = mkTempRoot();
  try {
    const bad = VALID_BODY.replace("seedLocale: vi-VN", "seedLocale: xx-XX");
    writeContractTree(root, bad, bad);
    const result = runLinter(root);
    assert.equal(result.status, 1);
    assert.match(result.stderr, /spec.seedLocale must be one of/);
  } finally {
    cleanup(root);
  }
});

test("chart mirror byte-mismatch fails", () => {
  const root = mkTempRoot();
  try {
    writeContractTree(root, VALID_BODY, VALID_BODY + "\n# trailing drift\n");
    const result = runLinter(root);
    assert.equal(result.status, 1);
    assert.match(result.stderr, /NOT byte-identical/);
  } finally {
    cleanup(root);
  }
});

test("forbidden literal (AWS key id) fails", () => {
  const root = mkTempRoot();
  try {
    const bad = VALID_BODY.replace(
      "description: ok",
      "description: ok # AKIAIOSFODNN7EXAMPLE leaked",
    );
    writeContractTree(root, bad, bad);
    const result = runLinter(root);
    assert.equal(result.status, 1);
    assert.match(result.stderr, /forbidden literal matches/);
  } finally {
    cleanup(root);
  }
});

test("JSON schema file exists at canonical path and is valid JSON", () => {
  const res = spawnSync("test", ["-f", VALID_SCHEMA]);
  assert.equal(res.status, 0, `expected ${VALID_SCHEMA} to exist`);
  const parsed = JSON.parse(readFileSyncSync(VALID_SCHEMA, "utf8"));
  assert.equal(parsed.title, "TreeRendererBenchPolicy");
});
