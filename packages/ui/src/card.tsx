import { forwardRef, type HTMLAttributes } from "react";

function cx(...values: Array<string | false | null | undefined>): string {
  return values.filter(Boolean).join(" ");
}

export interface CardProps extends HTMLAttributes<HTMLElement> {
  as?: "section" | "article" | "div";
  /** Render a subtle elevation. Disabled when the card is used inside a list. */
  raised?: boolean;
}

const baseClasses =
  "rounded-lg border border-surface-sunken bg-surface-raised text-surface-foreground shadow-sm";

/**
 * Card primitive — semantic, accessible container for grouped
 * content. The default element is `<section>`; callers can opt
 * into `<article>` for self-contained pieces of content.
 */
export const Card = forwardRef<HTMLElement, CardProps>(function Card(
  { as = "section", raised, className, children, ...rest },
  ref,
) {
  const Component = as;
  const classes = cx(baseClasses, !raised ? "shadow-none" : undefined, className);
  return (
    <Component ref={ref as never} className={classes} {...rest}>
      {children}
    </Component>
  );
});

export const CardHeader = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  function CardHeader({ className, ...rest }, ref) {
    return <div ref={ref} className={cx("flex flex-col gap-1 p-6 pb-3", className)} {...rest} />;
  },
);

export const CardTitle = forwardRef<HTMLHeadingElement, HTMLAttributes<HTMLHeadingElement>>(
  function CardTitle({ className, ...rest }, ref) {
    return (
      <h3
        ref={ref}
        className={cx("text-lg font-semibold leading-tight text-surface-foreground", className)}
        {...rest}
      />
    );
  },
);

export const CardDescription = forwardRef<
  HTMLParagraphElement,
  HTMLAttributes<HTMLParagraphElement>
>(function CardDescription({ className, ...rest }, ref) {
  return <p ref={ref} className={cx("text-sm text-surface-muted", className)} {...rest} />;
});

export const CardContent = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  function CardContent({ className, ...rest }, ref) {
    return <div ref={ref} className={cx("p-6 pt-3", className)} {...rest} />;
  },
);

export const CardFooter = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  function CardFooter({ className, ...rest }, ref) {
    return (
      <div
        ref={ref}
        className={cx(
          "flex items-center justify-end gap-3 border-t border-surface-sunken p-6",
          className,
        )}
        {...rest}
      />
    );
  },
);
