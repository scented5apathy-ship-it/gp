/*
 * PostCSS pipeline for the PWA shell.
 *
 * Tailwind's CLI + Autoprefixer are enough for the shell — we do not
 * pull in PostCSS Preset Env because the Next.js runtime already
 * compiles modern JS and CSS. Autoprefixer covers the few legacy
 * browsers we still support (Safari 16+, Chrome 120+, Edge 120+).
 */
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
