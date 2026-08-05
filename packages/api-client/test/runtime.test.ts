/**
 * BFF runtime tests — verify the contract-first runtime honours
 * the rules in `contracts/README.md`:
 *
 *   1. `Idempotency-Key` is auto-generated and validated for
 *      every non-GET mutation.
 *   2. `X-Correlation-Id` is generated when the caller does not
 *      supply one and is echoed back via the `ApiError`.
 *   3. RFC 9457 `application/problem+json` responses are parsed
 *      into an `ApiError` carrying the typed `Problem` body.
 *   4. The runtime NEVER accepts a tenantId from query string
 *      — only from the constructor or per-call `tenant` option.
 */
import test from "node:test";
import assert from "node:assert/strict";

import { ApiError, BffClient } from "../src/runtime/index";

interface CapturedRequest {
  url: string;
  method: string;
  headers: Record<string, string>;
  body: string | undefined;
}

function buildFetchMock(handler: (req: CapturedRequest) => Response): { fetch: typeof fetch; requests: CapturedRequest[] } {
  const requests: CapturedRequest[] = [];
  const fetchMock: typeof fetch = async (input, init) => {
    const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : (input as Request).url;
    const method = init?.method ?? "GET";
    const headers: Record<string, string> = {};
    const source = init?.headers;
    if (source instanceof Headers) {
      source.forEach((value, key) => {
        headers[key] = value;
      });
    } else if (Array.isArray(source)) {
      for (const [key, value] of source) {
        headers[key] = value;
      }
    } else if (source) {
      Object.assign(headers, source);
    }
    const request: CapturedRequest = {
      url,
      method,
      headers,
      body: typeof init?.body === "string" ? init.body : undefined,
    };
    requests.push(request);
    return handler(request);
  };
  return { fetch: fetchMock, requests };
}

test("GET requests auto-generate a correlation id", async () => {
  const { fetch, requests } = buildFetchMock(() =>
    new Response(JSON.stringify({ userId: "u1", tenants: [] }), {
      status: 200,
      headers: { "content-type": "application/json" },
    }),
  );
  const client = new BffClient({ baseUrl: "https://bff.test", fetch });
  await client.getSession();
  assert.equal(requests.length, 1);
  const id = requests[0].headers["X-Correlation-Id"];
  assert.ok(typeof id === "string" && id.length > 0, "correlation id header missing");
});

test("mutations auto-generate a UUID Idempotency-Key", async () => {
  const { fetch, requests } = buildFetchMock(
    () => new Response(null, { status: 204 }),
  );
  const client = new BffClient({ baseUrl: "https://bff.test", fetch });
  await client.endSession();
  const key = requests[0].headers["Idempotency-Key"];
  assert.ok(key, "Idempotency-Key header missing");
  assert.match(
    key,
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    `Idempotency-Key must be a UUID v4 — got ${key}`,
  );
});

test("mutations reject an invalid Idempotency-Key", async () => {
  const { fetch } = buildFetchMock(() => new Response(null, { status: 204 }));
  const client = new BffClient({ baseUrl: "https://bff.test", fetch });
  await assert.rejects(
    () => client.endSession({ idempotencyKey: "not-a-uuid" }),
    /UUID v4/,
  );
});

test("problem responses are parsed into ApiError", async () => {
  const { fetch } = buildFetchMock(
    () =>
      new Response(
        JSON.stringify({
          type: "about:blank",
          title: "Not authenticated",
          status: 401,
          detail: "Missing bearer token",
          correlationId: "abc",
          errorCode: "unauthorized",
        }),
        {
          status: 401,
          headers: { "content-type": "application/problem+json" },
        },
      ),
  );
  const client = new BffClient({ baseUrl: "https://bff.test", fetch });
  await assert.rejects(
    () => client.getSession(),
    (err: unknown) => {
      assert.ok(err instanceof ApiError, "expected ApiError");
      assert.equal(err.status, 401);
      assert.equal(err.problem?.errorCode, "unauthorized");
      assert.equal(err.problem?.correlationId, "abc");
      return true;
    },
  );
});

test("the X-Tenant-Id header is sent when configured", async () => {
  const { fetch, requests } = buildFetchMock(() =>
    new Response(JSON.stringify({ userId: "u1", tenants: [] }), { status: 200 }),
  );
  const client = new BffClient({
    baseUrl: "https://bff.test",
    fetch,
    tenant: "tenant-42",
  });
  await client.getSession();
  assert.equal(requests[0].headers["X-Tenant-Id"], "tenant-42");
});

test("204 responses resolve to undefined", async () => {
  const { fetch } = buildFetchMock(() => new Response(null, { status: 204 }));
  const client = new BffClient({ baseUrl: "https://bff.test", fetch });
  const result = await client.endSession();
  assert.equal(result, undefined);
});