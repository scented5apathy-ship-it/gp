/**
 * apps/web/src/lib/print/print-policy.test.ts
 *
 * Unit tests for the closed-set enums + helpers in
 * `print-policy.ts`. Pure functions only — no DOM, no React.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";

import {
  PRINT_FORMATS,
  PRINT_SCOPES,
  WATERMARK_MODES,
  PRIVACY_LEVELS,
  EXPORT_JOB_STATUSES,
  PAGE_BREAK_BEHAVIOURS,
  PRINT_LAYOUTS,
  PRINT_LAYOUT_CSS,
  EXPORT_MAX_NODES_PER_JOB,
  SIGNED_URL_TTL_MAX_SECONDS,
  SIGNED_URL_TTL_MIN_SECONDS,
  clampExportNodeCount,
  clampSignedUrlTtlSeconds,
  composeExportHeader,
  isExportJobStatus,
  isPrintFormat,
  isPrintScope,
  isPrivacyLevel,
  isWatermarkMode,
  resolveDefaultWatermark,
} from "./print-policy";

test("print-policy: closed-set enums are non-empty string arrays", () => {
  for (const set of [
    PRINT_SCOPES,
    PRINT_FORMATS,
    WATERMARK_MODES,
    PRIVACY_LEVELS,
    EXPORT_JOB_STATUSES,
    PAGE_BREAK_BEHAVIOURS,
    PRINT_LAYOUTS,
  ]) {
    assert.ok(set.length > 0);
    for (const value of set) {
      assert.equal(typeof value, "string");
      assert.ok(value.length > 0);
    }
  }
});

test("print-policy: type guards accept every enum member", () => {
  for (const scope of PRINT_SCOPES) assert.ok(isPrintScope(scope));
  for (const format of PRINT_FORMATS) assert.ok(isPrintFormat(format));
  for (const mode of WATERMARK_MODES) assert.ok(isWatermarkMode(mode));
  for (const privacy of PRIVACY_LEVELS) assert.ok(isPrivacyLevel(privacy));
  for (const status of EXPORT_JOB_STATUSES) assert.ok(isExportJobStatus(status));
});

test("print-policy: type guards reject non-members", () => {
  assert.equal(isPrintScope("galaxy"), false);
  assert.equal(isPrintFormat("svg"), false);
  assert.equal(isWatermarkMode("neon"), false);
  assert.equal(isPrivacyLevel("OPEN"), false);
  assert.equal(isExportJobStatus("queued-not"), false);
  assert.equal(isPrintScope(undefined), false);
  assert.equal(isPrintScope(42), false);
});

test("print-policy: default watermark respects privacy + share token", () => {
  assert.equal(resolveDefaultWatermark("PUBLIC", false), "tenant-id");
  assert.equal(resolveDefaultWatermark("PUBLIC", true), "tenant-id-and-token-hash");
  assert.equal(resolveDefaultWatermark("UNLISTED", false), "none");
  assert.equal(resolveDefaultWatermark("UNLISTED", true), "token-hash");
  assert.equal(resolveDefaultWatermark("PRIVATE", false), "none");
  assert.equal(resolveDefaultWatermark("PRIVATE", true), "none");
});

test("print-policy: signed URL TTL clamps inside bounds", () => {
  assert.equal(clampSignedUrlTtlSeconds(-5), SIGNED_URL_TTL_MIN_SECONDS);
  assert.equal(clampSignedUrlTtlSeconds(0), SIGNED_URL_TTL_MIN_SECONDS);
  assert.equal(clampSignedUrlTtlSeconds(SIGNED_URL_TTL_MIN_SECONDS), SIGNED_URL_TTL_MIN_SECONDS);
  assert.equal(clampSignedUrlTtlSeconds(SIGNED_URL_TTL_MAX_SECONDS), SIGNED_URL_TTL_MAX_SECONDS);
  assert.equal(
    clampSignedUrlTtlSeconds(SIGNED_URL_TTL_MAX_SECONDS + 1),
    SIGNED_URL_TTL_MAX_SECONDS,
  );
  assert.equal(clampSignedUrlTtlSeconds(Number.NaN), SIGNED_URL_TTL_MIN_SECONDS);
  assert.equal(clampSignedUrlTtlSeconds(3600.7), 3600);
});

test("print-policy: node count clamps inside bounds", () => {
  assert.equal(clampExportNodeCount(0), 1);
  assert.equal(clampExportNodeCount(-1), 1);
  assert.equal(clampExportNodeCount(EXPORT_MAX_NODES_PER_JOB), EXPORT_MAX_NODES_PER_JOB);
  assert.equal(clampExportNodeCount(EXPORT_MAX_NODES_PER_JOB + 5), EXPORT_MAX_NODES_PER_JOB);
  assert.equal(clampExportNodeCount(250.9), 250);
});

test("print-policy: PRINT_LAYOUT_CSS covers every layout", () => {
  for (const layout of PRINT_LAYOUTS) {
    const css = PRINT_LAYOUT_CSS[layout];
    assert.ok(css);
    assert.ok(css.size.includes("portrait") || css.size.includes("landscape"));
    assert.ok(css.margin.length > 0);
  }
});

test("print-policy: composeExportHeader is deterministic and parseable", () => {
  const issuedAt = new Date("2026-08-11T12:00:00.000Z");
  const a = composeExportHeader({
    tenantId: "tenant-1",
    treeId: "tree-1",
    rootPersonId: "p-1",
    scope: "subtree",
    format: "PDF",
    watermark: "tenant-id",
    issuedAt,
  });
  const b = composeExportHeader({
    tenantId: "tenant-1",
    treeId: "tree-1",
    rootPersonId: "p-1",
    scope: "subtree",
    format: "PDF",
    watermark: "tenant-id",
    issuedAt,
  });
  assert.equal(a, b);
  assert.match(a, /^tenant-1·tree-1·p-1·subtree·PDF·tenant-id·2026-08-11T12:00:00\.000Z$/);
});
