#!/usr/bin/env node
/**
 * scripts/__tests__/check-platform-baseline.test.mjs
 *
 * Unit tests for `scripts/check-platform-baseline.mjs` (E2.1).
 *
 * Strategy: point the script at a temporary directory that mirrors the
 * repo's `platform/` tree (clean fixture) and assert it exits 0. Then
 * introduce one violation per scenario (missing namespace, literal
 * secret, missing per-env file, missing docker-compose) and assert the
 * script exits 1 with a recognizable message.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import {
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

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const SCRIPT = join(ROOT, "scripts", "check-platform-baseline.mjs");

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
  const dir = mkdtempSync(join(tmpdir(), "baseline-"));
  mkdirSync(join(dir, "platform", "helm", "genealogy-platform", "templates", "baseline"), {
    recursive: true,
  });
  mkdirSync(join(dir, "platform", "local", "db"), { recursive: true });
  mkdirSync(join(dir, "platform", "kong"), { recursive: true });
  mkdirSync(join(dir, "platform", "kafka"), { recursive: true });
  mkdirSync(join(dir, "platform", "apicurio"), { recursive: true });
  mkdirSync(join(dir, "platform", "observability", "alerts"), { recursive: true });
  copyTree(
    join(ROOT, "platform", "helm", "genealogy-platform"),
    join(dir, "platform", "helm", "genealogy-platform"),
  );
  copyTree(join(ROOT, "platform", "local"), join(dir, "platform", "local"));
  copyTree(join(ROOT, "platform", "kong"), join(dir, "platform", "kong"));
  copyTree(join(ROOT, "platform", "kafka"), join(dir, "platform", "kafka"));
  copyTree(join(ROOT, "platform", "apicurio"), join(dir, "platform", "apicurio"));
  copyTree(
    join(ROOT, "platform", "observability", "alerts"),
    join(dir, "platform", "observability", "alerts"),
  );
  return dir;
}

test("baseline: clean repo passes", () => {
  const dir = makeFixture();
  try {
    const proc = runScript({ BASELINE_ROOT: dir });
    assert.equal(
      proc.status,
      0,
      `expected exit 0, got ${proc.status}\nstdout=${proc.stdout}\nstderr=${proc.stderr}`,
    );
    assert.match(proc.stdout, /\[baseline\] clean/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("baseline: missing namespace fails", () => {
  const dir = makeFixture();
  try {
    const valuesPath = join(dir, "platform", "helm", "genealogy-platform", "values.yaml");
    // Remove the `gp-data:` map key (with the `name: gp-data` line
    // that follows it inside the map entry).
    const text = readFileSync(valuesPath, "utf8").replace(
      /    gp-data:\n(?:      .*\n)+/,
      "# gp-data removed\n",
    );
    writeFileSync(valuesPath, text);
    const proc = runScript({ BASELINE_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /baseline namespace 'gp-data' not declared/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("baseline: literal password in values fails", () => {
  const dir = makeFixture();
  try {
    const valuesPath = join(dir, "platform", "helm", "genealogy-platform", "values.yaml");
    writeFileSync(
      valuesPath,
      readFileSync(valuesPath, "utf8") + '\n  password: "supersecretvalue123"\n',
    );
    const proc = runScript({ BASELINE_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /literal secret-like value for 'password'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("baseline: missing values-onprem.yaml fails", () => {
  const dir = makeFixture();
  try {
    const onprem = join(dir, "platform", "helm", "genealogy-platform", "values-onprem.yaml");
    rmSync(onprem);
    const proc = runScript({ BASELINE_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /per-environment overrides missing/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("baseline: missing local docker-compose fails", () => {
  const dir = makeFixture();
  try {
    const compose = join(dir, "platform", "local", "docker-compose.yml");
    rmSync(compose);
    const proc = runScript({ BASELINE_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /platform\/local missing required entry 'docker-compose\.yml'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
