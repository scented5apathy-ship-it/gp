/**
 * apps/web/src/lib/tree-view/store.ts
 *
 * Pure state machine + projection cache for the tree view (E5.3).
 *
 * Responsibilities:
 *
 *   1. Hold the **stable node identity** that R6 / `design.md`
 *      §10.2 requires — the renderer MUST be able to map a
 *      `personId` back to the same DOM/canvas slot across
 *      refetches so focus and selection survive.
 *   2. Merge incremental `TreeProjectionDelta` payloads (R6.3 —
 *      "never load the full graph to the browser") into the
 *      existing snapshot WITHOUT mutating the underlying objects.
 *      The store always produces new immutable snapshots so
 *      React renders are deterministic.
 *   3. Honour `ETag` + `If-None-Match` → 304 short-circuit and
 *      reject closed-set violations BEFORE issuing a request.
 *   4. Respect the policy hard caps (`maxDepth=12`,
 *      `maxNeighborhoodNodes=1000`, `maxRelationshipsPerResponse=2000`).
 *   5. Refuse to call the BFF when the viewport request would
 *      exceed the cap (offence against R6.3).
 *
 * The store is intentionally framework-agnostic. `TreeView`
 * (the React component) is a thin shell that subscribes to the
 * store via `subscribe()` and renders. The store is exercised
 * by `apps/web/src/lib/tree-view/store.test.ts` so the
 * invariants survive refactors.
 */
import {
  assertClosedSet,
  assertDepth,
  assertMaxNodes,
  TREE_PROJECTION_MAX_DEPTH,
  TREE_PROJECTION_MAX_NEIGHBORHOOD_NODES,
  TREE_PROJECTION_VIEW_KINDS,
  type RawHttpResponse,
  type TreeProjectionBody,
  type TreeProjectionDeltaBody,
  type TreeProjectionDirection,
  type TreeProjectionLivingStatus,
  type TreeProjectionRelationshipKind,
  type TreeProjectionViewKind,
} from "@genealogy/api-client";

/**
 * Adapter the store uses to talk to the BFF. The adapter is
 * deliberately a function (not a class) so unit tests can
 * inject a stub without touching module-level singletons. The
 * production wiring lives in `apps/web/src/lib/tree-view/client.ts`
 * and binds to the real `BffClient`.
 */
export interface TreeProjectionFetcher {
  getProjection(args: {
    treeId: string;
    viewKind: TreeProjectionViewKind;
    rootPersonId: string;
    direction?: TreeProjectionDirection;
    depth?: number;
    maxNodes?: number;
    maxRelationships?: number;
    filter?: {
      relationshipKinds?: readonly TreeProjectionRelationshipKind[];
      livingStatus?: readonly TreeProjectionLivingStatus[];
    };
    ifNoneMatch?: string;
  }): Promise<RawHttpResponse>;
  expandNeighborhood(args: {
    treeId: string;
    viewKind: TreeProjectionViewKind;
    anchorPersonId: string;
    direction: TreeProjectionDirection;
    depth: number;
    maxNodes: number;
    viewport?: {
      topLeftGeneration: number;
      bottomRightGeneration: number;
      siblingIndexRange?: readonly [number, number];
    };
    baseVersion: number;
    ifMatch?: string;
  }): Promise<RawHttpResponse>;
}

/**
 * Defaults sourced from the policy contract (E5.2). They are
 * duplicated here ONLY so the UI can render an initial state
 * without a round-trip; the BFF re-applies the same defaults on
 * the server side. Drift is caught by `check:treeProjection`
 * (E5.2 evidence).
 */
export const TREE_VIEW_DEFAULT_DEPTH = 4;
export const TREE_VIEW_DEFAULT_MAX_NODES = 250;
export const TREE_VIEW_DEFAULT_MAX_RELATIONSHIPS = 500;
export const TREE_VIEW_DEFAULT_DIRECTION: TreeProjectionDirection = "BOTH";

