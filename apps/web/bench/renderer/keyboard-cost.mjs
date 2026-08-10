/**
 * apps/web/bench/renderer/keyboard-cost.mjs
 *
 * Heuristic keyboard-navigation cost score for the E5.1
 * benchmark. Same scoring scale as `a11y-cost.mjs` — `1.0` is
 * free, `0.0` is impossible without a rewrite. Weights come
 * from `requirements.md` R6 + R18 (keyboard tree alternative)
 * and `design.md` §10.4 (keyboard tree alternative dạng
 * danh sách).
 */

const WEIGHTS = Object.freeze({
  arrowTraversal: 0.35, // arrow-key traversal of the tree
  skipLink: 0.2, // skip-link to bypass tree region
  focusTrap: 0.15, // focus trap inside tree widget
  rovingTabindex: 0.3, // roving tabindex implementation
});

/**
 * Score a renderer option.
 * @param {string} option
 * @returns {number}
 */
export function scoreKeyboard(option) {
  switch (option) {
    case "SVG_VIRTUALIZED":
      // SVG carries natural DOM nodes, so roving tabindex +
      // arrow handlers are well-trodden (combobox / listbox /
      // treeview WAI-ARIA patterns).
      return (
        WEIGHTS.arrowTraversal * 0.95 +
        WEIGHTS.skipLink * 0.9 +
        WEIGHTS.focusTrap * 0.85 +
        WEIGHTS.rovingTabindex * 0.9
      );
    case "CANVAS_HIERARCHY":
      // Canvas has no DOM nodes; arrow handlers must do their
      // own focus bookkeeping. The screen-reader "alt" path is
      // even more expensive here.
      return (
        WEIGHTS.arrowTraversal * 0.5 +
        WEIGHTS.skipLink * 0.85 +
        WEIGHTS.focusTrap * 0.5 +
        WEIGHTS.rovingTabindex * 0.4
      );
    case "HYBRID":
      return Math.min(scoreKeyboard("SVG_VIRTUALIZED"), scoreKeyboard("CANVAS_HIERARCHY")) - 0.05;
    default:
      throw new Error(`unknown option ${option}`);
  }
}
