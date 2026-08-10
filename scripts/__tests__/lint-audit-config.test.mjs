/**
 * Unit tests for `scripts/lint-audit-config.mjs`. Each test uses
 * a temp directory that contains a copy of the canonical
 * `contracts/audit/` fixtures; mutations are applied in place so
 * the linter exits non-zero on a regression.
 *
 * Mirrors the structure of
 * `scripts/__tests__/lint-trusted-context.test.mjs` (E3.5). Run
 * with `node --test scripts/__tests__/lint-audit-config.test.mjs`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import {
  mkdtempSync,
  mkdirSync,
  copyFileSync,
  rmSync,
  writeFileSync,
  readFileSync,
  existsSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const LINTER = join(ROOT, "scripts", "lint-audit-config.mjs");

const FIXTURES = [
  {
    contract: "contracts/audit/policy.yaml",
    mirror: "platform/helm/genealogy-platform/files/audit-policy.yaml",
  },
  {
    contract: "contracts/audit/retention.yaml",
    mirror: "platform/helm/genealogy-platform/files/audit-retention.yaml",
  },
  {
    contract: "contracts/audit/redaction.yaml",
    mirror: "platform/helm/genealogy-platform/files/audit-redaction.yaml",
  },
  {
    contract: "contracts/audit/export.yaml",
    mirror: "platform/helm/genealogy-platform/files/audit-export.yaml",
  },
];

function copyFixtureContracts(tmp) {
  for (const fixture of FIXTURES) {
    const contractDst = join(tmp, fixture.contract);
    const mirrorDst = join(tmp, fixture.mirror);
    mkdirSync(dirname(contractDst), { recursive: true });
    mkdirSync(dirname(mirrorDst), { recursive: true });
    copyFileSync(join(ROOT, fixture.contract), contractDst);
    copyFileSync(join(ROOT, fixture.mirror), mirrorDst);
  }
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

function replaceInBoth(work, sourceRel, mirrorRel, find, replace) {
  const sourcePath = join(work, sourceRel);
  const mirrorPath = join(work, mirrorRel);
  const mutated = readFileSync(sourcePath, "utf8").replace(find, replace);
  writeFileSync(sourcePath, mutated);
  writeFileSync(mirrorPath, mutated);
}

test("audit: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "audit-clean-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `expected exit 0, got ${r.code}\nstderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("audit: missing audit class fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "audit-class-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        "contracts/audit/policy.yaml",
        "platform/helm/genealogy-platform/files/audit-policy.yaml",
        "    - id: consent\n      description: Consent grant / revoke / expiry / DNA access / share receipt.",
        "    - id: dpo_only\n      description: 'Removed consent class to simulate gap.'",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.auditClasses missing "consent"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("audit: integrity hashAlgorithm != SHA-256 fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "audit-hash-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        "contracts/audit/policy.yaml",
        "platform/helm/genealogy-platform/files/audit-policy.yaml",
        "    hashAlgorithm: SHA-256",
        "    hashAlgorithm: MD5",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.integrity\.hashAlgorithm must be "SHA-256"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("audit: legalHold.enforcement != HARD_BLOCK fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "audit-legal-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        "contracts/audit/retention.yaml",
        "platform/helm/genealogy-platform/files/audit-retention.yaml",
        "    enforcement: HARD_BLOCK",
        "    enforcement: SOFT",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.legalHold\.enforcement must be "HARD_BLOCK"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("audit: redaction denyKeys missing rawDna fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "audit-redact-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        "contracts/audit/redaction.yaml",
        "platform/helm/genealogy-platform/files/audit-redaction.yaml",
        "    - rawDna\n    - raw_dna",
        "    - raw_dna",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.denyKeys missing "rawDna"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("audit: signedUrl requiresDpoRole != true fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "audit-export-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        "contracts/audit/export.yaml",
        "platform/helm/genealogy-platform/files/audit-export.yaml",
        "    requiresDpoRole: true",
        "    requiresDpoRole: false",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.signedUrl\.requiresDpoRole must be true/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("audit: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "audit-mirror-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      const mirrorPath = join(
        work,
        "platform",
        "helm",
        "genealogy-platform",
        "files",
        "audit-policy.yaml",
      );
      writeFileSync(mirrorPath, readFileSync(mirrorPath, "utf8") + "\n# drift\n");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /chart mirror drift/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("audit: forbidden literal (eyJ token) fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "audit-token-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, "contracts", "audit", "policy.yaml");
      const mirror = join(
        work,
        "platform",
        "helm",
        "genealogy-platform",
        "files",
        "audit-policy.yaml",
      );
      const mutated = readFileSync(path, "utf8") + "\n# debug: token=eyJhbGciOiJIUzI1NiJ9\n";
      writeFileSync(path, mutated);
      writeFileSync(mirror, mutated);
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /forbidden literal detected/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});
