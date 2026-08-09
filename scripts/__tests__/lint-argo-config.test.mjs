#!/usr/bin/env node
/**
 * scripts/__tests__/lint-argo-config.test.mjs
 *
 * Unit tests for the E2.9 Argo CD + Argo Rollouts deep
 * validator (`scripts/lint-argo-config.mjs`). The test
 * harness writes ephemeral fixtures under a temp dir,
 * sets `LINT_ROOT=<tmp>`, and asserts exit 0 on clean
 * fixtures + exit 1 on each documented violation.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import { mkdtempSync, writeFileSync, readFileSync, mkdirSync, copyFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, "..", "..");
const SCRIPT = join(ROOT, "scripts", "lint-argo-config.mjs");
const FIXTURE_SRC = join(ROOT, "platform", "argo");
const FIXTURE_MIRROR = join(ROOT, "platform", "helm", "genealogy-platform", "files", "argo");

function setupCleanTree() {
  const tmp = mkdtempSync(join(tmpdir(), "argo-lint-"));
  mkdirSync(join(tmp, "platform", "argo"), { recursive: true });
  mkdirSync(join(tmp, "platform", "helm", "genealogy-platform", "files", "argo"), { recursive: true });
  for (const f of [
    "argocd-server.yaml",
    "projects.yaml",
    "applications.yaml",
    "rollout-strategy.yaml",
    "sync-windows.yaml",
  ]) {
    copyFileSync(join(FIXTURE_SRC, f), join(tmp, "platform", "argo", f));
    copyFileSync(join(FIXTURE_SRC, f), join(tmp, "platform", "helm", "genealogy-platform", "files", "argo", f));
  }
  return tmp;
}

function runLint(tmp, mutate) {
  if (mutate) {
    mutate(tmp);
  }
  const res = spawnSync("node", [SCRIPT], {
    env: { ...process.env, LINT_ROOT: tmp },
    encoding: "utf8",
  });
  return res;
}

function readFixture(tmp, name) {
  return join(tmp, "platform", "argo", name);
}

test("clean repo passes", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp);
    assert.equal(res.status, 0, res.stderr);
    assert.match(res.stdout, /\[argo\] clean/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("argocd-server.yaml missing rolloutsController fails", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp, (t) => {
      const p = readFixture(t, "argocd-server.yaml");
      const text = readFileSync(p, "utf8");
      // Remove the `rolloutsController:` block (6-space indent) until
      // the next sibling at the same indent (controller:).
      const stripped = text.replace(
        /\n      rolloutsController:\n(?:        .*\n)*/,
        "\n",
      );
      writeFileSync(p, stripped);
    });
    assert.equal(res.status, 1, "lint should fail");
    assert.match(res.stderr, /rolloutsController/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("argocd-server.yaml with anonymousEnabled: true fails", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp, (t) => {
      const p = readFixture(t, "argocd-server.yaml");
      const text = readFileSync(p, "utf8");
      writeFileSync(p, text.replace("anonymousEnabled: false", "anonymousEnabled: true"));
    });
    assert.equal(res.status, 1);
    assert.match(res.stderr, /anonymousEnabled/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("projects.yaml missing four-eyes principle fails", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp, (t) => {
      const p = readFixture(t, "projects.yaml");
      const text = readFileSync(p, "utf8");
      writeFileSync(p, text.replace("fourEyesPrinciple: true", "fourEyesPrinciple: false"));
    });
    assert.equal(res.status, 1);
    assert.match(res.stderr, /fourEyesPrinciple/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("applications.yaml missing production promotion fails", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp, (t) => {
      const p = readFixture(t, "applications.yaml");
      const text = readFileSync(p, "utf8");
      writeFileSync(p, text.replace(/requiresMfa: true\n        promotionWindow:/, "promotionWindow:"));
    });
    assert.equal(res.status, 1);
    assert.match(res.stderr, /production promotion/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("rollout-strategy.yaml missing AnalysisTemplate 'saturation' fails", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp, (t) => {
      const p = readFixture(t, "rollout-strategy.yaml");
      const text = readFileSync(p, "utf8");
      const stripped = text.replace(/- name: saturation[\s\S]*?(?=\n    serviceClasses:)/, "");
      writeFileSync(p, stripped);
    });
    assert.equal(res.status, 1);
    assert.match(res.stderr, /saturation/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("sync-windows.yaml with raw email audit field fails", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp, (t) => {
      const p = readFixture(t, "sync-windows.yaml");
      const text = readFileSync(p, "utf8");
      writeFileSync(p, text.replace("- actor_pseudo_id", "- actor_pseudo_id\n        - email"));
    });
    assert.equal(res.status, 1);
    assert.match(res.stderr, /email/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});
