/**
 * apps/web/src/lib/a11y/use-prefers-reduced-motion.test.ts
 *
 * Static checks for the reduced-motion module. The hook itself
 * needs a DOM + `window.matchMedia` shim, which is heavy to wire
 * in the existing test runner (Node 22 `node --test`). The
 * constants + module shape checks here guarantee the API stays
 * stable; full DOM-driven coverage lands in E6 Playwright.
 */
import { strict as assert } from "node:assert";
import { test } from "node:test";

import { REDUCED_MOTION_QUERY, usePrefersReducedMotion } from "./use-prefers-reduced-motion";

test("REDUCED_MOTION_QUERY matches the WCAG media string", () => {
  assert.equal(REDUCED_MOTION_QUERY, "(prefers-reduced-motion: reduce)");
});

test("usePrefersReducedMotion is exported as a function", () => {
  assert.equal(typeof usePrefersReducedMotion, "function");
  assert.equal(usePrefersReducedMotion.length, 0);
});
