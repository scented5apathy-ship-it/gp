#!/usr/bin/env node
/**
 * scripts/__tests__/lint-vault-config.test.mjs
 *
 * Unit tests for `scripts/lint-vault-config.mjs` (E2.6).
 *
 * Strategy: drive the linter with `LINT_ROOT` pointed at a
 * tempdir that mirrors `platform/vault/`. Assert:
 *   1. The shipped config passes.
 *   2. A `server-config.yaml` whose seal stanza is missing
 *      fails.
 *   3. An `auth-methods.yaml` that enables the forbidden
 *      `userpass` auth method fails.
 *   4. A `policies.yaml` that drops the deny-all `default`
 *      policy fails.
 *   5. A `kms-abstraction.yaml` that reuses a keyId across
 *      two data classes fails.
 *   6. An `injector-templates.yaml` that drops the
 *      `agent-revoke-on-shutdown: "true"` annotation fails.
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
const SCRIPT = join(ROOT, "scripts", "lint-vault-config.mjs");

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
  const dir = mkdtempSync(join(tmpdir(), "vault-"));
  mkdirSync(join(dir, "platform", "vault"), { recursive: true });
  mkdirSync(join(dir, "platform", "helm", "genealogy-platform", "files", "vault"), {
    recursive: true,
  });
  copyTree(join(ROOT, "platform", "vault"), join(dir, "platform", "vault"));
  const filesDir = join(dir, "platform", "vault");
  const mirrorDir = join(dir, "platform", "helm", "genealogy-platform", "files", "vault");
  for (const f of [
    "server-config.yaml",
    "auth-methods.yaml",
    "policies.yaml",
    "kms-abstraction.yaml",
    "injector-templates.yaml",
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
  const src = join(dir, "platform", "vault", file);
  const dst = join(dir, "platform", "helm", "genealogy-platform", "files", "vault", file);
  writeFileSync(dst, readFileSync(src, "utf8"));
}

test("vault: shipped config passes", () => {
  const dir = makeFixture();
  try {
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(
      proc.status,
      0,
      `expected exit 0, got ${proc.status}\nstdout=${proc.stdout}\nstderr=${proc.stderr}`,
    );
    assert.match(proc.stdout, /\[vault\] clean/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("vault: server-config.yaml missing seal stanza fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "vault", "server-config.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = doc.data["config.hcl"];
    doc.data["config.hcl"] = inner.replace(/seal\s+"\w+"\s*\{[\s\S]*?\}\n/g, "");
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "server-config.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /server-config\.yaml must declare a seal stanza/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("vault: auth-methods.yaml enables forbidden userpass fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "vault", "auth-methods.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["config.yaml"]);
    inner.authMethods.push({ name: "userpass", type: "userpass" });
    doc.data["config.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "auth-methods.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /must not enable forbidden auth method 'userpass'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("vault: policies.yaml drops default deny-all policy fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "vault", "policies.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["config.yaml"]);
    inner.policies = inner.policies.filter((p) => p.name !== "default");
    doc.data["config.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "policies.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /missing required policy 'default'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("vault: kms-abstraction.yaml reuses keyId across classes fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "vault", "kms-abstraction.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["config.yaml"]);
    // Reuse the first keyId across PII.QUASI_ID.
    inner.kmsAbstraction.keys[1].keyId = inner.kmsAbstraction.keys[0].keyId;
    doc.data["config.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "kms-abstraction.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /keyId reuse across classes/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("vault: injector-templates.yaml drops agent-revoke-on-shutdown fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "vault", "injector-templates.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["config.yaml"]);
    for (const t of inner.injectorTemplates) {
      delete t.annotations["vault.hashicorp.com/agent-revoke-on-shutdown"];
    }
    doc.data["config.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "injector-templates.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /agent-revoke-on-shutdown/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
