/**
 * apps/web/components/tree/index.ts
 *
 * Tree placeholder module per ADR-E0.5-10 §Consequences
 * ("Until this ADR closes, `apps/web/components/tree/` uses
 * a placeholder canvas that renders at most 1 K nodes.").
 *
 * This module is intentionally tiny: it exposes the
 * documented placeholder API so any future caller in
 * `apps/web/src/components/` can import the placeholder
 * without coupling to a renderer library choice. The actual
 * placeholder component lives in `placeholder-canvas.tsx`.
 *
 * No renderer library is wired in here. ADR-E0.5-10 remains
 * `DEFERRED` until the prototype benchmark in E5.1 / E5.3
 * produces a p75 interaction time under 2,5 s on a 10 K
 * synthetic tree on mid-tier mobile, per
 * `architecture-decisions.md` ADR-E0.5-10 §Decision.
 */
export { PlaceholderTreeCanvas } from "./placeholder-canvas";
export const PLACEHOLDER_TREE_MAX_NODES = 1_000;
export const TREE_RENDERER_ENGINE_ADR = "ADR-E0.5-10";
