/**
 * apps/web/bench/renderer/hybrid/index.mjs
 *
 * Renderer option #3 for ADR-E0.5-10. Hybrid: SVG for ≤
 * `hybridThresholdNodes`, Canvas for > `hybridThresholdNodes`.
 * The bench measures the **switching overhead** so the
 * recommendation memo can flag whether the split is worth the
 * complexity.
 *
 * `hybridThresholdNodes` defaults to 5000 (ADR-E0.5-10 §Options
 * option 3) but is read from the contract via
 * `loadBenchPolicy().spec.hybridThresholdNodes` so the linter
 * stays the source of truth.
 */
import { render as renderSvg } from "../svg-virtualized/index.mjs";
import { render as renderCanvas } from "../canvas-hierarchy/index.mjs";
import { layoutBounds } from "../layout-core.mjs";

const DEFAULT_THRESHOLD = 5000;

/**
 * @param {{positions:{nodes:Map<string,{x:number,y:number,w:number,h:number}>, segments:Array<{from:{x:number,y:number},to:{x:number,y:number}}>}, nodeCount:number, edgeCount:number}} layout
 * @param {{threshold?:number}} [opts]
 */
export function render(layout, opts = {}) {
  if (!layout?.positions?.nodes) {
    throw new Error("layout.positions.nodes missing");
  }
  const threshold = opts.threshold ?? DEFAULT_THRESHOLD;
  const useSvg = layout.nodeCount <= threshold;
  const bounds = layoutBounds(layout);
  const start = process.hrtime.bigint();
  const payload = useSvg
    ? { backend: "SVG", ...renderSvg(layout, { viewport: { x: 0, y: 0, ...bounds } }) }
    : { backend: "CANVAS", ...renderCanvas(layout) };
  const elapsedNs = Number(process.hrtime.bigint() - start);
  return {
    ...payload,
    threshold,
    switchOverheadNs: elapsedNs,
    switchOverheadMs: elapsedNs / 1e6,
  };
}
