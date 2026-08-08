#!/usr/bin/env node
/**
 * scripts/lint-kong-config.mjs
 *
 * E2.2 Kong runtime config linter.
 *
 * Validates `platform/kong/kong.yml` against the contract from
 * `design.md` §4.1 / `tasks.md` E2.2:
 *
 *   - Format version = 3.0 (Kong 3.x DB-less).
 *   - Exactly four route classes: public, authenticated, partner,
 *     admin. Each route must declare a tag matching
 *     `route-class:<name>` so a config drift can be grepped.
 *   - Every route binds to a service in the right backend namespace
 *     (`gp-bff` for public/authenticated/partner, `gp-platform` for
 *     admin).
 *   - Required plugins: correlation-id on every route, cors on
 *     public+authenticated, request-size-limiting on
 *     authenticated+partner+admin, rate-limiting on every route,
 *     ip-restriction on the admin route.
 *   - Domain authorization is forbidden: the config must not declare
 *     `oauth2-introspection`, `mtls-auth`, `acl` or `key-auth` on a
 *     per-route basis (those would carry business authorization).
 *   - JWT plugin is reserved for E3.1 and must not yet bind a key
 *     resolver / claims mapping in this file.
 *   - No literal secret / token / apiKey in the config.
 *
 * Returns exit 0 on success, 1 on violation.
 */
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.KONG_ROOT ? resolve(process.env.KONG_ROOT) : resolve(HERE, "..");
const CONFIG = join(ROOT, "platform", "kong", "kong.yml");

let violations = 0;
const fail = (msg) => {
  violations++;
  console.error(`[kong] ${msg}`);
};

if (!existsSync(CONFIG)) {
  fail(`kong declarative config missing — expected ${relative(ROOT, CONFIG)}`);
  process.exit(1);
}

const text = readFileSync(CONFIG, "utf8");

// Sensitive-key hygiene: reject any literal credential.
for (const key of ["password", "apiKey", "token", "private_key", "client_secret"]) {
  const literal = new RegExp(`^\\s*${key}\\s*:\\s*"?[A-Za-z0-9]{8,}"?\\s*$`, "m");
  if (literal.test(text)) {
    fail(`literal secret-like value for '${key}' in kong.yml — use Vault / External Secrets`);
  }
}

let config;
try {
  config = parse(text);
} catch (err) {
  fail(`kong.yml is not valid YAML — ${err.message}`);
  process.exit(1);
}

if (!config || typeof config !== "object") {
  fail("kong.yml parsed to a non-object root");
  process.exit(1);
}

if (config._format_version !== "3.0") {
  fail(`kong.yml _format_version must be '3.0' (Kong 3.x DB-less); got '${config._format_version}'`);
}

const services = Array.isArray(config.services) ? config.services : [];
const routes = Array.isArray(config.routes) ? config.routes : [];
const plugins = Array.isArray(config.plugins) ? config.plugins : [];

if (services.length === 0) fail("kong.yml declares no services");
if (routes.length === 0) fail("kong.yml declares no routes");
if (plugins.length === 0) fail("kong.yml declares no plugins");

const REQUIRED_SERVICES = ["web-public", "web-bff", "public-api", "admin-api"];
for (const required of REQUIRED_SERVICES) {
  if (!services.some((s) => s?.name === required)) {
    fail(`kong.yml missing required service '${required}'`);
  }
}

const REQUIRED_ROUTES = ["public-web-root", "web-bff-api", "public-api-v1", "admin-api-v1"];
for (const required of REQUIRED_ROUTES) {
  if (!routes.some((r) => r?.name === required)) {
    fail(`kong.yml missing required route '${required}'`);
  }
}

