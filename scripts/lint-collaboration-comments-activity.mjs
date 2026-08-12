#!/usr/bin/env node
/**
 * scripts/lint-collaboration-comments-activity.mjs
 *
 * E6.4 deep validator for the comments / activity policy
 * contract under
 * `contracts/collaboration/comments-activity-policy.yaml`
 * and the platform mirror under
 * `platform/helm/genealogy-platform/files/collaboration-comments-activity-policy.yaml`.
 *
 * Mirrors the structure of `lint-collaboration-mixed-policy.mjs`
 * (E6.3):
 *   - parse + structural assertions on the closed-set
 *     vocabularies (commentStatuses, mentionTargetKinds,
 *     watchScopes, watchTriggers, assignmentRoles,
 *     assignmentStatuses, activityKinds, activityVisibilities,
 *     notificationChannels, notificationOutcomes,
 *     notificationHookKinds, redactionReasons);
 *   - mention / watch / assignment guard-rail validation
 *     (mentionSensitiveFields, assignmentRolesAllowed,
 *     activityFeedSnapshotRawPayloadAllowed=false,
 *     notificationHookNeverCopyRawPayload=true,
 *     notificationHookTemplateRequired=true);
 *   - comment / activity / notification numeric bounds;
 *   - audit hooks + redaction reason codes + re-authorization
 *     toggles;
 *   - forbidden-literal / forbidden-payload scan;
 *   - chart mirror byte-equality.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration
 * error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, relative, resolve } from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const CONTRACT = join(ROOT, "contracts/collaboration/comments-activity-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/collaboration-comments-activity-policy.yaml",
);

const REQUIRED_COMMENT_STATUSES = [
  "ACTIVE",
  "EDITED",
  "DELETED",
  "REDACTED",
  "HIDDEN",
];

const REQUIRED_MENTION_TARGET_KINDS = ["USER", "ROLE", "TREE", "BRANCH"];

const REQUIRED_WATCH_SCOPES = [
  "PROPOSAL",
  "REVIEW",
  "COMMENT",
  "PERSON",
  "RELATIONSHIP",
  "TREE_VISIBILITY",
  "COLLAB_THREAD",
];

const REQUIRED_WATCH_TRIGGERS = [
  "ANY_CHANGE",
  "MENTION",
  "STATUS_CHANGE",
  "DIRECT_EDIT",
  "APPROVAL_REQUIRED",
  "DENY",
];

const REQUIRED_ASSIGNMENT_ROLES = [
  "WATCHER",
  "REVIEWER",
  "APPROVER",
  "GATEKEEPER",
  "MENTIONED",
];

const REQUIRED_ASSIGNMENT_STATUSES = [
  "PENDING",
  "ACCEPTED",
  "DECLINED",
  "EXPIRED",
  "REVOKED",
];

const REQUIRED_ACTIVITY_KINDS = [
  "COMMENT_CREATED",
  "COMMENT_EDITED",
  "COMMENT_REDACTED",
  "COMMENT_DELETED",
  "MENTION_NOTIFIED",
  "MENTION_DROPPED",
  "WATCH_SUBSCRIBED",
  "WATCH_UNSUBSCRIBED",
  "ASSIGNMENT_OPENED",
  "ASSIGNMENT_ACCEPTED",
  "ASSIGNMENT_DECLINED",
  "ASSIGNMENT_REVOKED",
  "ASSIGNMENT_EXPIRED",
  "NOTIFICATION_DELIVERED",
  "NOTIFICATION_DROPPED",
];

const REQUIRED_ACTIVITY_VISIBILITIES = ["PUBLIC", "TREE", "BRANCH", "PRIVATE"];

const REQUIRED_NOTIFICATION_CHANNELS = ["IN_APP", "EMAIL", "PUSH", "WEBHOOK"];

const REQUIRED_NOTIFICATION_OUTCOMES = [
  "DELIVERED",
  "DROPPED",
  "RATE_LIMITED",
  "REDACTED",
  "TEMPLATE_MISSING",
  "CHANNEL_DISABLED",
  "RECIPIENT_OPTED_OUT",
];

const REQUIRED_NOTIFICATION_HOOK_KINDS = [
  "COMMENT_CREATED",
  "MENTION",
  "WATCH_TRIGGER",
  "ASSIGNMENT_DUE",
];

const REQUIRED_REDACTION_REASONS = [
  "LIVING_MINOR",
  "DNA_CONSENT_REVOKED",
  "RAW_PII_DETECTED",
  "VISIBILITY_DEMOTED",
  "SUBJECT_REMOVED",
  "CORRECTION_APPLIED",
];

const REQUIRED_MENTION_SENSITIVE_FIELDS = [
  "dnaRawData",
  "dnaMatchId",
  "consentReceipt",
  "livingMarker",
  "visibility",
  "redactedFields",
  "rawEmail",
  "rawPhone",
  "rawSsn",
  "rawPassport",
];

const REQUIRED_INVARIANTS = [
  "COMMENT_FORBIDDEN_SCOPE",
  "COMMENT_BODY_REQUIRED",
  "COMMENT_BODY_TOO_LARGE",
  "COMMENT_EDIT_FORBIDDEN_STATE",
  "COMMENT_DELETE_FORBIDDEN_STATE",
  "COMMENT_REDACTION_REQUIRED_REASON",
  "COMMENT_SENSITIVE_FIELD_MENTIONED",
  "MENTION_FORBIDDEN_TARGET_KIND",
  "MENTION_TARGET_REQUIRED",
  "MENTION_TARGET_NOT_IN_SCOPE",
  "MENTION_TOO_MANY_PER_COMMENT",
  "WATCH_SCOPE_NOT_PERMITTED",
  "WATCH_REAUTHORIZATION_DENIED",
  "WATCH_TRIGGER_NOT_PERMITTED",
  "ASSIGNMENT_ROLE_NOT_PERMITTED",
  "ASSIGNMENT_TARGET_REQUIRED",
  "ASSIGNMENT_TTL_OUT_OF_RANGE",
  "ASSIGNMENT_OPEN_FORBIDDEN_STATE",
  "ACTIVITY_FEED_RAW_PAYLOAD_FORBIDDEN",
  "ACTIVITY_FEED_REPROJECT_REQUIRED",
  "ACTIVITY_FEED_VISIBILITY_DEMOTED",
  "NOTIFICATION_RAW_PAYLOAD_FORBIDDEN",
  "NOTIFICATION_TEMPLATE_REQUIRED",
  "NOTIFICATION_CHANNEL_DISABLED",
  "NOTIFICATION_RECIPIENT_OPTED_OUT",
  "NOTIFICATION_RATE_LIMITED",
  "NOTIFICATION_SENSITIVE_CONTENT_DETECTED",
  "AUDIT_KEY_FORBIDDEN",
];

const REQUIRED_AUDIT_KEYS = [
  "actorPseudoId",
  "correlationId",
  "targetPseudoId",
];

const FORBIDDEN_LITERALS = [
  /password\s*[:=]\s*["']?[A-Za-z0-9!@#$%^&*()_+=\-]{6,}/i,
  /token\s*[:=]\s*["']?[A-Za-z0-9._\-]{20,}/i,
  /secret\s*[:=]\s*["']?[A-Za-z0-9._\-]{12,}/i,
  /jdbc:postgresql:\/\/[^"\s']+:[^"\s']+@/i,
  /AKIA[0-9A-Z]{16}/,
  /-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----/,
];

let violations = 0;

function fail(message) {
  console.error(`[collaboration-comments-activity] ${message}`);
  violations += 1;
}

function loadContract(path) {
  try {
    const raw = readFileSync(path, "utf8");
    return { raw, parsed: parseYamlLoose(raw) };
  } catch (err) {
    fail(`cannot read ${relative(ROOT, path)}: ${err.message}`);
    return null;
  }
}

function parseYamlLoose(raw) {
  const out = {};
  const stack = [{ indent: -1, value: out }];
  const lines = raw.split(/\r?\n/);
  function parseFlowList(text) {
    const inner = text.trim().slice(1, -1).trim();
    if (!inner) return [];
    return inner.split(",").map((s) => stripQuotesValue(s.trim()));
  }
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    if (!line.trim() || line.trim().startsWith("#")) continue;
    const indent = line.match(/^ */)[0].length;
    while (stack.length > 1 && stack[stack.length - 1].indent >= indent) {
      stack.pop();
    }
    const parentTop = stack[stack.length - 1];
    const parent = parentTop.value;
    const trimmed = line.trim();
    if (trimmed.startsWith("- ")) {
      if (!Array.isArray(parentTop.value)) continue;
      const itemRaw = trimmed.slice(2).trim();
      const sub = itemRaw.match(/^([A-Za-z0-9_.\-]+)\s*:\s*(.*)$/);
      if (sub) {
        const obj = {};
        obj[sub[1]] = stripQuotesValue(sub[2]);
        parentTop.value.push(obj);
        stack.push({ indent, value: obj });
      } else {
        parentTop.value.push(stripQuotesValue(itemRaw));
      }
      continue;
    }
    const m = trimmed.match(/^([A-Za-z0-9_.\-]+)\s*:\s*(.*)$/);
    if (!m) continue;
    const key = m[1];
    let rhs = m[2];
    if (rhs === "" || rhs === undefined) {
      const next = [];
      parent[key] = next;
      stack.push({ indent, value: next });
      continue;
    }
    if (rhs.startsWith("[") && rhs.endsWith("]")) {
      parent[key] = parseFlowList(rhs);
      continue;
    }
    if (rhs === "true") {
      parent[key] = true;
      continue;
    }
    if (rhs === "false") {
      parent[key] = false;
      continue;
    }
    const numeric = Number(rhs);
    if (!Number.isNaN(numeric) && rhs.trim() !== "") {
      parent[key] = numeric;
      continue;
    }
    if (rhs.startsWith(">") || rhs.startsWith("|")) {
      // Block scalar — collect subsequent indented lines.
      const blockIndent = indent + 2;
      const collected = [];
      let j = i + 1;
      while (j < lines.length) {
        const nl = lines[j];
        if (!nl.trim()) {
          j += 1;
          continue;
        }
        const nlIndent = nl.match(/^ */)[0].length;
        if (nlIndent < blockIndent) break;
        collected.push(nl.slice(blockIndent));
        j += 1;
      }
      parent[key] = collected.join(" ").trim();
      i = j - 1;
      continue;
    }
    parent[key] = stripQuotesValue(rhs);
  }
  return out;
}

