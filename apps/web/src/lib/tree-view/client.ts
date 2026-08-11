/**
 * apps/web/src/lib/tree-view/client.ts
 *
 * Production adapter between `TreeViewStore` and the BFF
 * `BffClient`. The adapter is intentionally thin: it exists so
 * the store stays framework-agnostic and so unit tests can swap
 * a stub fetcher without touching module-level singletons.
 *
 * The adapter also enforces the policy caps a SECOND time
 * (defence-in-depth) before issuing the request — even if the
 * store mis-validates, the adapter refuses to leak a violating
 * request to the BFF.
 */
import {
  assertDepth,
  assertMaxNodes,
  type BffClient,
  type RawHttpResponse,
} from "@genealogy/api-client";

import type { TreeProjectionFetcher } from "./store";

export interface BffTreeProjectionFetcherOptions {
  readonly client: BffClient;
  /**
   * Optional override used by unit tests to capture the wire
   * envelope (status + headers + parsed body) without poking
   * the real network.
   */
  readonly hook?: (response: RawHttpResponse) => void;
}

/**
 * Build a `TreeProjectionFetcher` that delegates to a real
 * `BffClient`. The adapter never throws on closed-set
 * violations — those are surfaced by the BFF as 4xx and the
 * store converts them to `meta.status = "error"`.
 */
export function createBffTreeProjectionFetcher(
  options: BffTreeProjectionFetcherOptions,
): TreeProjectionFetcher {
  return {
    async getProjection(args) {
      const request: Parameters<BffClient["getTreeProjection"]>[0] = {
        treeId: args.treeId,
        viewKind: args.viewKind,
        rootPersonId: args.rootPersonId,
      };
      if (args.direction !== undefined) request.direction = args.direction;
      if (args.depth !== undefined) request.depth = args.depth;
      if (args.maxNodes !== undefined) request.maxNodes = args.maxNodes;
      if (args.maxRelationships !== undefined) request.maxRelationships = args.maxRelationships;
      if (args.filter !== undefined) request.filter = args.filter;
      if (args.ifNoneMatch !== undefined) request.ifNoneMatch = args.ifNoneMatch;
      const response = await options.client.getTreeProjection(request);
      const envelope: RawHttpResponse = {
        status: response.status,
        headers: responseHeaders(response.etag, response.projectionVersion, response.generatedAt),
        parsed: response.body,
      };
      options.hook?.(envelope);
      return envelope;
    },
    async expandNeighborhood(args) {
      assertDepth(args.depth);
      assertMaxNodes(args.maxNodes);
      const request: Parameters<BffClient["expandNeighborhood"]>[0] = {
        treeId: args.treeId,
        viewKind: args.viewKind,
        anchorPersonId: args.anchorPersonId,
        direction: args.direction,
        depth: args.depth,
        maxNodes: args.maxNodes,
        baseVersion: args.baseVersion,
      };
      if (args.viewport !== undefined) request.viewport = args.viewport;
      if (args.ifMatch !== undefined) request.ifMatch = args.ifMatch;
      const response = await options.client.expandNeighborhood(request);
      const envelope: RawHttpResponse = {
        status: response.status,
        headers: responseHeaders(response.etag, response.projectionVersion, null),
        parsed: response.body,
      };
      options.hook?.(envelope);
      return envelope;
    },
  };
}

function responseHeaders(
  etag: string | null,
  projectionVersion: number | null,
  generatedAt: string | null,
): Readonly<Record<string, string>> {
  const out: Record<string, string> = {};
  if (etag) out["etag"] = etag;
  if (projectionVersion !== null) out["x-tree-projection-version"] = String(projectionVersion);
  if (generatedAt) out["x-tree-projection-generated-at"] = generatedAt;
  return out;
}

export type { TreeProjectionFetcher } from "./store";
