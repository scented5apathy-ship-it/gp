/**
 * Tiny Node.js loader hook that resolves TypeScript relative
 * imports without an explicit `.ts` extension. The PWA shell
 * tests follow the same convention as the rest of the repo
 * (extensionless relative imports); without this hook Node's
 * ESM resolver throws `ERR_MODULE_NOT_FOUND` because the file
 * is actually `foo.ts` on disk.
 *
 * The hook only handles `.ts` and `.tsx` extensions for relative
 * specifiers. Absolute and bare specifiers are passed through
 * unchanged so package resolution stays the responsibility of
 * `pnpm` / the lockfile.
 */
import { existsSync, statSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import { register } from "node:module";

// Self-register on import so `--import` to this file applies
// the resolver hooks without the caller needing to call
// `register()` themselves. This is the same pattern used by
// `tsx` and `ts-node` ESM loaders.
register(import.meta.url, import.meta.url);

const TS_EXTENSIONS = new Set([".ts", ".tsx", ".mts", ".cts"]);

function resolveTs(specifier, parentURL) {
  if (!parentURL) return undefined;
  if (
    !(specifier.startsWith("./") || specifier.startsWith("../")) ||
    /\.[mc]?[jt]sx?$/.test(specifier)
  ) {
    return undefined;
  }
  const baseUrl = new URL(specifier, parentURL);
  const candidatePath = fileURLToPath(baseUrl);
  for (const ext of TS_EXTENSIONS) {
    const withExt = `${candidatePath}${ext}`;
    if (existsSync(withExt) && statSync(withExt).isFile()) {
      return pathToFileURL(withExt).href;
    }
  }
  return undefined;
}

function runResolve(specifier, context, nextResolve) {
  if (process.env.TS_LOADER_DEBUG) {
    console.error("[ts-loader]", specifier, "<-", context.parentURL);
  }
  const resolved = resolveTs(specifier, context.parentURL);
  if (resolved) {
    if (process.env.TS_LOADER_DEBUG) console.error("[ts-loader] ->", resolved);
    return {
      url: resolved,
      shortCircuit: true,
    };
  }
  return nextResolve(specifier, context);
}

export function resolve(specifier, context, nextResolve) {
  return runResolve(specifier, context, nextResolve);
}

export function resolveSync(specifier, context, nextResolveSync) {
  const resolved = resolveTs(specifier, context.parentURL);
  if (resolved) {
    return {
      url: resolved,
      shortCircuit: true,
    };
  }
  return nextResolveSync(specifier, context);
}