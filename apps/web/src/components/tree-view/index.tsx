/**
 * apps/web/src/components/tree-view/index.tsx
 *
 * React shell for the E5.3 tree views. The component is a
 * **Client Component** (the only place in the PWA where we ship
 * JS for the tree); the state machine it drives is imported from
 * `@/lib/tree-view/store` and kept pure so the unit tests can
 * exercise every invariant without React.
 *
 * What this component does:
 *
 *   - Subscribes to a `TreeViewStore` and renders the current
 *     snapshot (nodes, edges, generations, redaction summary,
 *     pagination).
 *   - Provides pan / zoom controls (`+` / `-` / reset) — the
 *     `<canvas>` placeholder does the actual painting; E5.3 is
 *     scoped to the interaction layer per ADR-E0.5-10's deferral.
 *   - Provides collapse / expand branch toggles.
 *   - Provides a search-root text input that calls `load(query)`
 *     with the new `rootPersonId`.
 *   - Provides a minimap (smaller canvas overview of the
 *     generations present) and a breadcrumb chain from the tree
 *     id down to the selected person id.
 *   - Provides keyboard navigation (`Arrow` keys, `Home`, `End`,
 *     `Enter`) over a semantic list alternative (R6.5) so the
 *     tree is usable without a mouse.
 *
 * The component is intentionally **renderer-agnostic** — when
 * ADR-E0.5-10 closes, only `placeholder-canvas.tsx` (and the
 * layout strategy in `lib/tree-view/layout.ts`) need to be
 * swapped. The store, props, accessibility surface and fetch
 * wiring stay intact.
 */
"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type KeyboardEvent,
} from "react";

import type { Translator } from "@/i18n";
import {
  TREE_PROJECTION_VIEW_KINDS,
  type TreeProjectionDirection,
  type TreeProjectionViewKind,
} from "@genealogy/api-client";

import {
  defaultQuery,
  EMPTY_SNAPSHOT,
  TREE_VIEW_DEFAULT_DEPTH,
  TREE_VIEW_DEFAULT_MAX_NODES,
  TREE_VIEW_DEFAULT_DIRECTION,
  type TreeViewSnapshot,
  type TreeViewStore,
} from "@/lib/tree-view/store";
import { layoutSnapshot, SLOT_HEIGHT, SLOT_WIDTH } from "@/lib/tree-view/layout";

export interface TreeViewProps {
  readonly store: TreeViewStore;
  readonly translate: Translator;
  readonly locale: string;
}

const VIEW_KIND_LABELS: Readonly<Record<TreeProjectionViewKind, string>> = {
  pedigree: "tree.viewKindPedigree",
  descendant: "tree.viewKindDescendant",
  fan: "tree.viewKindFan",
  hourglass: "tree.viewKindHourglass",
  family: "tree.viewKindFamily",
};

