/**
 * `@genealogy/eslint-config` — public type surface.
 *
 * The actual ESLint flat config lives at `eslint.config.mjs` (repo
 * root) because ESLint 9 only consumes `eslint.config.js` /
 * `eslint.config.mjs` / `eslint.config.cjs`. Downstream packages
 * should reference the root file directly:
 *
 *     // eslint.config.mjs in the consumer package
 *     import base from "../../eslint.config.mjs";
 *     export default [...base, { rules: { "no-console": "off" } }];
 *
 * This TypeScript file exists so `pnpm -r typecheck` resolves the
 * `@genealogy/eslint-config` package and to provide a typed entry
 * point for consumers that prefer TS imports.
 */
export type FlatConfig = ReadonlyArray<unknown>;
export const configMarker: unique symbol = Symbol.for("@genealogy/eslint-config");
export default configMarker;
