/**
 * Unit tests for `scripts/lint-person-config.mjs`. Mirrors the
 * structure of `scripts/__tests__/lint-tree-config.test.mjs` (E4.1).
 *
 * Run with `node --test scripts/__tests__/lint-person-config.test.mjs`.
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
const LINTER = join(ROOT, "scripts", "lint-person-config.mjs");
const CONTRACT = "contracts/genealogy/person-policy.yaml";
const MIRROR = "platform/helm/genealogy-platform/files/person-policy.yaml";

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

function replaceInBoth(work, find, replace) {
  const sp = join(work, CONTRACT);
  const mp = join(work, MIRROR);
  const mutated = readFileSync(sp, "utf8").replace(find, replace);
  writeFileSync(sp, mutated);
  writeFileSync(mp, mutated);
}

test("person: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `stderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person: missing LIVING fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-living-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "    - LIVING\n    - DECEASED\n    - UNKNOWN\n    - INFERRED_LIVING",
        "    - DECEASED\n    - UNKNOWN\n    - INFERRED_LIVING",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.livingStatuses missing required value LIVING/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person: missing privacyLevel PUBLIC fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-priv-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "    - PRIVATE\n    - TREE_DEFAULT\n    - UNLISTED\n    - PUBLIC",
        "    - PRIVATE\n    - TREE_DEFAULT\n    - UNLISTED",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.privacyLevels missing required value PUBLIC/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person: auditActionOnChange drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-audit-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  auditActionOnChange: person.updated",
        "  auditActionOnChange: person.mutated",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.auditActionOnChange must equal "person\.updated"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person: userLinkRequiresVerification=false fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-link-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  userLinkRequiresVerification: true",
        "  userLinkRequiresVerification: false",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /userLinkRequiresVerification must equal true/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-mirror-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      const mp = join(w, MIRROR);
      writeFileSync(mp, readFileSync(mp, "utf8") + "\n# drift\n");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /chart mirror/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("person: forbidden literal fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "person-sec-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      const sp = join(w, CONTRACT);
      const mp = join(w, MIRROR);
      const mutated = readFileSync(sp, "utf8") + "\n# debug: password=Sup3rSecret!\n";
      writeFileSync(sp, mutated);
      writeFileSync(mp, mutated);
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /forbidden literal/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});
