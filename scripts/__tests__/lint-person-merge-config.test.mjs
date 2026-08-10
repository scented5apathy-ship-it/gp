/**
 * Unit tests for `scripts/lint-person-merge-config.mjs`.
 * Mirrors the structure of `scripts/__tests__/
 * lint-event-claim-config.test.mjs` (E4.5).
 *
 * Run with `node --test scripts/__tests__/lint-person-merge-config.test.mjs`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import { copyFileSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const LINTER = join(ROOT, "scripts", "lint-person-merge-config.mjs");
const CONTRACT = "contracts/genealogy/person-merge-policy.yaml";
const MIRROR = "platform/helm/genealogy-platform/files/person-merge-policy.yaml";

function copyFixture(tmp) {
  const contractDst = join(tmp, CONTRACT);
  const mirrorDst = join(tmp, MIRROR);
  mkdirSync(dirname(contractDst), { recursive: true });
  mkdirSync(dirname(mirrorDst), { recursive: true });
  copyFileSync(join(ROOT, CONTRACT), contractDst);
  copyFileSync(join(ROOT, MIRROR), mirrorDst);
}

function runLinter(tmp, mutation) {
  if (mutation) mutation(tmp);
  const r = spawnSync(process.execPath, [LINTER], {
    cwd: tmp,
    env: { ...process.env, LINT_ROOT: tmp },
    encoding: "utf8",
  });
  return { code: r.status, stdout: r.stdout || "", stderr: r.stderr || "" };
}

function readRaw(rel) {
  return readFileSync(join(ROOT, rel), "utf8");
}

test("person-merge-config: baseline OK", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-merge-ok-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, r.stderr);
    assert.match(r.stdout, /\[person-merge-config\] OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person-merge-config: detects missing DUPLICATE_PERSON_MERGE kind", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-merge-kind-"));
  try {
    copyFixture(tmp);
    const contractPath = join(tmp, CONTRACT);
    const raw = readFileSync(contractPath, "utf8").replace(
      "    - DUPLICATE_PERSON_MERGE",
      "    - SOMETHING_ELSE",
    );
    writeFileSync(contractPath, raw);
    const r = runLinter(tmp);
    assert.equal(r.code, 1, r.stderr);
    assert.match(r.stderr, /mergeKinds missing required value DUPLICATE_PERSON_MERGE/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person-merge-config: detects wrong auto threshold", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-merge-auto-"));
  try {
    copyFixture(tmp);
    const contractPath = join(tmp, CONTRACT);
    const raw = readFileSync(contractPath, "utf8").replace(
      "autoScoreThreshold: 0.85",
      "autoScoreThreshold: 0.7",
    );
    writeFileSync(contractPath, raw);
    const r = runLinter(tmp);
    assert.equal(r.code, 1, r.stderr);
    assert.match(r.stderr, /autoScoreThreshold must equal 0\.85/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person-merge-config: detects wrong revert window", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-merge-revert-"));
  try {
    copyFixture(tmp);
    const contractPath = join(tmp, CONTRACT);
    const raw = readFileSync(contractPath, "utf8").replace(
      "revertWindowDays: 30",
      "revertWindowDays: 7",
    );
    writeFileSync(contractPath, raw);
    const r = runLinter(tmp);
    assert.equal(r.code, 1, r.stderr);
    assert.match(r.stderr, /revertWindowDays must equal 30/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person-merge-config: detects scoring weight sum != 1.0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-merge-weights-"));
  try {
    copyFixture(tmp);
    const contractPath = join(tmp, CONTRACT);
    const raw = readFileSync(contractPath, "utf8").replace(
      "      weight: 0.4",
      "      weight: 0.3",
    );
    writeFileSync(contractPath, raw);
    const r = runLinter(tmp);
    assert.equal(r.code, 1, r.stderr);
    assert.match(r.stderr, /weights must sum to 1\.0/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person-merge-config: detects chart mirror drift", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-merge-drift-"));
  try {
    copyFixture(tmp);
    const mirrorPath = join(tmp, MIRROR);
    const raw = readFileSync(mirrorPath, "utf8").replace(
      "policyId: default-person-merge/v1",
      "policyId: drifted",
    );
    writeFileSync(mirrorPath, raw);
    const r = runLinter(tmp);
    assert.equal(r.code, 1, r.stderr);
    assert.match(r.stderr, /NOT byte-identical/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person-merge-config: detects removed audit hook", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-merge-audit-"));
  try {
    copyFixture(tmp);
    const contractPath = join(tmp, CONTRACT);
    const raw = readFileSync(contractPath, "utf8").replace(
      "  auditActionOnCommit: merge.committed",
      "  auditActionOnCommit: merge.bogus",
    );
    writeFileSync(contractPath, raw);
    const r = runLinter(tmp);
    assert.equal(r.code, 1, r.stderr);
    assert.match(r.stderr, /auditActionOnCommit must equal "merge\.committed"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person-merge-config: detects sourcePreservationRequired = false", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-merge-preserve-"));
  try {
    copyFixture(tmp);
    const contractPath = join(tmp, CONTRACT);
    const raw = readFileSync(contractPath, "utf8").replace(
      "sourcePreservationRequired: true",
      "sourcePreservationRequired: false",
    );
    writeFileSync(contractPath, raw);
    const r = runLinter(tmp);
    assert.equal(r.code, 1, r.stderr);
    assert.match(r.stderr, /sourcePreservationRequired must equal true/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person-merge-config: contract and mirror are byte-identical in repo", () => {
  const c = readRaw(CONTRACT);
  const m = readRaw(MIRROR);
  assert.equal(c, m, "chart mirror drifted from source contract");
});
