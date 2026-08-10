/**
 * Unit tests for `scripts/lint-dateplace-config.mjs`. Mirrors the
 * structure of `scripts/__tests__/lint-person-config.test.mjs`
 * (E4.2).
 *
 * Run with `node --test scripts/__tests__/lint-dateplace-config.test.mjs`.
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
const LINTER = join(ROOT, "scripts", "lint-dateplace-config.mjs");
const CONTRACT = "contracts/genealogy/date-place-policy.yaml";
const MIRROR = "platform/helm/genealogy-platform/files/date-place-policy.yaml";

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

test("dateplace: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "dateplace-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `stderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("dateplace: missing ABOUT qualifier fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "dateplace-about-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "    - EXACT\n    - ABOUT\n    - BEFORE\n    - AFTER\n    - BETWEEN\n    - UNKNOWN",
        "    - EXACT\n    - BEFORE\n    - AFTER\n    - BETWEEN\n    - UNKNOWN",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.dateQualifiers missing required value ABOUT/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("dateplace: missing VIETNAMESE_LUNISOLAR calendar fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "dateplace-cal-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - VIETNAMESE_LUNISOLAR\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.calendars missing required value VIETNAMESE_LUNISOLAR/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("dateplace: storageTimezone must be UTC", () => {
  const tmp = mkdtempSync(join(tmpdir(), "dateplace-tz-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "  storageTimezone: UTC", "  storageTimezone: GMT");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.storageTimezone must equal "UTC"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("dateplace: livingPersonRedactsByDefault=false fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "dateplace-redact-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  livingPersonRedactsByDefault: true",
        "  livingPersonRedactsByDefault: false",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /livingPersonRedactsByDefault must equal true/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("dateplace: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "dateplace-mirror-"));
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

test("dateplace: forbidden literal fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "dateplace-sec-"));
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
