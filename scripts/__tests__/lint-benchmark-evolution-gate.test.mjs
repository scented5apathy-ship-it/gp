/**
 * Unit tests for `scripts/lint-benchmark-evolution-gate.mjs`.
 * Run with `node --test scripts/__tests__/lint-benchmark-evolution-gate.test.mjs`.
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
const LINTER = join(ROOT, "scripts", "lint-benchmark-evolution-gate.mjs");
const CONTRACT = "contracts/search/benchmark-evolution-gate-policy.yaml";
const MIRROR =
  "platform/helm/genealogy-platform/files/benchmark-evolution-gate-policy.yaml";

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
  const result = spawnSync("node", [LINTER], {
    cwd: tmp,
    encoding: "utf8",
    env: { ...process.env, LINT_ROOT: tmp },
  });
  return {
    status: result.status,
    stdout: (result.stdout || "") + (result.stderr || ""),
    stderr: result.stderr || "",
  };
}

test("clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "bench-gate-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp);
    assert.equal(result.status, 0);
    assert.match(result.stdout, /benchmark evolution gate policy contract OK\./);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("missing PASS verdict fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "bench-gate-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "    - PASS\n    - PASS_WITH_NOTES\n    - FAIL_P95",
        "    - PASS_WITH_NOTES\n    - FAIL_P95",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /benchmarkVerdicts/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("opensearchRequiresAdr MUST be true", () => {
  const tmp = mkdtempSync(join(tmpdir(), "bench-gate-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "  opensearchRequiresAdr: true",
        "  opensearchRequiresAdr: false",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /opensearchRequiresAdr/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("p95/p99 ratio invariant violated when p99 != 2 × p95", () => {
  const tmp = mkdtempSync(join(tmpdir(), "bench-gate-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "  benchmarkSuiteP99BudgetMilliseconds: 2000",
        "  benchmarkSuiteP99BudgetMilliseconds: 5000",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /p95\/p99 invariant/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("terminal PROMOTED must have empty transitions", () => {
  const tmp = mkdtempSync(join(tmpdir(), "bench-gate-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "    - status: PROMOTED\n      transitions: []\n      terminal: true",
        "    - status: PROMOTED\n      transitions: [QUEUED]\n      terminal: true",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /terminal status PROMOTED/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("rolloutToEvolutionPath drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "bench-gate-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "RELEASE_CANDIDATE: [POSTGRES_HOLD]",
        "RELEASE_CANDIDATE: [OPENSEARCH_REQUIRED]",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /rolloutToEvolutionPath.RELEASE_CANDIDATE/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "bench-gate-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      writeFileSync(join(root, MIRROR), readFileSync(join(root, MIRROR), "utf8") + "\n# drift\n");
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /chart mirror drift/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("dnaBucketAccess MUST equal FORBIDDEN", () => {
  const tmp = mkdtempSync(join(tmpdir(), "bench-gate-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "  dnaBucketAccess: FORBIDDEN",
        "  dnaBucketAccess: ALLOWED",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /dnaBucketAccess/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});