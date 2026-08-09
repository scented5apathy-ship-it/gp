#!/usr/bin/env node
/**
 * scripts/__tests__/lint-s3-config.test.mjs
 *
 * Unit tests for `scripts/lint-s3-config.mjs` (E2.7).
 *
 * Strategy: drive the linter with `LINT_ROOT` pointed at a
 * tempdir that mirrors `platform/storage/`. Assert:
 *   1. The shipped config passes.
 *   2. A `bucket-policy.yaml` that drops the deny-all
 *      `media` bucket prefixTemplate + tenant_pseudo_id
 *      fails.
 *   3. A `bucket-policy.yaml` that enables a wildcard
 *      CORS origin fails.
 *   4. A `bucket-policy.yaml` that enables a public READ
 *      ACL on `media` fails.
 *   5. A `valkey-config.yaml` that uses `noeviction` fails.
 *   6. A `valkey-config.yaml` that grants `@admin` to a
 *      service user fails.
 *   7. A `compatibility-matrix.yaml` that drops a
 *      required S3 operation fails.
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
const SCRIPT = join(ROOT, "scripts", "lint-s3-config.mjs");

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
  const dir = mkdtempSync(join(tmpdir(), "s3-"));
  mkdirSync(join(dir, "platform", "storage"), { recursive: true });
  mkdirSync(join(dir, "platform", "helm", "genealogy-platform", "files", "storage"), {
    recursive: true,
  });
  copyTree(join(ROOT, "platform", "storage"), join(dir, "platform", "storage"));
  const filesDir = join(dir, "platform", "storage");
  const mirrorDir = join(dir, "platform", "helm", "genealogy-platform", "files", "storage");
  for (const f of [
    "s3-config.yaml",
    "bucket-policy.yaml",
    "compatibility-matrix.yaml",
    "valkey-config.yaml",
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
  const src = join(dir, "platform", "storage", file);
  const dst = join(dir, "platform", "helm", "genealogy-platform", "files", "storage", file);
  writeFileSync(dst, readFileSync(src, "utf8"));
}

test("s3: shipped config passes", () => {
  const dir = makeFixture();
  try {
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(
      proc.status,
      0,
      `expected exit 0, got ${proc.status}\nstdout=${proc.stdout}\nstderr=${proc.stderr}`,
    );
    assert.match(proc.stdout, /\[s3\] clean/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("s3: bucket-policy drops tenant_pseudo_id prefix fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "storage", "bucket-policy.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["config.yaml"]);
    inner.buckets[0].prefixTemplate = "tree/{tree_id}/{asset_id}";
    doc.data["config.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "bucket-policy.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /prefixTemplate must contain '\{tenant_pseudo_id\}'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("s3: bucket-policy wildcard CORS origin fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "storage", "bucket-policy.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["config.yaml"]);
    // Inject wildcard into the media bucket's CORS rule.
    inner.buckets[0].cors[0].allowedOrigins.push("*");
    doc.data["config.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "bucket-policy.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /allowedOrigins must not contain '\*'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("s3: bucket-policy public READ ACL on media fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "storage", "bucket-policy.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["config.yaml"]);
    const media = inner.buckets.find((b) => b.name === "media");
    media.iam.writers.push({ public: true });
    doc.data["config.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "bucket-policy.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /'media' bucket must NOT enable a public READ ACL/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("s3: valkey-config maxmemoryPolicy noeviction fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "storage", "valkey-config.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["config.yaml"]);
    inner.valkey.maxmemoryPolicy = "noeviction";
    doc.data["config.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "valkey-config.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /maxmemoryPolicy must be 'allkeys-lru'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("s3: valkey-config @admin on service user fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "storage", "valkey-config.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["config.yaml"]);
    // Grant @admin to a non-operator user.
    const webBff = inner.requiredUsers.find((u) => u.name === "web-bff");
    webBff.commands = "@admin";
    doc.data["config.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "valkey-config.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /user 'web-bff' must NOT carry @admin/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("s3: compatibility-matrix missing PutObject fails", () => {
  const dir = makeFixture();
  try {
    const p = join(dir, "platform", "storage", "compatibility-matrix.yaml");
    const doc = YAML.parse(readFileSync(p, "utf8"));
    const inner = YAML.parse(doc.data["config.yaml"]);
    inner.operations = inner.operations.filter((o) => o.op !== "PutObject");
    doc.data["config.yaml"] = YAML.stringify(inner);
    writeFileSync(p, YAML.stringify(doc));
    syncMirror(dir, "compatibility-matrix.yaml");
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /missing required S3 operation 'PutObject'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