export interface TreeViewQuery {
  readonly treeId: string;
  readonly viewKind: TreeProjectionViewKind;
  readonly rootPersonId: string;
  readonly direction: TreeProjectionDirection;
  readonly depth: number;
  readonly maxNodes: number;
  readonly maxRelationships: number;
  readonly filter: {
    readonly relationshipKinds: readonly TreeProjectionRelationshipKind[];
    readonly livingStatus: readonly TreeProjectionLivingStatus[];
  };
}

export interface TreeViewViewport {
  readonly topLeftGeneration: number;
  readonly bottomRightGeneration: number;
  readonly siblingIndexRange?: readonly [number, number];
}

export interface TreeViewUiState {
  /** Pan/zoom transform applied on top of the layout grid. */
  readonly transform: { readonly x: number; readonly y: number; readonly scale: number };
  /** Branch ids hidden by user. Stable across renders. */
  readonly collapsedBranchIds: ReadonlySet<string>;
  /** Person the user last selected — drives breadcrumb + a11y focus. */
  readonly selectedPersonId: string | null;
  /** Viewport rectangle in generation-space — drives neighborhood fetch. */
  readonly viewport: TreeViewViewport;
}

export interface TreeViewSnapshot {
  readonly query: TreeViewQuery;
  readonly etag: string | null;
  readonly version: number;
  readonly generatedAt: string | null;
  readonly nodesById: ReadonlyMap<string, TreeViewNode>;
  readonly edges: readonly TreeViewEdge[];
  readonly generations: readonly number[];
  readonly redaction: TreeProjectionBody["redaction"];
  readonly pagination: { readonly hasMore: boolean; readonly nextCursor?: string };
  readonly ui: TreeViewUiState;
  readonly meta: {
    readonly status: "idle" | "loading" | "ready" | "error" | "stale";
    readonly lastError?: string;
  };
}

export interface TreeViewNode {
  readonly personId: string;
  readonly displayName: string;
  readonly livingStatus: TreeProjectionLivingStatus;
  readonly birthYear?: number;
  readonly deathYear?: number;
  readonly generation: number;
  readonly privacyLevel?: "PUBLIC" | "UNLISTED" | "PRIVATE";
  readonly redacted: boolean;
  readonly reasonCodes: readonly string[];
}

export interface TreeViewEdge {
  readonly id: string;
  readonly fromPersonId: string;
  readonly toPersonId: string;
  readonly relationshipKind: TreeProjectionRelationshipKind;
}

export const EMPTY_SNAPSHOT: TreeViewSnapshot = {
  query: {
    treeId: "",
    viewKind: "family",
    rootPersonId: "",
    direction: TREE_VIEW_DEFAULT_DIRECTION,
    depth: TREE_VIEW_DEFAULT_DEPTH,
    maxNodes: TREE_VIEW_DEFAULT_MAX_NODES,
    maxRelationships: TREE_VIEW_DEFAULT_MAX_RELATIONSHIPS,
    filter: { relationshipKinds: [], livingStatus: [] },
  },
  etag: null,
  version: 0,
  generatedAt: null,
  nodesById: new Map(),
  edges: [],
  generations: [],
  redaction: { reasonCodes: [], droppedFieldCount: 0 },
  pagination: { hasMore: false },
  ui: {
    transform: { x: 0, y: 0, scale: 1 },
    collapsedBranchIds: new Set<string>(),
    selectedPersonId: null,
    viewport: { topLeftGeneration: -4, bottomRightGeneration: 4 },
  },
  meta: { status: "idle" },
};

export function defaultQuery(input: {
  treeId: string;
  viewKind: TreeProjectionViewKind;
  rootPersonId: string;
}): TreeViewQuery {
  if (!TREE_PROJECTION_VIEW_KINDS.includes(input.viewKind)) {
    throw new RangeError(`tree-view: viewKind must be in closed-set, got "${input.viewKind}"`);
  }
  return {
    treeId: input.treeId,
    viewKind: input.viewKind,
    rootPersonId: input.rootPersonId,
    direction: TREE_VIEW_DEFAULT_DIRECTION,
    depth: TREE_VIEW_DEFAULT_DEPTH,
    maxNodes: TREE_VIEW_DEFAULT_MAX_NODES,
    maxRelationships: TREE_VIEW_DEFAULT_MAX_RELATIONSHIPS,
    filter: { relationshipKinds: [], livingStatus: [] },
  };
}

