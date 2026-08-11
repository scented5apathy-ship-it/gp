/**
 * Typed surface for the BFF tree-projection endpoints
 * (`contracts/openapi/bff/v1/tree-projection.yaml`).
 *
 * The numeric/string literal sets are the **closed sets** enforced
 * by the contract. Anything outside them is rejected by the BFF
 * with a 400 `Problem` payload (RFC 9457) and surfaced to the UI
 * as `ApiError`. Mirroring the closed sets here lets the consumer
 * catch the violation BEFORE issuing the round-trip — the E5.3
 * tree state machine runs input through `assertClosedSet` so
 * invalid requests never leave the browser.
 *
 * The numeric caps (`maxDepth=12`, `maxNeighborhoodNodes=1000`,
 * `maxRelationshipsPerResponse=2000`) are pinned in
 * `contracts/genealogy/tree-projection-policy.yaml::spec` and
 * must not drift.
 */
export const TREE_PROJECTION_VIEW_KINDS = [
  "pedigree",
  "descendant",
  "fan",
  "hourglass",
  "family",
] as const;
export type TreeProjectionViewKind = (typeof TREE_PROJECTION_VIEW_KINDS)[number];

export const TREE_PROJECTION_DIRECTIONS = [
  "ANCESTORS",
  "DESCENDANTS",
  "BOTH",
  "SPOUSE_FAN",
] as const;
export type TreeProjectionDirection = (typeof TREE_PROJECTION_DIRECTIONS)[number];

export const TREE_PROJECTION_RELATIONSHIP_KINDS = [
  "BIRTH_PARENT",
  "ADOPTIVE_PARENT",
  "FOSTER_PARENT",
  "STEP_PARENT",
  "GUARDIAN",
  "SPOUSE",
  "PARTNER",
  "CUSTOM",
] as const;
export type TreeProjectionRelationshipKind = (typeof TREE_PROJECTION_RELATIONSHIP_KINDS)[number];

export const TREE_PROJECTION_LIVING_STATUSES = [
  "LIVING",
  "PRESUMED_LIVING",
  "DECEASED",
  "PRESUMED_DECEASED",
  "UNKNOWN",
] as const;
export type TreeProjectionLivingStatus = (typeof TREE_PROJECTION_LIVING_STATUSES)[number];

export const TREE_PROJECTION_REDACTION_REASON_CODES = [
  "living_redacted",
  "minor_guardian_required",
  "privacy_class_restricted",
  "visibility_unlisted_token_invalid",
] as const;
export type TreeProjectionRedactionReasonCode =
  (typeof TREE_PROJECTION_REDACTION_REASON_CODES)[number];

export const TREE_PROJECTION_MAX_DEPTH = 12;
export const TREE_PROJECTION_MAX_NEIGHBORHOOD_NODES = 1_000;
export const TREE_PROJECTION_MAX_RELATIONSHIPS_PER_RESPONSE = 2_000;
export const TREE_PROJECTION_FRESHNESS_TTL_SECONDS = 300;
export const TREE_PROJECTION_FRESHNESS_TTL_CEILING_SECONDS = 1_800;

/**
 * Body shape returned by
 * `GET /trees/{treeId}/projection/{viewKind}`. The 200 body is
 * modelled by `components.schemas.TreeProjection`; we re-declare
 * the fields we actually consume in the tree-view state machine.
 * `redaction` carries the closed-set reason codes that were
 * applied INSIDE `genealogy-service` — the renderer MUST NOT
 * re-redact (see glossary-and-policy-matrix.md §2.2).
 */
export interface TreeProjectionBody {
  readonly treeId: string;
  readonly viewKind: TreeProjectionViewKind;
  readonly direction: TreeProjectionDirection;
  readonly depth: number;
  readonly version: number;
  readonly generatedAt: string;
  readonly nodes: readonly TreeProjectionNodeBody[];
  readonly edges: readonly TreeProjectionEdgeBody[];
  readonly redaction: {
    readonly reasonCodes: readonly TreeProjectionRedactionReasonCode[];
    readonly droppedFieldCount: number;
    readonly policyVersion?: string;
  };
  readonly pagination?: {
    readonly hasMore: boolean;
    readonly nextCursor?: string;
  };
}