export function TreeView({ store, translate, locale }: TreeViewProps): JSX.Element {
  const snapshot = useStoreSnapshot(store);
  const [rootDraft, setRootDraft] = useState<string>(snapshot.query.rootPersonId);

  useEffect(() => {
    setRootDraft(snapshot.query.rootPersonId);
  }, [snapshot.query.rootPersonId]);

  const handleSearchRoot = useCallback(
    (event: ChangeEvent<HTMLFormElement>) => {
      event.preventDefault();
      const trimmed = rootDraft.trim();
      if (!trimmed) return;
      const query = defaultQuery({
        treeId: snapshot.query.treeId,
        viewKind: snapshot.query.viewKind,
        rootPersonId: trimmed,
      });
      void store.load(query);
    },
    [rootDraft, snapshot.query.treeId, snapshot.query.viewKind, store],
  );

  const handlePanZoom = useCallback(
    (delta: { x?: number; y?: number; scale?: number }) => {
      const transform = {
        x: (snapshot.ui.transform.x ?? 0) + (delta.x ?? 0),
        y: (snapshot.ui.transform.y ?? 0) + (delta.y ?? 0),
        scale: Math.max(
          0.25,
          Math.min(2.5, (snapshot.ui.transform.scale ?? 1) + (delta.scale ?? 0)),
        ),
      };
      store.patchUi({ transform });
    },
    [snapshot.ui.transform, store],
  );

  const handleResetView = useCallback(() => {
    store.patchUi({ transform: { x: 0, y: 0, scale: 1 } });
  }, [store]);

  const handleCollapseToggle = useCallback(
    (branchId: string) => {
      store.toggleCollapse(branchId);
    },
    [store],
  );

  const handleDirectionChange = useCallback(
    (direction: TreeProjectionDirection) => {
      const query = {
        ...snapshot.query,
        direction,
        depth: snapshot.query.depth || TREE_VIEW_DEFAULT_DEPTH,
        maxNodes: snapshot.query.maxNodes || TREE_VIEW_DEFAULT_MAX_NODES,
      };
      void store.load(query);
    },
    [snapshot.query, store],
  );

  const handleDepthChange = useCallback(
    (depth: number) => {
      const query = { ...snapshot.query, depth };
      void store.load(query);
    },
    [snapshot.query, store],
  );

  const handleViewportFetch = useCallback(() => {
    void store.requestViewport({
      anchorPersonId: snapshot.query.rootPersonId,
      direction: TREE_VIEW_DEFAULT_DIRECTION,
      depth: snapshot.query.depth || TREE_VIEW_DEFAULT_DEPTH,
      maxNodes: snapshot.query.maxNodes || TREE_VIEW_DEFAULT_MAX_NODES,
      viewport: snapshot.ui.viewport,
    });
  }, [
    snapshot.query.depth,
    snapshot.query.maxNodes,
    snapshot.query.rootPersonId,
    snapshot.ui.viewport,
    store,
  ]);

  const handleSelectPerson = useCallback(
    (personId: string) => {
      store.patchUi({ selectedPersonId: personId });
    },
    [store],
  );

  const handleKeyDown = useCallback(
    (event: KeyboardEvent<HTMLOListElement>) => {
      const ids = Array.from(snapshot.nodesById.keys());
      if (ids.length === 0) return;
      const currentIndex = ids.indexOf(snapshot.ui.selectedPersonId ?? "");
      if (event.key === "ArrowDown" || event.key === "ArrowRight") {
        const next = ids[(currentIndex + 1 + ids.length) % ids.length];
        if (next) handleSelectPerson(next);
        event.preventDefault();
      } else if (event.key === "ArrowUp" || event.key === "ArrowLeft") {
        const prev = ids[(currentIndex - 1 + ids.length) % ids.length];
        if (prev) handleSelectPerson(prev);
        event.preventDefault();
      } else if (event.key === "Home") {
        const first = ids[0];
        if (first) handleSelectPerson(first);
        event.preventDefault();
      } else if (event.key === "End") {
        const last = ids[ids.length - 1];
        if (last) handleSelectPerson(last);
        event.preventDefault();
      } else if (event.key === "Enter" && snapshot.ui.selectedPersonId) {
        handleCollapseToggle(snapshot.ui.selectedPersonId);
        event.preventDefault();
      }
    },
    [handleCollapseToggle, handleSelectPerson, snapshot.nodesById, snapshot.ui.selectedPersonId],
  );

  const layout = useMemo(() => layoutSnapshot(snapshot), [snapshot]);
  const slotsByPersonId = useMemo(() => {
    const map = new Map<string, ReturnType<typeof layoutSnapshot>["slots"][number]>();
    for (const slot of layout.slots) map.set(slot.personId, slot);
    return map;
  }, [layout]);

  const breadcrumb = useMemo(() => {
    return [
      { id: snapshot.query.treeId, label: snapshot.query.treeId },
      { id: snapshot.query.viewKind, label: translate(VIEW_KIND_LABELS[snapshot.query.viewKind]) },
      { id: snapshot.query.rootPersonId, label: snapshot.query.rootPersonId },
      ...(snapshot.ui.selectedPersonId
        ? [{ id: snapshot.ui.selectedPersonId, label: snapshot.ui.selectedPersonId }]
        : []),
    ];
  }, [
    snapshot.query.rootPersonId,
    snapshot.query.treeId,
    snapshot.query.viewKind,
    snapshot.ui.selectedPersonId,
    translate,
  ]);

  return (
    <section
      aria-label={translate("tree.sectionLabel")}
      data-tree-view-kind={snapshot.query.viewKind}
      data-tree-status={snapshot.meta.status}
      className="tree-view flex flex-col gap-4"
    >
      <header className="tree-view__header flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold text-surface-foreground">
          {translate("tree.heading")}
        </h1>
        <nav aria-label={translate("tree.breadcrumbLabel")} className="text-sm text-surface-muted">
          <ol className="flex flex-wrap gap-1">
            {breadcrumb.map((crumb, index) => (
              <li key={`${crumb.id}-${index}`} className="flex items-center gap-1">
                {index > 0 ? <span aria-hidden="true">›</span> : null}
                <span>{crumb.label}</span>
              </li>
            ))}
          </ol>
        </nav>
      </header>

      <form className="tree-view__search flex flex-wrap gap-2" onSubmit={handleSearchRoot}>
        <label className="flex flex-col text-sm">
          <span className="text-surface-muted">{translate("tree.searchRootLabel")}</span>
          <input
            type="text"
            value={rootDraft}
            onChange={(event) => setRootDraft(event.target.value)}
            placeholder={translate("tree.searchRootPlaceholder")}
            className="rounded border border-surface-sunken bg-surface-raised px-2 py-1"
            aria-describedby="tree-search-help"
          />
          <span id="tree-search-help" className="text-xs text-surface-muted">
            {translate("tree.searchRootHelp")}
          </span>
        </label>
        <button
          type="submit"
          className="rounded border border-surface-sunken bg-surface-raised px-3 py-2 text-sm"
        >
          {translate("tree.searchRootAction")}
        </button>
      </form>

      <div
        className="tree-view__controls flex flex-wrap gap-2"
        role="group"
        aria-label={translate("tree.controlsLabel")}
      >
        <fieldset className="flex flex-col gap-1 text-sm">
          <legend className="text-surface-muted">{translate("tree.directionLabel")}</legend>
          <select
            value={snapshot.query.direction}
            onChange={(event) =>
              handleDirectionChange(event.target.value as TreeProjectionDirection)
            }
            className="rounded border border-surface-sunken bg-surface-raised px-2 py-1"
          >
            {(["ANCESTORS", "DESCENDANTS", "BOTH", "SPOUSE_FAN"] as const).map((dir) => (
              <option key={dir} value={dir}>
                {translate(`tree.direction.${dir.toLowerCase()}` as const)}
              </option>
            ))}
          </select>
        </fieldset>
        <fieldset className="flex flex-col gap-1 text-sm">
          <legend className="text-surface-muted">{translate("tree.depthLabel")}</legend>
          <input
            type="number"
            min={1}
            max={12}
            value={snapshot.query.depth}
            onChange={(event) => {
              const next = Number.parseInt(event.target.value, 10);
              if (Number.isInteger(next) && next >= 1 && next <= 12) handleDepthChange(next);
            }}
            className="w-20 rounded border border-surface-sunken bg-surface-raised px-2 py-1"
          />
        </fieldset>
        <div className="flex items-end gap-1">
          <button
            type="button"
            onClick={() => handlePanZoom({ scale: -0.1 })}
            aria-label={translate("tree.zoomOut")}
            className="rounded border border-surface-sunken bg-surface-raised px-2 py-1 text-sm"
          >
            −
          </button>
          <button
            type="button"
            onClick={() => handlePanZoom({ scale: 0.1 })}
            aria-label={translate("tree.zoomIn")}
            className="rounded border border-surface-sunken bg-surface-raised px-2 py-1 text-sm"
          >
            +
          </button>
          <button
            type="button"
            onClick={handleResetView}
            aria-label={translate("tree.resetView")}
            className="rounded border border-surface-sunken bg-surface-raised px-2 py-1 text-sm"
          >
            {translate("tree.resetView")}
          </button>
        </div>
        <button
          type="button"
          onClick={handleViewportFetch}
          aria-label={translate("tree.viewportFetch")}
          className="rounded border border-surface-sunken bg-surface-raised px-2 py-1 text-sm"
        >
          {translate("tree.viewportFetch")}
        </button>
      </div>

      <div className="tree-view__status text-sm text-surface-muted" role="status">
        {translate(snapshotStatusKey(snapshot))}
        {snapshot.redaction.droppedFieldCount > 0
          ? ` · ${translate("tree.redactionSummary", {
              dropped: snapshot.redaction.droppedFieldCount,
              reasons: snapshot.redaction.reasonCodes.join(", "),
            })}`
          : null}
      </div>

      <div className="tree-view__layout grid grid-cols-1 gap-4 lg:grid-cols-[3fr_1fr]">
        <TreeViewCanvas
          layout={layout}
          snapshot={snapshot}
          translate={translate}
          onCollapse={handleCollapseToggle}
          onSelect={handleSelectPerson}
          transform={snapshot.ui.transform}
        />
        <TreeViewSidebar
          snapshot={snapshot}
          slotsByPersonId={slotsByPersonId}
          translate={translate}
          onKeyDown={handleKeyDown}
          onSelect={handleSelectPerson}
        />
      </div>

      <p className="text-xs text-surface-muted">{translate("tree.viewKindFootnote", { locale })}</p>
    </section>
  );
}