function stripQuotesValue(v) {
  if (v === undefined || v === null) return v;
  v = v.trim();
  if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
    return v.slice(1, -1);
  }
  return v;
}

function requireField(parsed, path) {
  const parts = path.split(".");
  let cur = parsed;
  for (const p of parts) {
    if (cur === undefined || cur === null) {
      fail(`spec.${path} missing (path broken at '${p}')`);
      return undefined;
    }
    cur = cur[p];
  }
  if (cur === undefined || cur === null) {
    fail(`spec.${path} missing`);
    return undefined;
  }
  return cur;
}

function assertString(value, expected, field) {
  if (value !== expected) {
    fail(
      `spec.${field} must equal ${JSON.stringify(expected)}, got ${JSON.stringify(value)}`,
    );
  }
}

function checkClosedSetField(parsed, field, required) {
  const value = requireField(parsed, field);
  if (!Array.isArray(value)) {
    fail(`spec.${field} must be an array`);
    return new Set();
  }
  const set = new Set(value);
  for (const r of required) {
    if (!set.has(r)) {
      fail(`spec.${field} missing required value '${r}'`);
    }
  }
  for (const v of value) {
    if (!required.includes(v)) {
      fail(`spec.${field} contains unexpected value '${v}'`);
    }
  }
  return set;
}

