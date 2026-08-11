/**
 * Unit tests for `scripts/lint-print-export.mjs`. Mirrors the
 * structure of `scripts/__tests__/lint-a11y-i18n.test.mjs`:
 *
 *   - `setupTmp` snapshots the touched files into a temp tree;
 *   - each test mutates one file and asserts the linter exits
 *     non-zero with the expected stderr substring;
 *   - the first test asserts the baseline exits 0.
 *
 * Run with `node --test scripts/__tests__/lint-print-export.test.mjs`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import {
  mkdtempSync,
  mkdirSync,
  copyFileSync,
  rmSync,
  readFileSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const LINTER = join(ROOT, "scripts", "lint-print-export.mjs");

const FILES = [
  "apps/web/src/lib/print/print-policy.ts",
  "apps/web/src/lib/print/export-job.ts",
  "apps/web/src/lib/print/redaction-preview.ts",
  "apps/web/src/lib/print/signed-url-handle.ts",
  "apps/web/src/lib/print/client.ts",
  "apps/web/src/components/print/export-request-panel.tsx",
  "apps/web/src/components/print/print-toolbar.tsx",
  "apps/web/src/styles/print.css",
  "apps/web/src/styles/globals.css",
  "apps/web/src/i18n/messages/en.ts",
  "apps/web/src/i18n/messages/vi.ts",
  "apps/web/src/i18n/messages/en-XA.ts",
  "apps/web/src/i18n/messages/ar-XB.ts",
  "apps/web/src/components/profile/person-route.tsx",
  "apps/web/src/components/tree-view/tree-view-route.tsx",
];

function setupTmp() {
  const tmp = mkdtempSync(join(tmpdir(), "print-export-"));
  for (const rel of FILES) {
    const dst = join(tmp, rel);
    mkdirSync(dirname(dst), { recursive: true });
    copyFileSync(join(ROOT, rel), dst);
  }
  return tmp;
}

function runLinter(tmp, mutate) {
  if (mutate) mutate(tmp);
  const result = spawnSync(process.execPath, [LINTER], {
    cwd: tmp,
    env: { ...process.env, LINT_ROOT: tmp },
    encoding: "utf8",
  });
  return { code: result.status, stdout: result.stdout || "", stderr: result.stderr || "" };
}

test("print-export: clean tree exits 0", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `expected exit 0, got ${r.code}\nstderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("print-export: vi.ts missing a print key fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/i18n/messages/vi.ts");
      const raw = readFileSync(path, "utf8");
      const next = raw.replace(/toolbarHeading: "/, "toolbarHeading_DELETED: \"");
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /parity|vi\.ts/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("print-export: en-XA missing watermark sub-namespace fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/i18n/messages/en-XA.ts");
      const raw = readFileSync(path, "utf8");
      const next = raw.replace(/watermark: \{/, "watermark_DELETED: {");
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /en-XA|sub-namespace|watermark/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("print-export: print.css without prefers-reduced-motion fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/styles/print.css");
      const raw = readFileSync(path, "utf8");
      const next = raw.replace(
        /@media\s+print\s+and\s*\(prefers-reduced-motion: reduce\)/,
        "@media print and (prefers-color-scheme: dark)",
      );
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /prefers-reduced-motion/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("print-export: print.css without per-node page-break fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/styles/print.css");
      const raw = readFileSync(path, "utf8");
      const next = raw.replace(/"per-node"/, "\"per-foo\"");
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /per-node/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("print-export: export panel without LiveRegion fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/components/print/export-request-panel.tsx");
      const raw = readFileSync(path, "utf8");
      const next = raw.replace(/LiveRegion/g, "RemovedLiveRegion");
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /LiveRegion/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("print-export: print toolbar without data-no-print fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/components/print/print-toolbar.tsx");
      const raw = readFileSync(path, "utf8");
      const next = raw.replace(/data-no-print="true"/, "");
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /data-no-print/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("print-export: BFF shim missing Idempotency-Key header fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/lib/print/client.ts");
      const raw = readFileSync(path, "utf8");
      const next = raw.replaceAll("Idempotency-Key", "Removed-Key");
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /Idempotency-Key/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("print-export: print-policy without clampSignedUrlTtlSeconds fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/lib/print/print-policy.ts");
      const raw = readFileSync(path, "utf8");
      const next = raw.replace(/clampSignedUrlTtlSeconds/g, "clampRemoved");
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /clampSignedUrlTtlSeconds/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("print-export: signed-url-handle without forbidden schemes fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/lib/print/signed-url-handle.ts");
      const raw = readFileSync(path, "utf8");
      // Replace every "javascript:" and "file:" literal so the
      // linter regex /javascript:|file:/ cannot find the guard.
      const next = raw.replaceAll("javascript:", "safeScheme:").replaceAll("file:", "okFile:");
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /javascript|file/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("print-export: pseudolocale import in panel fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/components/print/export-request-panel.tsx");
      const raw = readFileSync(path, "utf8");
      const next = `${raw}\nimport { enXA } from "@/i18n/messages/en-XA";\n`;
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /pseudolocale|en-XA/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("print-export: forbidden literal token in source fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/lib/print/print-policy.ts");
      const raw = readFileSync(path, "utf8");
      const next = `${raw}\n// debug: AKIAIOSFODNN7EXAMPLE\n`;
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /forbidden literal/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});