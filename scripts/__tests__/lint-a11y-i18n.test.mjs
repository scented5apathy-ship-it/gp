/**
 * Unit tests for `scripts/lint-a11y-i18n.mjs`. Mirrors the
 * structure of `scripts/__tests__/lint-profile-editor.test.mjs`
 * (E5.4): each test mutates a temp copy of the catalogues /
 * components and asserts the linter exits non-zero on a
 * regression.
 *
 * Run with `node --test scripts/__tests__/lint-a11y-i18n.test.mjs`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import { mkdtempSync, mkdirSync, copyFileSync, rmSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const LINTER = join(ROOT, "scripts", "lint-a11y-i18n.mjs");

const FILES = [
  "apps/web/src/i18n/messages/en.ts",
  "apps/web/src/i18n/messages/vi.ts",
  "apps/web/src/i18n/messages/en-XA.ts",
  "apps/web/src/i18n/messages/ar-XB.ts",
  "apps/web/src/styles/tokens.css",
  "apps/web/src/i18n/index.ts",
  "apps/web/src/components/tree-view/index.tsx",
  "apps/web/src/lib/tree-view/keyboard-navigation.ts",
  "apps/web/src/components/profile/person-route.tsx",
  "apps/web/src/components/profile/person-list-table.tsx",
  "apps/web/src/lib/i18n/name-order.ts",
  "apps/web/src/app/[locale]/layout.tsx",
  "apps/web/src/app/[locale]/trees/page.tsx",
  "apps/web/src/app/[locale]/persons/[treeId]/[personId]/page.tsx",
  "apps/web/src/components/skip-link.tsx",
  "apps/web/src/components/top-bar.tsx",
  "apps/web/src/components/profile/person-profile.tsx",
  "apps/web/src/components/profile/person-timeline.tsx",
  "apps/web/src/lib/a11y/use-prefers-reduced-motion.ts",
  "apps/web/src/lib/a11y/use-focus-return.ts",
  "apps/web/src/lib/a11y/use-live-region-announcer.ts",
  "apps/web/src/lib/a11y/live-region.tsx",
  "apps/web/src/lib/a11y/sr-only.tsx",
];

function setupTmp() {
  const tmp = mkdtempSync(join(tmpdir(), "a11y-i18n-"));
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

test("a11y-i18n: clean tree exits 0", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `expected exit 0, got ${r.code}\nstderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("a11y-i18n: vi.ts missing an en.ts key fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/i18n/messages/vi.ts");
      const raw = readFileSync(path, "utf8");
      // Drop a whole key/value pair that exists in en.ts.
      const next = raw.replace(/\n {4}sectionLabel: "Cây gia phả",\n/, "\n");
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0, "vi.ts parity must be enforced");
    assert.match(r.stderr, /parity|vi\.ts/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("a11y-i18n: en-XA missing a key fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/i18n/messages/en-XA.ts");
      const raw = readFileSync(path, "utf8");
      const next = raw.replace(/selectedAnnounce: /, "selectedAnnounce_DELETED: ");
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0, "pseudolocale parity must be enforced");
    assert.match(r.stderr, /en-XA/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("a11y-i18n: tokens.css without prefers-reduced-motion fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/styles/tokens.css");
      const raw = readFileSync(path, "utf8");
      const next = raw.replace(/@media\s*\(prefers-reduced-motion: reduce\)/, "@media (prefers-color-scheme: dark)");
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0, "reduced-motion media query must be present");
    assert.match(r.stderr, /prefers-reduced-motion/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("a11y-i18n: tree-view without keyboard-navigation helper fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/components/tree-view/index.tsx");
      const raw = readFileSync(path, "utf8");
      const next = raw.replace(/keyboard-navigation/g, "removed-helper");
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /keyboard-navigation/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("a11y-i18n: person-route without live region fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/components/profile/person-route.tsx");
      const raw = readFileSync(path, "utf8");
      const next = raw.replace(/useLiveRegionAnnouncer|live-region-announcer/g, "removed");
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /live-region/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("a11y-i18n: production component importing a pseudolocale fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/components/profile/person-profile.tsx");
      const raw = readFileSync(path, "utf8");
      const next = `${raw}\nimport { enXA } from "@/i18n/messages/en-XA";\n`;
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /pseudolocale/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("a11y-i18n: forbidden literal token fails", () => {
  const tmp = setupTmp();
  try {
    const r = runLinter(tmp, (work) => {
      const path = join(work, "apps/web/src/lib/i18n/name-order.ts");
      const raw = readFileSync(path, "utf8");
      // Inject an AWS access key into a comment-like string; the
      // token regex matches anywhere in the file.
      const next = `${raw}\n// debug note: AKIAIOSFODNN7EXAMPLE\n`;
      writeFileSync(path, next);
    });
    assert.notEqual(r.code, 0);
    assert.match(r.stderr, /forbidden literal/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});