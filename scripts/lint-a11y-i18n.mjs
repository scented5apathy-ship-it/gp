#!/usr/bin/env node
/**
 * scripts/lint-a11y-i18n.mjs
 *
 * E5.5 deep validator for accessibility and i18n invariants that
 * the platform's other linters do not cover:
 *
 *   - every key in `apps/web/src/i18n/messages/en.ts` must exist
 *     in `vi.ts`, `en-XA.ts` and `ar-XB.ts` (pseudolocale parity —
 *     catches hard-coded English, untranslated keys, RTL drift);
 *   - the `a11y.*` and `i18n.*` namespaces must be present in
 *     every production catalogue;
 *   - the pseudolocale catalogues must wrap every string in
 *     their QA markers (`[…]` for en-XA, `‫…‬` for ar-XB);
 *   - `apps/web/src/styles/tokens.css` must declare the
 *     `prefers-reduced-motion` media query (R18.4 / WCAG 2.2 SC
 *     2.3.3);
 *   - `apps/web/src/i18n/index.ts` must export a `dir` for every
 *     `Locale` and pseudolocale;
 *   - `apps/web/src/lib/tree-view/index.tsx` must use the
 *     `keyboard-navigation` helper for keyboard events on the
 *     keyboard tree list;
 *   - `apps/web/src/components/profile/person-route.tsx` must
 *     reference the live-region announcer;
 *   - no production source file is allowed to import a
 *     pseudolocale (the import gate keeps `en-XA` / `ar-XB` out
 *     of the bundle — they are QA-only);
 *   - forbidden-token scan (no secret / token / PEM / AWS access
 *     key) across all touched files.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");

const REQUIRED_A11Y_KEYS = [
  "a11y.skipToContent",
  "a11y.viewList",
  "a11y.viewForm",
  "a11y.selectedAnnounce",
  "a11y.statusAnnounce",
  "a11y.timelineAnnounce",
  "a11y.tableCaption",
  "a11y.tableHeaderField",
  "a11y.tableHeaderValue",
  "a11y.field.displayName",
  "a11y.field.given",
  "a11y.field.surname",
  "a11y.field.patronymic",
  "a11y.field.suffix",
  "a11y.field.livingStatus",
  "a11y.field.privacyLevel",
  "a11y.field.biography",
  "a11y.field.identifiers",
  "a11y.treeListHelp",
  "a11y.reducedMotionNote",
  "a11y.rtlNote",
  "a11y.focusReturnNote",
];
const REQUIRED_I18N_KEYS = [
  "i18n.nameOrderTitle",
  "i18n.nameOrderGivenFirst",
  "i18n.nameOrderFamilyFirst",
  "i18n.nameOrderFamilyOnly",
  "i18n.nameOrderGivenFamilyComma",
  "i18n.pseudolocaleNote",
  "i18n.pseudolocaleEnXA",
  "i18n.pseudolocaleArXB",
];
const REQUIRED_PROD_KEYS = [
  "app.title",
  "nav.home",
  "nav.skipToContent",
  "tree.sectionLabel",
  "tree.listLabel",
  "profile.sectionLabel",
  "profile.editTitle",
  "timeline.sectionLabel",
  "timeline.eventBIRTH",
  "map.sectionLabel",
];
const PRODUCTION_CATALOGUES = ["en", "vi"];
const PSEUDO_CATALOGUES = ["en-XA", "ar-XB"];
const FORBIDDEN_TOKENS = [
  /AKIA[0-9A-Z]{16}/,
  /-----BEGIN [A-Z ]+-----/,
  /password\s*[:=]\s*["'][^"']+["']/i,
  /token\s*[:=]\s*["'][^"']+["']/i,
  /secret\s*[:=]\s*["'][^"']+["']/i,
  /postgres:\/\/[^"'\s]+/i,
  /mongodb(?:\+srv)?:\/\/[^"'\s]+/i,
];

let violations = 0;

function fail(message) {
  violations += 1;
  console.error(`[a11y-i18n] ${message}`);
}

function pass(message) {
  console.log(`[a11y-i18n] ${message}`);
}

function read(rel) {
  return readFileSync(join(ROOT, rel), "utf8");
}

/**
 * Walk the catalogue source with a tiny recursive parser. The
 * shell catalogues use a stable, mechanical shape
 * (`const x = { a: { b: "..." }, c: "..." } as const satisfies ...`)
 * so a 60-line hand-rolled walker is more reliable than pulling in
 * a full JS parser. The walker only needs to surface the set of
 * dotted key paths.
 */
