/**
 * apps/web/test/telemetry/redaction.test.ts
 *
 * E13.1 browser redaction test suite. Mirrors the contract at
 * `contracts/reliability/telemetry-policy.yaml`. Each test
 * case is referenced by `browserRedactionTests` so the
 * `scripts/lint-telemetry.mjs` linter fails closed if a
 * mandatory scenario is removed.
 *
 * Run with `node ../../scripts/test-ts.mjs "test/telemetry/redaction.test.ts"`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import {
  applyRedactionToString,
  BROWSER_EVENT_WHITELIST,
  createCardinalityTracker,
  DEFAULT_EGRESS_ALLOWLIST,
  FallbackRingBuffer,
  FORBIDDEN_PAYLOAD_KEYS,
  hasTraceparent,
  isEgressAllowed,
  PSEUDONYM_LABELS,
  pseudonymize,
  scrubEvent,
  type ScrubbedEvent,
} from "../../src/lib/telemetry/redaction";

const baseEvent = (overrides: Partial<ScrubbedEvent> = {}): ScrubbedEvent => ({
  name: "route_changed",
  signalKind: "trace",
  attributes: { tenant_pseudo_id: "tp-1" },
  timestamp: Date.now(),
  ...overrides,
});

test("E13.1 browser redaction — email_scrub", () => {
  const { body, hits } = applyRedactionToString(
    "User foo@bar.com requested /v1/trees",
  );
  assert.equal(hits.email, 1);
  assert.ok(!body.includes("foo@bar.com"));
  assert.ok(body.includes("[REDACTED:email]"));
});

test("E13.1 browser redaction — phone_scrub", () => {
  const { body, hits } = applyRedactionToString("Call me at +1 415 555 1234");
  assert.ok((hits.phone ?? 0) >= 1);
  assert.ok(!body.includes("415 555 1234"));
});

test("E13.1 browser redaction — ipv4_scrub", () => {
  const { body, hits } = applyRedactionToString(
    "client ip_v4=10.0.0.42 connected",
  );
  assert.equal(hits.ipv4, 1);
  assert.ok(!body.includes("10.0.0.42"));
});

test("E13.1 browser redaction — jwt_scrub", () => {
  const { body, hits } = applyRedactionToString(
    "Authorization: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
      + "eyJzdWIiOiIxMjM0NTY3ODkwIn0."
      + "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
  );
  assert.equal(hits.jwt, 1);
  assert.ok(!body.includes("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
});

test("E13.1 browser redaction — raw_dna_marker_scrub", () => {
  const { body, hits } = applyRedactionToString(
    "mutation rs123 ACGT observed",
  );
  assert.equal(hits.rawDnaMarker, 1);
  assert.ok(!body.includes("rs123 ACGT"));
});

test("E13.1 browser redaction — forbidden_key_drop", () => {
  const safe = scrubEvent(baseEvent({
    attributes: { tenant_pseudo_id: "tp-1", rawEmail: "leak@example.com" },
  }));
  assert.equal(safe.state, "ALLOWED");
  if (safe.state === "ALLOWED") {
    assert.ok(!("rawEmail" in safe.event.attributes));
  }
  const escalated = scrubEvent(baseEvent({
    attributes: { tenant_pseudo_id: "tp-1", raw_dna: "rs7 AC" },
  }));
  assert.equal(escalated.state, "ESCALATED");
});

test("E13.1 browser redaction — pseudonym_label_present", () => {
  const outcome = scrubEvent({
    name: "route_changed",
    signalKind: "metric",
    attributes: { route: "/x" },
    timestamp: Date.now(),
  });
  assert.equal(outcome.state, "DROPPED");
  if (outcome.state === "DROPPED") {
    assert.equal(outcome.reason, "tenant_pseudo_id_missing");
  }
  assert.equal(
    pseudonymize("tenant-1", "pepper-A"),
    pseudonymize("tenant-1", "pepper-A"),
  );
  assert.notEqual(
    pseudonymize("tenant-1", "pepper-A"),
    pseudonymize("tenant-1", "pepper-B"),
  );
  assert.ok(PSEUDONYM_LABELS.has("tenant_pseudo_id"));
  const tracker = createCardinalityTracker();
  tracker.count("tenant_pseudo_id", "t-0");
  tracker.count("tenant_pseudo_id", "t-0");
  assert.equal(tracker.count("tenant_pseudo_id", "t-0"), 3);
});

test("E13.1 browser redaction — traceparent_propagation", () => {
  assert.ok(hasTraceparent({
    traceparent: "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
  }));
  assert.ok(!hasTraceparent({ traceparent: "garbage" }));
  assert.ok(!hasTraceparent({}));
});

test("E13.1 browser redaction — fallback_ring_buffer (drain)", async () => {
  const buf = new FallbackRingBuffer({
    maxBytes: 200,
    collectorUnreachable: () => false,
    flush: async () => { /* no-op */ },
  });
  buf.push(baseEvent({ attributes: { tenant_pseudo_id: "tp-1", payload: "x".repeat(80) } }));
  buf.push(baseEvent({ attributes: { tenant_pseudo_id: "tp-1", payload: "y".repeat(80) } }));
  assert.ok(buf.size() >= 1);
  const drained = await buf.drain();
  assert.ok(drained >= 1);
  assert.equal(buf.size(), 0);
});

test("E13.1 browser redaction — fallback_ring_buffer (circuit breaker)", async () => {
  let unreachable = true;
  let flushSucceeds = false;
  const buf = new FallbackRingBuffer({
    maxBytes: 1024,
    collectorUnreachable: () => unreachable,
    flush: async () => {
      if (!flushSucceeds) throw new Error("down");
    },
  });
  buf.push(baseEvent());
  for (let i = 0; i < 5; i += 1) {
    await buf.drain();
  }
  assert.equal(buf.isCircuitOpen(), true);
  unreachable = false;
  flushSucceeds = true;
  await buf.drain();
  assert.equal(buf.isCircuitOpen(), false);
});

test("E13.1 browser redaction — closed-set invariants", () => {
  assert.equal(BROWSER_EVENT_WHITELIST.size, 10);
  assert.equal(FORBIDDEN_PAYLOAD_KEYS.size, 28);
  assert.equal(DEFAULT_EGRESS_ALLOWLIST.length, 3);
  assert.ok(isEgressAllowed("otel-collector.gp-observability"));
  assert.ok(!isEgressAllowed("evil.example.com"));
});