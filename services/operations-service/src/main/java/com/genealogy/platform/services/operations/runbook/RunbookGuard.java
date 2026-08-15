package com.genealogy.platform.services.operations.runbook;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates operator runbook
 * procedures against the E14.5 invariants. Mirrors
 * <code>contracts/disaster-recovery/operator-runbook-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>procedure name is one of the 8 closed-set
 *       entries;</li>
 *   <li>owner role is bound to the closed-set of 8
 *       roles;</li>
 *   <li>severity is one of SEV1..SEV4;</li>
 *   <li>lastReviewedAt is a non-blank date string within
 *       the review cadence (≤ 90 days);</li>
 *   <li>evidenceAnchor lives under
 *       <code>.kiro/specs/genealogy-platform/evidence/</code>;</li>
 *   <li>runbookPath lives under
 *       <code>runbook/</code>;</li>
 *   <li>redactionRequirements covers ≥ 3 entries from the
 *       closed-set of 10 redactions;</li>
 *   <li>support bundle collector applies every closed-set
 *       redaction rule;</li>
 *   <li>shared-responsibility matrix binds each area to
 *       customer_managed or platform_managed;</li>
 *   <li>runbook state transitions respect the 5-status
 *       matrix (terminal: SUPERSEDED).</li>
 * </ul>
 */
public final class RunbookGuard {

  public static final String STATE_OK = "OK";
  public static final String STATE_OVER_LIMIT = "OVER_LIMIT";
  public static final String STATE_FORBIDDEN = "FORBIDDEN";
  public static final String STATE_INVALID = "INVALID";
  public static final String STATE_STALE = "STALE";

  public static final String STATUS_DRAFT = "DRAFT";
  public static final String STATUS_REVIEW = "REVIEW";
  public static final String STATUS_PUBLISHED = "PUBLISHED";
  public static final String STATUS_STALE = "STALE";
  public static final String STATUS_SUPERSEDED = "SUPERSEDED";