/**
 * Normalise a wire `ProjectionNode` into the in-memory
 * representation the tree state machine stores. The conversion
 * is intentionally lossy: only the fields the renderer actually
 * uses are kept. Anything else was already redacted by the
 * service (glossary-and-policy-matrix.md §2.2).
 */
export function toViewNode(body: TreeProjectionBody["nodes"][number]): TreeViewNode {
  const node: TreeViewNode = {
    personId: body.personId,
    displayName: body.redaction.redacted ? "" : body.displayName,
    livingStatus: body.livingStatus,
    generation: body.generation,
    redacted: body.redaction.redacted,
    reasonCodes: body.redaction.reasonCodes,
  };
  if (!body.redaction.redacted && body.birthYear !== undefined) {
    (node as { birthYear?: number }).birthYear = body.birthYear;
  }
  if (!body.redaction.redacted && body.deathYear !== undefined) {
    (node as { deathYear?: number }).deathYear = body.deathYear;
  }
  if (body.privacyLevel !== undefined) {
    (
      node as { privacyLevel?: TreeProjectionLivingStatus | "PUBLIC" | "UNLISTED" | "PRIVATE" }
    ).privacyLevel = body.privacyLevel;
  }
  return node;
}

export function toViewEdge(body: TreeProjectionBody["edges"][number]): TreeViewEdge {
  return {
    id: `${body.fromPersonId}->${body.toPersonId}:${body.relationshipKind}`,
    fromPersonId: body.fromPersonId,
    toPersonId: body.toPersonId,
    relationshipKind: body.relationshipKind,
  };
}

/**
 * Build a fresh snapshot from a wire body. The `nodesById`
 * map gives the renderer its stable identity: `personId` is
 * the key and never changes between refetches.
 */
export function snapshotFromBody(
  query: TreeViewQuery,
  body: TreeProjectionBody,
  etag: string | null,
  ui?: Partial<TreeViewUiState>,
): TreeViewSnapshot {
  const nodesById = new Map<string, TreeViewNode>();
  for (const node of body.nodes) {
    nodesById.set(node.personId, toViewNode(node));
  }
  const edges = body.edges.map(toViewEdge);
  const generations = collectGenerations(body.nodes.map((n) => n.generation));
  const pagination: { hasMore: boolean; nextCursor?: string } = {
    hasMore: body.pagination?.hasMore ?? false,
  };
  if (body.pagination?.nextCursor !== undefined) {
    pagination.nextCursor = body.pagination.nextCursor;
  }
  return {
    query,
    etag,
    version: body.version,
    generatedAt: body.generatedAt,
    nodesById,
    edges,
    generations,
    redaction: body.redaction,
    pagination,
    ui: {
      transform: ui?.transform ?? { x: 0, y: 0, scale: 1 },
      collapsedBranchIds: ui?.collapsedBranchIds ?? new Set<string>(),
      selectedPersonId: ui?.selectedPersonId ?? null,
      viewport: ui?.viewport ?? {
        topLeftGeneration: -query.depth,
        bottomRightGeneration: query.depth,
      },
    },
    meta: { status: "ready" },
  };
}

/**
 * Merge a `TreeProjectionDelta` into the snapshot. The merge
 * is immutable: a brand new `Map` + array is returned and the
 * caller's snapshot is untouched. `personId` keys keep the
 * renderer's stable identity invariant.
 */
