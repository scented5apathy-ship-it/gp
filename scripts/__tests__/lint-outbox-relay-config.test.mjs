/**
 * Unit tests for `scripts/lint-outbox-relay-config.mjs`.
 * Mirrors the structure of
 * `scripts/__tests__/lint-person-merge-config.test.mjs`
 * (E4.6) — copy fixture to a temp root, mutate one line
 * at a time, re-run the linter with `LINT_ROOT` set, and
 * assert on the violation message.
 *
 * Run with `node --test scripts/__tests__/lint-outbox-relay-config.test.mjs`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import {
  copyFileSync,
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const LINTER = join(ROOT, "scripts", "lint-outbox-relay-config.mjs");
const CONTRACT = "contracts/genealogy/outbox-relay-policy.yaml";
const MIRROR = "platform/helm/genealogy-platform/files/outbox-relay-policy.yaml";

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

test("outbox-relay-config: baseline OK", () => {
  const tmp = mkdtempSync(join(tmpdir(), "outbox-relay-ok-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, r.stderr);
    assert.match(r.stdout, /\[outbox-relay-config\] OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("outbox-relay-config: detects missing PENDING status", () => {
  const tmp = mkdtempSync(join(tmpdir(), "outbox-relay-status-"));
  try {
    copyFixture(tmp);
    const contractPath = join(tmp, CONTRACT);
    const raw = readFileSync(contractPath, "utf8").replace("    - PENDING\n", "");
    writeFileSync(contractPath, raw);
    const r = runLinter(tmp);
    assert.equal(r.code, 1, r.stderr);
    assert.match(r.stderr, /outboxStatusLifecycle missing required value PENDING/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("outbox-relay-config: detects non-AVRO envelope serialization", () => {
  const tmp = mkdtempSync(join(tmpdir(), "outbox-relay-encoding-"));
  try {
    copyFixture(tmp);
    const contractPath = join(tmp, CONTRACT);
    const raw = readFileSync(contractPath, "utf8").replace(
      "  envelopeSerialization: AVRO",
      "  envelopeSerialization: JSON",
    );
    writeFileSync(contractPath, raw);
    const mirrorPath = join(tmp, MIRROR);
    writeFileSync(mirrorPath, raw);
    const r = runLinter(tmp);
    assert.equal(r.code, 1, r.stderr);
    assert.match(r.stderr, /envelopeSerialization must equal "AVRO"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("outbox-relay-config: detects wrong maxAttempts", () => {
  const tmp = mkdtempSync(join(tmpdir(), "outbox-relay-attempts-"));
  try {
    copyFixture(tmp);
    const contractPath = join(tmp, CONTRACT);
    const raw = readFileSync(contractPath, "utf8").replace("maxAttempts: 5", "maxAttempts: 3");
    writeFileSync(contractPath, raw);
    const r = runLinter(tmp);
    assert.equal(r.code, 1, r.stderr);
    assert.match(r.stderr, /maxAttempts must equal 5/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("outbox-relay-config: detects wrong DLQ retention", () => {
  const tmp = mkdtempSync(join(tmpdir(), "outbox-relay-dlq-"));
  try {
    copyFixture(tmp);
    const contractPath = join(tmp, CONTRACT);
    const raw = readFileSync(contractPath, "utf8").replace(
      "dlqRetentionDays: 14",
      "dlqRetentionDays: 7",
    );
    writeFileSync(contractPath, raw);
    const r = runLinter(tmp);
    assert.equal(r.code, 1, r.stderr);
    assert.match(r.stderr, /dlqRetentionDays must equal 14/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("outbox-relay-config: detects missing partition key class", () => {
  const tmp = mkdtempSync(join(tmpdir(), "outbox-relay-partition-"));
  try {
    copyFixture(tmp);
    const contractPath = join(tmp, CONTRACT);
    const raw = readFileSync(contractPath, "utf8").replace(
      '    - name: TRACE_ID',
      '    - name: SOMETHING_ELSE',
    );
    writeFileSync(contractPath, raw);
    const r = runLinter(tmp);
    assert.equal(r.code, 1, r.stderr);
    assert.match(r.stderr, /partitionKeyClasses missing required name TRACE_ID/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("outbox-relay-config: detects chart mirror drift", () => {
  const tmp = mkdtempSync(join(tmpdir(), "outbox-relay-drift-"));
  try {
    copyFixture(tmp);
    const mirrorPath = join(tmp, MIRROR);
    const raw = readFileSync(mirrorPath, "utf8").replace(
      "policyId: default-outbox-relay/v1",
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

test("outbox-relay-config: detects forbidden literal in contract", () => {
  const tmp = mkdtempSync(join(tmpdir(), "outbox-relay-secret-"));
  try {
    copyFixture(tmp);
    const contractPath = join(tmp, CONTRACT);
    const raw = readFileSync(contractPath, "utf8").replace(
      "  policyId: default-outbox-relay/v1",
      "  policyId: default-outbox-relay/v1\n  password: hunter2hunter2",
    );
    writeFileSync(contractPath, raw);
    const r = runLinter(tmp);
    assert.equal(r.code, 1, r.stderr);
    assert.match(r.stderr, /forbidden literal matches \/password/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("outbox-relay-config: contract and mirror are byte-identical in repo", () => {
  const c = readRaw(CONTRACT);
  const m = readRaw(MIRROR);
  assert.equal(c, m, "chart mirror drifted from source contract");
});
