/**
 * Unit tests for `scripts/lint-event-claim-config.mjs`.
 * Mirrors the structure of `scripts/__tests__/
 * lint-relationship-config.test.mjs` (E4.4).
 *
 * Run with `node --test scripts/__tests__/lint-event-claim-config.test.mjs`.
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
const LINTER = join(ROOT, "scripts", "lint-event-claim-config.mjs");
const CONTRACT = "contracts/genealogy/event-claim-policy.yaml";
const MIRROR = "platform/helm/genealogy-platform/files/event-claim-policy.yaml";

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

test("event-claim: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "ec-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `stderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("event-claim: missing BIRTH fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "ec-birth-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - BIRTH\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.lifeEventKinds missing required value BIRTH/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("event-claim: WITNESS role missing fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "ec-witness-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - WITNESS\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.eventParticipantRoles missing required value WITNESS/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("event-claim: IMPORTED+VERIFIED combo is rejected (provenance invariant)", () => {
  const tmp = mkdtempSync(join(tmpdir(), "ec-prov-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      const sp = join(w, CONTRACT);
      const mp = join(w, MIRROR);
      const mutated = readFileSync(sp, "utf8").replace(
        "- provenance: IMPORTED\n        certainties: [HYPOTHESIS, ASSERTED, DISPUTED]",
        "- provenance: IMPORTED\n        certainties: [HYPOTHESIS, ASSERTED, VERIFIED, DISPUTED]",
      );
      writeFileSync(sp, mutated);
      writeFileSync(mp, mutated);
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.provenancePolicy\.allowedCombinations must NEVER pair IMPORTED with VERIFIED/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("event-claim: confidenceRange out of band fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "ec-conf-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    min: 0.0", "    min: -0.1");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.confidenceRange must be within \[0,1\]/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("event-claim: maxParticipantsPerEvent out of range fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "ec-maxp-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "  maxParticipantsPerEvent: 16", "  maxParticipantsPerEvent: 0");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.maxParticipantsPerEvent must be 1\.\.64/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("event-claim: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "ec-mirror-"));
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

test("event-claim: forbidden literal fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "ec-sec-"));
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
