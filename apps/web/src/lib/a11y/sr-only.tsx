/**
 * apps/web/src/lib/a11y/sr-only.tsx
 *
 * Visually hide content while keeping it available to assistive
 * technology. The CSS class is consumed both by the live-region
 * announcer and by any field that needs an explicit text label
 * (e.g. a magnifier-only icon button).
 *
 * Implementation note: we set every relevant CSS property because
 * Tailwind's default `sr-only` is great but a few users still ship
 * a stripped build with that plugin disabled.
 */
import type { PropsWithChildren } from "react";

export interface SrOnlyProps {
  readonly as?: keyof JSX.IntrinsicElements;
  readonly className?: string;
}

const STYLE: Readonly<Record<string, string>> = {
  position: "absolute",
  width: "1px",
  height: "1px",
  padding: "0",
  margin: "-1px",
  overflow: "hidden",
  clip: "rect(0, 0, 0, 0)",
  whiteSpace: "nowrap",
  borderWidth: "0",
};

export function SrOnly({
  children,
  as = "span",
  className,
}: PropsWithChildren<SrOnlyProps>): JSX.Element {
  const Tag = as as "span";
  return (
    <Tag className={className} style={STYLE}>
      {children}
    </Tag>
  );
}
