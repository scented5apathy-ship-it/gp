/**
 * Unit tests for `scripts/lint-media-processing-pipeline.mjs`.
 * Mirrors the structure of
 * `lint-media-malware-metadata-pipeline.test.mjs` (E7.2).
 *
 * Run with `node --test scripts/__tests__/lint-media-processing-pipeline.test.mjs`.
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
  "lint-media-processing-pipeline.mjs",
);
const CONTRACT = "contracts/media/media-processing-pipeline-policy.yaml";
const MIRROR =
  "platform/helm/genealogy-platform/files/media-processing-pipeline-policy.yaml";

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

test("media-processing-pipeline: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-processing-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `stderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-processing-pipeline: missing IMAGE_TRANSCODE processingTask fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-processing-task-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - IMAGE_TRANSCODE\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.processingTasks missing required value 'IMAGE_TRANSCODE'/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-processing-pipeline: missing LIBVIPS engine fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-processing-engine-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - LIBVIPS\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.processingEngines missing required value 'LIBVIPS'/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-processing-pipeline: missing PROCESSOR_UNAVAILABLE failureReason fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-processing-reason-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - PROCESSOR_UNAVAILABLE\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.processingFailureReasons missing required value 'PROCESSOR_UNAVAILABLE'/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-processing-pipeline: missing DERIVED_READY terminal status fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-processing-terminal-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "    - DERIVED_READY\n", "");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.processingTerminalStatuses missing required value 'DERIVED_READY'/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-processing-pipeline: processingInputs MUST equal [READY]", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-processing-input-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "processingInputs:\n    - READY",
        "processingInputs:\n    - READY\n    - METADATA_READY",
      );
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.processingInputs MUST equal \['READY'\]/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-processing-pipeline: sandboxOnly toggle fails when disabled", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-processing-sandbox-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "sandboxOnly: true", "sandboxOnly: false");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.sandboxOnly must be true/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-processing-pipeline: imageMagickFallbackPolicy MUST equal NEVER", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-processing-fallback-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  imageMagickFallbackPolicy: NEVER",
        "  imageMagickFallbackPolicy: ALWAYS",
      );
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.imageMagickFallbackPolicy MUST equal 'NEVER'/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-processing-pipeline: terminal DERIVED_READY state transition must be empty", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-processing-ready-terminal-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "DERIVED_READY: []", "DERIVED_READY:\n      - FAILED");
    });
    assert.equal(r.code, 1);
    assert.match(
      r.stderr,
      /spec\.derivedAssetStatusMatrix\['DERIVED_READY'\] MUST be empty/,
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("media-processing-pipeline: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "media-processing-mirror-"));
  try {
    copyFixture(tmp);
    const mirrorPath = join(tmp, MIRROR);
    const txt = readFileSync(mirrorPath, "utf8");
    writeFileSync(
      mirrorPath,
      txt.replace("    - IMAGE_TRANSCODE\n", "    - IMAGE_TRANSCODE_MUTATED\n"),
    );
    const r = runLinter(tmp);
    assert.equal(r.code, 1);
    assert.match(r.stderr, /chart mirror.*NOT byte-identical/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});