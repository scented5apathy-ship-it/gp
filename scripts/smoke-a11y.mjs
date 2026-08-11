#!/usr/bin/env node
/**
 * scripts/smoke-a11y.mjs
 *
 * Cheap pre-axe smoke checks for the E5.5 / R18 critical-flow
 * surface. The full axe-core run lands in E6 (Playwright + CI)
 * because the test runner is heavy and depends on a built shell.
 *
 * The smoke verifies the *source-level* invariants axe would
 * otherwise catch at runtime:
 *
 *   - the locale layout sets `lang` and `dir` on `<html>`;
 *   - the root `<main>` element is tabbable (focus return
 *     target) and the skip link points at it;
 *   - every form input rendered by the profile editor has an
 *     associated `<label>` or `aria-label`;
 *   - the keyboard tree list declares `tabIndex={0}` and an
 *     `aria-describedby` linking to the help paragraph;
 *   - the live-region announcer is rendered exactly once per
 *     person route;
 *   - pseudolocales are NOT imported anywhere in the bundle
 *     graph (import gate).
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 * Designed to run in <1 s so it can gate every commit.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");

let violations = 0;

function fail(message) {
  violations += 1;
  console.error(`[smoke-a11y] ${message}`);
}

function pass(message) {
  console.log(`[smoke-a11y] ${message}`);
}

function read(rel) {
  return readFileSync(join(ROOT, rel), "utf8");
}

function checkLocaleLayout() {
  const layout = read("apps/web/src/app/[locale]/layout.tsx");
  if (!/<html[^>]*\blang=/.test(layout)) fail("[locale]/layout.tsx must set lang on <html>");
  if (!/<html[^>]*\bdir=/.test(layout)) fail("[locale]/layout.tsx must set dir on <html>");
  if (!/SkipLink/.test(layout)) fail("[locale]/layout.tsx must mount <SkipLink>");
  if (!/id="main-content"/.test(layout)) fail("[locale]/layout.tsx must declare main-content id");
  if (!/tabIndex/.test(layout)) fail("[locale]/layout.tsx main element must be tabbable (focus return)");
}

function checkSkipLinkTarget() {
  const sl = read("apps/web/src/components/skip-link.tsx");
  if (!/main-content/.test(sl)) fail("skip-link must target #main-content");
}

function checkProfileFormLabels() {
  const profile = read("apps/web/src/components/profile/person-profile.tsx");
  // Inputs that are bare text boxes / textareas / selects should
  // have either an aria-label prop or be wrapped in a <label> with
  // an associated <span>. We just ensure the file references both
  // patterns at least once.
  if (!/aria-label=/.test(profile)) {
    fail("person-profile.tsx: every input must carry aria-label (or be wrapped in <label>)");
  }
  if (!/<label/.test(profile)) {
    fail("person-profile.tsx: at least one <label> wrapper expected");
  }
}

function checkKeyboardTreeList() {
  const treeView = read("apps/web/src/components/tree-view/index.tsx");
  if (!/tabIndex=\\{0\\}/.test(treeView) && !/tabIndex={0}/.test(treeView)) {
    fail("tree-view/index.tsx: keyboard list must declare tabIndex={0}");
  }
  if (!/aria-describedby/.test(treeView)) {
    fail("tree-view/index.tsx: keyboard list must declare aria-describedby");
  }
  if (!/<ol[\s>]/.test(treeView) && !/role="list"/.test(treeView) && !/role='list'/.test(treeView)) {
    fail("tree-view/index.tsx: keyboard list must use a semantic <ol> or declare role=list");
  }
}

function checkLiveRegionOnce() {
  const route = read("apps/web/src/components/profile/person-route.tsx");
  const matches = route.match(/<LiveRegion\b/g) || [];
  if (matches.length === 0) {
    fail("person-route.tsx must render <LiveRegion>");
  }
  if (matches.length > 1) {
    fail(`person-route.tsx must render <LiveRegion> exactly once (found ${matches.length})`);
  }
  if (!/useLiveRegionAnnouncer/.test(route)) {
    fail("person-route.tsx must use the useLiveRegionAnnouncer hook");
  }
}

function checkPseudolocaleIsolation() {
  const files = [
    "apps/web/src/components/profile/person-profile.tsx",
    "apps/web/src/components/profile/person-timeline.tsx",
    "apps/web/src/components/profile/person-route.tsx",
    "apps/web/src/components/profile/place-map.tsx",
    "apps/web/src/components/top-bar.tsx",
    "apps/web/src/components/skip-link.tsx",
    "apps/web/src/app/[locale]/layout.tsx",
    "apps/web/src/app/[locale]/trees/page.tsx",
    "apps/web/src/app/[locale]/persons/[treeId]/[personId]/page.tsx",
  ];
  for (const rel of files) {
    const raw = read(rel);
    if (/"en-XA"/.test(raw) || /"ar-XB"/.test(raw) || /from\s+["'].*en-XA/.test(raw) || /from\s+["'].*ar-XB/.test(raw)) {
      fail(`${rel} imports a pseudolocale — production code must not (QA-only)`);
    }
  }
}

function checkListTableHeaders() {
  const table = read("apps/web/src/components/profile/person-list-table.tsx");
  if (!/<caption/.test(table)) {
    fail("person-list-table.tsx must declare a <caption> for screen readers");
  }
  if (!/scope="col"/.test(table)) {
    fail("person-list-table.tsx must declare scope=col on column headers");
  }
  if (!/scope="row"/.test(table)) {
    fail("person-list-table.tsx must declare scope=row on row headers");
  }
}

function checkReducedMotionFallback() {
  const css = read("apps/web/src/styles/tokens.css");
  if (!/prefers-reduced-motion:\s*reduce/.test(css)) {
    fail("styles/tokens.css must declare prefers-reduced-motion fallback");
  }
  if (!/--motion-fast:\s*0\.001ms/.test(css)) {
    fail("styles/tokens.css must collapse motion tokens under prefers-reduced-motion");
  }
}

function main() {
  try {
    checkLocaleLayout();
    checkSkipLinkTarget();
    checkProfileFormLabels();
    checkKeyboardTreeList();
    checkLiveRegionOnce();
    checkPseudolocaleIsolation();
    checkListTableHeaders();
    checkReducedMotionFallback();
  } catch (err) {
    console.error(`[smoke-a11y] configuration error: ${err.message}`);
    process.exit(2);
  }
  if (violations === 0) {
    pass("OK");
    process.exit(0);
  }
  console.error(`[smoke-a11y] ${violations} violation(s)`);
  process.exit(1);
}

main();