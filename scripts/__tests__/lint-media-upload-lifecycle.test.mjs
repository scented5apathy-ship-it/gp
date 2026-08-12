/**
 * Unit tests for `scripts/lint-media-upload-lifecycle.mjs`.
 * Mirrors the structure of
 * `lint-collaboration-comments-activity.test.mjs` (E6.4).
 *
 * Run with `node --test scripts/__tests__/lint-media-upload-lifecycle.test.mjs`.
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
const LINTER = join(ROOT, "scripts", "lint-media-upload-lifecycle.mjs");
const CONTRACT = "contracts/media/upload-lifecycle-policy.yaml";
const MIRROR = "platform/helm/genealogy-platform/files/media-upload-lifecycle-policy.yaml";

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

test("media-upload-lifecycle: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-upload-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `stderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-upload-lifecycle: missing REQUESTED uploadSessionStatus fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-upload-status-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "REQUESTED,", "REQUESTED_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.uploadSessionStatuses missing required value 'REQUESTED'/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-upload-lifecycle: missing ATTACHMENT uploadSessionIntent fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-upload-intent-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "ATTACHMENT,", "ATTACHMENT_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.uploadSessionIntents missing required value 'ATTACHMENT'/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-upload-lifecycle: missing IMAGE mediaCategory fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-upload-cat-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "IMAGE,", "IMAGE_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.mediaCategories missing required value 'IMAGE'/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-upload-lifecycle: missing SHA256 checksumAlgorithm fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-upload-checksum-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "[SHA256,", "[SHA256_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.checksumAlgorithms missing required value 'SHA256'/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-upload-lifecycle: missing READY finalizeOutcome fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-upload-finalize-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "[READY,", "[READY_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.finalizeOutcomes missing required value 'READY'/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-upload-lifecycle: missing QUOTA_EXCEEDED_BYTES quotaDenialReason fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-upload-quota-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "QUOTA_EXCEEDED_BYTES,", "QUOTA_EXCEEDED_BYTES_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.quotaDenialReasons missing required value 'QUOTA_EXCEEDED_BYTES'/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-upload-lifecycle: missing SESSION_TTL_EXPIRED abandonedMultipartReason fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-upload-abandoned-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "SESSION_TTL_EXPIRED,", "SESSION_TTL_EXPIRED_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.abandonedMultipartReasons missing required value 'SESSION_TTL_EXPIRED'/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-upload-lifecycle: dnaBucketAccess toggle fails when allowed", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-upload-dna-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "  dnaBucketAccess: FORBIDDEN\n", "  dnaBucketAccess: ALLOWED\n");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /dnaBucketAccess must equal "FORBIDDEN"/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-upload-lifecycle: finalizeIdempotentOnChecksum toggle fails when disabled", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-upload-idem-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  finalizeIdempotentOnChecksum: true\n",
        "  finalizeIdempotentOnChecksum: false\n",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /finalizeIdempotentOnChecksum must equal true/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-upload-lifecycle: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-upload-mirror-"));
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
