/**
 * apps/web/bench/renderer/a11y-cost.mjs
 *
 * Heuristic accessibility cost score for the E5.1 benchmark.
 * The score is a number in `[0, 1]`; `1.0` means the option is
 * trivial to make WCAG 2.2 AA compliant, `0.0` means it is
 * nearly impossible without a complete rewrite. Weights come
 * from `architecture-decisions.md` ADR-E0.5-10 §Inputs
 * (accessibility cost = keyboard alternative + screen reader
 * cost + reduced motion + focus management).
 *
 * The score is a **heuristic**; the real axe-core + Playwright
 * audit lands in E5.5 with the editor milestone. The point of
 * computing the heuristic here is to make the renderer choice
 * aware of accessibility cost *before* committing to a
 * production library.
 */

const WEIGHTS = Object.freeze({
  semanticAlternative: 0.25, // semantic <table>/<list> alternative
  screenReader: 0.3, // ARIA + live region cost
  focusManagement: 0.2, // focus-visible + roving tabindex cost
  reducedMotion: 0.1, // prefers-reduced-motion override cost
  stableIdentity: 0.15, // design.md §10.2 stable node identity
});

/**
 * Score a renderer option.
 * @param {string} option
 * @returns {number}
 */
export function scoreAccessibility(option) {
  switch (option) {
    case "SVG_VIRTUALIZED":
      // SVG carries natural semantic structure (each `<g>` can
      // take role="treeitem", aria-level, aria-posinset,
      // aria-setsize). Reduced-motion and stable identity are
      // trivial. Focus management requires roving tabindex +
      // careful aria-activedescendant wiring.
      return (
        WEIGHTS.semanticAlternative * 0.9 +
        WEIGHTS.screenReader * 0.85 +
        WEIGHTS.focusManagement * 0.7 +
        WEIGHTS.reducedMotion * 0.95 +
        WEIGHTS.stableIdentity * 0.95
      );
    case "CANVAS_HIERARCHY":
      // Canvas has no semantic alternative unless we render a
      // hidden SVG fallback. Screen-reader cost is the highest
      // of the three options. Reduced motion is free but
      // focus management is fragile.
      return (
        WEIGHTS.semanticAlternative * 0.4 +
        WEIGHTS.screenReader * 0.35 +
        WEIGHTS.focusManagement * 0.5 +
        WEIGHTS.reducedMotion * 0.95 +
        WEIGHTS.stableIdentity * 0.9
      );
    case "HYBRID":
      // Split-brain: must support both backends a11y-wise. Score
      // is the *minimum* of the two backends because users who
      // happen to land above the threshold experience the lower
      // score.
      return (
        Math.min(scoreAccessibility("SVG_VIRTUALIZED"), scoreAccessibility("CANVAS_HIERARCHY")) -
        0.05
      );
    default:
      throw new Error(`unknown option ${option}`);
  }
}
