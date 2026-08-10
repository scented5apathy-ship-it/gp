/**
 * apps/web/bench/renderer/layout-core.mjs
 *
 * Pure-data layout pass shared by every renderer option. Given
 * a `Neighborhood`, returns a `Layout` (nodes + edges with x/y
 * coordinates) that each option then renders. The layout is
 * deliberately simple (vertical stack + horizontal lane per
 * generation) so the bench isolates **renderer cost** from
 * layout cost — the worker boundary cost dominates the
 * measured `layoutMs` only for very small neighborhoods.
 */
import { assertOpaqueId, RENDERER_OPTIONS } from "./contract.mjs";

const NODE_WIDTH = 96;
const NODE_HEIGHT = 32;
const GENERATION_GAP = 96;
const SIBLING_GAP = 16;

/**
 * Lays out a `Neighborhood` top-down: each `generation` row
 * shares the same y; nodes are placed left-to-right inside
 * the row in the order they appear in `nodes`. Returns the
 * positions plus a per-option metadata blob the renderers
 * consume.
 *
 * `sampleRuns` lets the bench repeat the layout pass to
 * amortise per-call overhead — used by the bench harness
 * but never by the production runtime.
 *
 * @param {{nodes: Array<{personId:string, generation:number}>, edges: Array<{parentId:string, childId:string}>}} neighborhood
 * @param {string} option
 * @param {number} sampleRuns
 */
export function layoutNeighborhood(neighborhood, option, sampleRuns = 1) {
  if (!RENDERER_OPTIONS.includes(option)) {
    throw new Error(`unsupported option ${option}`);
  }
  if (!Array.isArray(neighborhood.nodes)) {
    throw new Error("neighborhood.nodes must be an array");
  }
  for (const node of neighborhood.nodes) {
    assertOpaqueId(node.personId, "node.personId");
  }

  let positions = null;
  let layoutRuns = 0;
  for (let i = 0; i < Math.max(1, sampleRuns); i += 1) {
    // Group nodes by generation so we can place siblings
    // side-by-side without overlap.
    /** @type {Map<number, string[]>} */
    const byGen = new Map();
    for (const node of neighborhood.nodes) {
      const list = byGen.get(node.generation) ?? [];
      list.push(node.personId);
      byGen.set(node.generation, list);
    }
    const generations = Array.from(byGen.keys()).sort((a, b) => a - b);
    /** @type {Map<string, {x:number, y:number, w:number, h:number}>} */
    const pos = new Map();
    for (const g of generations) {
      const ids = byGen.get(g);
      ids.forEach((id, index) => {
        pos.set(id, {
          x: index * (NODE_WIDTH + SIBLING_GAP),
          y: g * GENERATION_GAP,
          w: NODE_WIDTH,
          h: NODE_HEIGHT,
        });
      });
    }
    // Translate edges into straight-line segments using the
    // positions above. Each edge becomes a single horizontal
    // line at the parent's y plus a vertical connector to
    // the child's y.
    /** @type {Array<{from: {x:number, y:number}, to: {x:number, y:number}}>} */
    const segments = [];
    for (const e of neighborhood.edges) {
      const a = pos.get(e.parentId);
      const b = pos.get(e.childId);
      if (!a || !b) continue;
      const ax = a.x + a.w / 2;
      const ay = a.y + a.h;
      const bx = b.x + b.w / 2;
      const by = b.y;
      // L-shape: vertical drop from parent + horizontal to child x.
      segments.push({ from: { x: ax, y: ay }, to: { x: ax, y: by } });
      segments.push({ from: { x: ax, y: by }, to: { x: bx, y: by } });
    }
    positions = { nodes: pos, segments };
    layoutRuns += 1;
  }

  return {
    option,
    sampleRuns: layoutRuns,
    nodeCount: neighborhood.nodes.length,
    edgeCount: neighborhood.edges.length,
    positions,
  };
}

/**
 * Returns the dimensions of the laid-out canvas. Used by the
 * canvas renderer to size its viewport and by the bench to
 * compute the render byte size.
 * @param {{nodes: Map<string,{x:number,y:number,w:number,h:number}>, segments: Array<unknown>}} layout
 */
export function layoutBounds(layout) {
  const positions = layout.positions?.nodes;
  if (!positions || positions.size === 0) {
    return { width: 0, height: 0 };
  }
  let maxX = 0;
  let maxY = 0;
  for (const p of positions.values()) {
    if (p.x + p.w > maxX) maxX = p.x + p.w;
    if (p.y + p.h > maxY) maxY = p.y + p.h;
  }
  return { width: maxX, height: maxY };
}
