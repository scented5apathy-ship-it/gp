/**
 * `apps/web/src/lib/tree-view/store.test.ts`
 *
 * Validates the tree-view state machine (E5.3). The store is
 * pure / framework-agnostic; these tests exercise every
 * invariant the React shell relies on:
 *
 *   - stable node identity (personId → slot map)
 *   - ETag round-trip on subsequent fetches
 *   - 304 short-circuit keeps the previous snapshot
 *   - viewport fetch budget: refuses requests above 1000 nodes
 *     or 12 generations deep (R6.3)
 *   - delta merge preserves stable identity (personId keys
 *     survive mergeDelta)
 *   - viewport request with `baseVersion` sends `If-Match`
 *     when an etag is held
 *   - toggleCollapse / patchUi / reset / subscribe behaviour
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  assertViewportBudget,
  defaultQuery,
  EMPTY_SNAPSHOT,
  mergeDelta,
  snapshotFromBody,
  toggleBranch,
  toViewEdge,
  toViewNode,
  TreeViewStore,
  type TreeProjectionFetcher,
  type TreeViewSnapshot,
} from "./store";
import {
  assertClosedSet,
  assertDepth,
  assertMaxNodes,
  TREE_PROJECTION_MAX_NEIGHBORHOOD_NODES,
  type RawHttpResponse,
  type TreeProjectionBody,
  type TreeProjectionDeltaBody,
  type TreeProjectionEdgeBody,
  type TreeProjectionNodeBody,
  type TreeProjectionViewKind,
} from "@genealogy/api-client";

interface CapturedCall {
  method: "getProjection" | "expandNeighborhood";
  args: unknown;
  response: RawHttpResponse;
}

function buildStub(responses: RawHttpResponse[]): {
  fetcher: TreeProjectionFetcher;
  calls: CapturedCall[];
} {
  const calls: CapturedCall[] = [];
  let cursor = 0;
  const fetcher: TreeProjectionFetcher = {
    async getProjection(args) {
      const response = responses[cursor++] ?? responses[responses.length - 1]!;
      calls.push({ method: "getProjection", args, response });
      return response;
    },
    async expandNeighborhood(args) {
      const response = responses[cursor++] ?? responses[responses.length - 1]!;
      calls.push({ method: "expandNeighborhood", args, response });
      return response;
    },
  };
  return { fetcher, calls };
}

function makeBody(overrides: Partial<TreeProjectionBody> = {}): TreeProjectionBody {
  return {
    treeId: "tree-1",
    viewKind: "family",
    direction: "BOTH",
    depth: 4,
    version: 1,
    generatedAt: "2026-08-11T00:00:00Z",
    nodes: [
      {
        personId: "person-1",
        displayName: "Root",
        livingStatus: "DECEASED",
        generation: 0,
        redaction: { redacted: false, reasonCodes: [] },
      } as TreeProjectionNodeBody,
      {
        personId: "person-2",
        displayName: "Ancestor",
        livingStatus: "DECEASED",
        generation: -1,
        redaction: { redacted: false, reasonCodes: [] },
      } as TreeProjectionNodeBody,
      {
        personId: "person-3",
        displayName: "Descendant",
        livingStatus: "LIVING",
        generation: 1,
        redaction: { redacted: true, reasonCodes: ["living_redacted"] },
      } as TreeProjectionNodeBody,
    ],
    edges: [
      {
        fromPersonId: "person-2",
        toPersonId: "person-1",
        relationshipKind: "BIRTH_PARENT",
      } as TreeProjectionEdgeBody,
    ],
    redaction: { reasonCodes: ["living_redacted"], droppedFieldCount: 1 },
    pagination: { hasMore: false },
    ...overrides,
  };
}

function envelope(body: TreeProjectionBody, etag: string, version: number): RawHttpResponse {
  return {
    status: 200,
    headers: {
      etag,
      "x-tree-projection-version": String(version),
    },
    parsed: body,
  };
}

test("defaultQuery validates the viewKind closed-set", () => {
  assert.throws(() =>
    defaultQuery({
      treeId: "tree-1",
      viewKind: "unknown" as TreeProjectionViewKind,
      rootPersonId: "p",
    }),
  );
});

test("toViewNode drops name + years when the wire body was redacted", () => {
  const redacted: TreeProjectionNodeBody = {
    personId: "person-3",
    displayName: "should-not-show",
    livingStatus: "LIVING",
    birthYear: 1990,
    deathYear: 2000,
    generation: 1,
    privacyLevel: "PRIVATE",
    redaction: { redacted: true, reasonCodes: ["living_redacted"] },
  };
  const node = toViewNode(redacted);
  assert.equal(node.redacted, true);
  assert.equal(node.displayName, "");
  assert.equal(node.birthYear, undefined);
  assert.equal(node.deathYear, undefined);
});

test("toViewEdge produces a stable id combining endpoints and kind", () => {
  const edge = toViewEdge({
    fromPersonId: "a",
    toPersonId: "b",
    relationshipKind: "SPOUSE",
  } as TreeProjectionEdgeBody);
  assert.equal(edge.id, "a->b:SPOUSE");
});

test("snapshotFromBody keeps stable personId identity across renders", () => {
  const body = makeBody();
  const query = defaultQuery({ treeId: "tree-1", viewKind: "family", rootPersonId: "person-1" });
  const snapshot = snapshotFromBody(query, body, '"v1"');
  assert.equal(snapshot.nodesById.size, 3);
  assert.ok(snapshot.nodesById.has("person-1"));
  assert.ok(snapshot.nodesById.has("person-2"));
  assert.ok(snapshot.nodesById.has("person-3"));
  assert.equal(snapshot.version, 1);
  assert.equal(snapshot.etag, '"v1"');
});

test("mergeDelta preserves identity and bumps the version forward", () => {
  const body = makeBody({ version: 1 });
  const snapshot = snapshotFromBody(
    defaultQuery({ treeId: "tree-1", viewKind: "family", rootPersonId: "person-1" }),
    body,
    '"v1"',
  );
  const delta: TreeProjectionDeltaBody = {
    version: 2,
    addedNodes: [
      {
        personId: "person-99",
        displayName: "Newly visible",
        livingStatus: "DECEASED",
        generation: 1,
        redaction: { redacted: false, reasonCodes: [] },
      } as TreeProjectionNodeBody,
    ],
    addedEdges: [
      {
        fromPersonId: "person-1",
        toPersonId: "person-99",
        relationshipKind: "BIRTH_PARENT",
      } as TreeProjectionEdgeBody,
    ],
  };
  const merged = mergeDelta(snapshot, delta);
  assert.equal(merged.version, 2);
  assert.equal(merged.nodesById.size, 4);
  assert.ok(
    merged.nodesById.has("person-1"),
    "stable identity: pre-existing person survives merge",
  );
  assert.ok(merged.nodesById.has("person-99"), "stable identity: new person slotted in");
});

test("mergeDelta refuses a delta whose version goes backwards", () => {
  const snapshot = snapshotFromBody(
    defaultQuery({ treeId: "tree-1", viewKind: "family", rootPersonId: "person-1" }),
    makeBody({ version: 5 }),
    '"v5"',
  );
  assert.throws(
    () =>
      mergeDelta(snapshot, {
        version: 4,
        addedNodes: [],
        addedEdges: [],
      }),
    /version went backwards/,
  );
});

test("toggleBranch round-trips the collapsed state", () => {
  const snapshot = snapshotFromBody(
    defaultQuery({ treeId: "tree-1", viewKind: "family", rootPersonId: "person-1" }),
    makeBody(),
    '"v1"',
  );
  const collapsed = toggleBranch(snapshot, "person-1");
  assert.ok(collapsed.ui.collapsedBranchIds.has("person-1"));
  const expanded = toggleBranch(collapsed, "person-1");
  assert.ok(!expanded.ui.collapsedBranchIds.has("person-1"));
});

test("assertClosedSet throws on a value outside the closed set", () => {
  assert.throws(() =>
    assertClosedSet(
      "filter.relationshipKinds",
      ["BIRTH_PARENT", "ALIEN_KIND"],
      ["BIRTH_PARENT", "ADOPTIVE_PARENT"],
    ),
  );
});

test("assertDepth / assertMaxNodes enforce the policy hard caps", () => {
  assert.throws(() => assertDepth(0));
  assert.throws(() => assertDepth(13));
  assert.throws(() => assertMaxNodes(0));
  assert.throws(() => assertMaxNodes(TREE_PROJECTION_MAX_NEIGHBORHOOD_NODES + 1));
  assert.equal(assertDepth(1), 1);
  assert.equal(assertMaxNodes(1), 1);
});

test("assertViewportBudget refuses a viewport that violates the caps", () => {
  assert.throws(() =>
    assertViewportBudget({
      depth: 12,
      maxNodes: 2000,
      topLeftGeneration: -4,
      bottomRightGeneration: 4,
    }),
  );
  assert.throws(() =>
    assertViewportBudget({
      depth: 12,
      maxNodes: 250,
      topLeftGeneration: 4,
      bottomRightGeneration: -4,
    }),
  );
});

test("TreeViewStore.load sends If-None-Match when a previous etag is held", async () => {
  const body = makeBody({ version: 2 });
  const stub = buildStub([envelope(body, '"v2"', 2)]);
  const store = new TreeViewStore(EMPTY_SNAPSHOT, { fetcher: stub.fetcher });
  const query = defaultQuery({ treeId: "tree-1", viewKind: "family", rootPersonId: "person-1" });
  await store.load(query);
  // second load — should send If-None-Match from the first snapshot
  await store.load(query);
  assert.equal(stub.calls.length, 2);
  const secondArgs = stub.calls[1]!.args as { ifNoneMatch?: string };
  assert.equal(secondArgs.ifNoneMatch, '"v2"');
});

test("TreeViewStore.load short-circuits on 304 without mutating nodesById", async () => {
  const body = makeBody();
  const first = envelope(body, '"v1"', 1);
  const stub = buildStub([first, { status: 304, headers: { etag: '"v1"' }, parsed: undefined }]);
  const store = new TreeViewStore(EMPTY_SNAPSHOT, { fetcher: stub.fetcher });
  const query = defaultQuery({ treeId: "tree-1", viewKind: "family", rootPersonId: "person-1" });
  await store.load(query);
  const afterFirst = store.getSnapshot();
  const sizeBefore = afterFirst.nodesById.size;
  await store.load(query);
  const afterSecond = store.getSnapshot();
  assert.equal(afterSecond.meta.status, "ready");
  assert.equal(afterSecond.nodesById.size, sizeBefore, "304 must not re-parse the body");
});

test("TreeViewStore.requestViewport merges the delta and bumps the version", async () => {
  const body = makeBody();
  const delta: TreeProjectionDeltaBody = {
    version: 2,
    addedNodes: [
      {
        personId: "person-99",
        displayName: "Visible",
        livingStatus: "DECEASED",
        generation: 2,
        redaction: { redacted: false, reasonCodes: [] },
      } as TreeProjectionNodeBody,
    ],
    addedEdges: [],
  };
  const deltaEnvelope: RawHttpResponse = {
    status: 200,
    headers: { etag: '"v2"', "x-tree-projection-version": "2" },
    parsed: delta,
  };
  const stub = buildStub([envelope(body, '"v1"', 1), deltaEnvelope]);
  const store = new TreeViewStore(EMPTY_SNAPSHOT, { fetcher: stub.fetcher });
  const query = defaultQuery({ treeId: "tree-1", viewKind: "family", rootPersonId: "person-1" });
  await store.load(query);
  await store.requestViewport({
    anchorPersonId: "person-1",
    direction: "DESCENDANTS",
    depth: 4,
    maxNodes: 250,
  });
  const snapshot = store.getSnapshot();
  assert.equal(snapshot.version, 2);
  assert.ok(snapshot.nodesById.has("person-99"));
});

test("TreeViewStore.requestViewport refuses a request that breaches the budget", async () => {
  const body = makeBody();
  const stub = buildStub([envelope(body, '"v1"', 1)]);
  const store = new TreeViewStore(EMPTY_SNAPSHOT, { fetcher: stub.fetcher });
  await store.load(
    defaultQuery({ treeId: "tree-1", viewKind: "family", rootPersonId: "person-1" }),
  );
  await assert.rejects(
    store.requestViewport({
      anchorPersonId: "person-1",
      direction: "DESCENDANTS",
      depth: 13,
      maxNodes: 100,
    }),
  );
  await assert.rejects(
    store.requestViewport({
      anchorPersonId: "person-1",
      direction: "DESCENDANTS",
      depth: 4,
      maxNodes: 1500,
    }),
  );
  assert.equal(stub.calls.length, 1, "no HTTP request beyond the initial load()");
});

test("TreeViewStore.requestViewport sends baseVersion + If-Match", async () => {
  const body = makeBody({ version: 7 });
  const stub = buildStub([
    envelope(body, '"v7"', 7),
    { status: 200, headers: {}, parsed: { version: 8, addedNodes: [], addedEdges: [] } },
  ]);
  const store = new TreeViewStore(EMPTY_SNAPSHOT, { fetcher: stub.fetcher });
  await store.load(
    defaultQuery({ treeId: "tree-1", viewKind: "family", rootPersonId: "person-1" }),
  );
  await store.requestViewport({
    anchorPersonId: "person-1",
    direction: "BOTH",
    depth: 4,
    maxNodes: 250,
  });
  const second = stub.calls[1]!.args as { baseVersion: number; ifMatch?: string };
  assert.equal(second.baseVersion, 7);
  assert.equal(second.ifMatch, '"v7"');
});

test("TreeViewStore surfaces 409 as meta.status = stale", async () => {
  const stub = buildStub([
    envelope(makeBody(), '"v1"', 1),
    { status: 409, headers: {}, parsed: undefined },
  ]);
  const store = new TreeViewStore(EMPTY_SNAPSHOT, { fetcher: stub.fetcher });
  await store.load(
    defaultQuery({ treeId: "tree-1", viewKind: "family", rootPersonId: "person-1" }),
  );
  await store.requestViewport({
    anchorPersonId: "person-1",
    direction: "DESCENDANTS",
    depth: 4,
    maxNodes: 250,
  });
  assert.equal(store.getSnapshot().meta.status, "stale");
});

test("TreeViewStore subscribe delivers the current snapshot immediately", () => {
  const stub = buildStub([]);
  const store = new TreeViewStore(EMPTY_SNAPSHOT, { fetcher: stub.fetcher });
  const received: TreeViewSnapshot[] = [];
  const unsubscribe = store.subscribe((s) => received.push(s));
  assert.equal(received.length, 1);
  unsubscribe();
  store.patchUi({ transform: { x: 1, y: 0, scale: 1 } });
  assert.equal(received.length, 1, "no listener firing after unsubscribe");
});
