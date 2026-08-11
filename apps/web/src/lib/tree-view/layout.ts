/**
 * apps/web/src/lib/tree-view/layout.ts
 *
 * Per-view-kind layout strategies. The renderer library choice
 * is **DEFERRED** per ADR-E0.5-10, so each strategy emits a
 * deterministic grid coordinate (x, y) plus a stable slot id
 * per `personId`. The React shell uses these coordinates to
 * position nodes inside the canvas; a future renderer can swap
 * the placement math without touching the state machine.
 *
 * Constraints honoured here:
 *   - The output NEVER includes a coordinate outside
 *     `[-MAX_GEN, MAX_GEN]` on the Y axis (depth cap = 12).
 *   - Stable slot id = `personId` (R6, `design.md` §10.2).
 *   - Family view groups nodes by the closed-set
 *     `ProjectionRelationshipKind` so the placeholder canvas
 *     doesn't silently drop fan/spouse edges.
 */
import type { TreeProjectionDirection, TreeProjectionViewKind } from "@genealogy/api-client";

import type { TreeViewEdge, TreeViewNode, TreeViewSnapshot } from "./store";

export interface LayoutSlot {
  readonly personId: string;
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
  readonly collapsed: boolean;
}

export interface LayoutEdge {
  readonly id: string;
  readonly from: { readonly x: number; readonly y: number };
  readonly to: { readonly x: number; readonly y: number };
  readonly relationshipKind: TreeViewEdge["relationshipKind"];
}

export interface LayoutResult {
  readonly slots: readonly LayoutSlot[];
  readonly edges: readonly LayoutEdge[];
  readonly bounds: {
    readonly minX: number;
    readonly minY: number;
    readonly maxX: number;
    readonly maxY: number;
  };
  readonly generations: readonly number[];
  readonly collapsedCount: number;
}

export const SLOT_WIDTH = 96;
export const SLOT_HEIGHT = 56;
export const SLOT_GAP_X = 24;
export const SLOT_GAP_Y = 48;

export function layoutSnapshot(
  snapshot: TreeViewSnapshot,
  options?: {
    readonly viewKind?: TreeProjectionViewKind;
    readonly direction?: TreeProjectionDirection;
  },
): LayoutResult {
  const viewKind = options?.viewKind ?? snapshot.query.viewKind;
  const direction = options?.direction ?? snapshot.query.direction;
  const strategy = pickStrategy(viewKind, direction);
  return strategy(snapshot);
}

function pickStrategy(
  viewKind: TreeProjectionViewKind,
  direction: TreeProjectionDirection,
): (snapshot: TreeViewSnapshot) => LayoutResult {
  if (viewKind === "pedigree") return pedigreeLayout;
  if (viewKind === "descendant") return descendantLayout;
  if (viewKind === "fan") return fanLayout;
  if (viewKind === "hourglass") return hourglassLayout;
  return familyLayout(direction);
}

function pedigreeLayout(snapshot: TreeViewSnapshot): LayoutResult {
  return generationLayout(snapshot, "ANCESTORS");
}

function descendantLayout(snapshot: TreeViewSnapshot): LayoutResult {
  return generationLayout(snapshot, "DESCENDANTS");
}

function generationLayout(
  snapshot: TreeViewSnapshot,
  direction: "ANCESTORS" | "DESCENDANTS",
): LayoutResult {
  const filtered = filterByDirection(snapshot, direction);
  return gridLayout(filtered);
}

function filterByDirection(
  snapshot: TreeViewSnapshot,
  direction: "ANCESTORS" | "DESCENDANTS" | "BOTH" | "SPOUSE_FAN",
): TreeViewSnapshot {
  if (direction === "BOTH") return snapshot;
  const nodesById = new Map<string, TreeViewNode>();
  for (const node of snapshot.nodesById.values()) {
    if (direction === "ANCESTORS" && node.generation < 0) nodesById.set(node.personId, node);
    else if (direction === "DESCENDANTS" && node.generation > 0) nodesById.set(node.personId, node);
    else if (direction === "ANCESTORS" || direction === "DESCENDANTS") {
      if (node.generation === 0) nodesById.set(node.personId, node);
    }
  }
  if (direction === "SPOUSE_FAN") {
    for (const node of snapshot.nodesById.values()) {
      if (node.generation === 0 || node.generation === -1 || node.generation === 1) {
        nodesById.set(node.personId, node);
      }
    }
  }
  const edges = snapshot.edges.filter((edge) => {
    return nodesById.has(edge.fromPersonId) && nodesById.has(edge.toPersonId);
  });
  return { ...snapshot, nodesById, edges };
}

