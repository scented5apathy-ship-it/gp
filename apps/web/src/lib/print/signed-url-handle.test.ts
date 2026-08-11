/**
 * apps/web/src/lib/print/signed-url-handle.test.ts
 *
 * Unit tests for the signed-URL lifecycle helpers. Pure functions
 * only — no DOM, no network.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";

import {
  assertSignedUrlHandle,
  parseSignedUrl,
  resolveSignedUrlOrigin,
  signedUrlTimeRemaining,
  type SignedUrlHandle,
} from "./signed-url-handle";
import { SIGNED_URL_TTL_MAX_SECONDS, SIGNED_URL_TTL_MIN_SECONDS } from "./print-policy";

void SIGNED_URL_TTL_MIN_SECONDS;
void SIGNED_URL_TTL_MAX_SECONDS;

function makeHandle(overrides: Partial<SignedUrlHandle> = {}): SignedUrlHandle {
  const issuedAt = overrides.issuedAt ?? new Date("2026-08-11T12:00:00.000Z");
  const ttl = 600;
  const expiresAt = overrides.expiresAt ?? new Date(issuedAt.getTime() + ttl * 1000);
  return {
    url: "https://minio.genealogy-minio.internal/x?sig=abc",
    expiresAt,
    issuedAt,
    correlationId: "corr-1",
    watermark: "tenant-id",
    obligationCount: 0,
    ...overrides,
  };
}

test("signed-url-handle: parseSignedUrl accepts http(s) and rejects garbage", () => {
  assert.ok(parseSignedUrl("https://minio.genealogy-minio.internal/x"));
  assert.equal(parseSignedUrl("javascript:alert(1)"), null);
  assert.equal(parseSignedUrl("file:///etc/passwd"), null);
  assert.equal(parseSignedUrl("not a url"), null);
});

test("signed-url-handle: resolveSignedUrlOrigin classifies MinIO and CDN", () => {
  assert.equal(resolveSignedUrlOrigin("https://cdn.genealogy-platform.com/x"), "cdn");
  assert.equal(resolveSignedUrlOrigin("https://minio.genealogy-minio.internal/x"), "minio");
  assert.equal(resolveSignedUrlOrigin("https://example.com/x"), "unknown");
  assert.equal(resolveSignedUrlOrigin("not a url"), "unknown");
});

test("signed-url-handle: time-remaining transitions", () => {
  const issuedAt = new Date("2026-08-11T12:00:00.000Z");
  const handle = makeHandle({
    issuedAt,
    expiresAt: new Date(issuedAt.getTime() + 600 * 1000),
  });
  assert.equal(signedUrlTimeRemaining(handle, issuedAt).status, "fresh");
  assert.equal(
    signedUrlTimeRemaining(handle, new Date(issuedAt.getTime() + 30_000)).status,
    "fresh",
  );
  assert.equal(
    signedUrlTimeRemaining(handle, new Date(issuedAt.getTime() + 595_000)).status,
    "refreshing",
  );
  assert.equal(
    signedUrlTimeRemaining(handle, new Date(issuedAt.getTime() + 700_000)).status,
    "expired",
  );
});

test("signed-url-handle: assertSignedUrlHandle refuses out-of-bounds TTL", () => {
  const issuedAt = new Date("2026-08-11T12:00:00.000Z");
  // TTL above max — expiresAt too far in the future.
  const tooLong = makeHandle({
    issuedAt,
    expiresAt: new Date(issuedAt.getTime() + (SIGNED_URL_TTL_MAX_SECONDS + 1) * 1000),
  });
  const verdict = assertSignedUrlHandle(tooLong);
  assert.equal(verdict.ok, false);
  if (!verdict.ok) assert.equal(verdict.reasonCode, "ttl-out-of-bounds");

  // TTL below min — expiresAt too close.
  const tooShort = makeHandle({
    issuedAt,
    expiresAt: new Date(issuedAt.getTime() + (SIGNED_URL_TTL_MIN_SECONDS - 1) * 1000),
  });
  const verdictShort = assertSignedUrlHandle(tooShort);
  assert.equal(verdictShort.ok, false);
});

test("signed-url-handle: assertSignedUrlHandle accepts a healthy handle", () => {
  const issuedAt = new Date("2026-08-11T12:00:00.000Z");
  const handle = makeHandle({
    issuedAt,
    expiresAt: new Date(issuedAt.getTime() + 600 * 1000),
  });
  assert.deepEqual(assertSignedUrlHandle(handle), { ok: true });
});

test("signed-url-handle: missing correlationId fails", () => {
  const issuedAt = new Date("2026-08-11T12:00:00.000Z");
  const handle = makeHandle({
    issuedAt,
    expiresAt: new Date(issuedAt.getTime() + 600 * 1000),
    correlationId: "",
  });
  const verdict = assertSignedUrlHandle(handle);
  assert.equal(verdict.ok, false);
  if (!verdict.ok) assert.equal(verdict.reasonCode, "missing-correlation-id");
});
