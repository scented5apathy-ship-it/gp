/**
 * apps/web/src/lib/tree-view/keyboard-navigation.test.ts
 *
 * Unit tests for the E5.5 / R6.5 keyboard tree navigation helper.
 * No React / DOM required — the helper is a pure function over an
 * ordered id list.
 */
import { strict as assert } from "node:assert";
import { test } from "node:test";

import {
  handleKeyboardTreeEvent,
  isTreeKey,
  resolveKeyboardTreeAction,
  TREE_KEYS,
} from "./keyboard-navigation";

const IDS = ["p1", "p2", "p3", "p4", "p5"] as const;

test("TREE_KEYS covers arrow + Home + End + Page + Enter/Space", () => {
  for (const key of [
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
  ]) {
    assert.ok(isTreeKey(key), `expected ${key} to be a tree key`);
  }
});

test("isTreeKey rejects unrelated keys", () => {
  assert.equal(isTreeKey("Tab"), false);
  assert.equal(isTreeKey("a"), false);
  assert.equal(isTreeKey("Escape"), false);
});

test("ArrowDown moves to the next id", () => {
  const action = resolveKeyboardTreeAction("ArrowDown", { ids: IDS, currentIndex: 0, pageSize: 2 });
  assert.deepEqual(action, { kind: "select", index: 1 });
});

test("ArrowUp moves to the previous id", () => {
  const action = resolveKeyboardTreeAction("ArrowUp", { ids: IDS, currentIndex: 2, pageSize: 2 });
  assert.deepEqual(action, { kind: "select", index: 1 });
});

test("ArrowDown wraps from last to first", () => {
  const action = resolveKeyboardTreeAction("ArrowDown", {
    ids: IDS,
    currentIndex: IDS.length - 1,
    pageSize: 2,
  });
  assert.deepEqual(action, { kind: "select", index: 0 });
});

test("ArrowUp wraps from first to last", () => {
  const action = resolveKeyboardTreeAction("ArrowUp", { ids: IDS, currentIndex: 0, pageSize: 2 });
  assert.deepEqual(action, { kind: "select", index: IDS.length - 1 });
});

test("Home jumps to index 0", () => {
  const action = resolveKeyboardTreeAction("Home", { ids: IDS, currentIndex: 3, pageSize: 2 });
  assert.deepEqual(action, { kind: "select", index: 0 });
});

test("End jumps to last index", () => {
  const action = resolveKeyboardTreeAction("End", { ids: IDS, currentIndex: 1, pageSize: 2 });
  assert.deepEqual(action, { kind: "select", index: IDS.length - 1 });
});

test("PageDown advances by pageSize", () => {
  const action = resolveKeyboardTreeAction("PageDown", { ids: IDS, currentIndex: 0, pageSize: 2 });
  assert.deepEqual(action, { kind: "select", index: 2 });
});

test("PageDown clamps to last index", () => {
  const action = resolveKeyboardTreeAction("PageDown", { ids: IDS, currentIndex: 3, pageSize: 5 });
  assert.deepEqual(action, { kind: "select", index: IDS.length - 1 });
});

test("PageUp retreats by pageSize", () => {
  const action = resolveKeyboardTreeAction("PageUp", { ids: IDS, currentIndex: 4, pageSize: 2 });
  assert.deepEqual(action, { kind: "select", index: 2 });
});

test("PageUp clamps to index 0", () => {
  const action = resolveKeyboardTreeAction("PageUp", { ids: IDS, currentIndex: 1, pageSize: 5 });
  assert.deepEqual(action, { kind: "select", index: 0 });
});

test("Enter / Space toggle the current index", () => {
  const enter = resolveKeyboardTreeAction("Enter", { ids: IDS, currentIndex: 2, pageSize: 2 });
  const space = resolveKeyboardTreeAction("Space", { ids: IDS, currentIndex: 2, pageSize: 2 });
  assert.deepEqual(enter, { kind: "toggle", index: 2 });
  assert.deepEqual(space, { kind: "toggle", index: 2 });
});

test("empty list returns noop", () => {
  for (const key of TREE_KEYS) {
    const action = resolveKeyboardTreeAction(key, { ids: [], currentIndex: 0, pageSize: 2 });
    assert.deepEqual(action, { kind: "noop" });
  }
});

test("handleKeyboardTreeEvent calls preventDefault on actionable keys", () => {
  let prevented = 0;
  const event = {
    key: "ArrowDown",
    preventDefault: () => {
      prevented += 1;
    },
  };
  handleKeyboardTreeEvent(event, { ids: IDS, currentIndex: 0, pageSize: 2 });
  assert.equal(prevented, 1);
});

test("handleKeyboardTreeEvent ignores non-tree keys without preventDefault", () => {
  let prevented = 0;
  const event = {
    key: "Tab",
    preventDefault: () => {
      prevented += 1;
    },
  };
  const action = handleKeyboardTreeEvent(event, { ids: IDS, currentIndex: 0, pageSize: 2 });
  assert.deepEqual(action, { kind: "noop" });
  assert.equal(prevented, 0);
});

test("negative currentIndex defaults to 0 for arrow navigation", () => {
  const action = resolveKeyboardTreeAction("ArrowDown", {
    ids: IDS,
    currentIndex: -1,
    pageSize: 2,
  });
  assert.deepEqual(action, { kind: "select", index: 1 });
});
