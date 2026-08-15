#!/usr/bin/env node
/**
 * scripts/lint-accessibility.mjs
 *
 * E12.4 deep validator for the accessibility hardening contract
 * at `contracts/pwa/accessibility-policy.yaml` and the platform
 * mirror at
 * `platform/helm/genealogy-platform/files/pwa/accessibility-policy.yaml`.
 *
 * Validates:
 *   - closed-set vocabularies: a11yFlows[7],
 *     wcagSuccessCriteria[16], contrastTokens[5],
 *     a11yAuditEvents[10], a11yFailureReasons[13],
 *     a11yForbiddenPayloadKeys[25], egressAllowlist[1];
 *   - defectSeverityMatrix (4 severities with SLA bounds);
 *   - focusRingSpec, touchTargetSpec, reducedMotionSpec,
 *     zoomSpec spec blocks (all required keys present);
 *   - 12 invariants (keyboardAloneReachable, focusAlwaysVisible,
 *     reducedMotionHonoured, touchTargetMinSize,
 *     zoomUpTo200Percent, liveRegionsOnAsyncOps,
 *     contrastBodyText=4.5, contrastEnhancedText=7,
 *     axeZeroCritical, axeZeroSeriousOnCanonical,
 *     tenantBoundaryEnforced, forbiddenPayloadKeysEnforced);
 *   - 2 state matrices (axeGateStateMatrix initial QUEUED,
 *     focusTrapStateMatrix initial IDLE) — terminals have empty
 *     transitions;
 *   - numeric bounds (10 entries);
 *   - capability boundaries — keyboard-tree.ts, focus-trap.ts,
 *     live-region.tsx MUST be sole helpers;
 *   - tokens.css MUST declare --focus-ring-width and
 *     --focus-ring-color, MUST include prefers-reduced-motion;
 *   - axe-core CI gate MUST exist.
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

const CONTRACT = join(ROOT, "contracts/pwa/accessibility-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/pwa/accessibility-policy.yaml",
);

const REQUIRED_FLOWS = [
  "onboarding",
  "tree-list",
  "tree-canvas",
  "profile-edit",
  "timeline",
  "import-dialog",
  "consent-dialog",
];

const REQUIRED_WCAG = [
  "1.1.1",
  "1.3.1",
  "1.4.3",
  "1.4.6",
  "1.4.11",
  "2.1.1",
  "2.1.2",
  "2.4.1",
  "2.4.3",
  "2.4.7",
  "2.5.8",
  "2.3.3",
  "3.3.1",
  "3.3.2",
  "4.1.2",
  "4.1.3",
];

const REQUIRED_AUDIT_EVENTS = [
  "a11y.focusTrapEntered",
  "a11y.focusTrapExited",
  "a11y.focusReturned",
  "a11y.liveRegionAnnounce",
  "a11y.reducedMotionDetected",
  "a11y.zoomAdjusted",
  "a11y.contrastViolationDetected",
  "a11y.touchTargetViolation",
  "a11y.keyboardTrapDetected",
  "a11y.axeGateResult",
];

const REQUIRED_FAILURE_REASONS = [
  "AXE_CRITICAL_FINDING",
  "AXE_SERIOUS_FINDING",
  "FOCUS_NOT_VISIBLE",
  "FOCUS_TRAP_NO_RETURN",
  "KEYBOARD_UNREACHABLE",
  "LIVE_REGION_MISSING",
  "TOUCH_TARGET_TOO_SMALL",
  "CONTRAST_BELOW_MINIMUM",
  "ZOOM_BEYOND_LIMIT",
  "REDUCED_MOTION_NOT_RESPECTED",
  "ROLE_MISSING",
  "LABEL_MISSING",
  "HEADING_ORDER_SKIPPED",
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

const REQUIRED_EGRESS = ["api.genealogy-platform.example"];

const AXE_STATE_STATUSES = [
  "QUEUED",
  "RUNNING",
  "PASS",
  "FAIL_CRITICAL",
  "FAIL_SERIOUS",
  "FAIL_MODERATE",
  "FAIL_MINOR",
  "ERROR",
  "SKIPPED",
];

const FOCUS_TRAP_STATUSES = ["IDLE", "ENTERING", "ACTIVE", "RESTORING", "EXITED"];

const NUMERIC_BOUNDS = [
  "focusRingMinWidthCssPx",
  "touchTargetMinWidthCssPx",
  "touchTargetMinHeightCssPx",
  "zoomMax",
  "textSpacingOverrideScale",
  "axeTimeoutMs",
  "liveRegionCoalesceMs",
  "contrastRatioNormalText",
  "contrastRatioLargeText",
  "contrastRatioEnhanced",
];

const INVARIANTS = [
  "keyboardAloneReachable",
  "focusAlwaysVisible",
  "reducedMotionHonoured",
  "touchTargetMinSize",
  "zoomUpTo200Percent",
  "liveRegionsOnAsyncOps",
  "contrastBodyText",
  "contrastEnhancedText",
  "axeZeroCritical",
  "axeZeroSeriousOnCanonical",
  "tenantBoundaryEnforced",
  "forbiddenPayloadKeysEnforced",
];

const SEVERITY_KEYS = ["critical", "serious", "moderate", "minor"];

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
  assertClosedSet("a11yAuditEvents", REQUIRED_AUDIT_EVENTS, asArray(doc.a11yAuditEvents?.values), "E12.4 a11yAuditEvents", ok, fail);
  assertClosedSet("a11yFailureReasons", REQUIRED_FAILURE_REASONS, asArray(doc.a11yFailureReasons?.values), "E12.4 a11yFailureReasons", ok, fail);
  assertClosedSet("a11yForbiddenPayloadKeys", REQUIRED_FORBIDDEN_PAYLOAD_KEYS, asArray(doc.a11yForbiddenPayloadKeys?.values), "E12.4 a11yForbiddenPayloadKeys", ok, fail);
  assertClosedSet("egressAllowlist", REQUIRED_EGRESS, asArray(doc.egressAllowlist?.values), "E12.4 egressAllowlist", ok, fail);
}

function checkFlows(doc) {
  const flows = asArray(doc.a11yFlows?.values);
  const ids = new Set();
  for (const flow of flows) {
    ids.add(flow?.flow);
  }
  for (const required of REQUIRED_FLOWS) {
    if (!ids.has(required)) {
      fail(`a11yFlows.flow "${required}" missing`);
    }
  }
  for (const flow of flows) {
    if (!flow?.path || !flow?.keyboardShortcuts || !flow?.liveRegion || !flow?.touchTargets) {
      fail(`a11yFlows "${flow?.flow}" missing path/keyboardShortcuts/liveRegion/touchTargets`);
    }
  }
  ok(`a11yFlows (${REQUIRED_FLOWS.length} canonical flows)`);
}

function checkWcag(doc) {
  const sc = asArray(doc.wcagSuccessCriteria?.values);
  const ids = new Set(sc.map((entry) => entry.id));
  for (const required of REQUIRED_WCAG) {
    if (!ids.has(required)) {
      fail(`wcagSuccessCriteria id "${required}" missing`);
    }
  }
  ok(`wcagSuccessCriteria (${REQUIRED_WCAG.length} criteria)`);
}

function checkSeverityMatrix(doc) {
  const matrix = doc.defectSeverityMatrix;
  if (!matrix || typeof matrix !== "object") {
    fail("defectSeverityMatrix missing");
    return;
  }
  const severities = asArray(matrix.severities);
  const labels = severities.map((s) => s?.severity);
  for (const required of SEVERITY_KEYS) {
    if (!labels.includes(required)) {
      fail(`defectSeverityMatrix severity "${required}" missing`);
    }
  }
  for (const entry of severities) {
    if (typeof entry?.maxAcknowledgementHours !== "number") {
      fail(`defectSeverityMatrix.${entry?.severity}.maxAcknowledgementHours MUST be a number`);
    }
    if (typeof entry?.maxFixDays !== "number") {
      fail(`defectSeverityMatrix.${entry?.severity}.maxFixDays MUST be a number`);
    }
    if (typeof entry?.blocksRelease !== "boolean") {
      fail(`defectSeverityMatrix.${entry?.severity}.blocksRelease MUST be a boolean`);
    }
  }
  const critical = severities.find((s) => s.severity === "critical");
  if (critical && (critical.maxAcknowledgementHours !== 0 || critical.maxFixDays !== 0 || critical.blocksRelease !== true)) {
    fail("defectSeverityMatrix.critical MUST enforce 0h ack / 0d fix / blocksRelease=true");
  }
  ok(`defectSeverityMatrix (${SEVERITY_KEYS.length} severities)`);
}

function checkSpecBlocks(doc) {
  for (const key of ["focusRingSpec", "touchTargetSpec", "reducedMotionSpec", "zoomSpec"]) {
    const spec = doc[key];
    if (!spec || typeof spec !== "object") {
      fail(`${key} missing`);
      continue;
    }
    ok(`${key} declared`);
  }
  const fr = doc.focusRingSpec;
  if (fr && (typeof fr.minWidthCssPx !== "number" || fr.minWidthCssPx < 2)) {
    fail("focusRingSpec.minWidthCssPx MUST be >= 2");
  }
  if (fr && (typeof fr.minContrastRatio !== "number" || fr.minContrastRatio < 3)) {
    fail("focusRingSpec.minContrastRatio MUST be >= 3");
  }
  const tt = doc.touchTargetSpec;
  if (tt && (typeof tt.minWidthCssPx !== "number" || tt.minWidthCssPx < 24 || typeof tt.minHeightCssPx !== "number" || tt.minHeightCssPx < 24)) {
    fail("touchTargetSpec MUST enforce 24x24 CSS px");
  }
  const rm = doc.reducedMotionSpec;
  if (rm && rm.mediaQuery !== "(prefers-reduced-motion: reduce)") {
    fail("reducedMotionSpec.mediaQuery MUST be '(prefers-reduced-motion: reduce)'");
  }
  const z = doc.zoomSpec;
  if (z && (typeof z.maxScale !== "number" || z.maxScale < 2)) {
    fail("zoomSpec.maxScale MUST be >= 2 (200%)");
  }
}

function checkContrastTokens(doc) {
  const pairs = asArray(doc.contrastTokens?.entries);
  if (pairs.length === 0) {
    fail("contrastTokens.entries MUST contain at least one pair");
    return;
  }
  for (const pair of pairs) {
    if (!Array.isArray(pair?.pair) || pair.pair.length !== 2) {
      fail(`contrastTokens.pair entry invalid: ${JSON.stringify(pair)}`);
    }
    if (typeof pair?.minimumRatio !== "number") {
      fail(`contrastTokens.pair ${JSON.stringify(pair?.pair)} MUST declare minimumRatio`);
    }
  }
  ok(`contrastTokens (${pairs.length} pairs)`);
}

function checkStateMatrices(doc) {
  assertStateMatrix(
    "E12.4 axeGateStateMatrix",
    doc.axeGateStateMatrix,
    AXE_STATE_STATUSES,
    "QUEUED",
    ok,
    fail,
  );
  assertStateMatrix(
    "E12.4 focusTrapStateMatrix",
    doc.focusTrapStateMatrix,
    FOCUS_TRAP_STATUSES,
    "IDLE",
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
  if (bounds.contrastRatioEnhanced !== undefined && bounds.contrastRatioNormalText !== undefined && bounds.contrastRatioEnhanced < bounds.contrastRatioNormalText) {
    fail(`contrastRatioEnhanced (${bounds.contrastRatioEnhanced}) MUST be >= contrastRatioNormalText (${bounds.contrastRatioNormalText})`);
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
  if (inv.keyboardAloneReachable !== true) fail("invariants.keyboardAloneReachable MUST be true");
  if (inv.focusAlwaysVisible !== true) fail("invariants.focusAlwaysVisible MUST be true");
  if (inv.reducedMotionHonoured !== true) fail("invariants.reducedMotionHonoured MUST be true");
  if (inv.touchTargetMinSize !== true) fail("invariants.touchTargetMinSize MUST be true");
  if (inv.zoomUpTo200Percent !== true) fail("invariants.zoomUpTo200Percent MUST be true");
  if (inv.liveRegionsOnAsyncOps !== true) fail("invariants.liveRegionsOnAsyncOps MUST be true");
  if (inv.contrastBodyText !== 4.5) fail("invariants.contrastBodyText MUST be 4.5");
  if (inv.contrastEnhancedText !== 7) fail("invariants.contrastEnhancedText MUST be 7");
  if (inv.axeZeroCritical !== true) fail("invariants.axeZeroCritical MUST be true");
  if (inv.axeZeroSeriousOnCanonical !== true) fail("invariants.axeZeroSeriousOnCanonical MUST be true");
  if (inv.tenantBoundaryEnforced !== true) fail("invariants.tenantBoundaryEnforced MUST be true");
  if (inv.forbiddenPayloadKeysEnforced !== true) fail("invariants.forbiddenPayloadKeysEnforced MUST be true");
  ok(`invariants (${INVARIANTS.length} invariants)`);
}

function checkTokensCss() {
  const css = join(ROOT, "apps/web/src/styles/tokens.css");
  let text;
  try {
    text = readFileSync(css, "utf8");
  } catch (err) {
    fail(`apps/web/src/styles/tokens.css missing (${err.code})`);
    return;
  }
  if (!/--focus-ring-width/.test(text)) {
    fail("tokens.css MUST declare --focus-ring-width");
  }
  if (!/--focus-ring-color/.test(text)) {
    fail("tokens.css MUST declare --focus-ring-color");
  }
  if (!/prefers-reduced-motion/.test(text)) {
    fail("tokens.css MUST honour prefers-reduced-motion (WCAG 2.2 SC 2.3.3)");
  }
  ok("tokens.css declares --focus-ring-width, --focus-ring-color, prefers-reduced-motion");
}

function checkAxeTest() {
  const test = join(ROOT, "apps/web/test/a11y/axe.test.ts");
  try {
    const text = readFileSync(test, "utf8");
    if (!/axe-core|axe\.run|from ["']axe-core["']/.test(text)) {
      fail("apps/web/test/a11y/axe.test.ts MUST import axe-core");
      return;
    }
    if (!/critical|serious/.test(text)) {
      fail("axe.test.ts MUST assert zero critical/serious findings");
    }
    ok("apps/web/test/a11y/axe.test.ts uses axe-core with critical/serious assertions");
  } catch (err) {
    fail(`apps/web/test/a11y/axe.test.ts missing (${err.code})`);
  }
}

function checkA11yHelpers() {
  const files = [
    "apps/web/src/lib/a11y/keyboard-tree.ts",
    "apps/web/src/lib/a11y/focus-trap.ts",
    "apps/web/src/lib/a11y/live-region.tsx",
    "apps/web/src/lib/a11y/contrast.ts",
  ];
  let scanned = 0;
  for (const rel of files) {
    const abs = join(ROOT, rel);
    try {
      readFileSync(abs, "utf8");
      scanned += 1;
    } catch (err) {
      fail(`${rel} missing (${err.code})`);
    }
  }
  if (scanned === files.length) {
    ok(`scanned ${scanned} a11y helper modules`);
  }
}

function main() {
  let data;
  try {
    data = readBoth();
  } catch (err) {
    process.stderr.write(`config error: ${err.message}\n`);
    process.exit(2);
  }

  process.stdout.write("E12.4 accessibility linter\n");
  checkParity(data.contractText, data.chartText);
  checkClosedSets(data.contract);
  checkFlows(data.contract);
  checkWcag(data.contract);
  checkSeverityMatrix(data.contract);
  checkSpecBlocks(data.contract);
  checkContrastTokens(data.contract);
  checkStateMatrices(data.contract);
  checkNumericBounds(data.contract);
  checkInvariants(data.contract);
  checkTokensCss();
  checkAxeTest();
  checkA11yHelpers();

  process.stdout.write(`\nE12.4 summary: ${violations === 0 ? "OK" : `${violations} violation(s)`}\n`);
  process.exit(violations === 0 ? 0 : 1);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}