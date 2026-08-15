package com.genealogy.platform.services.operations.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.operations.telemetry.TelemetryGuard.BrowserEvent;
import com.genealogy.platform.services.operations.telemetry.TelemetryGuard.Outcome;
import com.genealogy.platform.services.operations.telemetry.TelemetryGuard.OutboxEnvelope;
import com.genealogy.platform.services.operations.telemetry.TelemetryGuard.TelemetryPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TelemetryGuardTest {

  private static Map<String, Object> map(String... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put(kv[i], kv[i + 1]);
    }
    return m;
  }

  @Test
  void traceWithPseudonymIsAllowed() {
    Outcome out = TelemetryGuard.validate(new TelemetryPayload(
        TelemetryGuard.KIND_TRACE, true, false,
        map("tenant_pseudo_id", "abc", "route", "/x")));
    assertEquals(TelemetryGuard.STATE_ALLOWED, out.state);
    assertNull(out.reasonCode);
  }

  @Test
  void rawEmailForbiddenKeyIsDropped() {
    Outcome out = TelemetryGuard.validate(new TelemetryPayload(
        TelemetryGuard.KIND_LOG, true, false,
        map("tenant_pseudo_id", "abc", "rawEmail", "leak@example.com")));
    assertEquals(TelemetryGuard.STATE_DROPPED, out.state);
    assertNotNull(out.reasonCode);
    assertTrue(out.reasonCode.contains("rawEmail"));
  }

  @Test
  void rawDnaForbiddenKeyIsEscalated() {
    Outcome out = TelemetryGuard.validate(new TelemetryPayload(
        TelemetryGuard.KIND_METRIC, true, false,
        map("tenant_pseudo_id", "abc", "raw_dna", "rs123 ACGT")));
    assertEquals(TelemetryGuard.STATE_ESCALATED, out.state);
    assertTrue(out.reasonCode.contains("raw_dna"));
  }

  @Test
  void tenantScopedWithoutPseudonymIsDropped() {
    Outcome out = TelemetryGuard.validate(new TelemetryPayload(
        TelemetryGuard.KIND_TRACE, true, false,
        map("route", "/x")));
    assertEquals(TelemetryGuard.STATE_DROPPED, out.state);
    assertEquals("tenant_pseudo_id_missing", out.reasonCode);
  }

  @Test
  void auditWithoutActorIsEscalated() {
    Outcome out = TelemetryGuard.validate(new TelemetryPayload(
        TelemetryGuard.KIND_AUDIT, true, false,
        map("tenant_pseudo_id", "abc")));
    assertEquals(TelemetryGuard.STATE_ESCALATED, out.state);
    assertEquals("actor_pseudo_id_missing", out.reasonCode);
  }

  @Test
  void outboxEnvelopeMissingTraceIdIsDropped() {
    Outcome out = TelemetryGuard.validateOutboxEnvelope(new OutboxEnvelope(
        map("traceparent", "v", "tracestate", "v",
            "tenant_pseudo_id", "t", "actor_pseudo_id", "a",
            "correlation_id", "c")));
    assertEquals(TelemetryGuard.STATE_DROPPED, out.state);
    assertEquals("envelope_missing:trace_id", out.reasonCode);
  }

  @Test
  void outboxEnvelopeWithRawEmailIsEscalated() {
    Outcome out = TelemetryGuard.validateOutboxEnvelope(new OutboxEnvelope(
        map("traceparent", "v", "tracestate", "v",
            "trace_id", "t", "span_id", "s",
            "correlation_id", "c",
            "tenant_pseudo_id", "tp", "actor_pseudo_id", "ap",
            "email", "leak@example.com")));
    assertEquals(TelemetryGuard.STATE_ESCALATED, out.state);
    assertTrue(out.reasonCode.contains("email"));
  }

  @Test
  void temporalSearchAttributesRejectsRawEmail() {
    Outcome out = TelemetryGuard.validateTemporalSearchAttributes(map(
        "tenant_pseudo_id", "tp",
        "workflow_pseudo_id", "wp",
        "correlation_id", "c",
        "trace_id", "t",
        "email", "leak@example.com"));
    assertEquals(TelemetryGuard.STATE_ESCALATED, out.state);
    assertEquals("temporal_forbidden:email", out.reasonCode);
  }

  @Test
  void browserEventNotWhitelistedIsDropped() {
    Outcome out = TelemetryGuard.validateBrowserEvent(
        new BrowserEvent("payment_submitted",
            map("tenant_pseudo_id", "t")));
    assertEquals(TelemetryGuard.STATE_DROPPED, out.state);
    assertTrue(out.reasonCode.contains("payment_submitted"));
  }

  @Test
  void browserEventContentCaptureIsEscalated() {
    Outcome out = TelemetryGuard.validateBrowserEvent(
        new BrowserEvent("route_changed",
            map("content", "<html>leak</html>")));
    assertEquals(TelemetryGuard.STATE_ESCALATED, out.state);
    assertEquals("browser_capture_forbidden", out.reasonCode);
  }

  @Test
  void browserFlagExposureIsAllowed() {
    Outcome out = TelemetryGuard.validateBrowserEvent(
        new BrowserEvent("flag_exposure",
            map("flag", "checkout_v2", "tenant_pseudo_id", "t")));
    assertEquals(TelemetryGuard.STATE_ALLOWED, out.state);
  }

  @Test
  void logWithIdentityHintIsPseudonymized() {
    Outcome out = TelemetryGuard.validate(new TelemetryPayload(
        TelemetryGuard.KIND_LOG, true, true,
        map("tenant_pseudo_id", "abc")));
    assertEquals(TelemetryGuard.STATE_PSEUDONYMIZED, out.state);
  }

  @Test
  void metricWithoutTenantIsAllowed() {
    Outcome out = TelemetryGuard.validate(new TelemetryPayload(
        TelemetryGuard.KIND_METRIC, false, false,
        map("route", "/healthz")));
    assertEquals(TelemetryGuard.STATE_ALLOWED, out.state);
  }
}