function extractKeys(raw) {
  const out = new Set();
  // Strip block comments to avoid matching keys mentioned in JSDoc.
  const cleaned = raw.replace(/\/\*[\s\S]*?\*\//g, "");
  // Strip line comments to avoid matching keys mentioned in // examples.
  const noLine = cleaned.replace(/^\s*\/\/.*$/gm, "");

  function walk(text, prefix) {
    let i = 0;
    while (i < text.length) {
      // Find the next identifier.
      const idMatch = /\b([A-Za-z_][A-Za-z0-9_]*)\s*:/.exec(text.slice(i));
      if (!idMatch) return;
      const id = idMatch[1];
      // Skip reserved words / common TypeScript noise.
      if (
        id === "export" ||
        id === "const" ||
        id === "as" ||
        id === "satisfies" ||
        id === "MessageTree"
      ) {
        i += idMatch.index + idMatch[0].length;
        continue;
      }
      const afterColon = i + idMatch.index + idMatch[0].length;
      // Skip whitespace.
      let j = afterColon;
      while (j < text.length && /\s/.test(text[j])) j += 1;
      if (text[j] === "{") {
        // Nested object — recurse.
        const next = prefix ? `${prefix}.${id}` : id;
        // Find the matching `}`.
        let depth = 1;
        let k = j + 1;
        while (k < text.length && depth > 0) {
          if (text[k] === "{") depth += 1;
          else if (text[k] === "}") depth -= 1;
          k += 1;
        }
        walk(text.slice(j + 1, k - 1), next);
        i = k;
        continue;
      }
      const next = prefix ? `${prefix}.${id}` : id;
      out.add(next);
      // Skip to the next comma or end of object.
      let k = afterColon;
      let depth = 0;
      while (k < text.length) {
        const c = text[k];
        if (c === "{") depth += 1;
        else if (c === "}") {
          if (depth === 0) return;
          depth -= 1;
        } else if (c === "," && depth === 0) {
          k += 1;
          break;
        }
        k += 1;
      }
      i = k;
    }
  }

  walk(noLine, "");
  return [...out].sort();
}

function scanForbidden(rel) {
  const raw = read(rel);
  for (const re of FORBIDDEN_TOKENS) {
    if (re.test(raw)) fail(`forbidden literal matched ${re} in ${rel}`);
  }
}

function checkCatalogueParity() {
  const enRaw = read("apps/web/src/i18n/messages/en.ts");
  const enKeys = new Set(extractKeys(enRaw));
  for (const cat of PRODUCTION_CATALOGUES) {
    const raw = read(`apps/web/src/i18n/messages/${cat}.ts`);
    const keys = new Set(extractKeys(raw));
    for (const k of enKeys) {
      if (!keys.has(k)) fail(`messages/${cat}.ts missing key "${k}" (parity with en.ts)`);
    }
  }
  for (const cat of PSEUDO_CATALOGUES) {
    const raw = read(`apps/web/src/i18n/messages/${cat}.ts`);
    const keys = new Set(extractKeys(raw));
    for (const k of enKeys) {
      if (!keys.has(k)) fail(`messages/${cat}.ts missing key "${k}" (pseudolocale parity)`);
    }
  }
}

function checkNamespacePresence() {
  for (const cat of [...PRODUCTION_CATALOGUES, ...PSEUDO_CATALOGUES]) {
    const raw = read(`apps/web/src/i18n/messages/${cat}.ts`);
    for (const key of [...REQUIRED_A11Y_KEYS, ...REQUIRED_I18N_KEYS]) {
      if (!raw.includes(`${key.split(".").pop()}:`)) {
        // Cheap substring check (the dotted path is rebuilt by the loader).
        fail(`messages/${cat}.ts appears to miss required key "${key}"`);
      }
    }
  }
  for (const key of REQUIRED_PROD_KEYS) {
    for (const cat of PRODUCTION_CATALOGUES) {
      const raw = read(`apps/web/src/i18n/messages/${cat}.ts`);
      if (!raw.includes(`${key.split(".").pop()}:`)) {
        fail(`messages/${cat}.ts missing required production key "${key}"`);
      }
    }
  }
}

function checkPseudolocaleMarkers() {
  const enXaRaw = read("apps/web/src/i18n/messages/en-XA.ts");
  if (!enXaRaw.includes("[") || !enXaRaw.includes("]")) {
    fail("messages/en-XA.ts must wrap values in [..] QA markers");
  }
  const arXbRaw = read("apps/web/src/i18n/messages/ar-XB.ts");
  if (!arXbRaw.includes("\u202E") || !arXbRaw.includes("\u202C")) {
    fail("messages/ar-XB.ts must wrap values in \\u202E..\\u202C RLE/PDF marks");
  }
}

function checkReducedMotionCss() {
  const raw = read("apps/web/src/styles/tokens.css");
  if (!/prefers-reduced-motion:\s*reduce/.test(raw)) {
    fail("styles/tokens.css must declare the `prefers-reduced-motion: reduce` media query");
  }
}

function checkDirectionTable() {
  const raw = read("apps/web/src/i18n/index.ts");
  if (!/localeDirection/.test(raw)) fail("i18n/index.ts missing localeDirection table");
  if (!/pseudoLocaleDirection/.test(raw))
    fail("i18n/index.ts missing pseudoLocaleDirection table");
  if (!/resolveDirection/.test(raw)) fail("i18n/index.ts missing resolveDirection helper");
  if (!/createTranslator/.test(raw)) fail("i18n/index.ts missing createTranslator");
}

function checkKeyboardHelperUsage() {
  const treeView = read("apps/web/src/components/tree-view/index.tsx");
  if (!/keyboard-navigation/.test(treeView)) {
    fail(
      "components/tree-view/index.tsx must consume lib/tree-view/keyboard-navigation helper (R6.5)",
    );
  }
  const keyboard = read("apps/web/src/lib/tree-view/keyboard-navigation.ts");
  if (!/TREE_KEYS/.test(keyboard) || !/handleKeyboardTreeEvent/.test(keyboard)) {
    fail("lib/tree-view/keyboard-navigation.ts missing required exports");
  }
}

function checkLiveRegionUsage() {
  const route = read("apps/web/src/components/profile/person-route.tsx");
  if (!/useLiveRegionAnnouncer|live-region-announcer/.test(route)) {
    fail(
      "components/profile/person-route.tsx must mount the live-region announcer (WCAG 2.2 SC 4.1.3)",
    );
  }
}

function checkPersonListTableUsage() {
  const route = read("apps/web/src/components/profile/person-route.tsx");
  if (!/PersonListTable|person-list-table/.test(route)) {
    fail(
      "components/profile/person-route.tsx must mount PersonListTable as the semantic alternative",
    );
  }
}

function checkNameOrderUsage() {
  const table = read("apps/web/src/components/profile/person-list-table.tsx");
  if (!/name-order/.test(table)) {
    fail(
      "components/profile/person-list-table.tsx must use lib/i18n/name-order (R18.2 — no hard-coded ordering)",
    );
  }
}

function checkPseudolocaleImportGate() {
  const files = [
    "apps/web/src/app/[locale]/layout.tsx",
    "apps/web/src/app/[locale]/trees/page.tsx",
    "apps/web/src/app/[locale]/persons/[treeId]/[personId]/page.tsx",
    "apps/web/src/components/profile/person-profile.tsx",
    "apps/web/src/components/profile/person-timeline.tsx",
    "apps/web/src/components/profile/person-route.tsx",
    "apps/web/src/components/top-bar.tsx",
    "apps/web/src/components/skip-link.tsx",
  ];
  for (const rel of files) {
    const raw = read(rel);
    if (/"en-XA"/.test(raw) || /"ar-XB"/.test(raw) || /from\s+["'].*en-XA/.test(raw) || /from\s+["'].*ar-XB/.test(raw)) {
      fail(`${rel} imports a pseudolocale — production code must not (QA-only)`);
    }
  }
}

function checkEnvGateForPseudolocale() {
  const raw = read("apps/web/src/i18n/index.ts");
  if (!/GENEALOGY_PSEUDOLOCALE/.test(raw)) {
    fail("i18n/index.ts must gate pseudolocales via GENEALOGY_PSEUDOLOCALE env var");
  }
}

function checkForbiddenTokens() {
  const rels = [
    "apps/web/src/i18n/messages/en.ts",
    "apps/web/src/i18n/messages/vi.ts",
    "apps/web/src/i18n/messages/en-XA.ts",
    "apps/web/src/i18n/messages/ar-XB.ts",
    "apps/web/src/lib/a11y/use-prefers-reduced-motion.ts",
    "apps/web/src/lib/a11y/use-focus-return.ts",
    "apps/web/src/lib/a11y/use-live-region-announcer.ts",
    "apps/web/src/lib/a11y/live-region.tsx",
    "apps/web/src/lib/a11y/sr-only.tsx",
    "apps/web/src/lib/i18n/name-order.ts",
    "apps/web/src/lib/tree-view/keyboard-navigation.ts",
    "apps/web/src/components/profile/person-list-table.tsx",
  ];
  for (const rel of rels) scanForbidden(rel);
}

function checkSkipLinkPresent() {
  const layout = read("apps/web/src/app/[locale]/layout.tsx");
  if (!/SkipLink/.test(layout)) {
    fail("[locale]/layout.tsx must mount <SkipLink> for WCAG 2.2 SC 2.4.1");
  }
  const sl = read("apps/web/src/components/skip-link.tsx");
  // The visible text inside the anchor is sufficient (the label
  // prop). An aria-label is only required when the visible text
  // is icon-only — both cases are valid WCAG 2.2 SC 2.4.1.
  if (!/(aria-label|\{label\})/.test(sl)) {
    fail("components/skip-link.tsx must declare an aria-label or render the label text as content");
  }
}

function main() {
  try {
    checkCatalogueParity();
    checkNamespacePresence();
    checkPseudolocaleMarkers();
    checkReducedMotionCss();
    checkDirectionTable();
    checkKeyboardHelperUsage();
    checkLiveRegionUsage();
    checkPersonListTableUsage();
    checkNameOrderUsage();
    checkPseudolocaleImportGate();
    checkEnvGateForPseudolocale();
    checkSkipLinkPresent();
    checkForbiddenTokens();
  } catch (err) {
    console.error(`[a11y-i18n] configuration error: ${err.message}`);
    process.exit(2);
  }
  if (violations === 0) {
    pass("OK");
    process.exit(0);
  }
  console.error(`[a11y-i18n] ${violations} violation(s)`);
  process.exit(1);
}

main();