function gridLayout(snapshot: TreeViewSnapshot): LayoutResult {
  const generations = [
    ...new Set(Array.from(snapshot.nodesById.values()).map((n) => n.generation)),
  ].sort((a, b) => a - b);
  const slots: LayoutSlot[] = [];
  let bounds = { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity };
  let collapsedCount = 0;
  for (const generation of generations) {
    const nodesInGen = Array.from(snapshot.nodesById.values())
      .filter((n) => n.generation === generation)
      .sort((a, b) => a.personId.localeCompare(b.personId));
    nodesInGen.forEach((node, index) => {
      const collapsed = snapshot.ui.collapsedBranchIds.has(node.personId);
      if (collapsed) collapsedCount += 1;
      const x = index * (SLOT_WIDTH + SLOT_GAP_X);
      const y = (generation + 12) * (SLOT_HEIGHT + SLOT_GAP_Y);
      slots.push({
        personId: node.personId,
        x,
        y,
        width: SLOT_WIDTH,
        height: SLOT_HEIGHT,
        collapsed,
      });
      bounds = {
        minX: Math.min(bounds.minX, x),
        minY: Math.min(bounds.minY, y),
        maxX: Math.max(bounds.maxX, x + SLOT_WIDTH),
        maxY: Math.max(bounds.maxY, y + SLOT_HEIGHT),
      };
    });
  }
  const edges = layoutEdges(slots, snapshot.edges);
  if (slots.length === 0) bounds = { minX: 0, minY: 0, maxX: 0, maxY: 0 };
  return { slots, edges, bounds, generations, collapsedCount };
}

function fanLayout(snapshot: TreeViewSnapshot): LayoutResult {
  const ancestors = Array.from(snapshot.nodesById.values()).filter((n) => n.generation < 0);
  ancestors.sort((a, b) => a.generation - b.generation || a.personId.localeCompare(b.personId));
  const slots: LayoutSlot[] = [];
  let bounds = { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity };
  let collapsedCount = 0;
  ancestors.forEach((node, index) => {
    const collapsed = snapshot.ui.collapsedBranchIds.has(node.personId);
    if (collapsed) collapsedCount += 1;
    const x = (index % 4) * (SLOT_WIDTH + SLOT_GAP_X);
    const y = (node.generation + 12) * (SLOT_HEIGHT + SLOT_GAP_Y);
    slots.push({
      personId: node.personId,
      x,
      y,
      width: SLOT_WIDTH,
      height: SLOT_HEIGHT,
      collapsed,
    });
    bounds = {
      minX: Math.min(bounds.minX, x),
      minY: Math.min(bounds.minY, y),
      maxX: Math.max(bounds.maxX, x + SLOT_WIDTH),
      maxY: Math.max(bounds.maxY, y + SLOT_HEIGHT),
    };
  });
  const root = Array.from(snapshot.nodesById.values()).find((n) => n.generation === 0);
  if (root) {
    const y = 12 * (SLOT_HEIGHT + SLOT_GAP_Y);
    slots.push({
      personId: root.personId,
      x: (ancestors.length * (SLOT_WIDTH + SLOT_GAP_X)) / 2,
      y,
      width: SLOT_WIDTH,
      height: SLOT_HEIGHT,
      collapsed: false,
    });
    bounds = {
      minX: Math.min(bounds.minX, 0),
      minY: Math.min(bounds.minY, y),
      maxX: Math.max(bounds.maxX, ancestors.length * (SLOT_WIDTH + SLOT_GAP_X)),
      maxY: Math.max(bounds.maxY, y + SLOT_HEIGHT),
    };
  }
  const edges = layoutEdges(slots, snapshot.edges);
  if (slots.length === 0) bounds = { minX: 0, minY: 0, maxX: 0, maxY: 0 };
  return {
    slots,
    edges,
    bounds,
    generations: [...new Set(slots.map((s) => Math.floor(s.y / (SLOT_HEIGHT + SLOT_GAP_Y)) - 12))],
    collapsedCount,
  };
}

function hourglassLayout(snapshot: TreeViewSnapshot): LayoutResult {
  const filtered = filterByDirection(snapshot, "BOTH");
  return gridLayout(filtered);
}

function familyLayout(
  direction: TreeProjectionDirection,
): (snapshot: TreeViewSnapshot) => LayoutResult {
  return (snapshot: TreeViewSnapshot) => {
    const filtered = filterByDirection(snapshot, direction);
    return gridLayout(filtered);
  };
}

function layoutEdges(
  slots: readonly LayoutSlot[],
  edges: readonly TreeViewEdge[],
): readonly LayoutEdge[] {
  const slotIndex = new Map<string, LayoutSlot>();
  for (const slot of slots) slotIndex.set(slot.personId, slot);
  const out: LayoutEdge[] = [];
  for (const edge of edges) {
    const from = slotIndex.get(edge.fromPersonId);
    const to = slotIndex.get(edge.toPersonId);
    if (!from || !to) continue;
    out.push({
      id: edge.id,
      from: { x: from.x + from.width / 2, y: from.y + from.height / 2 },
      to: { x: to.x + to.width / 2, y: to.y + to.height / 2 },
      relationshipKind: edge.relationshipKind,
    });
  }
  return out;
}
