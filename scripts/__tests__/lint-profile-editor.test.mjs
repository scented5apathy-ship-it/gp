/**
 * Unit tests for `scripts/lint-profile-editor.mjs`. Mirrors the
 * structure of `scripts/__tests__/lint-tree-projection.test.mjs`
 * (E5.2): each test mutates a temp copy of the canonical
 * `contracts/openapi/bff/v1/person.yaml` fixture and asserts the
 * linter exits non-zero on a regression.
 *
 * Run with `node --test scripts/__tests__/lint-profile-editor.test.mjs`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import { mkdtempSync, mkdirSync, copyFileSync, rmSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const LINTER = join(ROOT, "scripts", "lint-profile-editor.mjs");
const CONTRACT_REL = "contracts/openapi/bff/v1/person.yaml";

function copyFixture(tmp) {
  const dst = join(tmp, CONTRACT_REL);
  mkdirSync(dirname(dst), { recursive: true });
  copyFileSync(join(ROOT, CONTRACT_REL), dst);
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

test("profile-editor: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "pe-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `expected exit 0, got ${r.code}\nstderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("profile-editor: missing LivingStatus closed-set entry fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "pe-living-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, CONTRACT_REL);
      const mutated = readFileSync(path, "utf8").replace(
        "enum: [LIVING, PRESUMED_LIVING, DECEASED, PRESUMED_DECEASED, UNKNOWN]",
        "enum: [LIVING, PRESUMED_LIVING, DECEASED, UNKNOWN]",
      );
      writeFileSync(path, mutated);
    });
    assert.notEqual(r.code, 0, "linter should fail when a required LivingStatus value is removed");
    assert.match(r.stderr, /LivingStatus/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("profile-editor: PersonPatch without additionalProperties:false fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "pe-patch-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, CONTRACT_REL);
      const mutated = readFileSync(path, "utf8").replace(
        "additionalProperties: false",
        "additionalProperties: true",
      );
      writeFileSync(path, mutated);
    });
    assert.notEqual(
      r.code,
      0,
      "linter should fail when PersonPatch accepts arbitrary keys (R10)",
    );
    assert.match(r.stderr, /PersonPatch\.additionalProperties/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("profile-editor: PlaceLookupResult without degraded field fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "pe-place-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, CONTRACT_REL);
      // Drop the entire degraded field (including its description).
      const mutated = readFileSync(path, "utf8").replace(
        /\s*degraded:\s*\n\s+type: boolean\s*\n\s+description: \|\s*\n\s+`true` when the configured provider is unavailable[\s\S]*?\.\n/,
        "\n",
      );
      writeFileSync(path, mutated);
    });
    assert.notEqual(r.code, 0, "linter should fail when degraded boolean is missing");
    assert.match(r.stderr, /degraded/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("profile-editor: forbidden literal token fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "pe-secret-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, CONTRACT_REL);
      const mutated = readFileSync(path, "utf8").replace(
        "description: |\n    Server-side person read/write",
        "description: |\n    AKIAIOSFODNN7EXAMPLE leaked credential",
      );
      writeFileSync(path, mutated);
    });
    assert.notEqual(r.code, 0, "linter should fail when a forbidden literal is present");
    assert.match(r.stderr, /forbidden literal/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("profile-editor: TimelineEvent kind missing BIRTH fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "pe-timeline-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, CONTRACT_REL);
      const mutated = readFileSync(path, "utf8").replace(
        "- BIRTH\n            - DEATH",
        "- DEATH",
      );
      writeFileSync(path, mutated);
    });
    assert.notEqual(r.code, 0, "linter should fail when BIRTH is removed from TimelineEvent.kind");
    assert.match(r.stderr, /TimelineEvent\.kind/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("profile-editor: PersonPermissions missing person.delete action fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "pe-perms-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, CONTRACT_REL);
      const mutated = readFileSync(path, "utf8").replace(
        "- person.view\n                  - person.edit\n                  - person.delete",
        "- person.view\n                  - person.edit",
      );
      writeFileSync(path, mutated);
    });
    assert.notEqual(r.code, 0, "linter should fail when person.delete action is missing");
    assert.match(r.stderr, /PersonPermissions/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("profile-editor: missing If-Match on PUT fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "pe-ifmatch-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (work) => {
      const path = join(work, CONTRACT_REL);
      const mutated = readFileSync(path, "utf8").replace(
        "        - name: If-Match\n          in: header\n          required: true",
        "        - name: If-Match\n          in: header\n          required: false",
      );
      writeFileSync(path, mutated);
    });
    assert.notEqual(r.code, 0, "linter should fail when If-Match is no longer required on PUT");
    assert.match(r.stderr, /If-Match/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});