/**
 * Unit tests for `scripts/lint-tree-projection.mjs`. Each test uses
 * a temp directory that contains a copy of the canonical
 * `contracts/genealogy/tree-projection-policy.yaml` fixture + the
 * JSON schema + the chart mirror; mutations are applied in place
 * so the linter exits non-zero on a regression.
 *
 * Mirrors the structure of `scripts/__tests__/lint-abac-config.test.mjs`
 * (E3.4). Run with `node --test scripts/__tests__/lint-tree-projection.test.mjs`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import { mkdtempSync, mkdirSync, copyFileSync, rmSync, writeFileSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const LINTER = join(ROOT, "scripts", "lint-tree-projection.mjs");

const CONTRACT_REL = "contracts/genealogy/tree-projection-policy.yaml";
const SCHEMA_REL = "contracts/genealogy/tree-projection-policy.schema.json";
const MIRROR_REL = "platform/helm/genealogy-platform/files/tree-projection-policy.yaml";
const CACHE_CONTRACT_REL = "contracts/genealogy/tree-projection-cache.yaml";
const CACHE_MIRROR_REL = "platform/helm/genealogy-platform/files/tree-projection-cache.yaml";

function copyFixture(tmp) {
  const contractDst = join(tmp, CONTRACT_REL);
  const schemaDst = join(tmp, SCHEMA_REL);
  const mirrorDst = join(tmp, MIRROR_REL);
  const cacheContractDst = join(tmp, CACHE_CONTRACT_REL);
  const cacheMirrorDst = join(tmp, CACHE_MIRROR_REL);
  mkdirSync(dirname(contractDst), { recursive: true });
  mkdirSync(dirname(mirrorDst), { recursive: true });
  copyFileSync(join(ROOT, CONTRACT_REL), contractDst);
  copyFileSync(join(ROOT, SCHEMA_REL), schemaDst);
  copyFileSync(join(ROOT, MIRROR_REL), mirrorDst);
  copyFileSync(join(ROOT, CACHE_CONTRACT_REL), cacheContractDst);
  copyFileSync(join(ROOT, CACHE_MIRROR_REL), cacheMirrorDst);
}

function runLinter(tmp, mutation) {
  if (mutation) mutation(tmp);
  const result = spawnSync(process.execPath, [LINTER], {
    cwd: tmp,
    env: { ...process.env, LINT_ROOT: tmp },
    encoding: "utf8",
  });
  return { code: result.status, stdout: result.stdout || "", stderr: result.stderr || "" };
}

function replaceInBoth(work, sourceRel, mirrorRel, find, replace) {
  const sourcePath = join(work, sourceRel);
  const mirrorPath = join(work, mirrorRel);
  const mutated = readFileSync(sourcePath, "utf8").replace(find, replace);
  writeFileSync(sourcePath, mutated);
  writeFileSync(mirrorPath, mutated);
}

test("tree-projection: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tp-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `expected exit 0, got ${r.code}\nstderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree-projection: missing direction SPOUSE_FAN fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tp-dir-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(work, CONTRACT_REL, MIRROR_REL, "    - BOTH\n    - SPOUSE_FAN", "    - BOTH");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.directions missing required value SPOUSE_FAN/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree-projection: maxDepth > 12 fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tp-depth-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(work, CONTRACT_REL, MIRROR_REL, "  maxDepth: 12", "  maxDepth: 13");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.maxDepth must be within \[1, 12\]/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree-projection: etagRequired != true fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tp-etag-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        CONTRACT_REL,
        MIRROR_REL,
        "  etagRequired: true",
        "  etagRequired: false",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.etagRequired must be true/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree-projection: missing view kind hourglass fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tp-view-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(work, CONTRACT_REL, MIRROR_REL, "    - id: hourglass", "    - id: ignored");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.viewKinds missing required view kind hourglass/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree-projection: forbidden audit action fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tp-audit-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        CONTRACT_REL,
        MIRROR_REL,
        "    auditActionOnQuery: treeProjection.queried",
        "    auditActionOnQuery: tree.read",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.audit\.auditActionOnQuery/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree-projection: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tp-mirror-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      const mirrorPath = join(work, MIRROR_REL);
      writeFileSync(mirrorPath, readFileSync(mirrorPath, "utf8") + "\n# drift\n");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /chart mirror/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree-projection: forbidden literal (token=eyJ) fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tp-token-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, CONTRACT_REL);
      const mirror = join(work, MIRROR_REL);
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

test("tree-projection: cacheKeyPrefix drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tp-prefix-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        CONTRACT_REL,
        MIRROR_REL,
        '  cacheKeyPrefix: "gp:{tenant_pseudo_id}:genealogy:projection"',
        '  cacheKeyPrefix: "gp:{tenant_pseudo_id}:genealogy:other"',
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.cacheKeyPrefix/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("tree-projection: freshnessTtlSeconds > ceiling fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "tp-ttl-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      replaceInBoth(
        work,
        CONTRACT_REL,
        MIRROR_REL,
        "  freshnessTtlSeconds: 300\n  freshnessTtlSecondsCeiling: 1800",
        "  freshnessTtlSeconds: 1800\n  freshnessTtlSecondsCeiling: 300",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /freshnessTtlSeconds/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});
