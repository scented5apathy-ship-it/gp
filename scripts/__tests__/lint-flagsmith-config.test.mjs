#!/usr/bin/env node
/**
 * scripts/__tests__/lint-flagsmith-config.test.mjs
 *
 * Unit tests for `scripts/lint-flagsmith-config.mjs` (E2.8).
 *
 * Strategy: drive the linter with `LINT_ROOT` pointed at a
 * tempdir that mirrors `platform/featureflags/`. Assert:
 *   1. The shipped config passes.
 *   2. A `flagsmith-server.yaml` whose serverImage is not
 *      pinned fails.
 *   3. A `environments.yaml` that drops the `audit`
 *      environment fails.
 *   4. A `flag-taxonomy.yaml` that drops the
 *      `legal.dna.enabled` flag fails.
 *   5. A `flag-taxonomy.yaml` that adds a forbidden
 *      `skip_auth` flag fails.
 *   6. A `safe-defaults.yaml` that drops the
 *      `tenant_pseudo_id` required attribute fails.
 *   7. A `sdk-config.yaml` that disables the drift check
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
const SCRIPT = join(ROOT, "scripts", "lint-flagsmith-config.mjs");

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
  const dir = mkdtempSync(join(tmpdir(), "flagsmith-"));
  mkdirSync(join(dir, "platform", "featureflags"), { recursive: true });
  mkdirSync(join(dir, "platform", "helm", "genealogy-platform", "files", "featureflags"), {
    recursive: true,
  });
  copyTree(join(ROOT, "platform", "featureflags"), join(dir, "platform", "featureflags"));
  const filesDir = join(dir, "platform", "featureflags");
  const mirrorDir = join(dir, "platform", "helm", "genealogy-platform", "files", "featureflags");
  for (const f of [
    "flagsmith-server.yaml",
    "environments.yaml",
    "flag-taxonomy.yaml",
    "safe-defaults.yaml",
    "sdk-config.yaml",
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
  const src = join(dir, "platform", "featureflags", file);
  const dst = join(dir, "platform", "helm", "genealogy-platform", "files", "featureflags", file);
  writeFileSync(dst, readFileSync(src, "utf8"));
}

test("flagsmith: shipped config passes", () => {
  const dir = makeFixture();
  try {
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(
      proc.status,
      0,
      `expected exit 0, got ${proc.status}\nstdout=${proc.stdout}\nstderr=${proc.stderr}`,
    );
    assert.match(proc.stdout, /\[flagsmith\] clean/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("flagsmith: server-image not pinned fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "featureflags", "flagsmith-server.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = doc.data["config.yaml"];
    doc.data["config.yaml"] = inner.replace(
      /serverImage:\s*"flagsmith\/flagsmith:2\.139\.4"/,
      'serverImage: "flagsmith/flagsmith:latest"',
    );
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "flagsmith-server.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /must pin serverImage to/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("flagsmith: environments.yaml drops audit env fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "featureflags", "environments.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["environments.yaml"]);
    inner.environments = inner.environments.filter((e) => e.id !== "audit");
    doc.data["environments.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "environments.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /missing required environment 'audit'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("flagsmith: flag-taxonomy.yaml drops legal.dna.enabled fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "featureflags", "flag-taxonomy.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["flags.yaml"]);
    inner.flags = inner.flags.filter((f) => f.key !== "legal.dna.enabled");
    doc.data["flags.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "flag-taxonomy.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /missing required legal-gate flag 'legal\.dna\.enabled'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("flagsmith: flag-taxonomy.yaml adds forbidden skip_auth flag fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "featureflags", "flag-taxonomy.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["flags.yaml"]);
    inner.flags.push({
      key: "feature.skip_auth",
      type: "boolean",
      safeDefault: false,
      owner: "test",
      whenTrue: "skip auth",
      whenFalse: "default",
      expiresOn: "2027-12-31",
      audit: false,
      scope: ["production"],
      legalGate: false,
      dataClass: "OPS.METADATA",
    });
    doc.data["flags.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "flag-taxonomy.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /matches forbidden pattern 'skip\.\*auth'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("flagsmith: safe-defaults.yaml drops tenant_pseudo_id fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "featureflags", "safe-defaults.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["safe-defaults.yaml"]);
    inner.safeDefaults.evaluationContext.requiredAttributes =
      inner.safeDefaults.evaluationContext.requiredAttributes.filter(
        (a) => a !== "tenant_pseudo_id",
      );
    doc.data["safe-defaults.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "safe-defaults.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /requiredAttributes must include 'tenant_pseudo_id'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("flagsmith: sdk-config.yaml disables drift check fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "featureflags", "sdk-config.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["bootstrap.yaml"]);
    inner.bootstrap.driftCheck.enabled = false;
    doc.data["bootstrap.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "sdk-config.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /must enable bootstrap\.driftCheck/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});