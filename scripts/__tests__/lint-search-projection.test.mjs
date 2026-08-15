/**
 * Unit tests for `scripts/lint-search-projection.mjs`.
 * Mirrors the structure of `lint-albums-linking.test.mjs` (E7.5).
 *
 * Run with `node --test scripts/__tests__/lint-search-projection.test.mjs`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import {
  copyFileSync,
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const LINTER = join(ROOT, "scripts", "lint-search-projection.mjs");
const CONTRACT = "contracts/search/search-projection-policy.yaml";
const MIRROR =
  "platform/helm/genealogy-platform/files/search-projection-policy.yaml";

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
  const result = spawnSync("node", [LINTER], {
    cwd: tmp,
    encoding: "utf8",
    env: { ...process.env, LINT_ROOT: tmp },
  });
  return {
    status: result.status,
    stdout: (result.stdout || "") + (result.stderr || ""),
    stderr: result.stderr || "",
  };
}

function injectLintRoot() {
  return {
    LINT_ROOT: ROOT,
  };
}

test("clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "search-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp);
    assert.equal(result.status, 0, `expected 0, got ${result.status}\n${result.stdout}\n${result.stderr}`);
    assert.match(result.stdout, /search projection policy contract OK\./);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("missing PERSON document kind fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "search-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "    - PERSON\n    - EVENT\n    - PLACE\n    - SOURCE\n    - CITATION\n    - MEDIA\n    - ALBUM",
        "    - EVENT\n    - PLACE\n    - SOURCE\n    - CITATION\n    - MEDIA\n    - ALBUM",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /searchDocumentKinds/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("dnaBucketAccess MUST equal FORBIDDEN", () => {
  const tmp = mkdtempSync(join(tmpdir(), "search-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "  dnaBucketAccess: FORBIDDEN",
        "  dnaBucketAccess: ALLOWED",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /dnaBucketAccess/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("missing dna/raw prefix fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "search-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "    - dna/raw\n    - dna/match\n    - dna/consent",
        "    - dna/match\n    - dna/consent",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /dna.?bucket.?prefix/i);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("postgresFullTextOnly MUST be true", () => {
  const tmp = mkdtempSync(join(tmpdir(), "search-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "  postgresFullTextOnly: true",
        "  postgresFullTextOnly: false",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /postgresFullTextOnly/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("projectionLagP95BudgetSeconds MUST equal 24", () => {
  const tmp = mkdtempSync(join(tmpdir(), "search-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "  projectionLagP95BudgetSeconds: 24",
        "  projectionLagP95BudgetSeconds: 60",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /projectionLagP95BudgetSeconds/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("terminal DECIDED must have empty transitions", () => {
  const tmp = mkdtempSync(join(tmpdir(), "search-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "    - status: DECIDED\n      transitions: []\n      terminal: true",
        "    - status: DECIDED\n      transitions: [HEALTHY]\n      terminal: true",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /terminal status DECIDED/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("lag invariant violated when p95 >= multiplier × heartbeat", () => {
  const tmp = mkdtempSync(join(tmpdir(), "search-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      let text = readFileSync(path, "utf8").replace(
        "  projectionLagP95BudgetSeconds: 24",
        "  projectionLagP95BudgetSeconds: 600",
      );
      text = text.replace(
        "  projectionLagHeartbeatSeconds: 5",
        "  projectionLagHeartbeatSeconds: 5",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /projection lag invariant violated/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "search-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      writeFileSync(
        join(root, MIRROR),
        readFileSync(join(root, MIRROR), "utf8") + "\n# drift\n",
      );
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /chart mirror drift/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("missing envelope field fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "search-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "        - payload",
        "        - traceId\n        - aggregateVersion",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /envelope field/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("sandbox egress allowlist drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "search-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "  values:\n    - postgres\n    - apicurio\n    - vault-agent\n    - openfga\n    - audit-service\n    - kafka-broker",
        "  values:\n    - postgres\n    - apicurio\n    - vault-agent\n    - openfga\n    - audit-service\n    - kafka-broker\n    - public-internet",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /sandbox egress allowlist/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("auditRequired missing one hook fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "search-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "    - SEARCH_PROJECTION_FACET_CACHE_REBUILT",
        "",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /auditHooks/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});