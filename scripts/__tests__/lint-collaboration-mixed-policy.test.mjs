/**
 * Unit tests for `scripts/lint-collaboration-mixed-policy.mjs`.
 * Mirrors the structure of `lint-collaboration-config.test.mjs`
 * (E6.2).
 *
 * Run with `node --test scripts/__tests__/lint-collaboration-mixed-policy.test.mjs`.
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
const LINTER = join(ROOT, "scripts", "lint-collaboration-mixed-policy.mjs");
const CONTRACT = "contracts/collaboration/mixed-collaboration-policy.yaml";
const MIRROR = "platform/helm/genealogy-platform/files/collaboration-mixed-policy.yaml";

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

test("mixed-collaboration: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-mixed-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `stderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("mixed-collaboration: missing TENANT_ADMIN collaborationRole fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-mixed-role-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - TENANT_ADMIN\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.collaborationRoles missing required value TENANT_ADMIN/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("mixed-collaboration: missing CUSTOM treeBranch fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-mixed-branch-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - CUSTOM\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.treeBranches missing required value CUSTOM/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("mixed-collaboration: missing routingDecision DENY fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-mixed-decision-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - DENY\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.routingDecisions missing required value DENY/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("mixed-collaboration: directEditMatrix entry with bad role fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-mixed-matrixrole-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "    - role: TENANT_ADMIN\n      branch: TRUNK\n      resourceType: PERSON\n      decision: DIRECT_EDIT\n",
        "    - role: NOT_A_ROLE\n      branch: TRUNK\n      resourceType: PERSON\n      decision: DIRECT_EDIT\n",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /directEditMatrix\[0\]\.role NOT_A_ROLE/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("mixed-collaboration: missing invariant CONFLICT_BASE_VERSION_STALE fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-mixed-inv-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - CONFLICT_BASE_VERSION_STALE\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.invariants missing required value CONFLICT_BASE_VERSION_STALE/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("mixed-collaboration: forbidden literal fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-mixed-sec-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      const sp = join(w, CONTRACT);
      const mp = join(w, MIRROR);
      const mutated = readFileSync(sp, "utf8")
        + "\n# debug: token=abcdefghijklmnopqrst\n";
      writeFileSync(sp, mutated);
      writeFileSync(mp, mutated);
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /forbidden literal/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("mixed-collaboration: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-mixed-mirror-"));
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

test("mixed-collaboration: empty forbiddenPayloadPatterns fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-mixed-payload-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  forbiddenPayloadPatterns:\n    - credential\n    - api[_-]?key\n    - private[_-]?key\n    - authorization\n    - bearer\n    - raw[_-]?dna\n    - raw[_-]?ssn\n    - raw[_-]?passport\n",
        "  forbiddenPayloadPatterns: []\n",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.forbiddenPayloadPatterns must be a non-empty array/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("mixed-collaboration: flagsmith contractSupersedesFlag toggle fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-mixed-toggle-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  flagsmithRolloutContractSupersedesFlag: true\n",
        "  flagsmithRolloutContractSupersedesFlag: false\n",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.flagsmithRolloutContractSupersedesFlag must equal true/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("mixed-collaboration: missing patchValidationForbiddenField dnaRawData fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-mixed-field-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - dnaRawData\n", "    - dnaRawData_REMOVED\n");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.patchValidationForbiddenFields missing required value dnaRawData/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});
