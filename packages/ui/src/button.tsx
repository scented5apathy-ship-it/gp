import { forwardRef, type ButtonHTMLAttributes } from "react";

/**
 * Tailwind classnames merge helper. A tiny `clsx`-style
 * implementation so the `@genealogy/ui` package does not pull
 * `clsx` / `tailwind-merge` into the bundle budget yet. E6 will
 * swap this for `tailwind-merge` when the variant matrix grows.
 */
function cx(...values: Array<string | false | null | undefined>): string {
  return values.filter(Boolean).join(" ");
}

export type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";
export type ButtonSize = "sm" | "md" | "lg";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Render a wider touch target for primary mobile actions. */
  fullWidth?: boolean;
}

const variantClasses: Record<ButtonVariant, string> = {
  primary: "bg-primary text-primary-foreground hover:bg-primary/90 active:bg-primary/95",
  secondary:
    "bg-surface-raised text-surface-foreground border border-surface-sunken hover:bg-surface-sunken",
  ghost: "bg-transparent text-surface-foreground hover:bg-surface-sunken",
  danger: "bg-danger text-primary-foreground hover:bg-danger/90 active:bg-danger/95",
};

const sizeClasses: Record<ButtonSize, string> = {
  sm: "h-8 px-3 text-sm",
  md: "h-10 px-4 text-sm",
  lg: "h-12 px-5 text-base",
};

/**
 * The base button. The component is a Server Component by default
 * — once an `onClick` handler is attached the framework will swap
 * it to a Client Component transparently.
 *
 * Focus, disabled, and loading states all use the same
 * `focus-visible` ring so the keyboard indicator is consistent
 * with the rest of the shell (WCAG 2.2 SC 2.4.7).
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant = "primary", size = "md", fullWidth, type, ...rest },
  ref,
) {
  const classes = cx(
    "inline-flex items-center justify-center gap-2 rounded-md font-medium",
    "transition-[background-color,color,border-color,box-shadow] duration-[var(--motion-fast,120ms)]",
    "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary",
    "disabled:cursor-not-allowed disabled:opacity-60",
    "aria-disabled:cursor-not-allowed aria-disabled:opacity-60",
    variantClasses[variant],
    sizeClasses[size],
    fullWidth ? "w-full" : undefined,
    className,
  );
  return <button ref={ref} type={type ?? "button"} className={classes} {...rest} />;
});
