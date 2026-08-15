/**
 * apps/web/src/lib/a11y/keyboard-tree.ts
 *
 * E12.4 — Keyboard navigation for the canonical a11y flows.
 *
 * Mirrors `apps/web/src/lib/tree-view/keyboard-navigation.ts`
 * but adds the profile-edit, timeline and consent flows so the
 * axe-core CI gate has a single helper to validate.
 */

export type KeyboardKey =
  | "Tab"
  | "Shift+Tab"
  | "Enter"
  | "Escape"
  | "Space"
  | "ArrowDown"
  | "ArrowUp"
  | "ArrowLeft"
  | "ArrowRight"
  | "Home"
  | "End"
  | "PageUp"
  | "PageDown";

export const REQUIRED_KEYS: ReadonlyArray<KeyboardKey> = [
  "Tab",
  "Shift+Tab",
  "Enter",
  "Escape",
  "Space",
  "ArrowDown",
  "ArrowUp",
  "ArrowLeft",
  "ArrowRight",
  "Home",
  "End",
  "PageUp",
  "PageDown",
];

export function isKeyboardKey(value: string): value is KeyboardKey {
  return (REQUIRED_KEYS as ReadonlyArray<string>).includes(value);
}

export interface KeyboardNavigationPlan {
  readonly currentIndex: number;
  readonly nextIndex: number;
  readonly action: "select" | "toggle" | "noop";
}

export function planNavigation(key: KeyboardKey, currentIndex: number, total: number, pageSize = 5): KeyboardNavigationPlan {
  if (total === 0) return { currentIndex, nextIndex: currentIndex, action: "noop" };
  const i = currentIndex >= 0 ? currentIndex : 0;
  switch (key) {
    case "ArrowDown":
    case "ArrowRight":
      return { currentIndex, nextIndex: (i + 1) % total, action: "select" };
    case "ArrowUp":
    case "ArrowLeft":
      return { currentIndex, nextIndex: (i - 1 + total) % total, action: "select" };
    case "Home":
      return { currentIndex, nextIndex: 0, action: "select" };
    case "End":
      return { currentIndex, nextIndex: total - 1, action: "select" };
    case "PageDown":
      return { currentIndex, nextIndex: Math.min(total - 1, i + Math.max(1, pageSize)), action: "select" };
    case "PageUp":
      return { currentIndex, nextIndex: Math.max(0, i - Math.max(1, pageSize)), action: "select" };
    case "Enter":
    case "Space":
      return { currentIndex, nextIndex: i, action: "toggle" };
    case "Tab":
    case "Shift+Tab":
    case "Escape":
    default:
      return { currentIndex, nextIndex: currentIndex, action: "noop" };
  }
}