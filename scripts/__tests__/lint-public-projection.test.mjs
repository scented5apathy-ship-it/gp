/**
 * Unit tests for `scripts/lint-public-projection.mjs`.
 * Run with `node --test scripts/__tests__/lint-public-projection.test.mjs`.
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
const LINTER = join(ROOT, "scripts", "lint-public-projection.mjs");
const CONTRACT = "contracts/search/public-projection-policy.yaml";
const MIRROR =
  "platform/helm/genealogy-platform/files/public-projection-policy.yaml";

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

test("clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "public-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp);
    assert.equal(result.status, 0);
    assert.match(result.stdout, /public projection policy contract OK\./);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("missing PUBLIC visibility scope fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "public-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "  values:\n    - PUBLIC\n    - UNLISTED",
        "  values:\n    - UNLISTED",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /publicProjectionVisibilityScopes/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("dnaBucketAccess MUST equal FORBIDDEN", () => {
  const tmp = mkdtempSync(join(tmpdir(), "public-proj-"));
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

test("terminal PURGED must have empty transitions", () => {
  const tmp = mkdtempSync(join(tmpdir(), "public-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "    - status: PURGED\n      transitions: []\n      terminal: true",
        "    - status: PURGED\n      transitions: [INDEXED]\n      terminal: true",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /terminal status PURGED/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("sitemap heartbeat invariant violated when p95 >= 2 × heartbeat", () => {
  const tmp = mkdtempSync(join(tmpdir(), "public-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "  sitemapRebuildP95BudgetSeconds: 45",
        "  sitemapRebuildP95BudgetSeconds: 600",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /sitemap invariant/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("projection batch size invariant when batch != 2 × outbox", () => {
  const tmp = mkdtempSync(join(tmpdir(), "public-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "  projectionBatchSize: 512",
        "  projectionBatchSize: 1024",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /projection batch invariant/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "public-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      writeFileSync(join(root, MIRROR), readFileSync(join(root, MIRROR), "utf8") + "\n# drift\n");
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /chart mirror drift/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("unlistedReturnsNoindex MUST be true", () => {
  const tmp = mkdtempSync(join(tmpdir(), "public-proj-"));
  try {
    copyFixture(tmp);
    const result = runLinter(tmp, (root) => {
      const path = join(root, CONTRACT);
      const text = readFileSync(path, "utf8").replace(
        "  unlistedReturnsNoindex: true",
        "  unlistedReturnsNoindex: false",
      );
      writeFileSync(path, text);
      writeFileSync(join(root, MIRROR), text);
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /unlistedReturnsNoindex/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});