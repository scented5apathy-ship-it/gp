/**
 * Unit tests for `scripts/lint-research-config.mjs`. Mirrors
 * the structure of `scripts/__tests__/lint-relationship-config.test.mjs`
 * (E4.4).
 *
 * Run with `node --test scripts/__tests__/lint-research-config.test.mjs`.
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
const LINTER = join(ROOT, "scripts", "lint-research-config.mjs");
const CONTRACT = "contracts/research/research-policy.yaml";
const MIRROR = "platform/helm/genealogy-platform/files/research-policy.yaml";

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

test("research: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "research-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `stderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("research: missing PRIMARY sourceKind fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "research-source-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - PRIMARY\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.sourceKinds missing required value PRIMARY/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("research: missing TRANSCRIPT citationQuality fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "research-quote-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - TRANSCRIPT\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.citationQualities missing required value TRANSCRIPT/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("research: hypothesisStatusMatrix SUPERSEDED terminal empty", () => {
  const tmp = mkdtempSync(join(tmpdir(), "research-supersede-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "    SUPERSEDED: []\n",
        "    SUPERSEDED: [DRAFT]\n",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /is terminal and must have empty transitions/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("research: researchTaskStatusMatrix missing transition target fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "research-matrix-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "    OPEN:\n      - IN_PROGRESS\n      - BLOCKED\n      - ABANDONED\n",
        "    OPEN:\n      - UNKNOWN_STATE\n",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /references unknown status UNKNOWN_STATE/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("research: confidence bounds out of range fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "research-conf-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "  confidenceMin: 0.0\n", "  confidenceMin: -0.1\n");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.confidenceMin must equal 0\.0/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("research: missing invariant code fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "research-inv-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      const mutated = readFileSync(join(w, CONTRACT), "utf8")
        .replace("    - SOURCE_POINTER_REQUIRES_ATTACHMENT\n", "");
      writeFileSync(join(w, CONTRACT), mutated);
      writeFileSync(join(w, MIRROR), mutated);
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.invariants missing required value SOURCE_POINTER_REQUIRES_ATTACHMENT/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("research: forbidden literal fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "research-sec-"));
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

test("research: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "research-mirror-"));
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

test("research: minConflictParticipants != 2 fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "research-part-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "  minConflictParticipants: 2\n", "  minConflictParticipants: 1\n");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.minConflictParticipants must equal 2/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("research: missing auditKey actorPseudoId fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "research-audit-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - actorPseudoId\n", "    - actorDisplayName\n");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.auditRequiredKeys missing required value actorPseudoId/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("research: missing FORBIDDEN AUDIT_KEY_FORBIDDEN invariant fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "research-audit-key-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - AUDIT_KEY_FORBIDDEN\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.invariants missing required value AUDIT_KEY_FORBIDDEN/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("research: empty forbiddenPayloadPatterns fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "research-payload-"));
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