interface TreeViewCanvasProps {
  readonly layout: ReturnType<typeof layoutSnapshot>;
  readonly snapshot: TreeViewSnapshot;
  readonly translate: Translator;
  readonly onCollapse: (personId: string) => void;
  readonly onSelect: (personId: string) => void;
  readonly transform: { readonly x: number; readonly y: number; readonly scale: number };
}

function TreeViewCanvas({
  layout,
  snapshot,
  translate,
  onCollapse,
  onSelect,
  transform,
}: TreeViewCanvasProps): JSX.Element {
  const ref = useRef<HTMLCanvasElement | null>(null);
  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    const dpr = typeof window !== "undefined" ? window.devicePixelRatio || 1 : 1;
    const width = canvas.clientWidth || 480;
    const height = canvas.clientHeight || 320;
    canvas.width = width * dpr;
    canvas.height = height * dpr;
    ctx.setTransform(
      dpr * transform.scale,
      0,
      0,
      dpr * transform.scale,
      transform.x * dpr,
      transform.y * dpr,
    );
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = "#0f172a";
    ctx.fillRect(
      -transform.x / transform.scale,
      -transform.y / transform.scale,
      canvas.width / transform.scale,
      canvas.height / transform.scale,
    );
    ctx.strokeStyle = "rgba(148,163,184,0.4)";
    for (const edge of layout.edges) {
      ctx.beginPath();
      ctx.moveTo(edge.from.x, edge.from.y);
      ctx.lineTo(edge.to.x, edge.to.y);
      ctx.stroke();
    }
    for (const slot of layout.slots) {
      ctx.fillStyle = slot.collapsed ? "#475569" : "#1d4ed8";
      ctx.fillRect(slot.x, slot.y, slot.width, slot.height);
      ctx.fillStyle = "#f8fafc";
      ctx.font = "12px sans-serif";
      ctx.fillText(slot.personId, slot.x + 6, slot.y + 16);
      if (slot.personId === snapshot.ui.selectedPersonId) {
        ctx.strokeStyle = "#facc15";
        ctx.lineWidth = 2;
        ctx.strokeRect(slot.x - 2, slot.y - 2, slot.width + 4, slot.height + 4);
      }
    }
  }, [layout, snapshot.ui.selectedPersonId, transform]);

  return (
    <div
      className="tree-view__canvas-frame relative overflow-hidden rounded border border-surface-sunken bg-surface-raised"
      role="presentation"
    >
      <canvas
        ref={ref}
        role="img"
        aria-label={translate("tree.canvasLabel", {
          count: layout.slots.length,
          edges: layout.edges.length,
        })}
        className="h-80 w-full"
        style={{ width: "100%", height: "20rem" }}
      />
      <div className="pointer-events-none absolute inset-0">
        <button
          type="button"
          onClick={() => {
            const selected = snapshot.ui.selectedPersonId;
            if (selected) onCollapse(selected);
          }}
          className="pointer-events-auto absolute right-2 top-2 rounded border border-surface-sunken bg-surface-raised px-2 py-1 text-xs"
        >
          {translate("tree.toggleCollapse")}
        </button>
        <button
          type="button"
          onClick={() => {
            const selected = snapshot.ui.selectedPersonId;
            if (selected) onSelect(selected);
          }}
          className="pointer-events-auto absolute right-2 top-12 rounded border border-surface-sunken bg-surface-raised px-2 py-1 text-xs"
        >
          {translate("tree.focusSelected")}
        </button>
      </div>
    </div>
  );
}

