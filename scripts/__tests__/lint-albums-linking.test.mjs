/**
 * Unit tests for `scripts/lint-albums-linking.mjs`.
 * Mirrors the structure of
 * `lint-media-protected-delivery.test.mjs` (E7.4).
 *
 * Run with `node --test scripts/__tests__/lint-albums-linking.test.mjs`.
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
const LINTER = join(ROOT, "scripts", "lint-albums-linking.mjs");
const CONTRACT = "contracts/media/albums-linking-policy.yaml";
const MIRROR =
  "platform/helm/genealogy-platform/files/albums-linking-policy.yaml";

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

test("albums-linking: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "albums-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `stderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("albums-linking: missing PRIVATE albumVisibility fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "albums-visibility-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "PRIVATE,", "PRIVATE_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.albumVisibilities missing required value 'PRIVATE'/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("albums-linking: missing DERIVED_READY albumLinkableAssetStatus fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "albums-linkable-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "albumLinkableAssetStatuses: [DERIVED_READY]", "albumLinkableAssetStatuses: [DERIVED_READY_REMOVED]");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /albumLinkableAssetStatuses MUST equal \['DERIVED_READY'\]/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("albums-linking: missing dna/raw dnaBucketPrefix fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "albums-dna-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "dnaBucketPrefixes: [dna/raw", "dnaBucketPrefixes: [dna/raw_REMOVED");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.dnaBucketPrefixes missing required value 'dna\/raw'/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("albums-linking: dnaBucketAccess MUST equal FORBIDDEN", () => {
  const tmp = mkdtempSync(join(tmpdir(), "albums-dna-access-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "dnaBucketAccess: FORBIDDEN", "dnaBucketAccess: ALLOWED");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.dnaBucketAccess MUST equal 'FORBIDDEN'/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("albums-linking: onlyDerivedReadyIsLinkable must be true", () => {
  const tmp = mkdtempSync(join(tmpdir(), "albums-linkable-bool-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "onlyDerivedReadyIsLinkable: true",
        "onlyDerivedReadyIsLinkable: false",
      );
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.onlyDerivedReadyIsLinkable must be true/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("albums-linking: maxItemsPerAlbum MUST equal 4096", () => {
  const tmp = mkdtempSync(join(tmpdir(), "albums-maxitems-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "maxItemsPerAlbum: 4096", "maxItemsPerAlbum: 9999");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.maxItemsPerAlbum must equal 4096, got 9999/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("albums-linking: reconciliation P95 budget must be < multiplier × heartbeat", () => {
  const tmp = mkdtempSync(join(tmpdir(), "albums-p95-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "reconciliationP95BudgetSeconds: 150",
        "reconciliationP95BudgetSeconds: 200",
      );
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /reconciliationP95BudgetSeconds \(200s\) must be < multiplier × heartbeat/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("albums-linking: terminal DECIDED must have empty transition list", () => {
  const tmp = mkdtempSync(join(tmpdir(), "albums-terminal-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "    REFERENCE_CHECKED: [DECIDED, SOFT_DELETED, PURGED, DENIED]\n    DECIDED: []",
        "    REFERENCE_CHECKED: [DENIED]\n    DECIDED: [DENIED]",
      );
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /albumAuthorizationMatrix/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("albums-linking: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "albums-mirror-"));
  try {
    copyFixture(tmp);
    const sp = join(tmp, CONTRACT);
    const mutated = readFileSync(sp, "utf8") + "\n# drift\n";
    writeFileSync(sp, mutated);
    const r = runLinter(tmp);
    assert.equal(r.code, 1);
    assert.match(r.stderr, /NOT byte-identical/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("albums-linking: missing outbox envelope field fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "albums-outbox-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "outboxRequiredFields: [eventId, eventType, occurredAt, tenantId, aggregateId, aggregateVersion, traceId]",
        "outboxRequiredFields: [eventId, eventType, occurredAt, tenantId, aggregateId, aggregateVersion]",
      );
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.outboxRequiredFields missing required value 'traceId'/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});