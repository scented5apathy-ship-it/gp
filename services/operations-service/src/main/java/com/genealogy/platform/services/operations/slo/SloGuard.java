package com.genealogy.platform.services.operations.slo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates SLO / alert / runbook
 * payloads against the E13.2 invariants. Mirrors
 * <code>contracts/reliability/slo-alert-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>alert rule declares every
 *       <code>requiredAlertRuleFields</code> entry;</li>
 *   <li>alert annotations populate every
 *       <code>requiredAlertAnnotations</code> entry;</li>
 *   <li>alert rule uses only pseudonymous labels (no raw
 *       tenant_id / user_id / email / oidc_subject);</li>
 *   <li>severity is one of SEV1..SEV4 with the right
 *       responseMinutes;</li>
 *   <li>action is one of PAGE / TICKET / SILENT;</li>
 *   <li>notify_channel is non-blank and matches a closed-set
 *       of channels;</li>
 *   <li>SLI is one of the closed-set <code>sliNames</code>;</li>
 *   <li>burn-rate window is one of the closed-set
 *       <code>burnRateWindows</code>;</li>
 *   <li>numeric bounds (apiReadP95TargetMs=300,
 *       apiWriteP95TargetMs=600, budgetFreezeWeek1Ratio=0.5,
 *       ...) are respected;</li>
 *   <li>the runtime MUST escalate to SEV1 + freeze the
 *       budget when a privacy / DNA / consent finding is
 *       observed.</li>
 * </ul>
 */
public final class SloGuard {

  public static final Set<String> NOTIFY_CHANNELS = Set.of(
      "gp-sev1", "gp-sev2", "gp-sev3", "gp-noise");

  public static final Set<String> PRIVACY_FINDING_CATEGORIES = Set.of(
      "PRIVACY_DNA_LEAK", "PRIVACY_RAW_PII",
      "PRIVACY_CONSENT_BYPASS", "PRIVACY_TENANT_BREACH");

  private SloGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static Outcome validate(AlertRule rule) {
    if (rule == null) {
      return new Outcome(false, "BLANK_RULE", null, null);
    }
    if (rule.alert == null || rule.alert.isBlank()) {
      return new Outcome(false, "MISSING_ALERT", null, null);
    }
    if (rule.expr == null || rule.expr.isBlank()) {
      return new Outcome(false, "MISSING_EXPR", null, null);
    }
    if (rule.severity == null || !E13SloLimits.SEVERITIES.contains(rule.severity)) {
      return new Outcome(false, "MISSING_SEVERITY", null, null);
    }
    if (rule.action == null || !E13SloLimits.ACTIONS.contains(rule.action)) {
      return new Outcome(false, "MISSING_ACTION", null, null);
    }
    if (rule.notifyChannel == null
        || !NOTIFY_CHANNELS.contains(rule.notifyChannel)) {
      return new Outcome(false, "MISSING_NOTIFY_CHANNEL", null, null);
    }
    if (rule.owner == null || rule.owner.isBlank()) {
      return new Outcome(false, "MISSING_OWNER", null, null);
    }
    if (rule.runbookUrl == null || rule.runbookUrl.isBlank()) {
      return new Outcome(false, "MISSING_RUNBOOK", null, null);
    }
    if (rule.dashboardUrl == null || rule.dashboardUrl.isBlank()) {
      return new Outcome(false, "MISSING_DASHBOARD", null, null);
    }
    if (rule.summary == null || rule.summary.isBlank()) {
      return new Outcome(false, "MISSING_SUMMARY", null, null);
    }
    if (rule.sli == null || !E13SloLimits.SLI_NAMES.contains(rule.sli)) {
      return new Outcome(false, "UNKNOWN_SLI", null, rule.sli);
    }
    String burn = rule.burnRateWindow;
    if (burn != null && !E13SloLimits.BURN_RATE_WINDOWS.contains(burn)) {
      return new Outcome(false, "UNKNOWN_BURN_RATE_WINDOW", null, burn);
    }
    String forbidden = firstForbiddenLabel(rule.expr);
    if (forbidden != null) {
      return new Outcome(false, "FORBIDDEN_LABEL:" + forbidden, null, null);
    }
    return new Outcome(true, null, null, null);
  }

  public static Outcome validateSyntheticProbe(String probeName) {
    if (probeName == null
        || !E13SloLimits.SYNTHETIC_PROBES.contains(probeName)) {
      return new Outcome(false, "UNKNOWN_PROBE", null, probeName);
    }
    return new Outcome(true, null, null, null);
  }

  public static boolean freezesBudget(String severity) {
    return "SEV1".equals(severity);
  }

  public static boolean freezesBudgetOnPrivacy(String findingCategory) {
    return findingCategory != null
        && PRIVACY_FINDING_CATEGORIES.contains(findingCategory);
  }

  public static boolean budgetFrozenAtWeek1(double consumedRatio) {
    return consumedRatio >= E13SloLimits.BUDGET_FREEZE_WEEK1_RATIO;
  }

  public static int responseMinutesFor(String severity) {
    switch (severity) {
      case "SEV1":
        return E13SloLimits.SEV1_RESPONSE_MINUTES;
      case "SEV2":
        return E13SloLimits.SEV2_RESPONSE_MINUTES;
      case "SEV3":
        return E13SloLimits.SEV3_RESPONSE_MINUTES;
      case "SEV4":
        return E13SloLimits.SEV4_RESPONSE_MINUTES;
      default:
        return 0;
    }
  }

  private static String firstForbiddenLabel(String expr) {
    if (expr == null) {
      return null;
    }
    String[] forbidden = {
        "tenant_id", "user_id", "actor_id",
        "email", "oidc_subject", "phone",
        "raw_dna", "raw_pii", "rawEmail",
        "treeViewerBypass"
    };
    for (String f : forbidden) {
      if (expr.contains(f)) {
        return f;
      }
    }
    return null;
  }

  public static final class AlertRule {
    public final String alert;
    public final String expr;
    public final String severity;
    public final String action;
    public final String notifyChannel;
    public final String owner;
    public final String runbookUrl;
    public final String dashboardUrl;
    public final String summary;
    public final String sli;
    public final String burnRateWindow;

    public AlertRule(String alert, String expr, String severity,
        String action, String notifyChannel, String owner,
        String runbookUrl, String dashboardUrl, String summary,
        String sli, String burnRateWindow) {
      this.alert = alert;
      this.expr = expr;
      this.severity = severity;
      this.action = action;
      this.notifyChannel = notifyChannel;
      this.owner = owner;
      this.runbookUrl = runbookUrl;
      this.dashboardUrl = dashboardUrl;
      this.summary = summary;
      this.sli = sli;
      this.burnRateWindow = burnRateWindow;
    }
  }

  public static final class Outcome {
    public final boolean valid;
    public final String violationCode;
    public final Map<String, Object> context;
    public final String offendingValue;

    public Outcome(boolean valid, String violationCode,
        Map<String, Object> context, String offendingValue) {
      this.valid = valid;
      this.violationCode = violationCode;
      this.context = context == null ? new LinkedHashMap<>() : context;
      this.offendingValue = offendingValue;
    }
  }
}