/*
 * Tailwind design-token pipeline.
 *
 * The tokens live in `src/styles/tokens.css` so the values are
 * readable from both the Tailwind config (for utility generation)
 * and the document body (for CSS variables). Adding a new semantic
 * token? Edit both files in lockstep.
 *
 * Colour choices follow WCAG 2.2 AA contrast ratios against the
 * neutral surfaces (`--color-surface`, `--color-on-surface`).
 * The platform colours (`primary`, `accent`) target ≥ 4.5:1 against
 * `--color-surface`.
 */
import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/app/**/*.{ts,tsx}",
    "./src/components/**/*.{ts,tsx}",
    "./src/lib/**/*.{ts,tsx}",
    "./src/i18n/**/*.{ts,tsx}",
  ],
  darkMode: ["class", '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: "rgb(var(--color-primary) / <alpha-value>)",
          foreground: "rgb(var(--color-on-primary) / <alpha-value>)",
        },
        accent: {
          DEFAULT: "rgb(var(--color-accent) / <alpha-value>)",
          foreground: "rgb(var(--color-on-accent) / <alpha-value>)",
        },
        surface: {
          DEFAULT: "rgb(var(--color-surface) / <alpha-value>)",
          raised: "rgb(var(--color-surface-raised) / <alpha-value>)",
          sunken: "rgb(var(--color-surface-sunken) / <alpha-value>)",
          foreground: "rgb(var(--color-on-surface) / <alpha-value>)",
          muted: "rgb(var(--color-on-surface-muted) / <alpha-value>)",
        },
        danger: "rgb(var(--color-danger) / <alpha-value>)",
        success: "rgb(var(--color-success) / <alpha-value>)",
        warning: "rgb(var(--color-warning) / <alpha-value>)",
      },
      fontFamily: {
        sans: ["var(--font-sans)", "system-ui", "sans-serif"],
        serif: ["var(--font-serif)", "ui-serif", "Georgia", "serif"],
        mono: ["var(--font-mono)", "ui-monospace", "monospace"],
      },
      borderRadius: {
        sm: "var(--radius-sm)",
        DEFAULT: "var(--radius-md)",
        lg: "var(--radius-lg)",
        xl: "var(--radius-xl)",
      },
      spacing: {
        focus: "var(--focus-ring-width, 2px)",
      },
    },
  },
  plugins: [],
};

export default config;
