/**
 * Unit tests for `scripts/lint-tree-config.mjs`. Each test uses
 * a temp directory that contains a copy of the canonical
 * `contracts/genealogy/` fixtures; mutations are applied in place
 * so the linter exits non-zero on a regression.
 *
 * Mirrors the structure of `lint-audit-config.test.mjs` (E3.6).
 * Run with `node --test scripts/__tests__/lint-tree-config.test.mjs`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import { mkdtempSync, mkdirSync, copyFileSync, rmSync, writeFileSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const LINTER = join(ROOT, "scripts", "lint-tree-config.mjs");

const FIXTURES = [
  {
    contract: "contracts/genealogy/tree-policy.yaml",
    mirror: "platform/helm/genealogy-platform/files/tree-policy.yaml",
  },
  {
    contract: "contracts/genealogy/collaboration-policy.yaml",
    mirror: "platform/helm/genealogy-platform/files/collaboration-policy.yaml",
  },
  {
    contract: "contracts/genealogy/unlisted-token.yaml",
    mirror: "platform/helm/genealogy-platform/files/unlisted-token.yaml",
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

test("tree: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tree-clean-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `expected exit 0, got ${r.code}\nstderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree: missing visibility fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tree-vis-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        "contracts/genealogy/tree-policy.yaml",
        "platform/helm/genealogy-platform/files/tree-policy.yaml",
        "    - UNLISTED\n    - PUBLIC",
        "    - PUBLIC",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.visibilities missing required value UNLISTED/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree: missing lifecycle state ARCHIVED fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tree-life-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        "contracts/genealogy/tree-policy.yaml",
        "platform/helm/genealogy-platform/files/tree-policy.yaml",
        "    - ACTIVE\n    - ARCHIVED\n    - DELETED",
        "    - ACTIVE\n    - DELETED",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.lifecycleStates missing required value ARCHIVED/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree: collaboration mode missing APPROVAL_REQUIRED fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tree-collab-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        "contracts/genealogy/tree-policy.yaml",
        "platform/helm/genealogy-platform/files/tree-policy.yaml",
        "    - DIRECT_EDIT\n    - APPROVAL_REQUIRED\n    - HYBRID_BY_ROLE",
        "    - DIRECT_EDIT\n    - HYBRID_BY_ROLE",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.collaborationModes missing required value APPROVAL_REQUIRED/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree: unlisted-token fingerprintAlgorithm != SHA-256 fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tree-fp-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        "contracts/genealogy/unlisted-token.yaml",
        "platform/helm/genealogy-platform/files/unlisted-token.yaml",
        "  fingerprintAlgorithm: SHA-256",
        "  fingerprintAlgorithm: MD5",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.fingerprintAlgorithm must equal "SHA-256"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree: unlisted-token missing robotsDirective noindex fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tree-robots-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        "contracts/genealogy/unlisted-token.yaml",
        "platform/helm/genealogy-platform/files/unlisted-token.yaml",
        "  robotsDirective: noindex",
        "  robotsDirective: index",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.robotsDirective must equal "noindex"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tree-mirror-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      const mirrorPath = join(
        work,
        "platform",
        "helm",
        "genealogy-platform",
        "files",
        "tree-policy.yaml",
      );
      writeFileSync(mirrorPath, readFileSync(mirrorPath, "utf8") + "\n# drift\n");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /chart mirror/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree: forbidden literal (eyJ token) fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tree-token-"));
  try {
    copyFixtureContracts(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, "contracts", "genealogy", "tree-policy.yaml");
      const mirror = join(
        work,
        "platform",
        "helm",
        "genealogy-platform",
        "files",
        "tree-policy.yaml",
      );
      const mutated = readFileSync(path, "utf8") + "\n# debug: token=eyJhbGciOiJIUzI1NiJ9\n";
      writeFileSync(path, mutated);
      writeFileSync(mirror, mutated);
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /forbidden literal/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});