export function mergeDelta(
  snapshot: TreeViewSnapshot,
  delta: TreeProjectionDeltaBody,
): TreeViewSnapshot {
  if (delta.version < snapshot.version) {
    throw new RangeError(
      `tree-view: cannot merge delta (version ${delta.version}) into snapshot (version ${snapshot.version}) — version went backwards`,
    );
  }
  const nodesById = new Map(snapshot.nodesById);
  for (const node of delta.addedNodes) {
    nodesById.set(node.personId, toViewNode(node));
  }
  if (delta.removedPersonIds?.length) {
    for (const removed of delta.removedPersonIds) {
      nodesById.delete(removed);
    }
  }
  const edges = [...snapshot.edges];
  for (const edge of delta.addedEdges) {
    edges.push(toViewEdge(edge));
  }
  const generations = collectGenerations(Array.from(nodesById.values()).map((n) => n.generation));
  return {
    ...snapshot,
    version: delta.version,
    nodesById,
    edges,
    generations,
    meta: { status: "ready" },
  };
}

function collectGenerations(values: readonly number[]): readonly number[] {
  const set = new Set<number>();
  for (const value of values) {
    if (
      Number.isInteger(value) &&
      value >= -TREE_PROJECTION_MAX_DEPTH &&
      value <= TREE_PROJECTION_MAX_DEPTH
    ) {
      set.add(value);
    }
  }
  return [...set].sort((a, b) => a - b);
}

/**
 * Apply a viewport / transform change without firing a fetch.
 * The fetch happens via `requestViewport()` below.
 */
export function applyUiPatch(
  snapshot: TreeViewSnapshot,
  patch: Partial<TreeViewUiState>,
): TreeViewSnapshot {
  return {
    ...snapshot,
    ui: {
      transform: patch.transform ?? snapshot.ui.transform,
      collapsedBranchIds: patch.collapsedBranchIds ?? snapshot.ui.collapsedBranchIds,
      selectedPersonId:
        patch.selectedPersonId !== undefined
          ? patch.selectedPersonId
          : snapshot.ui.selectedPersonId,
      viewport: patch.viewport ?? snapshot.ui.viewport,
    },
  };
}

export function toggleBranch(snapshot: TreeViewSnapshot, branchId: string): TreeViewSnapshot {
  const next = new Set(snapshot.ui.collapsedBranchIds);
  if (next.has(branchId)) {
    next.delete(branchId);
  } else {
    next.add(branchId);
  }
  return applyUiPatch(snapshot, { collapsedBranchIds: next });
}

/**
 * Validate a viewport fetch against the policy hard caps. The
 * function is **synchronous** and throws so the React UI can
 * surface the violation locally without a round-trip.
 */
export function assertViewportBudget(viewport: {
  depth: number;
  maxNodes: number;
  topLeftGeneration: number;
  bottomRightGeneration: number;
}): void {
  assertDepth(viewport.depth);
  assertMaxNodes(viewport.maxNodes);
  if (viewport.topLeftGeneration > viewport.bottomRightGeneration) {
    throw new RangeError(
      `tree-view: viewport topLeftGeneration (${viewport.topLeftGeneration}) must be <= bottomRightGeneration (${viewport.bottomRightGeneration})`,
    );
  }
  if (
    viewport.topLeftGeneration < -TREE_PROJECTION_MAX_DEPTH ||
    viewport.bottomRightGeneration > TREE_PROJECTION_MAX_DEPTH
  ) {
    throw new RangeError(
      `tree-view: viewport generations must fit within [-${TREE_PROJECTION_MAX_DEPTH}, ${TREE_PROJECTION_MAX_DEPTH}]`,
    );
  }
  const span = viewport.bottomRightGeneration - viewport.topLeftGeneration + 1;
  if (span > 12) {
    throw new RangeError(
      `tree-view: viewport generation span (${span}) exceeds the 12-generation hard cap`,
    );
  }
}

export interface TreeViewStoreOptions {
  readonly fetcher: TreeProjectionFetcher;
}

