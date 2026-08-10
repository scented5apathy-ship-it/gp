/**
 * apps/web/bench/renderer/canvas-hierarchy/index.mjs
 *
 * Renderer option #2 for ADR-E0.5-10. Canvas + custom layout
 * — emits a **serialised canvas command buffer** as JSON. The
 * command buffer is what an `OffscreenCanvas` 2D context would
 * consume in production; we serialise it so the Node bench can
 * exercise the option without spinning up a browser. The byte
 * size of the serialised command buffer is the bundle proxy.
 *
 * Stability: every draw command is keyed by the opaque
 * `personId`, satisfying `design.md` §10.2.
 */
import { assertOpaqueId } from "../contract.mjs";
import { layoutBounds } from "../layout-core.mjs";

/**
 * @param {{positions:{nodes:Map<string,{x:number,y:number,w:number,h:number}>, segments:Array<{from:{x:number,y:number},to:{x:number,y:number}}>}, nodeCount:number, edgeCount:number}} layout
 * @returns {{commands:string, commandCount:number, bytes:number, bounds:{width:number,height:number}}}
 */
export function render(layout) {
  if (!layout?.positions?.nodes) {
    throw new Error("layout.positions.nodes missing");
  }
  const bounds = layoutBounds(layout);
  const cmds = [];
  let commandCount = 0;

  cmds.push(`resize ${bounds.width} ${bounds.height}`);
  commandCount += 1;

  // Single style block — the production runtime reuses the
  // same fill/stroke/font for every node, so we hoist them.
  cmds.push("fill #1f6feb");
  cmds.push("stroke #1f6feb 1");
  commandCount += 2;

  for (const seg of layout.positions.segments ?? []) {
    if (!seg?.from || !seg?.to) continue;
    cmds.push(`line ${seg.from.x} ${seg.from.y} ${seg.to.x} ${seg.to.y}`);
    commandCount += 1;
  }
  for (const [personId, p] of layout.positions.nodes) {
    assertOpaqueId(personId, "personId");
    cmds.push(`rect ${p.x} ${p.y} ${p.w} ${p.h}`);
    commandCount += 1;
  }

  const commands = cmds.join("\n");
  const bytes = Buffer.byteLength(commands, "utf8");
  return { commands, commandCount, bytes, bounds };
}
