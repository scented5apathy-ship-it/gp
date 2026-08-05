#!/usr/bin/env node
/**
 * apps/web/scripts/codegen-openapi-client.mjs
 *
 * Generates a typed REST surface for the Next.js PWA shell from
 * the OpenAPI contracts in `contracts/openapi/`. The generator:
 *
 *   1. Discovers every `*.yaml` / `*.yml` / `*.json` file under
 *      `contracts/openapi/{bff,public-api,common}`.
 *   2. Pipes each contract through `openapi-typescript` (the same
 *      generator the platform build uses in CI). The output is a
 *      `paths` / `components` module per contract plus a barrel
 *      `index.ts` that re-exports the union.
 *   3. Writes the output to either `apps/web/src/lib/api/generated`
 *      (the default) or `--out <dir>` if the caller passes the
 *      flag. The generated directory is `.gitignore`d because the
 *      artefacts are reproducible from the contracts.
 *   4. Emits a `@generated` marker on the first line of every
 *      file so the E1.2 generated-code policy (`check-generated-code.mjs`)
 *      catches accidental manual edits.
 *
 * Exit code:
 *   0 - clean
 *   1 - validation failed (contract not found, generator error)
 *   2 - configuration error
 */
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, "..", "..", "..");
const CONTRACTS = join(ROOT, "contracts", "openapi");

const FALLBACK_ROOT = resolve(HERE, "..", "..");
const FALLBACK_CONTRACTS = join(FALLBACK_ROOT, "contracts", "openapi");

function parseArgs(argv) {
  const args = { out: join(HERE, "..", "src", "lib", "api", "generated") };
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (token === "--out") {
      const next = argv[i + 1];
      if (!next) {
        console.error("[codegen] --out requires a path argument");
        process.exit(2);
      }
      args.out = resolve(ROOT, next);
      i += 1;
    } else if (token === "--root") {
      const next = argv[i + 1];
      if (!next) {
        console.error("[codegen] --root requires a path argument");
        process.exit(2);
      }
      args.root = resolve(ROOT, next);
      i += 1;
    } else {
      console.error(`[codegen] unknown argument: ${token}`);
      process.exit(2);
    }
  }
  if (!args.root) {
    args.root = ROOT;
  }
  return args;
}

function findSpecs(dir) {
  const out = [];
  if (!existsSync(dir)) return out;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...findSpecs(full));
    } else if (/\.(ya?ml|json)$/.test(entry.name)) {
      out.push(full);
    }
  }
  return out;
}

function contractSlug(specPath) {
  const rel = relative(CONTRACTS, specPath).replace(/\\/g, "/");
  return rel.replace(/[\\/]/g, "__").replace(/\.(ya?ml|json)$/, "");
}

function runOpenapiTypescript(spec, outFile) {
  // `openapi-typescript` ships a bin entry; we invoke the bundled
  // module via the local node_modules install to honour the lockfile.
  const bin = join(ROOT, "node_modules", "openapi-typescript", "bin", "cli.js");
  if (!existsSync(bin)) {
    console.error(`[codegen] openapi-typescript bin not found at ${bin}`);
    process.exit(2);
  }
  const proc = spawnSync("node", [bin, spec, "--output", outFile, "--enum", "--immutable"], {
    stdio: "inherit",
    cwd: ROOT,
  });
  return proc.status === 0;
}

function writeMarker(filePath) {
  // Prepend the `@generated` marker. `openapi-typescript` already
  // emits the marker; the explicit rewrite keeps the contract
  // honest even when the upstream generator's output changes.
  const marker = "// @generated\n//\n// Source: contracts/openapi — DO NOT EDIT.\n";
  const content = readFileSync(filePath, "utf8");
  if (content.startsWith("// @generated")) {
    return;
  }
  writeFileSync(filePath, marker + content);
}

function generate() {
  if (!existsSync(CONTRACTS)) {
    console.error(`[codegen] contracts directory not found at ${CONTRACTS}`);
    process.exit(2);
  }
  const specs = findSpecs(CONTRACTS);
  if (specs.length === 0) {
    console.error("[codegen] no OpenAPI contracts found");
    process.exit(2);
  }

  mkdirSync(args.out, { recursive: true });

  const generated = [];
  let failed = 0;
  for (const spec of specs) {
    const slug = contractSlug(spec);
    const outFile = join(args.out, `${slug}.ts`);
    const rel = relative(ROOT, spec);
    console.log(`[codegen] ${rel} → ${relative(ROOT, outFile)}`);
    const ok = runOpenapiTypescript(spec, outFile);
    if (!ok) {
      failed += 1;
      continue;
    }
    writeMarker(outFile);
    generated.push({ slug, path: outFile });
  }

  // Barrel — re-exports every generated module under a flat
  // namespace. Consumers can import `import { components } from
  // "@genealogy/api-client/generated"`.
  const barrel = [
    "// @generated",
    "// Source: contracts/openapi — DO NOT EDIT.",
    "",
    ...generated.map(
      ({ slug }) => `export * as ${toIdentifier(slug)} from "./${slug}";`,
    ),
    "",
  ].join("\n");
  writeFileSync(join(args.out, "index.ts"), barrel);

  if (failed > 0) {
    console.error(`[codegen] ${failed} contract(s) failed to generate`);
    process.exit(1);
  }
  console.log(`[codegen] wrote ${generated.length} module(s) + barrel to ${relative(ROOT, args.out)}`);
}

function toIdentifier(slug) {
  return slug
    .replace(/[^a-zA-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .replace(/^([0-9])/, "_$1");
}

const args = parseArgs(process.argv.slice(2));
generate();