interface TreeViewSidebarProps {
  readonly snapshot: TreeViewSnapshot;
  readonly slotsByPersonId: Map<string, ReturnType<typeof layoutSnapshot>["slots"][number]>;
  readonly translate: Translator;
  readonly onKeyDown: (event: KeyboardEvent<HTMLOListElement>) => void;
  readonly onSelect: (personId: string) => void;
}

function TreeViewSidebar({
  snapshot,
  slotsByPersonId,
  translate,
  onKeyDown,
  onSelect,
}: TreeViewSidebarProps): JSX.Element {
  const generations = [...snapshot.generations].sort((a, b) => a - b);
  const minimap = useMemo(() => {
    const total = Math.max(1, generations.length);
    const cellHeight = 6;
    return generations.map((generation) => ({
      generation,
      y: ((generation + 12) / 24) * total * cellHeight,
    }));
  }, [generations]);
  const visibleNodes = Array.from(snapshot.nodesById.values())
    .filter((node) => !snapshot.ui.collapsedBranchIds.has(node.personId))
    .sort((a, b) => a.generation - b.generation || a.personId.localeCompare(b.personId));
  return (
    <aside className="tree-view__sidebar flex flex-col gap-4">
      <section
        aria-labelledby="tree-minimap"
        className="rounded border border-surface-sunken bg-surface-raised p-3"
      >
        <h2 id="tree-minimap" className="text-sm font-semibold">
          {translate("tree.minimapLabel")}
        </h2>
        <svg
          width="100%"
          height="120"
          viewBox="0 0 120 120"
          role="img"
          aria-label={translate("tree.minimapLabel")}
          className="tree-view__minimap"
        >
          {minimap.map(({ generation, y }) => (
            <rect
              key={generation}
              x={4}
              y={y}
              width={112}
              height={4}
              fill={generation === 0 ? "#facc15" : "#1d4ed8"}
            />
          ))}
        </svg>
        <p className="text-xs text-surface-muted">{translate("tree.minimapFootnote")}</p>
      </section>
      <section
        aria-labelledby="tree-list"
        className="rounded border border-surface-sunken bg-surface-raised p-3"
      >
        <h2 id="tree-list" className="text-sm font-semibold">
          {translate("tree.listLabel")}
        </h2>
        <p id="tree-list-help" className="text-xs text-surface-muted">
          {translate("tree.listHelp")}
        </p>
        <ol
          tabIndex={0}
          aria-describedby="tree-list-help"
          onKeyDown={onKeyDown}
          className="tree-view__list mt-2 max-h-72 overflow-auto focus:outline-none"
        >
          {visibleNodes.length === 0 ? (
            <li className="text-sm text-surface-muted">{translate("tree.empty")}</li>
          ) : (
            visibleNodes.map((node) => {
              const slot = slotsByPersonId.get(node.personId);
              const selected = snapshot.ui.selectedPersonId === node.personId;
              return (
                <li
                  key={node.personId}
                  className={selected ? "bg-primary/10" : undefined}
                  aria-selected={selected}
                >
                  <button
                    type="button"
                    onClick={() => onSelect(node.personId)}
                    className="flex w-full items-center justify-between gap-2 rounded px-2 py-1 text-left text-sm hover:bg-surface-sunken"
                  >
                    <span>
                      {node.redacted
                        ? translate("tree.redacted")
                        : node.displayName || node.personId}
                    </span>
                    <span className="text-xs text-surface-muted">
                      g{node.generation} · {node.livingStatus.toLowerCase()} ·{" "}
                      {slot ? `${slot.x},${slot.y}` : "—"}
                    </span>
                  </button>
                </li>
              );
            })
          )}
        </ol>
      </section>
      <section
        aria-labelledby="tree-view-kinds"
        className="rounded border border-surface-sunken bg-surface-raised p-3"
      >
        <h2 id="tree-view-kinds" className="text-sm font-semibold">
          {translate("tree.viewKindsLabel")}
        </h2>
        <ul className="mt-1 space-y-1 text-sm">
          {TREE_PROJECTION_VIEW_KINDS.map((kind) => (
            <li key={kind}>
              <a
                href={`?viewKind=${kind}`}
                aria-current={snapshot.query.viewKind === kind ? "true" : undefined}
                className="underline-offset-2 hover:underline"
              >
                {translate(VIEW_KIND_LABELS[kind])}
              </a>
            </li>
          ))}
        </ul>
      </section>
      <section className="text-xs text-surface-muted">
        <p>{translate("tree.slotsFootnote", { width: SLOT_WIDTH, height: SLOT_HEIGHT })}</p>
      </section>
    </aside>
  );
}

function snapshotStatusKey(snapshot: TreeViewSnapshot): string {
  switch (snapshot.meta.status) {
    case "idle":
      return "tree.statusIdle";
    case "loading":
      return "tree.statusLoading";
    case "ready":
      return "tree.statusReady";
    case "stale":
      return "tree.statusStale";
    case "error":
      return "tree.statusError";
  }
  return "tree.statusIdle";
}

function useStoreSnapshot(store: TreeViewStore): TreeViewSnapshot {
  const [snapshot, setSnapshot] = useState<TreeViewSnapshot>(() => store.getSnapshot());
  useEffect(() => {
    return store.subscribe((next) => setSnapshot(next));
  }, [store]);
  return snapshot;
}

export { EMPTY_SNAPSHOT };
