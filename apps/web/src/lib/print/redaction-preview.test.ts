/**
 * apps/web/src/lib/print/redaction-preview.test.ts
 *
 * Unit tests for the server-mirrored redaction summary helpers.
 * Pure functions only.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";

import {
  formatRedactionSummary,
  previewObligationDescription,
  safeRedactionCount,
  summariseObligations,
  type RedactionSummary,
} from "./redaction-preview";
import type { RedactionObligation } from "./export-job";

const obligations: RedactionObligation[] = [
  { reasonCode: "LIVING", droppedFieldCount: 2, appliedAt: "2026-08-11T12:00:00.000Z" },
  {
    reasonCode: "MISSING_CONSENT",
    droppedFieldCount: 1,
    appliedAt: "2026-08-11T12:00:00.000Z",
  },
];

const summary: RedactionSummary = {
  totalFieldsDropped: 3,
  obligations,
  emittedAt: "2026-08-11T12:00:00.000Z",
};

test("redaction-preview: formatRedactionSummary is stable", () => {
  assert.equal(
    formatRedactionSummary(summary),
    "redaction-summary: total=3 reasons=[LIVING,MISSING_CONSENT]",
  );
});

test("redaction-preview: summariseObligations aggregates by reason and keeps order", () => {
  const summary = summariseObligations([
    ...obligations,
    { reasonCode: "LIVING", droppedFieldCount: 1, appliedAt: "2026-08-11T12:00:00.000Z" },
  ]);
  assert.deepEqual(
    [...summary],
    [
      { reasonCode: "LIVING", count: 3 },
      { reasonCode: "MISSING_CONSENT", count: 1 },
    ],
  );
});

test("redaction-preview: previewObligationDescription falls back gracefully", () => {
  const translate = (key: string, params?: Readonly<Record<string, string | number>>) => {
    if (key === "print.redactionObligationReason.LIVING") {
      return `Living fields hidden (${params?.["count"] ?? 0})`;
    }
    return key;
  };
  assert.equal(
    previewObligationDescription(obligations[0]!, translate),
    "Living fields hidden (2)",
  );
  // Falls back to the generic key when no specialised copy exists.
  assert.equal(
    previewObligationDescription(obligations[1]!, translate),
    "print.redactionObligationGeneric",
  );
});

test("redaction-preview: safeRedactionCount rejects negative / non-finite", () => {
  assert.equal(safeRedactionCount(-2), 0);
  assert.equal(safeRedactionCount(Number.NaN), 0);
  assert.equal(safeRedactionCount(2.9), 2);
  assert.equal(safeRedactionCount(5), 5);
});