export interface TreeProjectionNodeBody {
  readonly personId: string;
  readonly displayName: string;
  readonly livingStatus: TreeProjectionLivingStatus;
  readonly birthYear?: number;
  readonly deathYear?: number;
  readonly generation: number;
  readonly privacyLevel?: "PUBLIC" | "UNLISTED" | "PRIVATE";
  readonly redaction: {
    readonly redacted: boolean;
    readonly reasonCodes: readonly TreeProjectionRedactionReasonCode[];
    readonly droppedFields?: readonly string[];
  };
}

export interface TreeProjectionEdgeBody {
  readonly fromPersonId: string;
  readonly toPersonId: string;
  readonly relationshipKind: TreeProjectionRelationshipKind;
  readonly provenanceStatus?: "HYPOTHESIS" | "ASSERTED" | "VERIFIED" | "DISPUTED";
}

export interface TreeProjectionDeltaBody {
  readonly version: number;
  readonly addedNodes: readonly TreeProjectionNodeBody[];
  readonly addedEdges: readonly TreeProjectionEdgeBody[];
  readonly removedPersonIds?: readonly string[];
}

/**
 * Response envelope from `getTreeProjection`. `notModified=true`
 * means the BFF returned 304; the caller keeps the previous
 * projection and short-circuits the state-machine update.
 */
export interface TreeProjectionResponse {
  readonly status: number;
  readonly etag: string | null;
  readonly projectionVersion: number | null;
  readonly generatedAt: string | null;
  readonly body: TreeProjectionBody | undefined;
  readonly notModified: boolean;
}

/**
 * Response envelope from `expandNeighborhood`. `stale=true`
 * means the BFF replied 409 (baseVersion drift) so the caller
 * must refetch the full snapshot before retrying.
 */
export interface NeighborhoodResponse {
  readonly status: number;
  readonly etag: string | null;
  readonly projectionVersion: number | null;
  readonly body: TreeProjectionDeltaBody | undefined;
  readonly stale: boolean;
  readonly preconditionFailed: boolean;
}

export interface RawHttpResponse {
  readonly status: number;
  readonly headers: Readonly<Record<string, string>>;
  readonly parsed: unknown;
}

/**
 * Pure validation helpers — used by the tree state machine to
 * keep all closed-set enforcement in one place. `assertClosedSet`
 * returns a normalised array so the tree store always sees the
 * canonical wire values, never arbitrary strings.
 */
export function assertClosedSet<T extends string>(
  field: string,
  values: readonly T[],
  allowed: readonly T[],
): readonly T[] {
  if (values.length === 0) return values;
  const allowedSet = new Set<string>(allowed);
  const normalised: T[] = [];
  for (const value of values) {
    if (!allowedSet.has(value)) {
      throw new RangeError(
        `tree-projection: ${field} value "${value}" is outside the contract closed-set (${allowed.join(", ")})`,
      );
    }
    normalised.push(value);
  }
  return normalised;
}

export function assertDepth(depth: number): number {
  if (!Number.isInteger(depth) || depth < 1 || depth > TREE_PROJECTION_MAX_DEPTH) {
    throw new RangeError(
      `tree-projection: depth must be an integer in [1, ${TREE_PROJECTION_MAX_DEPTH}], got ${depth}`,
    );
  }
  return depth;
}

export function assertMaxNodes(maxNodes: number): number {
  if (
    !Number.isInteger(maxNodes) ||
    maxNodes < 1 ||
    maxNodes > TREE_PROJECTION_MAX_NEIGHBORHOOD_NODES
  ) {
    throw new RangeError(
      `tree-projection: maxNodes must be an integer in [1, ${TREE_PROJECTION_MAX_NEIGHBORHOOD_NODES}], got ${maxNodes}`,
    );
  }
  return maxNodes;
}
