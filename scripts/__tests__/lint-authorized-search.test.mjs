/**
 * Unit tests for `scripts/lint-authorized-search.mjs`.
 * Run with `node --test scripts/__tests__/lint-authorized-search.test.mjs`.
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
const LINTER = join(ROOT, "scripts", "lint-authorized-search.mjs");
const CONTRACT = "contracts/search/authorized-search-policy.yaml";
const MIRROR =
  "platform/helm/genealogy-platform/files/authorized-search-policy.yaml";

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
  const tmp = mkdtempSync(join(tmpdir(), "auth-search-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp);
    assert.equal(result.status, 0);
    assert.match(result.stdout, /authorized search policy contract OK\./);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("missing ALLOWED authorization outcome fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "auth-search-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "    - ALLOWED\n    - TENANT_MISMATCH\n    - OPENFGA_DENY\n    - ABAC_LIVING_FORBIDDEN\n    - ABAC_MINOR_FORBIDDEN\n    - ABAC_DNA_FORBIDDEN\n    - ABAC_CONSENT_REQUIRED\n    - ABAC_CONTEXTUAL_DENY\n    - PERMISSION_VERSION_STALE\n    - REJECTED",
        "    - TENANT_MISMATCH\n    - OPENFGA_DENY\n    - ABAC_LIVING_FORBIDDEN\n    - ABAC_MINOR_FORBIDDEN\n    - ABAC_DNA_FORBIDDEN\n    - ABAC_CONSENT_REQUIRED\n    - ABAC_CONTEXTUAL_DENY\n    - PERMISSION_VERSION_STALE\n    - REJECTED",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /searchAuthorizationOutcomes/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("dnaBucketAccess MUST equal FORBIDDEN", () => {
  const tmp = mkdtempSync(join(tmpdir(), "auth-search-"));
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

test("terminal PURGED must have empty transitions", () => {
  const tmp = mkdtempSync(join(tmpdir(), "auth-search-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "    - status: PURGED\n      transitions: []\n      terminal: true",
        "    - status: PURGED\n      transitions: [VALID]\n      terminal: true",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /terminal status PURGED/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("page size invariant violated when max != 5 × default", () => {
  const tmp = mkdtempSync(join(tmpdir(), "auth-search-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "  defaultPageSize: 20",
        "  defaultPageSize: 25",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /page size invariant/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "auth-search-"));
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

test("tenantFilterEnforced MUST be true", () => {
  const tmp = mkdtempSync(join(tmpdir(), "auth-search-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "  tenantFilterEnforced: true",
        "  tenantFilterEnforced: false",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /tenantFilterEnforced/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("missing dna/raw prefix fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "auth-search-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "    - dna/raw\n    - dna/match\n    - dna/consent",
        "    - dna/match\n    - dna/consent",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /dna.?bucket.?prefix/i);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});