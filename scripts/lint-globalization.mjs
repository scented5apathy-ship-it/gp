#!/usr/bin/env node
/**
 * scripts/lint-globalization.mjs
 *
 * E12.3 deep validator for the globalization hardening contract
 * at `contracts/pwa/globalization-policy.yaml` and the platform
 * mirror at
 * `platform/helm/genealogy-platform/files/pwa/globalization-policy.yaml`.
 *
 * Validates:
 *   - closed-set vocabularies: supportedLocales[20],
 *     pseudoLocales[2], calendarSystems[6], timezoneSources[1],
 *     dateAmbiguityKinds[11], transliterationScripts[10],
 *     nameOrders[5], glossaryDomains[14],
 *     globalizationAuditEvents[13], globalizationFailureReasons[12],
 *     globalizationForbiddenPayloadKeys[25], egressAllowlist[2];
 *   - localeFallbackChain — every fallback MUST resolve to a
 *     supported base locale or the defaultLocale;
 *   - 2 state matrices (localeNegotiationStateMatrix initial
 *     NEGOTIATING, ambiguousDateStateMatrix initial PARSING);
 *   - numeric bounds (10 numeric invariants);
 *   - 14 invariants (fallbackChainTerminates,
 *     pseudolocaleProductionForbidden, everyStringIcuCompatible,
 *     forbiddenHardcodedStringsRejected, dateIncludesCalendar,
 *     dateIncludesTimezone, ambiguousDateRoundTripsCleanly,
 *     glossaryAppliedBeforeSend, placeholderSetParityEnforced,
 *     dstGapRejected, dstOverlapDisambiguated,
 *     transliterationViaAdapter, tenantBoundaryEnforced,
 *     forbiddenPayloadKeysEnforced);
 *   - capability boundaries — icu-message.ts MUST be sole path
 *     for translator-visible strings, glossary MUST be applied
 *     before send across email/PDF/report pipelines.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import {
  loadYaml,
  asArray,
  assertClosedSet,
  assertStateMatrix,
} from "./lint-yaml.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT
  ? resolve(process.env.LINT_ROOT)
  : resolve(__dirname, "..");

const CONTRACT = join(ROOT, "contracts/pwa/globalization-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/pwa/globalization-policy.yaml",
);

const REQUIRED_SUPPORTED_LOCALES = [
  "en",
  "vi",
  "fr",
  "de",
  "es",
  "ja",
  "ar",
  "he",
  "fa",
  "ur",
  "zh",
  "ru",
  "th",
  "ko",
  "id",
  "ms",
  "tr",
  "pl",
  "nl",
  "it",
];

const REQUIRED_PSEUDO_LOCALES = ["en-XA", "ar-XB"];

const REQUIRED_CALENDARS = [
  "GREGORIAN",
  "JULIAN",
  "HEBREW",
  "ISLAMIC_CIVIL",
  "ETHIOPIC",
  "INDIAN_SAKA",
];

const REQUIRED_TIMEZONE_SOURCES = ["IANA"];

const REQUIRED_AMBIGUITY_KINDS = [
  "EXACT",
  "ABOUT",
  "BEFORE",
  "AFTER",
  "BETWEEN",
  "POSSIBLE_BETWEEN",
  "UNKNOWN_DAY",
  "UNKNOWN_MONTH",
  "UNKNOWN_YEAR",
  "APPROXIMATE",
  "CALCULATED",
];

const REQUIRED_TRANSLITERATION_SCRIPTS = [
  "CYRILLIC_TO_LATIN",
  "LATIN_TO_CYRILLIC",
  "ARABIC_TO_LATIN",
  "LATIN_TO_ARABIC",
  "HAN_TO_LATIN",
  "LATIN_TO_HAN",
  "DEVANAGARI_TO_LATIN",
  "LATIN_TO_DEVANAGARI",
  "HEBREW_TO_LATIN",
  "LATIN_TO_HEBREW",
];

const REQUIRED_NAME_ORDERS = [
  "GIVEN_FIRST",
  "FAMILY_FIRST",
  "FAMILY_ONLY",
  "GIVEN_FAMILY_COMMA",
];

const REQUIRED_GLOSSARY_DOMAINS = [
  "PERSON",
  "RELATIONSHIP",
  "EVENT",
  "PLACE",
  "SOURCE",
  "CITATION",
  "CONSENT",
  "DNA",
  "PRIVACY",
  "PRINT",
  "TIMELINE",
  "TREE",
  "ALBUM",
  "COLLABORATION",
];

const REQUIRED_AUDIT_EVENTS = [
  "i18n.localeNegotiated",
  "i18n.localeFallback",
  "i18n.fallbackMiss",
  "i18n.transliterationApplied",
  "i18n.calendarConverted",
  "i18n.timezoneResolved",
  "i18n.dstGapDetected",
  "i18n.dstOverlapResolved",
  "i18n.ambiguousDateRoundTripped",
  "i18n.glossaryApplied",
  "i18n.glossaryMiss",
  "i18n.pseudolocaleActivated",
  "i18n.pseudolocaleBlocked",
];

const REQUIRED_FAILURE_REASONS = [
  "LOCALE_UNKNOWN",
  "FALLBACK_CHAIN_BROKEN",
  "CALENDAR_UNKNOWN",
  "TIMEZONE_UNKNOWN",
  "DST_GAP",
  "DST_OVERLAP_AMBIGUOUS",
  "AMBIGUOUS_DATE_LOSS",
  "PLACEHOLDER_MISSING",
  "PLACEHOLDER_TYPE_MISMATCH",
  "TRANSLITERATION_UNAVAILABLE",
  "GLOSSARY_KEY_MISSING",
  "PSEUDOLOCALE_IN_PRODUCTION",
];

const REQUIRED_FORBIDDEN_PAYLOAD_KEYS = [
  "rawDna",
  "rawMedia",
  "dnaRawBytes",
  "dnaMatchResult",
  "signedUrlSecret",
  "oidcAccessToken",
  "oidcRefreshToken",
  "oidcIdToken",
  "rawWebhookSecret",
  "rawProviderApiKey",
  "rawKmsKey",
  "rawVaultToken",
  "rawSessionCookie",
  "rawPin",
  "rawBiometric",
  "rawDnaConsentToken",
  "rawExportToken",
  "rawS3AccessKey",
  "rawS3Secret",
  "treeViewerBypass",
  "rawGuardianReason",
  "rawSupportReason",
  "rawDeletionReason",
  "rawOnboardingToken",
  "rawOidcClientSecret",
];

const REQUIRED_EGRESS = [
  "api.genealogy-platform.example",
  "cdn.genealogy-platform.example",
];

const LOCALE_NEGOTIATION_STATUSES = [
  "NEGOTIATING",
  "EXACT_MATCH",
  "FALLBACK_CHAIN_WALK",
  "DEFAULT_LOCALE",
  "PSEUDOLOCALE_BLOCKED",
  "UNKNOWN",
];

const AMBIGUOUS_DATE_STATUSES = [
  "PARSING",
  "EXACT",
  "APPROXIMATE",
  "BETWEEN",
  "BEFORE",
  "AFTER",
  "UNKNOWN_DAY",
  "UNKNOWN_MONTH",
  "UNKNOWN_YEAR",
  "CALENDAR_CONVERTED",
  "LOSSY",
  "REJECTED",
];

const NUMERIC_BOUNDS = [
  "minIanaVersion",
  "maxPlaceholderCount",
  "maxGlossaryKeyLength",
  "maxLocaleTagLength",
  "transliterationCacheTtlSeconds",
  "glossaryCacheTtlSeconds",
  "ambiguousDateToleranceSeconds",
  "dstGapWindowSeconds",
  "placeholderNameMaxLength",
];

const INVARIANTS = [
  "fallbackChainTerminates",
  "pseudolocaleProductionForbidden",
  "everyStringIcuCompatible",
  "forbiddenHardcodedStringsRejected",
  "dateIncludesCalendar",
  "dateIncludesTimezone",
  "ambiguousDateRoundTripsCleanly",
  "glossaryAppliedBeforeSend",
  "placeholderSetParityEnforced",
  "dstGapRejected",
  "dstOverlapDisambiguated",
  "transliterationViaAdapter",
  "tenantBoundaryEnforced",
  "forbiddenPayloadKeysEnforced",
];

const FORBIDDEN_HARDCODED_STRINGS = [
  "Sign in",
  "Save",
  "Cancel",
  "Delete",
  "Edit",
  "Loading",
  "Error",
  "Confirm",
  "Continue",
  "Submit",
  "Send",
  "OK",
  "Yes",
  "No",
];

let violations = 0;

function ok(message) {
  process.stdout.write(`  ok  ${message}\n`);
}

function fail(message) {
  violations += 1;
  process.stderr.write(`  fail  ${message}\n`);
}

function readBoth() {
  const contractText = readFileSync(CONTRACT, "utf8");
  const chartText = readFileSync(CHART_FILE, "utf8");
  return { contractText, chartText, contract: loadYaml(contractText), chart: loadYaml(chartText) };
}

function checkParity(contractText, chartText) {
  if (contractText !== chartText) {
    fail("contract <-> helm chart mirror mismatch — byte-equal copy required");
    return;
  }
  ok("contract <-> helm chart mirror byte-equal");
}

function checkClosedSets(doc) {
  assertClosedSet("supportedLocales", REQUIRED_SUPPORTED_LOCALES, asArray(doc.supportedLocales?.values), "E12.3 supportedLocales", ok, fail);
  assertClosedSet("pseudoLocales", REQUIRED_PSEUDO_LOCALES, asArray(doc.pseudoLocales?.values), "E12.3 pseudoLocales", ok, fail);
  assertClosedSet("calendarSystems", REQUIRED_CALENDARS, asArray(doc.calendarSystems?.values), "E12.3 calendarSystems", ok, fail);
  assertClosedSet("timezoneSources", REQUIRED_TIMEZONE_SOURCES, asArray(doc.timezoneSources?.values), "E12.3 timezoneSources", ok, fail);
  assertClosedSet("dateAmbiguityKinds", REQUIRED_AMBIGUITY_KINDS, asArray(doc.dateAmbiguityKinds?.values), "E12.3 dateAmbiguityKinds", ok, fail);
  assertClosedSet("transliterationScripts", REQUIRED_TRANSLITERATION_SCRIPTS, asArray(doc.transliterationScripts?.values), "E12.3 transliterationScripts", ok, fail);
  assertClosedSet("nameOrders", REQUIRED_NAME_ORDERS, asArray(doc.nameOrders?.values), "E12.3 nameOrders", ok, fail);
  assertClosedSet("glossaryDomains", REQUIRED_GLOSSARY_DOMAINS, asArray(doc.glossaryDomains?.values), "E12.3 glossaryDomains", ok, fail);
  assertClosedSet("globalizationAuditEvents", REQUIRED_AUDIT_EVENTS, asArray(doc.globalizationAuditEvents?.values), "E12.3 globalizationAuditEvents", ok, fail);
  assertClosedSet("globalizationFailureReasons", REQUIRED_FAILURE_REASONS, asArray(doc.globalizationFailureReasons?.values), "E12.3 globalizationFailureReasons", ok, fail);
  assertClosedSet("globalizationForbiddenPayloadKeys", REQUIRED_FORBIDDEN_PAYLOAD_KEYS, asArray(doc.globalizationForbiddenPayloadKeys?.values), "E12.3 globalizationForbiddenPayloadKeys", ok, fail);
  assertClosedSet("egressAllowlist", REQUIRED_EGRESS, asArray(doc.egressAllowlist?.values), "E12.3 egressAllowlist", ok, fail);
  assertClosedSet("forbiddenHardcodedStrings", FORBIDDEN_HARDCODED_STRINGS, asArray(doc.forbiddenHardcodedStrings?.values), "E12.3 forbiddenHardcodedStrings", ok, fail);
}

function checkLocaleFallbackChain(doc) {
  const chain = doc.localeFallbackChain;
  if (!chain || typeof chain !== "object") {
    fail("localeFallbackChain missing");
    return;
  }
  if (chain.defaultLocale !== "en") {
    fail("localeFallbackChain.defaultLocale MUST be 'en'");
  }
  const supported = new Set(REQUIRED_SUPPORTED_LOCALES);
  const chains = asArray(chain.chains);
  if (chains.length === 0) {
    fail("localeFallbackChain.chains MUST contain at least one entry");
    return;
  }
  for (const link of chains) {
    if (!link.tag || !link.fallback) {
      fail(`localeFallbackChain: invalid entry ${JSON.stringify(link)}`);
      continue;
    }
    if (!supported.has(link.fallback)) {
      fail(`localeFallbackChain.${link.tag}.fallback="${link.fallback}" MUST be a supported base locale`);
    }
    if (link.tag === link.fallback) {
      fail(`localeFallbackChain.${link.tag} MUST not self-reference`);
    }
  }
  ok(`localeFallbackChain (${chains.length} entries)`);
}

function checkStateMatrices(doc) {
  const negotiation = doc.localeNegotiationStateMatrix;
  assertStateMatrix(
    "E12.3 localeNegotiationStateMatrix",
    negotiation,
    LOCALE_NEGOTIATION_STATUSES,
    "NEGOTIATING",
    ok,
    fail,
  );
  const ambiguous = doc.ambiguousDateStateMatrix;
  assertStateMatrix(
    "E12.3 ambiguousDateStateMatrix",
    ambiguous,
    AMBIGUOUS_DATE_STATUSES,
    "PARSING",
    ok,
    fail,
  );
}

function checkNumericBounds(doc) {
  const bounds = doc.numericBounds || {};
  for (const key of NUMERIC_BOUNDS) {
    if (bounds[key] === undefined) {
      fail(`numericBounds.${key} missing`);
    }
  }
  if (typeof bounds.maxPlaceholderCount === "number" && bounds.maxPlaceholderCount < 1) {
    fail(`numericBounds.maxPlaceholderCount MUST be >= 1`);
  }
  ok(`numericBounds (${NUMERIC_BOUNDS.length} entries)`);
}

function checkInvariants(doc) {
  const inv = doc.invariants || {};
  for (const key of INVARIANTS) {
    if (inv[key] === undefined) {
      fail(`invariants.${key} missing`);
    }
  }
  if (inv.fallbackChainTerminates !== true) fail("invariants.fallbackChainTerminates MUST be true");
  if (inv.pseudolocaleProductionForbidden !== true) fail("invariants.pseudolocaleProductionForbidden MUST be true");
  if (inv.everyStringIcuCompatible !== true) fail("invariants.everyStringIcuCompatible MUST be true");
  if (inv.forbiddenHardcodedStringsRejected !== true) fail("invariants.forbiddenHardcodedStringsRejected MUST be true");
  if (inv.dateIncludesCalendar !== true) fail("invariants.dateIncludesCalendar MUST be true");
  if (inv.dateIncludesTimezone !== true) fail("invariants.dateIncludesTimezone MUST be true");
  if (inv.ambiguousDateRoundTripsCleanly !== true) fail("invariants.ambiguousDateRoundTripsCleanly MUST be true");
  if (inv.glossaryAppliedBeforeSend !== true) fail("invariants.glossaryAppliedBeforeSend MUST be true");
  if (inv.placeholderSetParityEnforced !== true) fail("invariants.placeholderSetParityEnforced MUST be true");
  if (inv.dstGapRejected !== true) fail("invariants.dstGapRejected MUST be true");
  if (inv.dstOverlapDisambiguated !== true) fail("invariants.dstOverlapDisambiguated MUST be true");
  if (inv.transliterationViaAdapter !== true) fail("invariants.transliterationViaAdapter MUST be true");
  if (inv.tenantBoundaryEnforced !== true) fail("invariants.tenantBoundaryEnforced MUST be true");
  if (inv.forbiddenPayloadKeysEnforced !== true) fail("invariants.forbiddenPayloadKeysEnforced MUST be true");
  ok(`invariants (${INVARIANTS.length} invariants)`);
}

function checkIcuHelper() {
  const helper = join(ROOT, "apps/web/src/lib/i18n/icu-message.ts");
  let text;
  try {
    text = readFileSync(helper, "utf8");
  } catch (err) {
    fail(`apps/web/src/lib/i18n/icu-message.ts missing (${err.code})`);
    return;
  }
  if (!/plural|select/.test(text)) {
    fail("icu-message.ts MUST support ICU plural / select");
  }
  if (!/placeholder/.test(text)) {
    fail("icu-message.ts MUST validate placeholder names");
  }
  ok("icu-message.ts supports ICU plural/select + placeholder validation");
}

function checkFallbackHelper() {
  const helper = join(ROOT, "apps/web/src/lib/i18n/locale-fallback.ts");
  let text;
  try {
    text = readFileSync(helper, "utf8");
  } catch (err) {
    fail(`apps/web/src/lib/i18n/locale-fallback.ts missing (${err.code})`);
    return;
  }
  if (!/fallback/.test(text)) {
    fail("locale-fallback.ts MUST export a fallback function");
  }
  if (!/en/.test(text)) {
    fail("locale-fallback.ts MUST terminate at the defaultLocale 'en'");
  }
  ok("locale-fallback.ts exports fallback() terminating at defaultLocale");
}

function checkCalendarHelper() {
  const helper = join(ROOT, "apps/web/src/lib/calendar/index.ts");
  let text;
  try {
    text = readFileSync(helper, "utf8");
  } catch (err) {
    fail(`apps/web/src/lib/calendar/index.ts missing (${err.code})`);
    return;
  }
  for (const calendar of REQUIRED_CALENDARS) {
    if (!text.includes(calendar)) {
      fail(`calendar/index.ts MUST handle calendar "${calendar}"`);
    }
  }
  ok(`calendar/index.ts handles ${REQUIRED_CALENDARS.length} calendar systems`);
}

function checkAmbiguousDateHelper() {
  const helper = join(ROOT, "apps/web/src/lib/date/ambiguous-date.ts");
  let text;
  try {
    text = readFileSync(helper, "utf8");
  } catch (err) {
    fail(`apps/web/src/lib/date/ambiguous-date.ts missing (${err.code})`);
    return;
  }
  if (!/BETWEEN|BEFORE|AFTER/.test(text)) {
    fail("ambiguous-date.ts MUST handle BETWEEN / BEFORE / AFTER");
  }
  if (!/serialize|round.?trip/.test(text)) {
    fail("ambiguous-date.ts MUST expose a serialize/round-trip helper");
  }
  ok("ambiguous-date.ts handles BETWEEN/BEFORE/AFTER with round-trip serialize");
}

function checkGlossary() {
  const helper = join(ROOT, "apps/web/src/lib/print/glossary.ts");
  let text;
  try {
    text = readFileSync(helper, "utf8");
  } catch (err) {
    fail(`apps/web/src/lib/print/glossary.ts missing (${err.code})`);
    return;
  }
  if (!/apply\(/.test(text)) {
    fail("glossary.ts MUST export apply()");
  }
  for (const domain of ["PERSON", "EVENT", "PLACE", "CITATION"]) {
    if (!text.includes(domain)) {
      fail(`glossary.ts MUST cover domain "${domain}"`);
    }
  }
  ok("glossary.ts exports apply() and covers PERSON/EVENT/PLACE/CITATION");
}

function main() {
  let data;
  try {
    data = readBoth();
  } catch (err) {
    process.stderr.write(`config error: ${err.message}\n`);
    process.exit(2);
  }

  process.stdout.write("E12.3 globalization linter\n");
  checkParity(data.contractText, data.chartText);
  checkClosedSets(data.contract);
  checkLocaleFallbackChain(data.contract);
  checkStateMatrices(data.contract);
  checkNumericBounds(data.contract);
  checkInvariants(data.contract);
  checkIcuHelper();
  checkFallbackHelper();
  checkCalendarHelper();
  checkAmbiguousDateHelper();
  checkGlossary();

  process.stdout.write(`\nE12.3 summary: ${violations === 0 ? "OK" : `${violations} violation(s)`}\n`);
  process.exit(violations === 0 ? 0 : 1);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}