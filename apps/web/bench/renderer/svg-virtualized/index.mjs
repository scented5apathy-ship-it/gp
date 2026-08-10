/**
 * apps/web/bench/renderer/svg-virtualized/index.mjs
 *
 * Renderer option #1 for ADR-E0.5-10. SVG + DOM virtualization
 * — emits an SVG `<svg>` document with one `<g>` per node and
 * one `<path>` per edge segment. Virtualization is simulated
 * by emitting only the nodes whose `x,y` lies inside the
 * supplied `viewport`; the bench calls this with a viewport
 * sized to the layout bounds.
 *
 * The emitted artefact is **string** SVG (UTF-8 bytes count as
 * the bundle proxy). No DOM mutation happens in this module so
 * the Node bench can exercise it without `jsdom`.
 *
 * Stability: every node carries `data-person-id` keyed by the
 * opaque `personId`, not by array index, satisfying
 * `design.md` §10.2 (stable node identity across re-renders).
 */
import { assertOpaqueId } from "../contract.mjs";
import { layoutBounds } from "../layout-core.mjs";

/**
 * @param {{positions:{nodes:Map<string,{x:number,y:number,w:number,h:number}>, segments:Array<{from:{x:number,y:number},to:{x:number,y:number}}>}, nodeCount:number, edgeCount:number}} layout
 * @param {{viewport?:{x:number,y:number,width:number,height:number}}} [opts]
 * @returns {{svg:string, nodeCount:number, segmentCount:number, bytes:number}}
 */
export function render(layout, opts = {}) {
  if (!layout?.positions?.nodes) {
    throw new Error("layout.positions.nodes missing");
  }
  const bounds = layoutBounds(layout);
  const viewport = opts.viewport ?? {
    x: 0,
    y: 0,
    width: bounds.width,
    height: bounds.height,
  };

  /** @type {string[]} */
  const nodeTags = [];
  let visibleNodes = 0;
  for (const [personId, p] of layout.positions.nodes) {
    assertOpaqueId(personId, "personId");
    if (
      p.x + p.w < viewport.x ||
      p.x > viewport.x + viewport.width ||
      p.y + p.h < viewport.y ||
      p.y > viewport.y + viewport.height
    ) {
      continue;
    }
    visibleNodes += 1;
    nodeTags.push(
      `<g class="node" data-person-id="${escapeAttr(personId)}" transform="translate(${p.x},${p.y})"><rect width="${p.w}" height="${p.h}" rx="4"/></g>`,
    );
  }

  /** @type {string[]} */
  const edgeTags = [];
  let visibleSegments = 0;
  for (const seg of layout.positions.segments ?? []) {
    if (!seg?.from || !seg?.to) continue;
    // Skip segments whose bounding box falls entirely outside
    // the viewport (cheap virtualization heuristic).
    const minX = Math.min(seg.from.x, seg.to.x);
    const maxX = Math.max(seg.from.x, seg.to.x);
    const minY = Math.min(seg.from.y, seg.to.y);
    const maxY = Math.max(seg.from.y, seg.to.y);
    if (maxX < viewport.x || minX > viewport.x + viewport.width) continue;
    if (maxY < viewport.y || minY > viewport.y + viewport.height) continue;
    visibleSegments += 1;
    edgeTags.push(`<path class="edge" d="M${seg.from.x},${seg.from.y} L${seg.to.x},${seg.to.y}"/>`);
  }

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${viewport.width}" height="${viewport.height}" viewBox="0 0 ${viewport.width} ${viewport.height}">${edgeTags.join("")}${nodeTags.join("")}</svg>`;
  const bytes = Buffer.byteLength(svg, "utf8");
  return { svg, nodeCount: visibleNodes, segmentCount: visibleSegments, bytes };
}

function escapeAttr(value) {
  return String(value).replace(/[&<>"']/g, (ch) => {
    switch (ch) {
      case "&":
        return "&amp;";
      case "<":
        return "&lt;";
      case ">":
        return "&gt;";
      case '"':
        return "&quot;";
      case "'":
        return "&apos;";
      default:
        return ch;
    }
  });
}
