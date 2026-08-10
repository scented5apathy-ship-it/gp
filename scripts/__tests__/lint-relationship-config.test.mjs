/**
 * Unit tests for `scripts/lint-relationship-config.mjs`. Mirrors
 * the structure of `scripts/__tests__/lint-dateplace-config.test.mjs`
 * (E4.3).
 *
 * Run with `node --test scripts/__tests__/lint-relationship-config.test.mjs`.
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
const LINTER = join(ROOT, "scripts", "lint-relationship-config.mjs");
const CONTRACT = "contracts/genealogy/relationship-graph-policy.yaml";
const MIRROR = "platform/helm/genealogy-platform/files/relationship-graph-policy.yaml";

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

test("relationship: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "rel-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `stderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("relationship: missing BIOLOGICAL_PARENT fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "rel-bio-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - BIOLOGICAL_PARENT\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.relationshipKinds missing required value BIOLOGICAL_PARENT/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("relationship: cyclePolicy != deny fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "rel-cycle-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "  cyclePolicy: deny", "  cyclePolicy: allow");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.cyclePolicy must equal "deny"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("relationship: chronologicalConflictPolicy != warn-only fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "rel-chrono-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  chronologicalConflictPolicy: warn-only",
        "  chronologicalConflictPolicy: deny",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.chronologicalConflictPolicy must equal "warn-only"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("relationship: maxParticipantsPerRelationship out of range fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "rel-max-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  maxParticipantsPerRelationship: 8",
        "  maxParticipantsPerRelationship: 0",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.maxParticipantsPerRelationship must be 1\.\.16/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("relationship: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "rel-mirror-"));
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

test("relationship: forbidden literal fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "rel-sec-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      const sp = join(w, CONTRACT);
      const mp = join(w, MIRROR);
      const mutated = readFileSync(sp, "utf8") + "\n# debug: token=abcdefghijklmnopqrst\n";
      writeFileSync(sp, mutated);
      writeFileSync(mp, mutated);
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /forbidden literal/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});