function assertPositiveNumber(value, field) {
  if (typeof value !== "number" || value <= 0) {
    fail(`spec.${field} must be a positive number (got ${JSON.stringify(value)})`);
  }
}

function assertNonNegativeNumber(value, field) {
  if (typeof value !== "number" || value < 0) {
    fail(`spec.${field} must be a non-negative number (got ${JSON.stringify(value)})`);
  }
}

function assertInRange(value, min, max, field) {
  if (typeof value !== "number") {
    fail(`spec.${field} must be a number (got ${JSON.stringify(value)})`);
    return;
  }
  if (value < min || value > max) {
    fail(`spec.${field} must be in [${min}, ${max}] (got ${value})`);
  }
}

function scanForbiddenLiterals(raw, scope) {
  for (const re of FORBIDDEN_LITERALS) {
    if (re.test(raw)) {
      fail(`forbidden literal matched ${re} in ${scope}`);
    }
  }
}

function checkContract(loaded) {
  const { raw, parsed } = loaded;
  if (!parsed || typeof parsed !== "object") {
    fail("contract parsed to empty object");
    return;
  }
  if (parsed.kind !== "CommentsActivityPolicy") {
    fail(`spec.kind must be 'CommentsActivityPolicy' (got '${parsed.kind}')`);
  }
  const spec = parsed.spec;
  if (!spec || typeof spec !== "object") {
    fail("spec must be an object");
    return;
  }
  checkClosedSetField(spec, "commentStatuses", REQUIRED_COMMENT_STATUSES);
  checkClosedSetField(spec, "mentionTargetKinds", REQUIRED_MENTION_TARGET_KINDS);
  checkClosedSetField(spec, "watchScopes", REQUIRED_WATCH_SCOPES);
  checkClosedSetField(spec, "watchTriggers", REQUIRED_WATCH_TRIGGERS);
  checkClosedSetField(spec, "assignmentRoles", REQUIRED_ASSIGNMENT_ROLES);
  checkClosedSetField(spec, "assignmentStatuses", REQUIRED_ASSIGNMENT_STATUSES);
  checkClosedSetField(spec, "activityKinds", REQUIRED_ACTIVITY_KINDS);
  checkClosedSetField(spec, "activityVisibilities", REQUIRED_ACTIVITY_VISIBILITIES);
  checkClosedSetField(spec, "notificationChannels", REQUIRED_NOTIFICATION_CHANNELS);
  checkClosedSetField(spec, "notificationOutcomes", REQUIRED_NOTIFICATION_OUTCOMES);
  checkClosedSetField(spec, "notificationHookKinds", REQUIRED_NOTIFICATION_HOOK_KINDS);
  checkClosedSetField(spec, "redactionReasons", REQUIRED_REDACTION_REASONS);

  // Guard rails
  assertString(requireField(spec, "mentionSensitiveFieldGuardRail"), true, "mentionSensitiveFieldGuardRail");
  checkClosedSetField(spec, "mentionSensitiveFields", REQUIRED_MENTION_SENSITIVE_FIELDS);
  assertString(requireField(spec, "watchReAuthorizationRequiredOnVisibilityChange"), true, "watchReAuthorizationRequiredOnVisibilityChange");
  assertString(requireField(spec, "watchReAuthorizationRequiredOnProposalStatus"), true, "watchReAuthorizationRequiredOnProposalStatus");
  checkClosedSetField(spec, "assignmentRolesAllowed", REQUIRED_ASSIGNMENT_ROLES);
  assertString(requireField(spec, "activityFeedAlwaysReproject"), true, "activityFeedAlwaysReproject");
  assertString(requireField(spec, "activityFeedSnapshotRawPayloadAllowed"), false, "activityFeedSnapshotRawPayloadAllowed");
  assertString(requireField(spec, "activityFeedRedactedFieldMarker"), "[REDACTED]", "activityFeedRedactedFieldMarker");
  assertString(requireField(spec, "notificationHookNeverCopyRawPayload"), true, "notificationHookNeverCopyRawPayload");
  assertString(requireField(spec, "notificationHookTemplateRequired"), true, "notificationHookTemplateRequired");
  checkClosedSetField(spec, "notificationHookAllowedReasons", REQUIRED_NOTIFICATION_HOOK_KINDS);

  // Numeric bounds
  assertInRange(requireField(spec, "maxCommentBodyLength"), 1, 65536, "maxCommentBodyLength");
  assertInRange(requireField(spec, "maxCommentEditTrailLength"), 0, 1024, "maxCommentEditTrailLength");
  assertInRange(requireField(spec, "maxMentionsPerComment"), 1, 256, "maxMentionsPerComment");
  assertInRange(requireField(spec, "maxWatchScopesPerUser"), 1, 4096, "maxWatchScopesPerUser");
  assertInRange(requireField(spec, "maxAssignmentScopesPerUser"), 1, 4096, "maxAssignmentScopesPerUser");
  assertInRange(requireField(spec, "maxActivityFeedItemsPerPage"), 1, 500, "maxActivityFeedItemsPerPage");
  assertInRange(requireField(spec, "maxActivityFeedPageWindows"), 1, 168, "maxActivityFeedPageWindows");
  assertInRange(requireField(spec, "maxNotificationHookPayloadBytes"), 256, 65536, "maxNotificationHookPayloadBytes");
  assertInRange(requireField(spec, "maxNotificationHookTemplateKeyLength"), 1, 256, "maxNotificationHookTemplateKeyLength");
  assertInRange(requireField(spec, "maxNotificationHookCorrelationReasonLength"), 16, 1024, "maxNotificationHookCorrelationReasonLength");
  assertInRange(requireField(spec, "maxRedactionReasonCodeLength"), 1, 256, "maxRedactionReasonCodeLength");
  assertInRange(requireField(spec, "maxMentionTargetIdLength"), 1, 256, "maxMentionTargetIdLength");
  assertInRange(requireField(spec, "maxWatchScopeIdLength"), 1, 256, "maxWatchScopeIdLength");
  assertPositiveNumber(requireField(spec, "maxAssignmentDueSeconds"), "maxAssignmentDueSeconds");
  assertPositiveNumber(requireField(spec, "minAssignmentDueSeconds"), "minAssignmentDueSeconds");
  assertInRange(requireField(spec, "maxNotificationHookBatchSize"), 1, 1024, "maxNotificationHookBatchSize");
  assertInRange(requireField(spec, "maxActivityFeedRetentionSeconds"), 86400, 63072000, "maxActivityFeedRetentionSeconds");

  // Audit hooks
  const audit = requireField(spec, "auditRequiredKeys");
  if (!Array.isArray(audit)) {
    fail("spec.auditRequiredKeys must be an array");
  } else {
    for (const k of REQUIRED_AUDIT_KEYS) {
      if (!audit.includes(k)) {
        fail(`spec.auditRequiredKeys missing required key '${k}'`);
      }
    }
  }
  assertString(requireField(spec, "auditRequiredOnCommentCreated"), true, "auditRequiredOnCommentCreated");
  assertString(requireField(spec, "auditRequiredOnCommentEdited"), true, "auditRequiredOnCommentEdited");
  assertString(requireField(spec, "auditRequiredOnCommentRedacted"), true, "auditRequiredOnCommentRedacted");
  assertString(requireField(spec, "auditRequiredOnCommentDeleted"), true, "auditRequiredOnCommentDeleted");
  assertString(requireField(spec, "auditRequiredOnMention"), true, "auditRequiredOnMention");
  assertString(requireField(spec, "auditRequiredOnWatchSubscribed"), true, "auditRequiredOnWatchSubscribed");
  assertString(requireField(spec, "auditRequiredOnAssignmentOpened"), true, "auditRequiredOnAssignmentOpened");
  assertString(requireField(spec, "auditRequiredOnActivityFeedProjected"), true, "auditRequiredOnActivityFeedProjected");
  assertString(requireField(spec, "auditRequiredOnNotification"), true, "auditRequiredOnNotification");

  // Re-authorization
  assertString(requireField(spec, "mentionReAuthorizationRequired"), true, "mentionReAuthorizationRequired");
  assertString(requireField(spec, "watchReAuthorizationRequiredOnTrigger"), true, "watchReAuthorizationRequiredOnTrigger");
  assertString(requireField(spec, "assignmentReAuthorizationRequiredOnAccept"), true, "assignmentReAuthorizationRequiredOnAccept");
  assertString(requireField(spec, "activityFeedReAuthorizationRequiredOnRead"), true, "activityFeedReAuthorizationRequiredOnRead");
  assertString(requireField(spec, "notificationHookReAuthorizationRequiredOnDispatch"), true, "notificationHookReAuthorizationRequiredOnDispatch");
  assertString(requireField(spec, "reAuthorizationDenyClosesSubscription"), true, "reAuthorizationDenyClosesSubscription");
  assertString(requireField(spec, "reAuthorizationAbacDenyClosesSubscription"), true, "reAuthorizationAbacDenyClosesSubscription");

  // Invariants
  const invariants = requireField(spec, "invariants");
  if (!Array.isArray(invariants) || invariants.length === 0) {
    fail("spec.invariants must be a non-empty list");
  } else {
    const set = new Set(invariants);
    for (const code of REQUIRED_INVARIANTS) {
      if (!set.has(code)) {
        fail(`spec.invariants missing required code '${code}'`);
      }
    }
  }

  // Forbidden payload patterns
  const forbidden = requireField(spec, "forbiddenPayloadPatterns");
  if (!Array.isArray(forbidden) || forbidden.length === 0) {
    fail("spec.forbiddenPayloadPatterns must be a non-empty list");
  }

  scanForbiddenLiterals(raw, "comments-activity-policy contract");
}

function checkMirror(contractRaw) {
  let chartRaw;
  try {
    chartRaw = readFileSync(CHART_FILE, "utf8");
  } catch (err) {
    fail(`cannot read ${relative(ROOT, CHART_FILE)}: ${err.message}`);
    return;
  }
  if (chartRaw !== contractRaw) {
    fail(`chart mirror ${relative(ROOT, CHART_FILE)} drifted from contract`);
  }
}

function main() {
  const loaded = loadContract(CONTRACT);
  if (!loaded) {
    process.exit(2);
  }
  checkContract(loaded);
  checkMirror(loaded.raw);
  if (violations === 0) {
    console.log("[collaboration-comments-activity] OK");
    process.exit(0);
  }
  console.error(`[collaboration-comments-activity] ${violations} violation(s)`);
  process.exit(1);
}

main();
