package com.genealogy.platform.services.operations.slo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.operations.slo.SloGuard.AlertRule;
import com.genealogy.platform.services.operations.slo.SloGuard.Outcome;
import org.junit.jupiter.api.Test;

class SloGuardTest {

  private static AlertRule happyPath() {
    return new AlertRule(
        "ApiReadP95Burn1h",
        "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket"
            + "{service=\"genealogy-platform\",method=\"GET\"}[1h])) by (le, route)) > 0.3",
        "SEV2",
        "PAGE",
        "gp-sev2",
        "sre-primary",
        "runbook/slo.md#sev2",
        "grafana/dashboards/api-overview.json",
        "Read p95 > 300 ms for 5m",
        "api_read_p95",
        "1h");
  }

  @Test
  void happyPathIsValid() {
    Outcome out = SloGuard.validate(happyPath());
    assertTrue(out.valid);
    assertEquals(null, out.violationCode);
  }

  @Test
  void missingSeverityIsRejected() {
    AlertRule rule = new AlertRule(
        "X", "rate(foo[1m]) > 0", null,
        "PAGE", "gp-sev2", "sre-primary",
        "runbook/slo.md", "grafana/dashboards/api.json",
        "summary", "api_read_p95", "1h");
    Outcome out = SloGuard.validate(rule);
    assertFalse(out.valid);
    assertEquals("MISSING_SEVERITY", out.violationCode);
  }

  @Test
  void unknownSeverityIsRejected() {
    AlertRule rule = new AlertRule(
        "X", "rate(foo[1m]) > 0", "SEV9",
        "PAGE", "gp-sev2", "sre-primary",
        "runbook/slo.md", "grafana/dashboards/api.json",
        "summary", "api_read_p95", "1h");
    Outcome out = SloGuard.validate(rule);
    assertFalse(out.valid);
    assertEquals("MISSING_SEVERITY", out.violationCode);
  }

  @Test
  void missingActionIsRejected() {
    AlertRule rule = new AlertRule(
        "X", "rate(foo[1m]) > 0", "SEV2",
        "WARN", "gp-sev2", "sre-primary",
        "runbook/slo.md", "grafana/dashboards/api.json",
        "summary", "api_read_p95", "1h");
    Outcome out = SloGuard.validate(rule);
    assertFalse(out.valid);
    assertEquals("MISSING_ACTION", out.violationCode);
  }

  @Test
  void forbiddenLabelIsRejected() {
    AlertRule rule = new AlertRule(
        "X",
        "sum(http_server_requests_seconds_count{tenant_id=\"abc\"}) > 0",
        "SEV2", "PAGE", "gp-sev2", "sre-primary",
        "runbook/slo.md", "grafana/dashboards/api.json",
        "summary", "api_read_p95", "1h");
    Outcome out = SloGuard.validate(rule);
    assertFalse(out.valid);
    assertNotNull(out.violationCode);
    assertTrue(out.violationCode.contains("tenant_id"));
  }

  @Test
  void unknownSliIsRejected() {
    AlertRule rule = new AlertRule(
        "X", "rate(foo[1m]) > 0", "SEV2",
        "PAGE", "gp-sev2", "sre-primary",
        "runbook/slo.md", "grafana/dashboards/api.json",
        "summary", "made_up_sli", "1h");
    Outcome out = SloGuard.validate(rule);
    assertFalse(out.valid);
    assertEquals("UNKNOWN_SLI", out.violationCode);
  }

  @Test
  void unknownBurnRateWindowIsRejected() {
    AlertRule rule = new AlertRule(
        "X", "rate(foo[1m]) > 0", "SEV2",
        "PAGE", "gp-sev2", "sre-primary",
        "runbook/slo.md", "grafana/dashboards/api.json",
        "summary", "api_read_p95", "10y");
    Outcome out = SloGuard.validate(rule);
    assertFalse(out.valid);
    assertEquals("UNKNOWN_BURN_RATE_WINDOW", out.violationCode);
  }

  @Test
  void sev1FreezesBudget() {
    assertTrue(SloGuard.freezesBudget("SEV1"));
    assertFalse(SloGuard.freezesBudget("SEV2"));
  }

  @Test
  void privacyFindingFreezesBudget() {
    assertTrue(SloGuard.freezesBudgetOnPrivacy("PRIVACY_DNA_LEAK"));
    assertTrue(SloGuard.freezesBudgetOnPrivacy("PRIVACY_RAW_PII"));
    assertFalse(SloGuard.freezesBudgetOnPrivacy("BENIGN_LOG"));
  }

  @Test
  void budgetFreezeThresholdIsFiftyPercent() {
    assertTrue(SloGuard.budgetFrozenAtWeek1(0.5));
    assertTrue(SloGuard.budgetFrozenAtWeek1(0.75));
    assertFalse(SloGuard.budgetFrozenAtWeek1(0.4));
  }

  @Test
  void responseMinutesMatchSpec() {
    assertEquals(15, SloGuard.responseMinutesFor("SEV1"));
    assertEquals(30, SloGuard.responseMinutesFor("SEV2"));
    assertEquals(240, SloGuard.responseMinutesFor("SEV3"));
    assertEquals(1440, SloGuard.responseMinutesFor("SEV4"));
  }

  @Test
  void syntheticProbeIsValidated() {
    assertTrue(SloGuard.validateSyntheticProbe("kong_health").valid);
    assertFalse(SloGuard.validateSyntheticProbe("made_up").valid);
  }
}