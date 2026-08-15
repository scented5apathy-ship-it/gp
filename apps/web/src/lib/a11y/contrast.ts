/**
 * apps/web/src/lib/a11y/contrast.ts
 *
 * E12.4 — Color contrast guard.
 *
 * The runtime computes the WCAG 2.2 contrast ratio for every
 * `contrastTokens.pairs` entry in
 * `contracts/pwa/accessibility-policy.yaml` and refuses any
 * pair that falls below the bound.
 */

export interface RgbColor {
  readonly r: number;
  readonly g: number;
  readonly b: number;
}

export function parseRgb(raw: string): RgbColor {
  const m = /rgb\((\d{1,3}),\s*(\d{1,3}),\s*(\d{1,3})\)/i.exec(raw);
  if (!m) {
    throw new Error(`invalid rgb literal: ${raw}`);
  }
  return { r: Number(m[1]), g: Number(m[2]), b: Number(m[3]) };
}

export function relativeLuminance(c: RgbColor): number {
  const channel = (value: number) => {
    const s = value / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b);
}

export function contrastRatio(fg: RgbColor, bg: RgbColor): number {
  const l1 = relativeLuminance(fg);
  const l2 = relativeLuminance(bg);
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);
  return (lighter + 0.05) / (darker + 0.05);
}

export interface ContrastCheckResult {
  readonly ratio: number;
  readonly pass: boolean;
  readonly required: number;
}

export function checkContrast(fg: RgbColor, bg: RgbColor, required: number): ContrastCheckResult {
  const ratio = contrastRatio(fg, bg);
  return { ratio, pass: ratio >= required, required };
}