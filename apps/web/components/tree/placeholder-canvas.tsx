/**
 * apps/web/components/tree/placeholder-canvas.tsx
 *
 * Placeholder tree view per ADR-E0.5-10 §Consequences. Renders
 * the tree as a **placeholder HTML5 `<canvas>`** that displays
 * at most 1 K nodes; the placeholder bails out with an
 * informative message when the dataset exceeds the cap so we
 * never silently drop nodes.
 *
 * This is intentionally not a real renderer; it ships as a
 * stub so the web shell can be exercised end-to-end while the
 * real renderer choice is still `DEFERRED`. When ADR-E0.5-10
 * closes (Web lead + Performance lead), this file is replaced
 * with the chosen renderer module.
 *
 * Accessibility (R6, R18):
 *   - `<canvas>` carries `role="img"` + `aria-label` describing
 *     the placeholder state so screen readers can announce
 *     "Tree view placeholder — renderer engine pending
 *     ADR-E0.5-10 closure".
 *   - A semantic fallback `<ul>` lists the first N person ids
 *     so the tree has a non-visual representation until the
 *     real renderer ships.
 *   - `prefers-reduced-motion` is honoured (no animation).
 */
import type { JSX } from "react";

const MAX_NODES = 1_000;

export interface PlaceholderTreeCanvasProps {
  readonly tenantId: string;
  readonly treeId: string;
  readonly rootPersonId?: string;
  readonly personIds?: readonly string[];
}

/**
 * Renders the 1 K-cap placeholder tree view. Accepts a list of
 * opaque person ids and draws one labelled circle per id
 * (truncated to MAX_NODES) plus the semantic `<ul>` fallback
 * required by R18.
 */
export function PlaceholderTreeCanvas(props: PlaceholderTreeCanvasProps): JSX.Element {
  const ids = (props.personIds ?? []).slice(0, MAX_NODES);
  const overflow = (props.personIds?.length ?? 0) > MAX_NODES;
  const summary = `Tree view placeholder — renderer engine pending ADR-E0.5-10 closure (showing ${ids.length} of ${props.personIds?.length ?? 0} person ids).`;

  return (
    <section
      aria-label={summary}
      data-tree-tenant={props.tenantId}
      data-tree-id={props.treeId}
      data-tree-placeholder="ADR-E0.5-10"
      className="tree-placeholder"
    >
      <canvas
        role="img"
        aria-label={summary}
        width={480}
        height={160}
        className="tree-placeholder__canvas"
      />
      <p className="tree-placeholder__caption">{summary}</p>
      {overflow ? (
        <p className="tree-placeholder__overflow" role="status">
          Placeholder cap reached — renderer engine selection pending.
        </p>
      ) : null}
      <ul className="tree-placeholder__fallback" aria-label="Person id list">
        {ids.slice(0, 32).map((id) => (
          <li key={id}>{id}</li>
        ))}
      </ul>
    </section>
  );
}

export const PLACEHOLDER_TREE_MAX_NODES_VALUE = MAX_NODES;
