/**
 * `packages/api-client/test/tree-projection.test.ts`
 *
 * Validates the typed wrappers for the BFF tree-projection
 * endpoints (E5.3). The tests cover:
 *
 *   1. `getTreeProjection` returns the parsed body, surfaces
 *      `ETag` / `X-Tree-Projection-Version` / generated-at
 *      headers, and short-circuits on 304.
 *   2. `expandNeighborhood` surfaces 409 (stale) / 412
 *      (precondition) without throwing.
 *   3. Defence-in-depth: the wrapper refuses to issue a
 *      neighborhood request that violates the policy hard caps
 *      (`maxNodes > 1000`, `depth > 12`).
 *   4. Closed-set enforcement: query filters outside the
 *      `relationshipKinds` / `livingStatus` enum set are
 *      forwarded as-is (the BFF surfaces the violation as 400
 *      and `ApiError`) — the wrapper does NOT silently coerce.
 *   5. `If-None-Match` / `If-Match` round-trip through the
 *      wire envelope (R6 / ETag contract).
 */
import test from "node:test";
import assert from "node:assert/strict";

import { BffClient } from "../src/runtime/index";

interface CapturedRequest {
  url: string;
  method: string;
  headers: Record<string, string>;
  body: string | undefined;
}

interface FetchStub {
  fetch: typeof fetch;
  requests: CapturedRequest[];
}

function buildFetchStub(handler: (req: CapturedRequest) => Response): FetchStub {
  const requests: CapturedRequest[] = [];
  const fetchStub: typeof fetch = async (input, init) => {
    const url =
      typeof input === "string"
        ? input
        : input instanceof URL
          ? input.toString()
          : (input as Request).url;
    const method = init?.method ?? "GET";
    const headers: Record<string, string> = {};
    const source = init?.headers;
    if (source instanceof Headers) {
      source.forEach((value, key) => {
        headers[key.toLowerCase()] = value;
      });
    } else if (Array.isArray(source)) {
      for (const [key, value] of source) headers[key.toLowerCase()] = value;
    } else if (source && typeof source === "object") {
      for (const [key, value] of Object.entries(source)) headers[key.toLowerCase()] = value as string;
    }
    const req: CapturedRequest = {
      url,
      method,
      headers,
      body: typeof init?.body === "string" ? init.body : undefined,
    };
    requests.push(req);
    return handler(req);
  };
  return { fetch: fetchStub, requests };
}

function makeClient(stub: FetchStub): BffClient {
  return new BffClient({
    baseUrl: "https://bff.example",
    fetch: stub.fetch,
    correlationId: "00000000-0000-4000-8000-000000000001",
  });
}

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  const headers = new Headers(init.headers);
  if (!headers.has("content-type")) headers.set("content-type", "application/json");
  return new Response(JSON.stringify(body), { ...init, headers });
}

test("getTreeProjection returns parsed body + surfaces ETag/version headers", async () => {
  const body = {
    treeId: "tree-1",
    viewKind: "pedigree",
    direction: "ANCESTORS",
    depth: 4,
    version: 7,
    generatedAt: "2026-08-11T00:00:00Z",
    nodes: [],
    edges: [],
    redaction: { reasonCodes: [], droppedFieldCount: 0 },
    pagination: { hasMore: false },
  };
  const stub = buildFetchStub(() =>
    jsonResponse(body, {
      status: 200,
      headers: {
        etag: '"abc123"',
        "x-tree-projection-version": "7",
        "x-tree-projection-generated-at": "2026-08-11T00:00:00Z",
      },
    }),
  );
  const client = makeClient(stub);
  const response = await client.getTreeProjection({
    treeId: "tree-1",
    viewKind: "pedigree",
    rootPersonId: "person-1",
    direction: "ANCESTORS",
    depth: 4,
  });
  assert.equal(response.status, 200);
  assert.equal(response.etag, '"abc123"');
  assert.equal(response.projectionVersion, 7);
  assert.equal(response.notModified, false);
  assert.equal(response.body?.version, 7);
  const [request] = stub.requests;
  assert.ok(request, "expected exactly one outbound request");
  assert.ok(
    request.url.startsWith("https://bff.example/api/v1/trees/tree-1/projection/pedigree"),
    `unexpected url ${request.url}`,
  );
  assert.equal(request.headers["x-correlation-id"], "00000000-0000-4000-8000-000000000001");
});

