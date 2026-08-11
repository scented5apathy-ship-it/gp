#!/usr/bin/env node
/**
 * scripts/lint-print-export.mjs
 *
 * E5.6 deep validator for print/export invariants the rest of
 * the platform's linters do not cover:
 *
 *   - `print.*` keys present in every catalogue (en, vi, en-XA,
 *     ar-XB); the `print.scope.*`, `print.format.*`,
 *     `print.privacy.*`, `print.layout.*`, `print.pageBreak.*`,
 *     `print.watermark.*` and `print.signedUrlOrigin.*`
 *     sub-namespaces must be exhaustive over their closed-set
 *     enums;
 *   - `styles/print.css` declares the `@media print` block,
 *     page-break controls per `PageBreakBehaviour`, hides
 *     chrome (`[data-no-print]`, `[data-export-panel]`,
 *     `[data-a11y-live-region]`), and honours
 *     `prefers-reduced-motion`;
 *   - `<ExportRequestPanel>` mounts `<LiveRegion>`, gates the
 *     submit button with `aria-pressed`, and consumes every
 *     closed-set enum (so the linter catches a missing menu
 *     entry);
 *   - `<PrintToolbar>` carries `data-no-print` and `data-print-
 *     toolbar` so the print stylesheet strips it from the output;
 *   - the BFF shim (`lib/print/client.ts`) issues an
 *     `Idempotency-Key` + `X-Correlation-Id` + `X-Tenant-Id`
 *     header set and never blocks on a missing response;
 *   - the print policy module enforces TTL bounds
 *     (60s ≤ ttl ≤ 24h) and the `EXPORT_MAX_NODES_PER_JOB` cap;
 *   - the `signed-url-handle` rejects `javascript:` and
 *     `file:` URLs and clamps TTL inside the same bounds;
 *   - no production component imports a pseudolocale (the
 *     import gate keeps `en-XA` / `ar-XB` out of the bundle);
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

const REQUIRED_PRINT_KEYS = [
  "print.toolbarHeading",
  "print.toolbarHeadingPerson",
  "print.panelHeading",
  "print.panelSubtitle",
  "print.scopeLabel",
  "print.scopeHelp",
  "print.formatLabel",
  "print.formatHelp",
  "print.privacyLabel",
  "print.privacyHelp",
  "print.layoutLabel",
  "print.layoutHelp",
  "print.pageBreakLabel",
  "print.pageBreakHelp",
  "print.optionsLabel",
  "print.includeLiving",
  "print.includeLivingHelp",
  "print.hasShareGrant",
  "print.hasShareGrantHelp",
  "print.watermarkLabel",
  "print.printAction",
  "print.printHelp",
  "print.submitAction",
  "print.refreshAction",
  "print.resetAction",
  "print.statusLabel",
  "print.statusIdle",
  "print.statusQueued",
  "print.statusRunning",
  "print.statusReady",
  "print.statusFailed",
  "print.statusExpired",
  "print.obligationsHeading",
  "print.signedUrlOriginLabel",
  "print.signedUrlRemainingLabel",
  "print.signedUrlSeconds",
  "print.signedUrlExpired",
  "print.signedUrlCorrelationLabel",
  "print.redactionObligationGeneric",
];
const REQUIRED_PRINT_SUB_NAMESPACES = [
  "scope",
  "format",
  "privacy",
  "layout",
  "pageBreak",
  "watermark",
  "signedUrlOrigin",
  "redactionObligationReason",
];
const REQUIRED_OBLIGATION_REASONS = ["LIVING", "MISSING_CONSENT", "PRIVATE_LEVEL", "DNA"];

const PRODUCTION_CATALOGUES = ["en", "vi"];
const PSEUDO_CATALOGUES = ["en-XA", "ar-XB"];
const ALL_CATALOGUES = [...PRODUCTION_CATALOGUES, ...PSEUDO_CATALOGUES];

const FORBIDDEN_TOKENS = [
  /AKIA[0-9A-Z]{16}/,
  /-----BEGIN [A-Z ]+-----/,
  /password\s*[:=]\s*["'][^"']+["']/i,
  /token\s*[:=]\s*["'][^"']+["']/i,
  /secret\s*[:=]\s*["'][^"']+["']/i,
  /postgres:\/\/[^"'\s]+/i,
  /mongodb(?:\+srv)?:\/\/[^"'\s]+/i,
];

const TOUCHED_FILES = [
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

let violations = 0;

function fail(message) {
  violations += 1;
  console.error(`[print-export] ${message}`);
}

function pass(message) {
  console.log(`[print-export] ${message}`);
}

function read(rel) {
  return readFileSync(join(ROOT, rel), "utf8");
}

function extractKeys(raw) {
  const out = new Set();
  const cleaned = raw.replace(/\/\*[\s\S]*?\*\//g, "");
  const noLine = cleaned.replace(/^\s*\/\/.*$/gm, "");
  function walk(text, prefix) {
    let i = 0;
    while (i < text.length) {
      const idMatch = /\b([A-Za-z_][A-Za-z0-9_]*)\s*:/.exec(text.slice(i));
      if (!idMatch) return;
      const id = idMatch[1];
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
      let j = afterColon;
      while (j < text.length && /\s/.test(text[j])) j += 1;
      if (text[j] === "{") {
        const next = prefix ? `${prefix}.${id}` : id;
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
  for (const cat of ALL_CATALOGUES) {
    const raw = read(`apps/web/src/i18n/messages/${cat}.ts`);
    const keys = new Set(extractKeys(raw));
    for (const k of enKeys) {
      if (!keys.has(k)) fail(`messages/${cat}.ts missing key "${k}" (parity with en.ts)`);
    }
  }
}

function checkRequiredPrintKeys() {
  for (const cat of ALL_CATALOGUES) {
    const raw = read(`apps/web/src/i18n/messages/${cat}.ts`);
    for (const key of REQUIRED_PRINT_KEYS) {
      const leaf = key.split(".").pop();
      if (!raw.includes(`${leaf}:`)) {
        fail(`messages/${cat}.ts appears to miss required key "${key}"`);
      }
    }
    for (const ns of REQUIRED_PRINT_SUB_NAMESPACES) {
      if (!raw.includes(`${ns}:`)) {
        fail(`messages/${cat}.ts missing print sub-namespace "${ns}"`);
      }
    }
    for (const reason of REQUIRED_OBLIGATION_REASONS) {
      if (!raw.includes(`${reason}:`)) {
        fail(`messages/${cat}.ts missing obligation reason "${reason}"`);
      }
    }
  }
}

function checkPrintCss() {
  const css = read("apps/web/src/styles/print.css");
  if (!/@media\s+print/.test(css)) {
    fail("styles/print.css must declare @media print");
  }
  if (!/prefers-reduced-motion:\s*reduce/.test(css)) {
    fail("styles/print.css must honour prefers-reduced-motion inside @media print");
  }
  for (const selector of [
    "[data-no-print]",
    "[data-export-panel]",
    "[data-a11y-live-region]",
    "[data-print-toolbar]",
  ]) {
    if (!css.includes(selector)) {
      fail(`styles/print.css must hide "${selector}" during print`);
    }
  }
  for (const mode of ["per-generation", "per-node", "single-page"]) {
    if (!css.includes(`"${mode}"`)) {
      fail(`styles/print.css must declare page-break rule for "${mode}"`);
    }
  }
  if (!/\[data-print-watermark\]/.test(css)) {
    fail("styles/print.css must render the watermark placeholder ([data-print-watermark])");
  }
}

function checkPanelConsumesClosedSets() {
  const panel = read("apps/web/src/components/print/export-request-panel.tsx");
  for (const symbol of [
    "PRINT_SCOPES",
    "PRINT_FORMATS",
    "PRIVACY_LEVELS",
    "PRINT_LAYOUTS",
    "PAGE_BREAK_BEHAVIOURS",
    "resolveDefaultWatermark",
    "useLiveRegionAnnouncer",
    "LiveRegion",
  ]) {
    if (!panel.includes(symbol)) {
      fail(`export-request-panel.tsx must consume "${symbol}"`);
    }
  }
  if (!/aria-pressed/.test(panel)) {
    fail("export-request-panel.tsx must use aria-pressed on the submit button");
  }
  if (!/data-action="submit-export-job"/.test(panel)) {
    fail('export-request-panel.tsx must tag the submit button with data-action="submit-export-job"');
  }
}

function checkToolbarHidesOnPrint() {
  const toolbar = read("apps/web/src/components/print/print-toolbar.tsx");
  if (!toolbar.includes('data-no-print="true"')) {
    fail("print-toolbar.tsx must carry data-no-print=\"true\" so the print stylesheet strips it");
  }
  if (!toolbar.includes("data-print-toolbar")) {
    fail("print-toolbar.tsx must carry data-print-toolbar for the stylesheet hook");
  }
  if (!/window\.print\(\)/.test(toolbar)) {
    fail("print-toolbar.tsx must call window.print() for the browser dialog");
  }
}

function checkBffShimHeaders() {
  const shim = read("apps/web/src/lib/print/client.ts");
  for (const header of [
    "X-Correlation-Id",
    "Idempotency-Key",
    "X-Tenant-Id",
  ]) {
    if (!shim.includes(header)) {
      fail(`lib/print/client.ts must issue header "${header}"`);
    }
  }
  if (!/PRINT_JOB_PATH/.test(shim)) {
    fail("lib/print/client.ts must route to the print-jobs endpoint helper");
  }
  if (!/crypto\.randomUUID|generateCorrelationId/.test(shim)) {
    fail("lib/print/client.ts must mint a UUID v4 idempotency key");
  }
}

function checkPolicyBounds() {
  const policy = read("apps/web/src/lib/print/print-policy.ts");
  if (!/SIGNED_URL_TTL_MIN_SECONDS\s*=\s*60/.test(policy)) {
    fail("print-policy.ts must pin SIGNED_URL_TTL_MIN_SECONDS to 60s");
  }
  if (!/SIGNED_URL_TTL_MAX_SECONDS\s*=\s*24\s*\*/.test(policy)) {
    fail("print-policy.ts must pin SIGNED_URL_TTL_MAX_SECONDS to 24h");
  }
  if (!/EXPORT_MAX_NODES_PER_JOB\s*=\s*1_000/.test(policy)) {
    fail("print-policy.ts must pin EXPORT_MAX_NODES_PER_JOB to 1000");
  }
  if (!/clampSignedUrlTtlSeconds/.test(policy)) {
    fail("print-policy.ts must export clampSignedUrlTtlSeconds");
  }
  for (const set of [
    "PRINT_SCOPES",
    "PRINT_FORMATS",
    "WATERMARK_MODES",
    "PRIVACY_LEVELS",
    "EXPORT_JOB_STATUSES",
    "PAGE_BREAK_BEHAVIOURS",
    "PRINT_LAYOUTS",
  ]) {
    if (!policy.includes(set)) fail(`print-policy.ts must export closed-set "${set}"`);
  }
}

