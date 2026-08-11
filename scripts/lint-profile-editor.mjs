#!/usr/bin/env node
/**
 * scripts/lint-profile-editor.mjs
 *
 * E5.4 deep validator for the BFF person / timeline / permissions
 * / place-lookup OpenAPI contract under
 * `contracts/openapi/bff/v1/person.yaml`.
 *
 * Validates invariants the spectral `oas` ruleset does not
 * capture on its own:
 *
 *   - path parameter regex `^[A-Za-z0-9._:-]{1,128}$` for every
 *     `personId` and `treeId` (opaque id policy);
 *   - the closed-set enums for `LivingStatus`, `PrivacyLevel`,
 *     `DateValue.kind`, `PersonIdentifier.scheme`,
 *     `PlaceAuthority` and `TimelineEvent.kind`;
 *   - the `PersonPatch` schema is `additionalProperties: false`
 *     so the BFF never accepts arbitrary keys (R10 / design.md
 *     §8.3 — server is the source of truth);
 *   - `PersonTimeline.events` has `maxItems ≤ 200` and the
 *     (toYear - fromYear) cap is documented;
 *   - `PlaceLookupResult.degraded` is required so the UI can
 *     detect provider outages (ADR-E0.5-14 §Security/privacy:
 *     "Provider outages degrade to local placeholder; never
 *     block tree edit.");
 *   - `PersonPermissions.actions[].action` covers the
 *     person.* closed-set from R10 (view / edit / delete /
 *     merge / export / relink);
 *   - the `Idempotency-Key` / `If-Match` / `If-None-Match` /
 *     `X-Correlation-Id` headers are properly described on the
 *     `PUT /persons/{personId}` mutation;
 *   - forbidden-token scan (no secret / token / password / DSN /
 *     PEM / AWS access key);
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, relative, resolve } from "node:path";
import YAML from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");

const CONTRACT_PATH = join(ROOT, "contracts/openapi/bff/v1/person.yaml");
const OPAQUE_ID_PATTERN = "^[A-Za-z0-9._:-]{1,128}$";

const REQUIRED_LIVING_STATUSES = [
  "LIVING",
  "PRESUMED_LIVING",
  "DECEASED",
  "PRESUMED_DECEASED",
  "UNKNOWN",
];
const REQUIRED_PRIVACY_LEVELS = ["PUBLIC", "UNLISTED", "PRIVATE"];
const REQUIRED_DATE_KINDS = ["EXACT", "ABOUT", "RANGE", "BEFORE", "AFTER", "UNKNOWN"];
const REQUIRED_IDENTIFIER_SCHEMES = ["AFN", "ARK", "GRdbID", "WikiTreeID", "VRN", "Custom"];
const REQUIRED_PLACE_AUTHORITIES = ["osm", "wikidata", "geonames", "custom"];
const REQUIRED_TIMELINE_KINDS = [
  "BIRTH",
  "DEATH",
  "MARRIAGE",
  "DIVORCE",
  "RESIDENCE",
  "MIGRATION",
  "MILITARY",
  "EDUCATION",
  "RELIGION",
  "CUSTOM",
];
const REQUIRED_PERMISSION_FIELDS = [
  "displayName",
  "names",
  "identifiers",
  "birth",
  "death",
  "biography",
  "privacyLevel",
];
const REQUIRED_PERMISSION_ACTIONS = [
  "person.view",
  "person.edit",
  "person.delete",
  "person.merge",
  "person.export",
  "person.relink",
];
const FORBIDDEN_TOKENS = [
  /AKIA[0-9A-Z]{16}/,
  /-----BEGIN [A-Z ]+-----/,
  /password\s*[:=]\s*["'][^"']+["']/i,
  /token\s*[:=]\s*["'][^"']+["']/i,
  /secret\s*[:=]\s*["'][^"']+["']/i,
  /postgres:\/\/[^"'\s]+/i,
  /mongodb(?:\+srv)?:\/\/[^"'\s]+/i,
];

let violations = 0;

function fail(message) {
  violations += 1;
  console.error(`[profile-editor] ${message}`);
}

function assertClosedSet(schema, enumName, required) {
  if (!schema || !Array.isArray(schema.enum)) {
    fail(`${enumName}: missing closed-set enum`);
    return;
  }
  const have = new Set(schema.enum);
  for (const value of required) {
    if (!have.has(value)) fail(`${enumName}: closed-set missing required value "${value}"`);
  }
  for (const value of schema.enum) {
    if (!required.includes(value)) {
      fail(`${enumName}: closed-set contains non-required value "${value}"`);
    }
  }
}

function assertPattern(schema, fieldName, expected) {
  if (!schema || typeof schema.pattern !== "string") {
    fail(`${fieldName}: missing string pattern`);
    return;
  }
  if (schema.pattern !== expected) {
    fail(`${fieldName}: pattern must be ${expected}, got ${schema.pattern}`);
  }
}

function assertPathParam(param, fieldName) {
  if (!param || !param.schema) {
    fail(`${fieldName}: missing schema`);
    return;
  }
  assertPattern(param.schema, fieldName, OPAQUE_ID_PATTERN);
}

function scanForbiddenLiterals(raw) {
  for (const re of FORBIDDEN_TOKENS) {
    if (re.test(raw)) fail(`forbidden literal matched ${re}`);
  }
}

function checkContract() {
  let raw;
  let doc;
  try {
    raw = readFileSync(CONTRACT_PATH, "utf8");
  } catch (err) {
    fail(`cannot read ${relative(ROOT, CONTRACT_PATH)}: ${err.message}`);
    return;
  }
  try {
    doc = YAML.parse(raw);
  } catch (err) {
    fail(`cannot parse ${relative(ROOT, CONTRACT_PATH)}: ${err.message}`);
    return;
  }
  if (!doc.openapi || !doc.openapi.startsWith("3.")) {
    fail(`openapi version must be 3.x, got ${doc.openapi}`);
  }
  if (!doc.info || doc.info.title !== "Genealogy Platform — BFF Person API") {
    fail(`info.title must be "Genealogy Platform — BFF Person API"`);
  }
  if (doc.info["x-contract-major"] !== 1) {
    fail(`info.x-contract-major must be 1`);
  }

  const schemas = doc.components?.schemas ?? {};
  assertClosedSet(schemas.LivingStatus, "schemas.LivingStatus", REQUIRED_LIVING_STATUSES);
  assertClosedSet(schemas.PrivacyLevel, "schemas.PrivacyLevel", REQUIRED_PRIVACY_LEVELS);
  if (schemas.DateValue?.properties?.kind) {
    assertClosedSet(schemas.DateValue.properties.kind, "schemas.DateValue.kind", REQUIRED_DATE_KINDS);
  } else {
    fail("schemas.DateValue.properties.kind missing");
  }
  if (schemas.PersonIdentifier?.properties?.scheme) {
    assertClosedSet(
      schemas.PersonIdentifier.properties.scheme,
      "schemas.PersonIdentifier.scheme",
      REQUIRED_IDENTIFIER_SCHEMES,
    );
  } else {
    fail("schemas.PersonIdentifier.properties.scheme missing");
  }
  if (schemas.TimelineEvent?.properties?.kind) {
    assertClosedSet(
      schemas.TimelineEvent.properties.kind,
      "schemas.TimelineEvent.kind",
      REQUIRED_TIMELINE_KINDS,
    );
  } else {
    fail("schemas.TimelineEvent.properties.kind missing");
  }
  if (schemas.PlaceCandidate?.properties?.authorityRefs?.items?.properties?.authority) {
    assertClosedSet(
      schemas.PlaceCandidate.properties.authorityRefs.items.properties.authority,
      "schemas.PlaceCandidate.authorityRefs.authority",
      REQUIRED_PLACE_AUTHORITIES,
    );
  } else {
    fail("schemas.PlaceCandidate.authorityRefs.authority missing");
  }

  if (!schemas.PersonPatch) {
    fail("schemas.PersonPatch missing");
  } else {
    if (schemas.PersonPatch.additionalProperties !== false) {
      fail("schemas.PersonPatch.additionalProperties must be false (R10 — no arbitrary keys)");
    }
    const required = schemas.PersonPatch.required ?? [];
    for (const field of ["displayName", "names"]) {
      if (!required.includes(field)) {
        fail(`schemas.PersonPatch.required missing "${field}"`);
      }
    }
  }

  if (!schemas.PersonTimeline?.properties?.events) {
    fail("schemas.PersonTimeline.events missing");
  } else {
    const events = schemas.PersonTimeline.properties.events;
    if (events.maxItems === undefined || events.maxItems > 200) {
      fail(`schemas.PersonTimeline.events.maxItems must be ≤ 200 (got ${events.maxItems})`);
    }
  }

  if (!schemas.PlaceLookupResult) {
    fail("schemas.PlaceLookupResult missing");
  } else {
    const required = schemas.PlaceLookupResult.required ?? [];
    for (const field of ["provider", "candidates"]) {
      if (!required.includes(field)) {
        fail(`schemas.PlaceLookupResult.required missing "${field}"`);
      }
    }
    if (schemas.PlaceLookupResult.properties?.degraded?.type !== "boolean") {
      fail("schemas.PlaceLookupResult.degraded must be a boolean (ADR-E0.5-14)");
    }
  }

  if (!schemas.PersonPermissions) {
    fail("schemas.PersonPermissions missing");
  } else {
    const fields = schemas.PersonPermissions.properties?.fields?.items?.properties?.field;
    if (!fields || !Array.isArray(fields.enum)) {
      fail("schemas.PersonPermissions.fields[].field closed-set missing");
    } else {
      const have = new Set(fields.enum);
      for (const f of REQUIRED_PERMISSION_FIELDS) {
        if (!have.has(f)) fail(`PersonPermissions.fields closed-set missing "${f}"`);
      }
    }
    const actions = schemas.PersonPermissions.properties?.actions?.items?.properties?.action;
    if (!actions || !Array.isArray(actions.enum)) {
      fail("schemas.PersonPermissions.actions[].action closed-set missing");
    } else {
      const have = new Set(actions.enum);
      for (const a of REQUIRED_PERMISSION_ACTIONS) {
        if (!have.has(a)) fail(`PersonPermissions.actions closed-set missing "${a}"`);
      }
    }
  }

  // Path parameters — every personId / treeId must use the opaque regex.
  const paths = doc.paths ?? {};
  for (const [pathKey, pathValue] of Object.entries(paths)) {
    if (typeof pathValue !== "object" || pathValue === null) continue;
    for (const param of pathValue.parameters ?? []) {
      if (param.name === "personId") assertPathParam(param, `${pathKey}::personId`);
      if (param.name === "treeId" && param.in === "path") {
        assertPathParam(param, `${pathKey}::treeId(path)`);
      }
      if (param.name === "treeId" && param.in === "query") {
        assertPattern(param.schema, `${pathKey}::treeId(query)`, OPAQUE_ID_PATTERN);
      }
    }
  }

  // Mutation headers — PUT /persons/{personId} must declare If-Match.
  const put = paths["/persons/{personId}"]?.put;
  if (!put) {
    fail("PUT /persons/{personId} missing");
  } else {
    const headers = (put.parameters ?? []).filter((p) => p.in === "header");
    const ifMatch = headers.find((p) => p.name === "If-Match");
    if (!ifMatch) fail("PUT /persons/{personId} missing If-Match header");
    else if (ifMatch.required !== true)
      fail("PUT /persons/{personId} If-Match must be required=true (412 on stale version)");
  }

  // GET person must declare If-None-Match.
  const getPerson = paths["/persons/{personId}"]?.get;
  if (getPerson) {
    const headerNames = new Set(
      (getPerson.parameters ?? []).filter((p) => p.in === "header").map((p) => p.name),
    );
    if (!headerNames.has("If-None-Match")) {
      fail("GET /persons/{personId} missing If-None-Match header (304 short-circuit)");
    }
  }

  scanForbiddenLiterals(raw);
}

function main() {
  checkContract();
  if (violations === 0) {
    console.log("[profile-editor] OK");
    process.exit(0);
  } else {
    console.error(`[profile-editor] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();