  private RunbookGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static Outcome validateProcedure(Procedure p) {
    if (p == null) {
      return new Outcome(STATE_INVALID, "BLANK_PROCEDURE", null, null);
    }
    if (p.name == null
        || !E14RunbookLimits.MANDATORY_PROCEDURES.contains(p.name)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_PROCEDURE",
          null, p.name);
    }
    if (p.owner == null
        || !E14RunbookLimits.OWNER_ROLES.contains(p.owner)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_OWNER",
          Map.of("procedure", p.name), p.owner);
    }
    if (p.severity == null
        || !E14RunbookLimits.SEVERITIES.contains(p.severity)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_SEVERITY",
          Map.of("procedure", p.name), p.severity);
    }
    if (p.lastReviewedAt == null || p.lastReviewedAt.isBlank()) {
      return new Outcome(STATE_INVALID, "BLANK_LAST_REVIEWED_AT",
          Map.of("procedure", p.name), null);
    }
    if (p.daysSinceReview
        > E14RunbookLimits.RUNBOOK_REVIEW_CADENCE_DAYS) {
      return new Outcome(STATE_STALE, "RUNBOOK_REVIEW_OVERDUE",
          Map.of("procedure", p.name,
              "cadenceDays",
              E14RunbookLimits.RUNBOOK_REVIEW_CADENCE_DAYS,
              "actualDays", p.daysSinceReview),
          p.name);
    }
    if (p.evidenceAnchor == null
        || !p.evidenceAnchor.startsWith(
            ".kiro/specs/genealogy-platform/evidence/")) {
      return new Outcome(STATE_INVALID, "EVIDENCE_ANCHOR_INVALID",
          Map.of("procedure", p.name), p.evidenceAnchor);
    }
    if (p.runbookPath == null
        || !p.runbookPath.startsWith("runbook/")) {
      return new Outcome(STATE_INVALID, "RUNBOOK_PATH_INVALID",
          Map.of("procedure", p.name), p.runbookPath);
    }
    if (p.redactionRequirements == null
        || p.redactionRequirements.size()
            < E14RunbookLimits.MIN_REDACTION_RULES_PER_PROCEDURE) {
      return new Outcome(STATE_OVER_LIMIT,
          "REDACTION_RULES_BELOW_MINIMUM",
          Map.of("procedure", p.name,
              "min",
              E14RunbookLimits.MIN_REDACTION_RULES_PER_PROCEDURE,
              "actual",
              p.redactionRequirements == null ? 0
                  : p.redactionRequirements.size()),
          p.name);
    }
    for (String r : p.redactionRequirements) {
      if (!E14RunbookLimits.REDACTIONS.contains(r)) {
        return new Outcome(STATE_INVALID, "UNKNOWN_REDACTION_RULE",
            Map.of("procedure", p.name), r);
      }
    }
    return new Outcome(STATE_OK, null, null, p.name);
  }

  public static Outcome validateSupportBundle(java.util.Set<String> applied) {
    if (applied == null
        || !applied.containsAll(E14RunbookLimits.REDACTIONS)) {
      java.util.Set<String> missing = new java.util.LinkedHashSet<>(
          E14RunbookLimits.REDACTIONS);
      if (applied != null) missing.removeAll(applied);
      return new Outcome(STATE_FORBIDDEN, "SUPPORT_BUNDLE_REDACTION_MISSING",
          null, String.join(",", missing));
    }
    return new Outcome(STATE_OK, null, null, null);
  }

  public static Outcome validateSharedResponsibility(
      String area, String owner) {
    if (area == null
        || !E14RunbookLimits.RESPONSIBILITY_AREAS.contains(area)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_AREA", null, area);
    }
    if (owner == null
        || !E14RunbookLimits.RESPONSIBILITY_OWNERS.contains(owner)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_RESPONSIBILITY_OWNER",
          Map.of("area", area), owner);
    }
    return new Outcome(STATE_OK, null, null, area + ":" + owner);
  }

  public static Outcome validateSupportChannel(String channel) {
    if (channel == null
        || !E14RunbookLimits.SUPPORT_CHANNELS.contains(channel)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_SUPPORT_CHANNEL",
          null, channel);
    }
    return new Outcome(STATE_OK, null, null, channel);
  }

  public static Outcome validateBundleSize(long bytes) {
    long max = (long) E14RunbookLimits.MAX_SUPPORT_BUNDLE_SIZE_GB
        * 1024L * 1024L * 1024L;
    if (bytes <= 0 || bytes > max) {
      return new Outcome(STATE_OVER_LIMIT, "SUPPORT_BUNDLE_SIZE_OVER_LIMIT",
          Map.of("maxBytes", max, "actualBytes", bytes),
          String.valueOf(bytes));
    }
    return new Outcome(STATE_OK, null, null, String.valueOf(bytes));
  }

  public static Outcome validateTransition(String from, String to) {
    Set<String> valid = E14RunbookLimits.RUNBOOK_STATUSES;
    if (from == null || !valid.contains(from)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_FROM", null, from);
    }
    if (to == null || !valid.contains(to)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_TO", null, to);
    }
    Map<String, Set<String>> allowed = Map.of(
        STATUS_DRAFT, Set.of(STATUS_REVIEW, STATUS_SUPERSEDED),
        STATUS_REVIEW, Set.of(STATUS_PUBLISHED, STATUS_DRAFT,
            STATUS_SUPERSEDED),
        STATUS_PUBLISHED, Set.of(STATUS_STALE, STATUS_SUPERSEDED),
        STATUS_STALE, Set.of(STATUS_REVIEW, STATUS_SUPERSEDED),
        STATUS_SUPERSEDED, Set.of());
    Set<String> fromAllowed = allowed.get(from);
    if (fromAllowed == null || !fromAllowed.contains(to)) {
      return new Outcome(STATE_INVALID,
          "INVALID_TRANSITION:" + from + "->" + to,
          Map.of("from", from, "to", to), to);
    }
    return new Outcome(STATE_OK, null, null, to);
  }

  public static final class Procedure {
    public final String name;
    public final String owner;
    public final String severity;
    public final String lastReviewedAt;
    public final int daysSinceReview;
    public final String evidenceAnchor;
    public final String runbookPath;
    public final List<String> redactionRequirements;

    public Procedure(String name, String owner, String severity,
        String lastReviewedAt, int daysSinceReview,
        String evidenceAnchor, String runbookPath,
        List<String> redactionRequirements) {
      this.name = name;
      this.owner = owner;
      this.severity = severity;
      this.lastReviewedAt = lastReviewedAt;
      this.daysSinceReview = daysSinceReview;
      this.evidenceAnchor = evidenceAnchor;
      this.runbookPath = runbookPath;
      this.redactionRequirements = redactionRequirements;
    }
  }

  public static final class Outcome {
    public final String state;
    public final String violationCode;
    public final Map<String, ?> context;
    public final String offendingValue;

    public Outcome(String state, String violationCode,
        Map<String, ?> context, String offendingValue) {
      this.state = state;
      this.violationCode = violationCode;
      this.context = context == null ? new LinkedHashMap<>() : context;
      this.offendingValue = offendingValue;
    }
  }
}