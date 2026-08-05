/*
 * Repository-wide ESLint v9 flat config.
 *
 * The `packages/eslint-config` workspace re-exports this file as
 * `@genealogy/eslint-config` so downstream packages can extend it
 * without re-importing the entire toolchain. Adding a new package? Run
 *   pnpm add -D @genealogy/eslint-config
 *   # and re-export `config` from your package.json `"eslintConfig"` field.
 *
 * Why flat config? ESLint 9 retired `.eslintrc*`; flat config aligns
 * with ADR-E0.5-01 (Node 22 LTS, ESLint 9.13) and lets us share the
 * rule matrix with downstream packages without symlinks.
 */
import js from "@eslint/js";
import tseslint from "typescript-eslint";
import prettierConfig from "eslint-config-prettier";

export default [
  // Ignore build artefacts and generated code. `scripts/check-generated-code.mjs`
  // enforces that no human edits land in these paths; the eslint disable here
  // exists purely for performance.
  {
    ignores: [
      "**/node_modules/**",
      "**/dist/**",
      "**/build/**",
      "**/.next/**",
      "**/coverage/**",
      "**/target/**",
      "**/.gradle/**",
      "**/.turbo/**",
      "**/generated/**",
      "**/*.{generated,gen}.{ts,js}",
    ],
  },
  // Base JS rules
  js.configs.recommended,
  // TypeScript strict rules (type-checked only within packages that
  // ship their own tsconfig.json so we do not require every script
  // under `scripts/` to belong to a project).
  ...tseslint.configs.recommended,
  // Node.js scripts (root-level `scripts/`, `tools/scripts/*`) need
  // the Node globals; the package-level configs override this when
  // they target the browser / Next.js.
  {
    files: [
      "scripts/**/*.{js,mjs,cjs,ts,mts,cts}",
      "tools/**/*.{js,mjs,cjs,ts,mts,cts}",
    ],
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: "module",
      globals: {
        process: "readonly",
        console: "readonly",
        Buffer: "readonly",
        __dirname: "readonly",
        __filename: "readonly",
        global: "readonly",
        URL: "readonly",
        setTimeout: "readonly",
        clearTimeout: "readonly",
        setInterval: "readonly",
        clearInterval: "readonly",
      },
    },
    rules: {
      // Repo-wide hygiene.
      "no-console": ["warn", { allow: ["warn", "error"] }],
      "no-process-exit": "error",
      "prefer-const": "error",
      "no-var": "error",
      "object-shorthand": "error",
      // TypeScript strictness beyond `recommended`.
      "@typescript-eslint/consistent-type-imports": [
        "error",
        { prefer: "type-imports", fixStyle: "separate-type-imports" },
      ],
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
      ],
      "@typescript-eslint/no-explicit-any": "error",
      // Honour AGENTS.md §5: do not commit secrets.
      "no-template-curly-in-string": "error",
    },
  },
  // YAML files are linted by `scripts/lint-yaml.mjs` (Node.js, no
// ESLint plugin needed). Editor integration is provided by the same
// script — VSCode users add:
//   "yaml.schemas": { "file:///path/to/.spectral.yaml": "*.yaml" }
// and rely on RedHat's YAML extension for inline hints. Skipping the
// plugin here keeps `eslint.config.mjs` declarative and avoids
// version-skew between `eslint-plugin-yml` and ESLint 9.
  // CLI scripts are intentionally allowed to call `process.exit` and
// `console.log/error` — they are the public interface of the linter.
  {
    files: [
      "scripts/**/*.{js,mjs,cjs,ts,mts,cts}",
      "tools/**/*.{js,mjs,cjs,ts,mts,cts}",
    ],
    rules: {
      "no-process-exit": "off",
      "no-console": "off",
    },
  },
  // Markdown is checked separately by markdownlint-cli2 to avoid duplicating
  // rules. eslint still ignores the file glob to keep startup fast.
  {
    ignores: ["**/*.md"],
  },
  // Prettier compatibility last — turns off conflicting stylistic rules.
  prettierConfig,
];