#!/usr/bin/env node
/**
 * scripts/__tests__/lint-kong-config.test.mjs
 *
 * Unit tests for `scripts/lint-kong-config.mjs` (E2.2).
 *
 * Strategy: drive the linter with `KONG_ROOT` pointed at a tempdir
 * that mirrors `platform/kong/kong.yml`. Assert:
 *   1. The shipped kong.yml passes.
 *   2. A synthetic kong.yml that drops the `_format_version` fails.
 *   3. A synthetic kong.yml that wires the forbidden `acl` plugin
 *      fails (domain authorization is forbidden in Kong).
 *   4. A kong.yml without the admin route fails.
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
const SCRIPT = join(ROOT, "scripts", "lint-kong-config.mjs");
const KONG_DIR = join(ROOT, "platform", "kong");

function runScript(env) {
  return spawnSync(process.execPath, [SCRIPT], {
    cwd: ROOT,
    env: { ...process.env, ...env },
    encoding: "utf8",
  });
}

function makeFixture() {
  const dir = mkdtempSync(join(tmpdir(), "kong-"));
  mkdirSync(join(dir, "platform", "kong"), { recursive: true });
  writeFileSync(
    join(dir, "platform", "kong", "kong.yml"),
    readFileSync(join(KONG_DIR, "kong.yml"), "utf8"),
  );
  return dir;
}

test("kong: shipped kong.yml passes", () => {
  const proc = runScript({});
  assert.equal(
    proc.status,
    0,
    `expected exit 0, got ${proc.status}\nstdout=${proc.stdout}\nstderr=${proc.stderr}`,
  );
  assert.match(proc.stdout, /\[kong\] clean/);
});

test("kong: missing _format_version fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kong", "kong.yml");
    const text = readFileSync(target, "utf8").replace(/_format_version:\s*"3\.0"\n/, "");
    writeFileSync(target, text);
    const proc = runScript({ KONG_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /_format_version must be '3\.0'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kong: forbidden acl plugin fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kong", "kong.yml");
    const text = readFileSync(target, "utf8").replace(
      /upstreams: \[\]\n?$/,
      '  - name: acl\n    route: admin-api-v1\n    config:\n      allow:\n        - "admin"\nupstreams: []\n',
    );
    writeFileSync(target, text);
    const proc = runScript({ KONG_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /forbidden plugin 'acl'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kong: top-level database key fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kong", "kong.yml");
    const text = readFileSync(target, "utf8").replace(
      /_format_version:\s*"3\.0"\n/,
      '_format_version: "3.0"\ndatabase: "off"\n',
    );
    writeFileSync(target, text);
    const proc = runScript({ KONG_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /must not declare a top-level 'database' key/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kong: string allowed_payload_size fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kong", "kong.yml");
    const text = readFileSync(target, "utf8").replace(
      "allowed_payload_size: 8",
      'allowed_payload_size: "8"',
    );
    writeFileSync(target, text);
    const proc = runScript({ KONG_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /allowed_payload_size must be an integer/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kong: missing admin route fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kong", "kong.yml");
    const text = readFileSync(target, "utf8").replace(
      /- name: admin-api-v1\n[\s\S]*?snis:\n\s+- "admin\.genealogy\.local"\n\s+tags:\n\s+- "route-class:admin"\n/,
      "",
    );
    writeFileSync(target, text);
    const proc = runScript({ KONG_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /missing required route 'admin-api-v1'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