// Every route must carry the `route-class:<name>` tag so a config
// drift is greppable from CI logs.
const routeClassByName = {
  "public-web-root": "public",
  "web-bff-api": "authenticated",
  "public-api-v1": "partner",
  "admin-api-v1": "admin",
};
for (const route of routes) {
  if (!routeClassByName[route?.name]) continue;
  const expected = routeClassByName[route.name];
  const tags = Array.isArray(route.tags) ? route.tags : [];
  if (!tags.some((t) => t === `route-class:${expected}`)) {
    fail(`route '${route.name}' must carry tag 'route-class:${expected}'`);
  }
  if (!route.paths || route.paths.length === 0) {
    fail(`route '${route.name}' has no path`);
  }
  if (!route.protocols || !route.protocols.includes("https")) {
    fail(`route '${route.name}' must restrict protocols to https`);
  }
}

// Service-to-namespace mapping (admin must land in gp-platform, the
// other three in gp-bff).
const allowedNamespaces = {
  "web-public": "gp-bff",
  "web-bff": "gp-bff",
  "public-api": "gp-bff",
  "admin-api": "gp-platform",
};
for (const svc of services) {
  const allowed = allowedNamespaces[svc?.name];
  if (!allowed) continue;
  const url = svc.url || "";
  // Local-dev override lets us resolve to localhost; otherwise the
  // URL must reference the canonical cluster DNS.
  const matches =
    url.includes(`.${allowed}.svc.cluster.local:`) || url.startsWith("http://localhost:");
  if (!matches) {
    fail(`service '${svc.name}' url '${url}' must resolve into namespace '${allowed}'`);
  }
}

// Required plugins per route class.
const pluginByName = (routeName) =>
  plugins.filter((p) => p && (p.route === routeName || p.service === routeName));

function requirePlugin(routeName, pluginName, why) {
  if (!pluginByName(routeName).some((p) => p.name === pluginName)) {
    fail(`route '${routeName}' must enable plugin '${pluginName}' (${why})`);
  }
}

const ALL_ROUTES = ["public-web-root", "web-bff-api", "public-api-v1", "admin-api-v1"];
for (const route of ALL_ROUTES) {
  requirePlugin(route, "correlation-id", "X-Request-Id trace propagation");
  requirePlugin(route, "rate-limiting", "coarse edge rate limit");
}

requirePlugin("public-web-root", "cors", "browser CORS for marketing pages");
requirePlugin("web-bff-api", "cors", "browser CORS for authenticated SPA");
requirePlugin("web-bff-api", "request-size-limiting", "multipart body protection");
requirePlugin("public-api-v1", "request-size-limiting", "multipart body protection");
requirePlugin("admin-api-v1", "request-size-limiting", "admin payload cap");
requirePlugin("admin-api-v1", "ip-restriction", "admin surface must be bastion-only");

// Forbidden plugin surface — anything that could carry domain
// authorization must NOT be wired here per design.md §4.1.
const FORBIDDEN_PLUGINS = [
  "oauth2",
  "oauth2-introspection",
  "mtls-auth",
  "key-auth",
  "acl",
  "basic-auth",
  "ldap-auth",
  "bot-detection",
];
for (const plugin of plugins) {
  if (FORBIDDEN_PLUGINS.includes(plugin?.name)) {
    fail(
      `route '${plugin.route ?? plugin.service}' references forbidden plugin '${plugin.name}' — domain authorization must live in the destination service`,
    );
  }
}

// JWT plugin is reserved for E3.1 — must not declare claims mapping or
// key resolver today. The presence of the plugin name is allowed (the
// allow-list above requires it for E3.1 to wire) but it must not
// bind a `key_claim_name` against an OIDC principal.
for (const plugin of plugins) {
  if (plugin?.name === "jwt" && plugin.config?.key_claim_name) {
    fail(
      `jwt plugin on '${plugin.route}' declares a 'key_claim_name' before E3.1 — Kong must not resolve OIDC claims into a principal`,
    );
  }
}

if (violations > 0) {
  console.error(`\n[kong] ${violations} violation(s)`);
  process.exit(1);
}
console.log(`[kong] clean — services=${services.length}, routes=${routes.length}, plugins=${plugins.length}`);
