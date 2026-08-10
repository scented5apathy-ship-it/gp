#!/usr/bin/env node
/**
 * Unit tests for the E3.3 OpenFGA config-as-code deep validator
 * (`scripts/lint-openfga-config.mjs`).
 *
 * The fixture copies the 5 platform/openfga/* source-of-truth +
 * chart mirror files + the contracts/openfga/{model.v1.json,
 * migrations/v1-to-v2.json} into a temp tree, then mutates one
 * invariant per test. The clean fixture must exit 0; every
 * mutation must exit 1 with a recognizable violation message.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
  copyFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const SCRIPT = join(ROOT, "scripts", "lint-openfga-config.mjs");

const PLATFORM_FILES = [
  "store-strategy.yaml",
  "model-registry.yaml",
  "audit-hook.yaml",
  "sync-workflow.yaml",
  "bootstrap-tuples.json",
];

function runScript(root) {
  return spawnSync(process.execPath, [SCRIPT], {
    cwd: ROOT,
    env: { ...process.env, LINT_ROOT: root },
    encoding: "utf8",
  });
}

function copyTree(srcRel, dstDir) {
  const src = join(ROOT, ...srcRel.split("/"));
  if (src.endsWith(".json") || src.endsWith(".yaml")) {
    copyFileSync(src, dstDir);
  } else {
    mkdirSync(dstDir, { recursive: true });
    copyFileSync(src, join(dstDir, srcRel.split("/").pop()));
  }
}

function makeFixture() {
  const dir = mkdtempSync(join(tmpdir(), "openfga-lint-"));
  const platformDir = join(dir, "platform", "openfga");
  const mirrorDir = join(
    dir,
    "platform",
    "helm",
    "genealogy-platform",
    "files",
  );
  const contractsDir = join(dir, "contracts", "openfga", "migrations");
  mkdirSync(platformDir, { recursive: true });
  mkdirSync(mirrorDir, { recursive: true });
  mkdirSync(contractsDir, { recursive: true });
  for (const f of PLATFORM_FILES) {
    const text = readFileSync(join(ROOT, "platform", "openfga", f), "utf8");
    writeFileSync(join(platformDir, f), text);
    writeFileSync(join(mirrorDir, `openfga-${f}`), text);
  }
  // contracts files
  copyFileSync(
    join(ROOT, "contracts", "openfga", "model.v1.json"),
    join(dir, "contracts", "openfga", "model.v1.json"),
  );
  copyFileSync(
    join(ROOT, "contracts", "openfga", "migrations", "v1-to-v2.json"),
    join(dir, "contracts", "openfga", "migrations", "v1-to-v2.json"),
  );
  return dir;
}

function mutate(dir, file, replacement) {
  const src = join(dir, "platform", "openfga", file);
  const mirror = join(
    dir,
    "platform",
    "helm",
    "genealogy-platform",
    "files",
    `openfga-${file}`,
  );
  const text = readFileSync(src, "utf8");
  const next = replacement(text);
  writeFileSync(src, next);
  writeFileSync(mirror, next);
}

test("openfga linter: clean fixture passes", () => {
  const dir = makeFixture();
  try {
    const proc = runScript(dir);
    assert.equal(
      proc.status,
      0,
      `expected exit 0, got ${proc.status}\nstdout=${proc.stdout}\nstderr=${proc.stderr}`,
    );
    assert.match(proc.stdout, /E3\.3 OpenFGA source-of-truth files conform to contract/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("openfga linter: rejects storeTopology drift", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "store-strategy.yaml", (text) =>
      text.replace("storeTopology: store-per-tenant", "storeTopology: shared"),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /storeTopology must be 'store-per-tenant'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("openfga linter: rejects TTL-only cache", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "store-strategy.yaml", (text) =>
      text.replace("ttlOnly: forbidden", "ttlOnly: allowed"),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /cache\.ttlOnly must be 'forbidden'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("openfga linter: rejects audit sink drift", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "store-strategy.yaml", (text) =>
      text.replace(
        "audit-service:9090/audit.v1.AuditService/Append",
        "audit-service:9090/audit.v1.AuditService/Write",
      ),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /audit\.sink must point at/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("openfga linter: rejects raw_dna literal in tuple content", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "bootstrap-tuples.json", (text) =>
      text.replace(
        "tenant:t_admin#owner@user:u_admin",
        "tenant:t_admin#raw_dna@user:u_admin",
      ),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /forbidden literal 'raw_dna'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("openfga linter: rejects revoke-first-priority disabled", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "bootstrap-tuples.json", (text) =>
      text.replace('"enabled": true,', '"enabled": false,', 1),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /revoke_first_priority\.enabled must be true/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("openfga linter: rejects sync-workflow without cacheInvalidationAck", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "sync-workflow.yaml", (text) =>
      text.replace(
        "      - name: cacheInvalidationAck\n",
        "",
      ),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /cacheInvalidationAck/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("openfga linter: rejects chart mirror drift", () => {
  const dir = makeFixture();
  try {
    // Drift the mirror file only (source stays clean).
    const mirror = join(
      dir,
      "platform",
      "helm",
      "genealogy-platform",
      "files",
      "openfga-store-strategy.yaml",
    );
    const text = readFileSync(mirror, "utf8");
    writeFileSync(mirror, text.replace("storeTopology: store-per-tenant", "storeTopology: shared"));
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /chart mirror drift/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