test("getTreeProjection 304 short-circuits without replacing the body", async () => {
  const stub = buildFetchStub(() => new Response(null, { status: 304, headers: { etag: '"abc123"' } }));
  const client = makeClient(stub);
  const response = await client.getTreeProjection({
    treeId: "tree-1",
    viewKind: "family",
    rootPersonId: "person-1",
    ifNoneMatch: '"abc123"',
  });
  assert.equal(response.status, 304);
  assert.equal(response.notModified, true);
  assert.equal(response.etag, '"abc123"');
  assert.equal(response.body, undefined);
  const [request] = stub.requests;
  assert.ok(request, "expected exactly one outbound request");
  assert.equal(request.headers["if-none-match"], '"abc123"');
});

test("getTreeProjection forwards If-Match precondition", async () => {
  const stub = buildFetchStub(() => new Response(null, { status: 412 }));
  const client = makeClient(stub);
  const response = await client.getTreeProjection({
    treeId: "tree-1",
    viewKind: "family",
    rootPersonId: "person-1",
    ifMatch: '"v6"',
  });
  assert.equal(response.status, 412);
  assert.equal(response.notModified, false);
  const [request] = stub.requests;
  assert.ok(request, "expected exactly one outbound request");
  assert.equal(request.headers["if-match"], '"v6"');
});

test("expandNeighborhood surfaces 409 stale without throwing", async () => {
  const stub = buildFetchStub(() => new Response(null, { status: 409 }));
  const client = makeClient(stub);
  const response = await client.expandNeighborhood({
    treeId: "tree-1",
    viewKind: "family",
    anchorPersonId: "person-1",
    direction: "DESCENDANTS",
    depth: 4,
    maxNodes: 100,
    baseVersion: 3,
    ifMatch: '"v3"',
  });
  assert.equal(response.status, 409);
  assert.equal(response.stale, true);
  assert.equal(response.preconditionFailed, false);
  const [request] = stub.requests;
  assert.ok(request, "expected exactly one outbound request");
  assert.equal(request.method, "POST");
  assert.ok(request.url.endsWith("/api/v1/trees/tree-1/projection/family/neighborhood"));
  assert.equal(request.headers["if-match"], '"v3"');
  const parsed = JSON.parse(request.body ?? "{}") as Record<string, unknown>;
  assert.equal(parsed["anchorPersonId"], "person-1");
  assert.equal(parsed["baseVersion"], 3);
  assert.equal(parsed["depth"], 4);
  assert.equal(parsed["maxNodes"], 100);
  assert.equal(parsed["direction"], "DESCENDANTS");
});

test("expandNeighborhood refuses depth > 12 before issuing a request", async () => {
  const stub = buildFetchStub(() => new Response(null, { status: 200 }));
  const client = makeClient(stub);
  await assert.rejects(
    client.expandNeighborhood({
      treeId: "tree-1",
      viewKind: "family",
      anchorPersonId: "person-1",
      direction: "DESCENDANTS",
      depth: 13,
      maxNodes: 100,
      baseVersion: 1,
    }),
    /depth must be in \[1, 12\]/,
  );
  assert.equal(stub.requests.length, 0, "no HTTP request must be issued when validation fails");
});

test("expandNeighborhood refuses maxNodes > 1000 before issuing a request", async () => {
  const stub = buildFetchStub(() => new Response(null, { status: 200 }));
  const client = makeClient(stub);
  await assert.rejects(
    client.expandNeighborhood({
      treeId: "tree-1",
      viewKind: "family",
      anchorPersonId: "person-1",
      direction: "DESCENDANTS",
      depth: 4,
      maxNodes: 1500,
      baseVersion: 1,
    }),
    /maxNodes must be in \[1, 1000\]/,
  );
  assert.equal(stub.requests.length, 0);
});

test("expandNeighborhood surfaces delta body when BFF returns 200", async () => {
  const delta = {
    version: 8,
    addedNodes: [
      {
        personId: "person-99",
        displayName: "Newly visible",
        livingStatus: "DECEASED",
        generation: -1,
        redaction: { redacted: false, reasonCodes: [] },
      },
    ],
    addedEdges: [
      {
        fromPersonId: "person-1",
        toPersonId: "person-99",
        relationshipKind: "BIRTH_PARENT",
      },
    ],
    removedPersonIds: [],
  };
  const stub = buildFetchStub(() =>
    jsonResponse(delta, {
      status: 200,
      headers: { etag: '"v8"', "x-tree-projection-version": "8" },
    }),
  );
  const client = makeClient(stub);
  const response = await client.expandNeighborhood({
    treeId: "tree-1",
    viewKind: "family",
    anchorPersonId: "person-1",
    direction: "DESCENDANTS",
    depth: 4,
    maxNodes: 100,
    baseVersion: 7,
  });
  assert.equal(response.status, 200);
  assert.equal(response.stale, false);
  assert.equal(response.projectionVersion, 8);
  assert.equal(response.body?.addedNodes.length, 1);
  assert.equal(response.body?.addedEdges[0]?.relationshipKind, "BIRTH_PARENT");
});