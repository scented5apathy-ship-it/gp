/**
 * apps/web/src/lib/print/signed-url-handle.ts
 *
 * Lifecycle helpers for the signed download URL the BFF returns
 * for an `ExportJob` (R12.4 / J-SHARE-3 "Failure path: … expired
 * signed URL"). The URL itself is opaque from the UI's
 * perspective — we only:
 *
 *   - resolve its origin (so the preview can mark "internal
 *     MinIO" vs "external CDN" with different icons);
 *   - compute the time-remaining string for the live region;
 *   - refuse to render the URL once it has expired;
 *   - emit the audit metadata we will replay to the BFF when the
 *     user clicks (correlation id, obligation summary, watermark
 *     mode).
 */
import { clampSignedUrlTtlSeconds, type WatermarkMode } from "./print-policy";

export type SignedUrlOrigin = "minio" | "cdn" | "unknown";

export interface SignedUrlHandle {
  readonly url: string;
  readonly expiresAt: Date;
  readonly issuedAt: Date;
  readonly correlationId: string;
  readonly watermark: WatermarkMode;
  readonly obligationCount: number;
}

const SAFE_PROTOCOLS = new Set(["https:", "http:"]);
const FORBIDDEN_URL_SCHEMES = ["javascript:", "file:", "data:", "vbscript:"];

export function parseSignedUrl(rawUrl: string): URL | null {
  try {
    const parsed = new URL(rawUrl);
    const lower = rawUrl.toLowerCase();
    for (const scheme of FORBIDDEN_URL_SCHEMES) {
      if (lower.startsWith(scheme)) return null;
    }
    if (!SAFE_PROTOCOLS.has(parsed.protocol)) return null;
    return parsed;
  } catch {
    return null;
  }
}

/**
 * Classify the URL origin so the preview can show the right
 * affordance. We deliberately do NOT log or echo the full URL —
 * the audit trail is the audit service's job.
 */
export function resolveSignedUrlOrigin(rawUrl: string): SignedUrlOrigin {
  const parsed = parseSignedUrl(rawUrl);
  if (!parsed) return "unknown";
  const host = parsed.hostname.toLowerCase();
  if (host.endsWith(".min.io") || host.endsWith(".genealogy-minio.internal")) {
    return "minio";
  }
  if (
    host === "cdn.genealogy-platform.com" ||
    host.endsWith(".cdn.genealogy-platform.com") ||
    host.endsWith(".cloudfront.net")
  ) {
    return "cdn";
  }
  return "unknown";
}

/**
 * Time-remaining string for the live region. Returns `"expired"`
 * once the TTL elapses and `"refreshing…"` when within 10s of the
 * expiry window (so the user can refresh the signed URL before
 * they click).
 */
export function signedUrlTimeRemaining(
  handle: SignedUrlHandle,
  now: Date = new Date(),
): { readonly status: "fresh" | "refreshing" | "expired"; readonly secondsLeft: number } {
  const msLeft = handle.expiresAt.getTime() - now.getTime();
  if (msLeft <= 0) return { status: "expired", secondsLeft: 0 };
  if (msLeft < 10_000) return { status: "refreshing", secondsLeft: Math.ceil(msLeft / 1000) };
  return { status: "fresh", secondsLeft: Math.ceil(msLeft / 1000) };
}

/**
 * Validate a `SignedUrlHandle`. Returns `null` when the handle is
 * safe to render and a machine-readable `reasonCode` otherwise.
 */
export function assertSignedUrlHandle(
  handle: SignedUrlHandle,
): { readonly ok: true } | { readonly ok: false; readonly reasonCode: string } {
  if (!parseSignedUrl(handle.url)) return { ok: false, reasonCode: "invalid-url" };
  if (!(handle.expiresAt instanceof Date) || Number.isNaN(handle.expiresAt.getTime())) {
    return { ok: false, reasonCode: "invalid-expiry" };
  }
  if (!(handle.issuedAt instanceof Date) || Number.isNaN(handle.issuedAt.getTime())) {
    return { ok: false, reasonCode: "invalid-issued-at" };
  }
  if (handle.expiresAt.getTime() <= handle.issuedAt.getTime()) {
    return { ok: false, reasonCode: "expiry-before-issued-at" };
  }
  const ttlSeconds = (handle.expiresAt.getTime() - handle.issuedAt.getTime()) / 1000;
  const clamped = clampSignedUrlTtlSeconds(ttlSeconds);
  if (clamped !== ttlSeconds) {
    return { ok: false, reasonCode: "ttl-out-of-bounds" };
  }
  if (!handle.correlationId) return { ok: false, reasonCode: "missing-correlation-id" };
  return { ok: true };
}