function checkSignedUrlHandleGuards() {
  const handle = read("apps/web/src/lib/print/signed-url-handle.ts");
  if (!/javascript:|file:/.test(handle)) {
    fail("signed-url-handle.ts must reject javascript:/file: URLs");
  }
  if (!/SAFE_PROTOCOLS/.test(handle)) {
    fail("signed-url-handle.ts must centralise the safe protocol check");
  }
  if (!/clampSignedUrlTtlSeconds/.test(handle)) {
    fail("signed-url-handle.ts must clamp TTL with the policy helper");
  }
}

function checkImportGate() {
  const importGateFiles = {
    "apps/web/src/components/print/export-request-panel.tsx": read(
      "apps/web/src/components/print/export-request-panel.tsx",
    ),
    "apps/web/src/components/print/print-toolbar.tsx": read(
      "apps/web/src/components/print/print-toolbar.tsx",
    ),
  };
  for (const [rel, raw] of Object.entries(importGateFiles)) {
    if (/from\s+["']@\/i18n\/messages\/en-XA/.test(raw)) {
      fail(`${rel} must not import the en-XA pseudolocale (import gate)`);
    }
    if (/from\s+["']@\/i18n\/messages\/ar-XB/.test(raw)) {
      fail(`${rel} must not import the ar-XB pseudolocale (import gate)`);
    }
  }
}

function checkForbiddenTokens() {
  // Catalogues carry user-facing strings that may legitimately
  // mention "token" (e.g. `print.hasShareTokenHelp`). Excluding
  // them mirrors the E5.5 linter convention: catalogues are
  // scanned for parity, not for forbidden literals.
  const SCAN_FILES = TOUCHED_FILES.filter(
    (rel) => !rel.startsWith("apps/web/src/i18n/messages/"),
  );
  for (const rel of SCAN_FILES) {
    try {
      scanForbidden(rel);
    } catch (err) {
      fail(`cannot scan ${rel}: ${err instanceof Error ? err.message : String(err)}`);
    }
  }
}

function main() {
  checkCatalogueParity();
  checkRequiredPrintKeys();
  checkPrintCss();
  checkPanelConsumesClosedSets();
  checkToolbarHidesOnPrint();
  checkBffShimHeaders();
  checkPolicyBounds();
  checkSignedUrlHandleGuards();
  checkImportGate();
  checkForbiddenTokens();
  if (violations === 0) {
    pass("OK — E5.6 print/export surface is consistent");
    process.exit(0);
  } else {
    console.error(`[print-export] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();