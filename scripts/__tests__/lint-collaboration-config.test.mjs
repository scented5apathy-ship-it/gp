/**
 * Unit tests for `scripts/lint-collaboration-config.mjs`.
 * Mirrors the structure of `lint-research-config.test.mjs`
 * (E6.1).
 *
 * Run with `node --test scripts/__tests__/lint-collaboration-config.test.mjs`.
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
const LINTER = join(ROOT, "scripts", "lint-collaboration-config.mjs");
const CONTRACT = "contracts/collaboration/collaboration-policy.yaml";
const MIRROR = "platform/helm/genealogy-platform/files/collaboration-proposal-policy.yaml";

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

test("collaboration: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `stderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("collaboration: missing PERSON proposalKind fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-kind-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - PERSON\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.proposalKinds missing required value PERSON/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("collaboration: missing PARTIAL_MERGE proposalDecision fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-decision-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - PARTIAL_MERGE\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.proposalDecisions missing required value PARTIAL_MERGE/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("collaboration: proposalStatusMatrix MERGED terminal empty enforced", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-matrix-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "    MERGED: []\n",
        "    MERGED: [DRAFT]\n",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /is terminal and must have empty transitions/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("collaboration: missing reviewStatus APPROVED fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-review-status-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  reviewStatuses:\n    - PENDING\n    - APPROVED\n    - REJECTED\n    - CHANGES_REQUESTED\n    - PARTIAL_MERGED\n",
        "  reviewStatuses:\n    - PENDING\n    - REJECTED\n    - CHANGES_REQUESTED\n    - PARTIAL_MERGED\n",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.reviewStatuses missing required value APPROVED/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("collaboration: missing forbidden domainCommandField dnaRawData fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-field-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - dnaRawData\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.forbiddenDomainCommandFields missing required value dnaRawData/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("collaboration: missing forbidden proposalKind operation fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-kind-op-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "    PERSON:\n      - SET_TREE_VISIBILITY\n",
        "    PERSON: []\n",
      );
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.forbiddenProposalKindOperations\.PERSON missing SET_TREE_VISIBILITY/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("collaboration: missing invariant PROPOSAL_REAUTHORIZATION_ABAC_DENIED fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-inv-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - PROPOSAL_REAUTHORIZATION_ABAC_DENIED\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.invariants missing required value PROPOSAL_REAUTHORIZATION_ABAC_DENIED/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("collaboration: forbidden literal fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-sec-"));
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

test("collaboration: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-mirror-"));
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

test("collaboration: empty forbiddenPayloadPatterns fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-payload-"));
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

test("collaboration: missing reAuthorizationOnSubmit toggle fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-toggle-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "  reAuthorizationOnSubmit: true\n", "  reAuthorizationOnSubmit: false\n");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.reAuthorizationOnSubmit must equal true/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});