/**
 * Reactive store. `subscribe()` returns an unsubscribe handle;
 * the listener is invoked with every snapshot transition. The
 * store is intentionally **single-snapshot-per-treeId** — when
 * the user navigates between views (pedigree ↔ descendant) we
 * swap the whole snapshot rather than mutating.
 */
export class TreeViewStore {
  private snapshot: TreeViewSnapshot;
  private readonly listeners = new Set<(snapshot: TreeViewSnapshot) => void>();
  private readonly fetcher: TreeProjectionFetcher;
  private inflight: AbortController | null = null;

  constructor(initial: TreeViewSnapshot, options: TreeViewStoreOptions) {
    this.snapshot = initial;
    this.fetcher = options.fetcher;
  }

  getSnapshot(): TreeViewSnapshot {
    return this.snapshot;
  }

  subscribe(listener: (snapshot: TreeViewSnapshot) => void): () => void {
    this.listeners.add(listener);
    listener(this.snapshot);
    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Replace the current snapshot wholesale. Used by the route
   * loader when the user navigates between `viewKind`s or
   * `treeId`s so we don't leak state across distinct trees.
   */
  reset(next: TreeViewSnapshot): void {
    this.snapshot = next;
    this.emit();
  }

  /**
   * Apply a UI-only patch (pan/zoom/collapse/select). No
   * network call; the user expects immediate feedback.
   */
  patchUi(patch: Partial<TreeViewUiState>): void {
    this.snapshot = applyUiPatch(this.snapshot, patch);
    this.emit();
  }

  toggleCollapse(branchId: string): void {
    this.snapshot = toggleBranch(this.snapshot, branchId);
    this.emit();
  }

  /**
   * Fetch the initial projection snapshot. The caller passes
   * the validated query; the store validates once more before
   * issuing the request so a tampered URL still fails closed.
   */
  async load(query: TreeViewQuery): Promise<void> {
    const validated = validateQuery(query);
    this.cancelInflight();
    const controller = new AbortController();
    this.inflight = controller;
    this.snapshot = { ...this.snapshot, query: validated, meta: { status: "loading" } };
    this.emit();
    try {
      const projectionArgs: Parameters<TreeProjectionFetcher["getProjection"]>[0] = {
        treeId: validated.treeId,
        viewKind: validated.viewKind,
        rootPersonId: validated.rootPersonId,
        direction: validated.direction,
        depth: validated.depth,
        maxNodes: validated.maxNodes,
        maxRelationships: validated.maxRelationships,
        filter: validated.filter,
      };
      if (this.snapshot.etag !== null) projectionArgs.ifNoneMatch = this.snapshot.etag;
      const response = await this.fetcher.getProjection(projectionArgs);
      if (controller.signal.aborted) return;
      this.applyResponse(validated, response);
    } catch (error) {
      if (controller.signal.aborted) return;
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "error", lastError: toMessage(error) },
      };
      this.emit();
    }
  }

  /**
   * Request a viewport fetch. The function refuses to issue a
   * request that violates the policy hard caps so we never ask
   * the BFF for the full graph (R6.3).
   */
  async requestViewport(input: {
    anchorPersonId: string;
    direction: TreeProjectionDirection;
    depth: number;
    maxNodes: number;
    viewport?: TreeViewViewport;
  }): Promise<void> {
    assertViewportBudget({
      depth: input.depth,
      maxNodes: input.maxNodes,
      topLeftGeneration: input.viewport?.topLeftGeneration ?? -input.depth,
      bottomRightGeneration: input.viewport?.bottomRightGeneration ?? input.depth,
    });
    if (input.maxNodes > TREE_PROJECTION_MAX_NEIGHBORHOOD_NODES) {
      throw new RangeError(
        `tree-view: viewport maxNodes (${input.maxNodes}) exceeds the policy cap (${TREE_PROJECTION_MAX_NEIGHBORHOOD_NODES})`,
      );
    }
    this.cancelInflight();
    const controller = new AbortController();
    this.inflight = controller;
    this.snapshot = { ...this.snapshot, meta: { status: "loading" } };
    this.emit();
    try {
      const neighborhoodArgs: Parameters<TreeProjectionFetcher["expandNeighborhood"]>[0] = {
        treeId: this.snapshot.query.treeId,
        viewKind: this.snapshot.query.viewKind,
        anchorPersonId: input.anchorPersonId,
        direction: input.direction,
        depth: input.depth,
        maxNodes: input.maxNodes,
        baseVersion: this.snapshot.version,
      };
      if (input.viewport !== undefined) neighborhoodArgs.viewport = input.viewport;
      if (this.snapshot.etag !== null) neighborhoodArgs.ifMatch = this.snapshot.etag;
      const response = await this.fetcher.expandNeighborhood(neighborhoodArgs);
      if (controller.signal.aborted) return;
      this.applyDelta(response);
    } catch (error) {
      if (controller.signal.aborted) return;
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "error", lastError: toMessage(error) },
      };
      this.emit();
    }
  }

  private applyResponse(query: TreeViewQuery, response: RawHttpResponse): void {
    if (response.status === 304) {
      this.snapshot = { ...this.snapshot, meta: { status: "ready" } };
      this.emit();
      return;
    }
    if (response.status !== 200) {
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "error", lastError: `BFF status ${response.status}` },
      };
      this.emit();
      return;
    }
    const body = response.parsed as TreeProjectionBody | undefined;
    if (!body) {
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "error", lastError: "BFF returned an empty body" },
      };
      this.emit();
      return;
    }
    const etag = response.headers["etag"] ?? null;
    const versionHeader = response.headers["x-tree-projection-version"];
    const version = versionHeader ? Number.parseInt(versionHeader, 10) : body.version;
    const next = snapshotFromBody(query, body, etag, this.snapshot.ui);
    if (Number.isFinite(version) && version > this.snapshot.version) {
      this.snapshot = { ...next, version };
    } else {
      this.snapshot = next;
    }
    this.emit();
  }

  private applyDelta(response: RawHttpResponse): void {
    if (response.status === 409) {
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "stale", lastError: "projection stale" },
      };
      this.emit();
      return;
    }
    if (response.status !== 200) {
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "error", lastError: `BFF status ${response.status}` },
      };
      this.emit();
      return;
    }
    const body = response.parsed as TreeProjectionDeltaBody | undefined;
    if (!body) {
      this.snapshot = {
        ...this.snapshot,
        meta: { status: "error", lastError: "BFF returned an empty delta" },
      };
      this.emit();
      return;
    }
    this.snapshot = mergeDelta(this.snapshot, body);
    this.emit();
  }

  private cancelInflight(): void {
    if (this.inflight) {
      this.inflight.abort();
      this.inflight = null;
    }
  }

  private emit(): void {
    for (const listener of this.listeners) {
      listener(this.snapshot);
    }
  }
}

function validateQuery(query: TreeViewQuery): TreeViewQuery {
  if (!TREE_PROJECTION_VIEW_KINDS.includes(query.viewKind)) {
    throw new RangeError(`tree-view: viewKind must be in closed-set, got "${query.viewKind}"`);
  }
  const relationshipKinds = assertClosedSet(
    "filter.relationshipKinds",
    [...query.filter.relationshipKinds],
    [
      "BIRTH_PARENT",
      "ADOPTIVE_PARENT",
      "FOSTER_PARENT",
      "STEP_PARENT",
      "GUARDIAN",
      "SPOUSE",
      "PARTNER",
      "CUSTOM",
    ] as const,
  );
  const livingStatus = assertClosedSet("filter.livingStatus", [...query.filter.livingStatus], [
    "LIVING",
    "PRESUMED_LIVING",
    "DECEASED",
    "PRESUMED_DECEASED",
    "UNKNOWN",
  ] as const);
  return {
    ...query,
    depth: assertDepth(query.depth),
    maxNodes: assertMaxNodes(query.maxNodes),
    filter: { relationshipKinds, livingStatus },
  };
}

function toMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  return String(error);
}
