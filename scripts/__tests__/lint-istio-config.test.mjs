#!/usr/bin/env node
/**
 * scripts/__tests__/lint-istio-config.test.mjs
 *
 * Unit tests for `scripts/lint-istio-config.mjs` (E2.5).
 *
 * Strategy: drive the linter with `LINT_ROOT` pointed at a
 * tempdir that mirrors `platform/istio/`. Assert:
 *   1. The shipped config passes.
 *   2. A `mesh-config.yaml` whose `outboundTrafficPolicy.mode`
 *      is not `REGISTRY_ONLY` fails.
 *   3. A `peer-auth.yaml` that drops a namespace fails.
 *   4. An `authz-policies.yaml` that drops the
 *      `dna-service-egress-deny` block fails.
 *   5. A `telemetry.yaml` whose `mesh.retryBudget` is no
 *      longer `null` fails.
 *   6. A `telemetry.yaml` whose `accesslog.format` is not
 *      `JSON` fails.
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
const SCRIPT = join(ROOT, "scripts", "lint-istio-config.mjs");

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
  const dir = mkdtempSync(join(tmpdir(), "istio-"));
  mkdirSync(join(dir, "platform", "istio"), { recursive: true });
  mkdirSync(join(dir, "platform", "helm", "genealogy-platform", "files", "istio"), {
    recursive: true,
  });
  copyTree(join(ROOT, "platform", "istio"), join(dir, "platform", "istio"));
  const filesDir = join(dir, "platform", "istio");
  const mirrorDir = join(dir, "platform", "helm", "genealogy-platform", "files", "istio");
  for (const f of [
    "mesh-config.yaml",
    "peer-auth.yaml",
    "authz-policies.yaml",
    "telemetry.yaml",
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
  const src = join(dir, "platform", "istio", file);
  const dst = join(dir, "platform", "helm", "genealogy-platform", "files", "istio", file);
  writeFileSync(dst, readFileSync(src, "utf8"));
}

test("istio: shipped config passes", () => {
  const dir = makeFixture();
  try {
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(
      proc.status,
      0,
      `expected exit 0, got ${proc.status}\nstdout=${proc.stdout}\nstderr=${proc.stderr}`,
    );
    assert.match(proc.stdout, /\[istio\] clean/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("istio: mesh-config.yaml wrong outboundTrafficPolicy.mode fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "istio", "mesh-config.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["mesh.yaml"]);
    inner.outboundTrafficPolicy.mode = "ALLOW_ANY";
    doc.data["mesh.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "mesh-config.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /outboundTrafficPolicy\.mode must be 'REGISTRY_ONLY'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("istio: peer-auth.yaml missing namespace fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "istio", "peer-auth.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["policy.yaml"]);
    inner.peerAuthentications = inner.peerAuthentications.filter(
      (p) => p.namespace !== "gp-data",
    );
    doc.data["policy.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "peer-auth.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /missing PeerAuthentication for namespace 'gp-data'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("istio: authz-policies.yaml missing dna-service-egress-deny fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "istio", "authz-policies.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["policy.yaml"]);
    inner.authorizationPolicies = inner.authorizationPolicies.filter(
      (p) => p.name !== "dna-service-egress-deny",
    );
    doc.data["policy.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "authz-policies.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /missing mandatory AuthorizationPolicy 'dna-service-egress-deny'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("istio: telemetry.yaml mesh.retryBudget not null fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "istio", "telemetry.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["policy.yaml"]);
    inner.mesh.retryBudget = { maxRetries: 3 };
    doc.data["policy.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "telemetry.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /mesh\.retryBudget must be null/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("istio: telemetry.yaml accesslog.format not JSON fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "istio", "telemetry.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["policy.yaml"]);
    inner.telemetry.accesslog.format = "PLAIN";
    doc.data["policy.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "telemetry.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /accesslog\.format must be 'JSON'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
