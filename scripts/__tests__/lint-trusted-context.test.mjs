/**
 * Unit tests for `scripts/lint-trusted-context.mjs`. Each test
 * uses a temp directory that contains a copy of the canonical
 * `contracts/trusted-context/` fixtures; mutations are applied
 * in place so the linter exits non-zero on a regression.
 *
 * Mirrors the structure of `scripts/__tests__/lint-abac-config.test.mjs`
 * (E3.4). Run with
 * `node --test scripts/__tests__/lint-trusted-context.test.mjs`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import { mkdtempSync, mkdirSync, copyFileSync, rmSync, writeFileSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const LINTER = join(ROOT, "scripts", "lint-trusted-context.mjs");

function copyFixtureContracts(tmp) {
  const contractsDst = join(tmp, "contracts", "trusted-context");
  const mirrorDst = join(tmp, "platform", "helm", "genealogy-platform", "files");
  mkdirSync(contractsDst, { recursive: true });
  mkdirSync(mirrorDst, { recursive: true });
  copyFileSync(
    join(ROOT, "contracts", "trusted-context", "policy.yaml"),
    join(contractsDst, "policy.yaml"),
  );
  copyFileSync(
    join(ROOT, "platform", "helm", "genealogy-platform", "files", "trusted-context-policy.yaml"),
    join(mirrorDst, "trusted-context-policy.yaml"),
  );
}

function runLinter(tmp, mutation) {
  if (mutation) {
    mutation(tmp);
  }
  const result = spawnSync(process.execPath, [LINTER], {
    cwd: tmp,
    env: { ...process.env, LINT_ROOT: tmp },
    encoding: "utf8",
  });
  return {
    code: result.status,
    stdout: result.stdout || "",
    stderr: result.stderr || "",
  };
}

test("trusted-context: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "trusted-context-clean-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp);
    assert.equal(
      r.code,
      0,
      `expected exit 0, got ${r.code}\nstdout=${r.stdout}\nstderr=${r.stderr}`,
    );
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("trusted-context: refuseClientSupplied.rest missing tenant_id entry fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "trusted-context-rest-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, "contracts", "trusted-context", "policy.yaml");
      const raw = readFileSync(path, "utf8");
      const mutated = raw.replace("      - request.body.tenant_id", "      # removed");
      writeFileSync(path, mutated);
      // keep the chart mirror in sync so the linter isolates
      // the structural mutation
      const mirror = join(
        work,
        "platform",
        "helm",
        "genealogy-platform",
        "files",
        "trusted-context-policy.yaml",
      );
      writeFileSync(mirror, mutated);
    });
    assert.equal(r.code, 1, `expected exit 1, got ${r.code}`);
    assert.match(r.stderr, /refuseClientSupplied\.rest missing request\.body\.tenant_id/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("trusted-context: refuseClientSupplied.grpc missing message.context.actor_role fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "trusted-context-grpc-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, "contracts", "trusted-context", "policy.yaml");
      const raw = readFileSync(path, "utf8");
      const mutated = raw.replace("      - message.context.actor_role", "      # removed");
      writeFileSync(path, mutated);
      const mirror = join(
        work,
        "platform",
        "helm",
        "genealogy-platform",
        "files",
        "trusted-context-policy.yaml",
      );
      writeFileSync(mirror, mutated);
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /refuseClientSupplied\.grpc missing message\.context\.actor_role/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("trusted-context: mtls.mode != STRICT fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "trusted-context-mtls-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, "contracts", "trusted-context", "policy.yaml");
      const raw = readFileSync(path, "utf8");
      const mutated = raw.replace("mode: STRICT", "mode: PERMISSIVE");
      writeFileSync(path, mutated);
      const mirror = join(
        work,
        "platform",
        "helm",
        "genealogy-platform",
        "files",
        "trusted-context-policy.yaml",
      );
      writeFileSync(mirror, mutated);
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.mtls\.mode must be "STRICT"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("trusted-context: reconciliation.membershipStatusRequired != ACTIVE fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "trusted-context-recon-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, "contracts", "trusted-context", "policy.yaml");
      const raw = readFileSync(path, "utf8");
      const mutated = raw.replace(
        "membershipStatusRequired: ACTIVE",
        "membershipStatusRequired: ANY",
      );
      writeFileSync(path, mutated);
      const mirror = join(
        work,
        "platform",
        "helm",
        "genealogy-platform",
        "files",
        "trusted-context-policy.yaml",
      );
      writeFileSync(mirror, mutated);
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /membershipStatusRequired must be "ACTIVE"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("trusted-context: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "trusted-context-mirror-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      const mirror = join(
        work,
        "platform",
        "helm",
        "genealogy-platform",
        "files",
        "trusted-context-policy.yaml",
      );
      const raw = readFileSync(mirror, "utf8");
      // Add a trailing newline + a benign comment so the
      // byte contents differ without affecting the linter's
      // structural assertions.
      writeFileSync(mirror, raw + "\n# drift\n");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /chart mirror drift/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("trusted-context: missing contract file fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "trusted-context-missing-"));
  try {
    // do NOT copy the fixtures
    const r = runLinter(tmp);
    assert.equal(r.code, 1);
    assert.match(r.stderr, /missing contract file/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("trusted-context: forbidden literal (eyJ token) fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "trusted-context-token-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, "contracts", "trusted-context", "policy.yaml");
      const raw = readFileSync(path, "utf8");
      const mutated = raw + "\n# debug: token=eyJhbGciOiJIUzI1NiJ9\n";
      writeFileSync(path, mutated);
      const mirror = join(
        work,
        "platform",
        "helm",
        "genealogy-platform",
        "files",
        "trusted-context-policy.yaml",
      );
      writeFileSync(mirror, mutated);
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /forbidden literal detected/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});
