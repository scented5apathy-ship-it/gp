/**
 * Unit tests for `scripts/lint-media-protected-delivery.mjs`.
 * Mirrors the structure of
 * `lint-media-processing-pipeline.test.mjs` (E7.3).
 *
 * Run with `node --test scripts/__tests__/lint-media-protected-delivery.test.mjs`.
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
const LINTER = join(
  ROOT,
  "scripts",
  "lint-media-protected-delivery.mjs",
);
const CONTRACT = "contracts/media/media-protected-delivery-policy.yaml";
const MIRROR =
  "platform/helm/genealogy-platform/files/media-protected-delivery-policy.yaml";

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

test("media-protected-delivery: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-delivery-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `stderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-protected-delivery: missing DOWNLOAD subject fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-delivery-subject-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - DOWNLOAD\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.deliverySubjects missing required value 'DOWNLOAD'/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-protected-delivery: missing OPENFGA_DENY failure reason fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-delivery-reason-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - OPENFGA_DENY\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.deliveryFailureReasons missing required value 'OPENFGA_DENY'/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-protected-delivery: missing DERIVED_READY linkable status fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-delivery-linkable-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "deliveryLinkableStatuses:\n    - DERIVED_READY",
        "deliveryLinkableStatuses:\n    - READY",
      );
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.deliveryLinkableStatuses MUST equal \['DERIVED_READY'\]/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-protected-delivery: deliveryLinkableStatuses MUST equal [DERIVED_READY]", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-delivery-linkable-equiv-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "deliveryLinkableStatuses:\n    - DERIVED_READY",
        "deliveryLinkableStatuses:\n    - DERIVED_READY\n    - READY",
      );
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.deliveryLinkableStatuses MUST equal \['DERIVED_READY'\]/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-protected-delivery: dnaBucketAccess MUST equal FORBIDDEN", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-delivery-dna-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  dnaBucketAccess: FORBIDDEN",
        "  dnaBucketAccess: ALLOWED",
      );
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

test("media-protected-delivery: deliveryDenyBeforeOpenFgaAndAbac must be true", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-delivery-deny-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  deliveryDenyBeforeOpenFgaAndAbac: true",
        "  deliveryDenyBeforeOpenFgaAndAbac: false",
      );
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.deliveryDenyBeforeOpenFgaAndAbac must be true/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-protected-delivery: signedUrlTtlCeilingSeconds MUST equal 900", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-delivery-ttl-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  signedUrlTtlCeilingSeconds: 900",
        "  signedUrlTtlCeilingSeconds: 1800",
      );
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.signedUrlTtlCeilingSeconds must equal 900/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-protected-delivery: terminal DENIED state transition must be empty", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-delivery-denied-terminal-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    DENIED: []", "    DENIED:\n      - DECIDED");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.deliveryAuthorizationMatrix\['DENIED'\] MUST be empty/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-protected-delivery: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-delivery-mirror-"));
  try {
    copyFixture(tmp);
    const mirrorPath = join(tmp, MIRROR);
    const txt = readFileSync(mirrorPath, "utf8");
    writeFileSync(
      mirrorPath,
      txt.replace("    - DOWNLOAD\n", "    - DOWNLOAD_MUTATED\n"),
    );
    const r = runLinter(tmp);
    assert.equal(r.code, 1);
    assert.match(r.stderr, /chart mirror.*NOT byte-identical/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});