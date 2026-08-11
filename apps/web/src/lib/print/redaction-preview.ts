/**
 * apps/web/src/lib/print/redaction-preview.ts
 *
 * Server-issued redaction summary mirror (R15 / glossary §2.2).
 *
 * The Genealogy platform enforces redaction on the server. The UI
 * is allowed to *display* what the server dropped so the user
 * understands the preview before clicking download — but it MUST
 * NOT re-redact, must NOT infer obligations, and must NOT widen
 * the surface area.
 *
 * This module exposes the pure data shape the BFF returns in the
 * export response (`RedactionSummary`) and the helpers that build
 * the preview blocks:
 *
 *   - `formatRedactionSummary(summary)` — deterministic, locale-
 *     agnostic string used by the UI live region (no PII; only
 *     counts + reason codes).
 *   - `previewObligationDescription(obligation, translate)` —
 *     resolves a `print.redactionObligationReason.*` translation
 *     so the audit panel stays inside the i18n catalogue.
 *   - `summariseObligations(obligations)` — returns a stable map
 *     of reason → count, in insertion order, ready for a screen-
 *     reader description.
 */
import type { RedactionObligation } from "./export-job";

export interface RedactionSummary {
  readonly totalFieldsDropped: number;
  readonly obligations: ReadonlyArray<RedactionObligation>;
  readonly emittedAt: string;
}

/**
 * Format the redaction summary as a single string suitable for the
 * `a11y` live region. The format is intentionally stable so the
 * linter and the manual-audit script can grep for it.
 */
export function formatRedactionSummary(summary: RedactionSummary): string {
  const reasons = summary.obligations.map((o) => o.reasonCode).join(",");
  return `redaction-summary: total=${summary.totalFieldsDropped} reasons=[${reasons}]`;
}

/**
 * Render an obligation block for the preview UI. The translate
 * callback is injected so this helper stays pure and unit-testable
 * (no `i18n` module dependency).
 */
export function previewObligationDescription(
  obligation: RedactionObligation,
  translate: (key: string, params?: Readonly<Record<string, string | number>>) => string,
): string {
  const reasonKey = `print.redactionObligationReason.${obligation.reasonCode}`;
  const reasonLabel = translate(reasonKey, { count: obligation.droppedFieldCount });
  if (reasonLabel === reasonKey) {
    // Fallback when the catalogue has no specialised copy for this
    // reason code — keeps the UI out of the i18n contract.
    return translate("print.redactionObligationGeneric", {
      count: obligation.droppedFieldCount,
      reasonCode: obligation.reasonCode,
    });
  }
  return reasonLabel;
}

/**
 * Reduce an obligations list to a stable `{reason → count}` map,
 * preserving the order in which the server emitted the obligations
 * (first-seen). Stable order matters because the live region
 * announcement reads left-to-right.
 */
export function summariseObligations(
  obligations: ReadonlyArray<RedactionObligation>,
): ReadonlyArray<{ readonly reasonCode: string; readonly count: number }> {
  const seen = new Map<string, number>();
  const ordered: string[] = [];
  for (const o of obligations) {
    if (!seen.has(o.reasonCode)) {
      seen.set(o.reasonCode, 0);
      ordered.push(o.reasonCode);
    }
    seen.set(o.reasonCode, (seen.get(o.reasonCode) ?? 0) + o.droppedFieldCount);
  }
  return ordered.map((reasonCode) => ({
    reasonCode,
    count: seen.get(reasonCode) ?? 0,
  }));
}

/**
 * A safe redaction counter that refuses negative inputs. The BFF
 * always emits non-negative integers; this guard exists for unit
 * tests and for any future BFF that may emit a placeholder
 * (`-1`) during cache misses.
 */
export function safeRedactionCount(value: number): number {
  if (!Number.isFinite(value) || value < 0) return 0;
  return Math.floor(value);
}
