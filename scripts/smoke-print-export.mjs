#!/usr/bin/env node
/**
 * scripts/smoke-print-export.mjs
 *
 * Source-level smoke checks for the E5.6 print/export surface.
 * Mirrors `scripts/smoke-a11y.mjs` (E5.5): each check is a
 * 5-10 line grep over the touched files so the smoke completes
 * in under a second and exits non-zero on a regression.
 *
 * Categories:
 *   1. Catalogue parity (print.* keys present in en/vi/en-XA/ar-XB)
 *   2. Print stylesheet has @media print + reduced-motion + page-break
 *   3. ExportRequestPanel mounts LiveRegion + closed-set menus
 *   4. PrintToolbar hides on print + calls window.print()
 *   5. BFF shim issues Idempotency-Key + X-Correlation-Id + X-Tenant-Id
 *   6. Print policy pins TTL bounds (60s ≤ ttl ≤ 24h)
 *   7. Signed-url-handle rejects javascript:/file:
 *   8. Pseudolocale import gate enforced
 *
 * Exits 0 on success, 1 on failure.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.SMOKE_ROOT ? resolve(process.env.SMOKE_ROOT) : resolve(HERE, "..");

let failures = 0;

function check(label, fn) {
  try {
    fn();
    console.log(`[smoke-print-export] OK ${label}`);
  } catch (err) {
    failures += 1;
    console.error(`[smoke-print-export] FAIL ${label}: ${err instanceof Error ? err.message : String(err)}`);
  }
}

function read(rel) {
  return readFileSync(join(ROOT, rel), "utf8");
}

function assertMatch(haystack, re, label) {
  if (!re.test(haystack)) throw new Error(`${label}: pattern not found (${re})`);
}

// 1. Catalogue parity — print.* present in all 4 catalogues
check("catalogue parity: print.* present in en/vi/en-XA/ar-XB", () => {
  for (const cat of ["en", "vi", "en-XA", "ar-XB"]) {
    const raw = read(`apps/web/src/i18n/messages/${cat}.ts`);
    assertMatch(raw, /toolbarHeading:/, `${cat}.ts toolbarHeading`);
    assertMatch(raw, /submitAction:/, `${cat}.ts submitAction`);
    assertMatch(raw, /signedUrlCorrelationLabel:/, `${cat}.ts signedUrlCorrelationLabel`);
  }
});

// 2. Print stylesheet has @media print + reduced-motion + page-break
check("print.css declares @media print + page-break + reduced-motion", () => {
  const css = read("apps/web/src/styles/print.css");
  assertMatch(css, /@media\s+print/, "@media print");
  assertMatch(css, /prefers-reduced-motion:\s*reduce/, "prefers-reduced-motion: reduce");
  assertMatch(css, /"per-generation"/, "per-generation page-break");
  assertMatch(css, /"per-node"/, "per-node page-break");
  assertMatch(css, /"single-page"/, "single-page page-break");
});

// 3. ExportRequestPanel mounts LiveRegion + closed-set menus
check("ExportRequestPanel mounts LiveRegion + consumes closed-set enums", () => {
  const panel = read("apps/web/src/components/print/export-request-panel.tsx");
  assertMatch(panel, /LiveRegion/, "LiveRegion");
  assertMatch(panel, /useLiveRegionAnnouncer/, "useLiveRegionAnnouncer");
  assertMatch(panel, /PRINT_SCOPES/, "PRINT_SCOPES");
  assertMatch(panel, /PRINT_FORMATS/, "PRINT_FORMATS");
  assertMatch(panel, /PRIVACY_LEVELS/, "PRIVACY_LEVELS");
  assertMatch(panel, /PRINT_LAYOUTS/, "PRINT_LAYOUTS");
  assertMatch(panel, /aria-pressed/, "aria-pressed");
});

// 4. PrintToolbar hides on print + calls window.print()
check("PrintToolbar hides on print + calls window.print()", () => {
  const toolbar = read("apps/web/src/components/print/print-toolbar.tsx");
  assertMatch(toolbar, /data-no-print="true"/, 'data-no-print="true"');
  assertMatch(toolbar, /data-print-toolbar/, "data-print-toolbar");
  assertMatch(toolbar, /window\.print\(\)/, "window.print()");
});

// 5. BFF shim issues Idempotency-Key + X-Correlation-Id + X-Tenant-Id
check("BFF shim issues Idempotency-Key + X-Correlation-Id + X-Tenant-Id", () => {
  const shim = read("apps/web/src/lib/print/client.ts");
  assertMatch(shim, /"Idempotency-Key"/, "Idempotency-Key header");
  assertMatch(shim, /"X-Correlation-Id"/, "X-Correlation-Id header");
  assertMatch(shim, /"X-Tenant-Id"/, "X-Tenant-Id header");
  assertMatch(shim, /crypto\.randomUUID/, "UUID v4 idempotency key");
});

// 6. Print policy pins TTL bounds
check("print-policy pins TTL bounds and EXPORT_MAX_NODES_PER_JOB", () => {
  const policy = read("apps/web/src/lib/print/print-policy.ts");
  assertMatch(policy, /SIGNED_URL_TTL_MIN_SECONDS\s*=\s*60/, "TTL_MIN=60");
  assertMatch(policy, /SIGNED_URL_TTL_MAX_SECONDS\s*=\s*24\s*\*/, "TTL_MAX=24h");
  assertMatch(policy, /EXPORT_MAX_NODES_PER_JOB\s*=\s*1_000/, "MAX_NODES=1000");
  assertMatch(policy, /clampSignedUrlTtlSeconds/, "clampSignedUrlTtlSeconds");
});

// 7. Signed-url-handle rejects javascript:/file:
check("signed-url-handle rejects javascript:/file: URLs", () => {
  const handle = read("apps/web/src/lib/print/signed-url-handle.ts");
  assertMatch(handle, /javascript:/, "javascript: guard");
  assertMatch(handle, /file:/, "file: guard");
  assertMatch(handle, /SAFE_PROTOCOLS/, "SAFE_PROTOCOLS check");
  assertMatch(handle, /clampSignedUrlTtlSeconds/, "clampSignedUrlTtlSeconds call");
});

// 8. Pseudolocale import gate enforced
check("Pseudolocale import gate (no production component imports en-XA/ar-XB)", () => {
  const panel = read("apps/web/src/components/print/export-request-panel.tsx");
  const toolbar = read("apps/web/src/components/print/print-toolbar.tsx");
  for (const [label, raw] of [
    ["export-request-panel", panel],
    ["print-toolbar", toolbar],
  ]) {
    if (/from\s+["']@\/i18n\/messages\/en-XA/.test(raw)) {
      throw new Error(`${label} imports en-XA pseudolocale`);
    }
    if (/from\s+["']@\/i18n\/messages\/ar-XB/.test(raw)) {
      throw new Error(`${label} imports ar-XB pseudolocale`);
    }
  }
});

if (failures === 0) {
  console.log("[smoke-print-export] all 8 checks passed");
  process.exit(0);
} else {
  console.error(`[smoke-print-export] ${failures} check(s) failed`);
  process.exit(1);
}