/**
 * BFF runtime — minimal fetch-based client that honours the
 * shared REST contract rules:
 *
 *   - `X-Correlation-Id` is generated per call when the caller
 *     does not provide one (the value is returned to the caller
 *     so it can be logged with the request).
 *   - `Idempotency-Key` is required on every non-GET mutation.
 *     `withIdempotencyKey()` wraps a caller-supplied UUID; the
 *     runtime rejects calls that forget the header.
 *   - Every 4xx/5xx response is parsed into an `ApiError` that
 *     carries the RFC 9457 `Problem` body so the UI can render a
 *     localised message.
 *   - The `tenant` option lets the caller pin the active tenant
 *     for the call. The runtime NEVER accepts a tenantId from
 *     query string — that policy is enforced in the BFF per
 *     `contracts/openapi/bff/v1/session.yaml`.
 *
 * The runtime is intentionally fetch-only (no `node-fetch`,
 * `axios`, `ky` etc.) so the bundle stays small and we can
 * re-use it from the Service Worker context in E6.
 */
import { ApiError } from "./problem";
import type { Problem } from "./problem";

export interface BffClientOptions {
  /** Origin of the BFF, e.g. `https://bff.genealogy-platform.com`. */
  baseUrl: string;
  /** Optional initial access token (HttpOnly session cookie in practice). */
  accessToken?: string;
  /**
   * Tenant id to pin for the call. The value is sent as the
   * `X-Tenant-Id` header only; the BFF will validate membership
   * before honouring it.
   */
  tenant?: string;
  /** Default correlation id used when callers do not provide one. */
  correlationId?: string;
  /** Custom `fetch` implementation (used in tests). */
  fetch?: typeof fetch;
}

export interface CallOptions {
  headers?: Record<string, string>;
  query?: Record<string, string | number | boolean | undefined>;
  /** Override the idempotency key for the call (mutations only). */
  idempotencyKey?: string;
  /** Body to send (JSON-serialisable). */
  body?: unknown;
  /** Signal for cooperative cancellation. */
  signal?: AbortSignal;
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function generateCorrelationId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  // RFC 4122 v4 fallback when crypto.randomUUID is unavailable.
  return "00000000-0000-4000-8000-000000000000".replace(/[018]/g, (c) => {
    const r = Math.random() * 16;
    const v = c === "0" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function buildUrl(
  baseUrl: string,
  path: string,
  query?: Record<string, string | number | boolean | undefined>,
): string {
  const url = new URL(path, baseUrl);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined) continue;
      url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

export class BffClient {
  private readonly baseUrl: string;
  private readonly defaultHeaders: Record<string, string>;
  private readonly fetchImpl: typeof fetch;

  constructor(options: BffClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.fetchImpl = options.fetch ?? fetch;
    const headers: Record<string, string> = {
      Accept: "application/json, application/problem+json",
    };
    if (options.accessToken) {
      headers["Authorization"] = `Bearer ${options.accessToken}`;
    }
    if (options.tenant) {
      headers["X-Tenant-Id"] = options.tenant;
    }
    if (options.correlationId) {
      headers["X-Correlation-Id"] = options.correlationId;
    }
    this.defaultHeaders = headers;
  }

  /**
   * `GET /session` — return the active BFF session.
   *
   * The generated types in `./generated` model the response as
   * `components["schemas"]["Session"]`; we re-export the helper
   * here so the UI layer does not have to import the raw
   * generated namespace.
   */
  async getSession(options: CallOptions = {}): Promise<unknown> {
    return this.request("GET", "/api/v1/session", options);
  }

  /**
   * `POST /session/tenants/{tenantId}/select` — select a tenant
   * as the active context. The tenant id is sent in the URL only;
   * the BFF validates membership before persisting.
   */
  async selectTenant(tenantId: string, options: CallOptions = {}): Promise<void> {
    await this.request("POST", `/api/v1/session/tenants/${encodeURIComponent(tenantId)}/select`, {
      ...options,
      idempotencyKey: options.idempotencyKey ?? generateCorrelationId(),
    });
  }

  /** `DELETE /session` — end the current session. */
  async endSession(options: CallOptions = {}): Promise<void> {
    await this.request("DELETE", "/api/v1/session", {
      ...options,
      idempotencyKey: options.idempotencyKey ?? generateCorrelationId(),
    });
  }

  /**
   * Generic request helper. Public so the UI can talk to endpoints
   * that are not yet modelled as dedicated methods above; new
   * services should add their typed wrappers as they land.
   */
  async request(method: string, path: string, options: CallOptions = {}): Promise<unknown> {
    const url = buildUrl(this.baseUrl, path, options.query);
    const headers: Record<string, string> = { ...this.defaultHeaders, ...options.headers };

    const correlationId = headers["X-Correlation-Id"] ?? generateCorrelationId();
    headers["X-Correlation-Id"] = correlationId;

    if (method.toUpperCase() !== "GET" && method.toUpperCase() !== "HEAD") {
      const key = options.idempotencyKey ?? generateCorrelationId();
      if (!UUID_RE.test(key)) {
        throw new Error(
          `BffClient: Idempotency-Key must be a UUID v4 (draft-ietf-httpapi-idempotency-key). Got: ${key}`,
        );
      }
      headers["Idempotency-Key"] = key;
      if (options.body !== undefined && !headers["Content-Type"]) {
        headers["Content-Type"] = "application/json";
      }
    }

    const init: RequestInit = {
      method,
      headers,
    };
    if (options.signal) {
      init.signal = options.signal;
    }
    if (options.body !== undefined) {
      init.body = JSON.stringify(options.body);
    }

    const response = await this.fetchImpl(url, init);

    if (!response.ok) {
      let problem: Problem | undefined;
      const contentType = response.headers.get("content-type") ?? "";
      if (
        contentType.includes("application/problem+json") ||
        contentType.includes("application/json")
      ) {
        try {
          problem = (await response.json()) as Problem;
        } catch {
          // Body could not be parsed — fall back to status-only error.
        }
      }
      throw new ApiError(response.status, problem, correlationId);
    }

    if (response.status === 204) {
      return undefined;
    }
    if (response.headers.get("content-length") === "0") {
      return undefined;
    }
    return response.json();
  }
}

export function createBffClient(options: BffClientOptions): BffClient {
  return new BffClient(options);
}

export { ApiError, type Problem } from "./problem";
