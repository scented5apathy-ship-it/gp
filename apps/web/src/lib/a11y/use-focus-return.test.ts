/**
 * apps/web/src/lib/a11y/use-focus-return.test.ts
 *
 * Static API checks for the focus-return hook. The full DOM
 * round-trip is covered in E6 Playwright (`axe-core` + manual
 * keyboard flow).
 */
import { strict as assert } from "node:assert";
import { test } from "node:test";

import { useFocusReturn } from "./use-focus-return";

test("useFocusReturn is a zero-arg hook", () => {
  assert.equal(typeof useFocusReturn, "function");
  assert.equal(useFocusReturn.length, 0);
});
