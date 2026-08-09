#!/usr/bin/env node
/**
 * scripts/__tests__/lint-temporal-config.test.mjs
 *
 * Unit tests for `scripts/lint-temporal-config.mjs` (E2.4).
 *
 * Strategy: drive the linter with `LINT_ROOT` pointed at a tempdir
 * that mirrors `platform/temporal/`. Assert:
 *   1. The shipped config passes.
 *   2. A `search-attrs.yaml` that drops the `TenantId` field fails.
 *   3. A `namespace-config.yaml` that declares a namespace outside
 *      the whitelist (`genea-rogue`) fails.
 *   4. A `task-queues.yaml` that drops the `genea-dna-match` queue
 *      fails.
 *   5. A `dynamic-config.yaml` whose `genea-dna` retention is not
 *      365d fails.
 *   6. A `search-attrs.yaml` that omits the `Email` forbidden name
 *      fails.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  existsSync,
  mkdtempSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import YAML from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const SCRIPT = join(ROOT, "scripts", "lint-temporal-config.mjs");

function runScript(env) {
  return spawnSync(process.execPath, [SCRIPT], {
    cwd: ROOT,
    env: { ...process.env, ...env },
    encoding: "utf8",
  });
}

function copyTree(from, to) {
  mkdirSync(to, { recursive: true });
  for (const entry of readdirSync(from)) {
    const src = join(from, entry);
    const dst = join(to, entry);
    if (statSync(src).isDirectory()) {
      copyTree(src, dst);
    } else {
      const text = readFileSync(src, "utf8");
      mkdirSync(dirname(dst), { recursive: true });
      writeFileSync(dst, text);
    }
  }
}

function makeFixture() {
  const dir = mkdtempSync(join(tmpdir(), "temporal-"));
  mkdirSync(join(dir, "platform", "temporal", "schemas"), { recursive: true });
  mkdirSync(join(dir, "platform", "helm", "genealogy-platform", "files", "temporal"), {
    recursive: true,
  });
  copyTree(join(ROOT, "platform", "temporal"), join(dir, "platform", "temporal"));
  // Mirror every file into the chart's files/temporal/ directory
  // so the source/mirror invariant does not falsely fail when the
  // test only edits the source-of-truth.
  const filesDir = join(dir, "platform", "temporal");
  const mirrorDir = join(dir, "platform", "helm", "genealogy-platform", "files", "temporal");
  for (const f of [
    "namespace-config.yaml",
    "search-attrs.yaml",
    "dynamic-config.yaml",
    "task-queues.yaml",
  ]) {
    const src = join(filesDir, f);
    if (existsSync(src)) {
      const dst = join(mirrorDir, f);
      mkdirSync(dirname(dst), { recursive: true });
      writeFileSync(dst, readFileSync(src, "utf8"));
    }
  }
  return dir;
}

function syncMirror(dir, file) {
  // After mutating a source-of-truth file, copy the new content
  // into the chart's files/temporal/ mirror so the source/mirror
  // invariant does not fire during the test.
  const src = join(dir, "platform", "temporal", file);
  const dst = join(dir, "platform", "helm", "genealogy-platform", "files", "temporal", file);
  writeFileSync(dst, readFileSync(src, "utf8"));
}

test("temporal: shipped config passes", () => {
  const dir = makeFixture();
  try {
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(
      proc.status,
      0,
      `expected exit 0, got ${proc.status}\nstdout=${proc.stdout}\nstderr=${proc.stderr}`,
    );
    assert.match(proc.stdout, /\[temporal\] clean/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("temporal: search-attrs.yaml missing TenantId fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "temporal", "search-attrs.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["schema.yaml"]);
    inner.schema = inner.schema.filter((e) => e.name !== "TenantId");
    doc.data["schema.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "search-attrs.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /missing visibility attribute 'TenantId'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("temporal: namespace-config.yaml unknown namespace fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "temporal", "namespace-config.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["policy.yaml"]);
    inner.namespaces = inner.namespaces.filter((n) => n.name !== "genea-dna");
    doc.data["policy.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "namespace-config.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /missing namespace 'genea-dna'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("temporal: task-queues.yaml missing genea-dna-match fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "temporal", "task-queues.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["policy.yaml"]);
    inner.queues = inner.queues.filter((q) => q.name !== "genea-dna-match");
    doc.data["policy.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "task-queues.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /missing required queue 'genea-dna-match'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("temporal: dynamic-config.yaml wrong genea-dna retention fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "temporal", "dynamic-config.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["config.yaml"]);
    inner.system.visibility.attribute = inner.system.visibility.attribute.filter(
      (a) => a.name !== "ConsentId",
    );
    doc.data["config.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "dynamic-config.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /visibility\.attribute list must cover all/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("temporal: search-attrs.yaml missing forbidden Email fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "temporal", "search-attrs.yaml");
    const text = readFileSync(p, "utf8").replace(
      / {6}- name: Email\n {8}reason: raw email forbidden\n/,
      "",
    );
    writeFileSync(p, text);
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /missing forbidden name 'Email'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
