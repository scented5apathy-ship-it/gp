/**
 * apps/web/src/lib/tree-view/keyboard-navigation.ts
 *
 * Pure keyboard navigation logic for the E5.5 / R6.5 keyboard tree
 * alternative. Extracted from `<TreeView>` so it can be unit-tested
 * in isolation (no DOM, no React) and reused by:
 *
 *   - the `<TreeViewSidebar>` keyboard list,
 *   - any future "tree as table" semantic alternative (E6 R18),
 *   - mobile screen-reader hints that want the same logic.
 *
 * The contract intentionally mirrors ARIA grid navigation keys
 * (`Home`, `End`, `Arrow*`, `PageUp`, `PageDown`, `Enter`,
 * `Space`) so screen-readers that already teach those bindings
 * work without re-learning.
 *
 * Design choices:
 *
 *   - `nextIndex` is the only output. The caller is responsible
 *     for moving the highlight and announcing the new selection —
 *     this keeps the helper synchronous and easy to test.
 *   - Linear navigation wraps around so the user never gets
 *     "stuck" at either end. Linear + grid + search siblings are
 *     out of scope for this slice (R6.5 keeps it 1-D for now).
 *   - `Enter` toggles the *current* node's collapse state, which
 *     is what the existing `<TreeView>` does; that is preserved.
 */
export type TreeKey =
  | "ArrowDown"
  | "ArrowUp"
  | "ArrowLeft"
  | "ArrowRight"
  | "Home"
  | "End"
  | "PageUp"
  | "PageDown"
  | "Enter"
  | "Space";

export const TREE_KEYS: ReadonlyArray<TreeKey> = [
  "ArrowDown",
  "ArrowUp",
  "ArrowLeft",
  "ArrowRight",
  "Home",
  "End",
  "PageUp",
  "PageDown",
  "Enter",
  "Space",
];

export function isTreeKey(value: string): value is TreeKey {
  return (TREE_KEYS as ReadonlyArray<string>).includes(value);
}

export interface KeyboardTreeState {
  readonly ids: readonly string[];
  readonly currentIndex: number;
  readonly pageSize: number;
}

export type KeyboardTreeAction =
  | { readonly kind: "select"; readonly index: number }
  | { readonly kind: "toggle"; readonly index: number }
  | { readonly kind: "noop" };

export function resolveKeyboardTreeAction(
  key: TreeKey,
  state: KeyboardTreeState,
): KeyboardTreeAction {
  const total = state.ids.length;
  if (total === 0) return { kind: "noop" };
  const i = state.currentIndex >= 0 ? state.currentIndex : 0;
  switch (key) {
    case "ArrowDown":
    case "ArrowRight":
      return { kind: "select", index: (i + 1) % total };
    case "ArrowUp":
    case "ArrowLeft":
      return { kind: "select", index: (i - 1 + total) % total };
    case "Home":
      return { kind: "select", index: 0 };
    case "End":
      return { kind: "select", index: total - 1 };
    case "PageDown": {
      const step = Math.max(1, state.pageSize);
      return { kind: "select", index: Math.min(total - 1, i + step) };
    }
    case "PageUp": {
      const step = Math.max(1, state.pageSize);
      return { kind: "select", index: Math.max(0, i - step) };
    }
    case "Enter":
    case "Space":
      return { kind: "toggle", index: i };
    default:
      return { kind: "noop" };
  }
}

/**
 * Wrap the keydown handler so it returns the React event AND a
 * structured action. The handler will call `event.preventDefault`
 * when the action is anything but `noop` so the browser does not
 * scroll the page on arrow / space.
 */
export function handleKeyboardTreeEvent(
  event: { readonly key: string; readonly preventDefault: () => void },
  state: KeyboardTreeState,
): KeyboardTreeAction {
  if (!isTreeKey(event.key)) return { kind: "noop" };
  const action = resolveKeyboardTreeAction(event.key, state);
  if (action.kind !== "noop") event.preventDefault();
  return action;
}
