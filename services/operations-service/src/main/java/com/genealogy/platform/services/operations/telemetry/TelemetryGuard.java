package com.genealogy.platform.services.operations.telemetry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure orchestrator that validates telemetry payloads against the
 * E13.1 invariants. Mirrors
 * <code>contracts/reliability/telemetry-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>forbidden metric labels / payload keys are rejected
 *       (raw tenant_id / user_id / email / oidc_subject / phone /
 *       raw_dna / raw_pii / treeViewerBypass / ...);</li>
 *   <li>pseudonymous labels MUST carry
 *       <code>tenant_pseudo_id</code> or
 *       <code>actor_pseudo_id</code> for tenant-scoped payloads;</li>
 *   <li>outbox Kafka envelope MUST carry
 *       <code>traceparent</code> + <code>tracestate</code> +
 *       <code>trace_id</code> + <code>tenant_pseudo_id</code> +
 *       <code>actor_pseudo_id</code>;</li>
 *   <li>Temporal search attributes MUST carry only pseudonymous
 *       IDs (no raw email / tenant_id / user_id / raw_dna /
 *       raw_pii);</li>
 *   <li>browser telemetry MUST respect the closed-set
 *       <code>eventWhitelist</code>;</li>
 *   <li>browser telemetry MUST NOT include content / input / DNA
 *       / media capture.</li>
 * </ul>
 *
 * <p>The guard returns one of the closed-set
 * <code>runtimeStateMatrix</code> statuses:
 * <code>ALLOWED</code> / <code>PSEUDONYMIZED</code> /
 * <code>REDACTED</code> / <code>DROPPED</code> / <code>ESCALATED</code>.
 */
public final class TelemetryGuard {

  public static final String STATE_ALLOWED = "ALLOWED";
  public static final String STATE_PSEUDONYMIZED = "PSEUDONYMIZED";
  public static final String STATE_REDACTED = "REDACTED";
  public static final String STATE_DROPPED = "DROPPED";
  public static final String STATE_ESCALATED = "ESCALATED";

  public static final String KIND_TRACE = "trace";
  public static final String KIND_METRIC = "metric";
  public static final String KIND_LOG = "log";
  public static final String KIND_AUDIT = "audit";

  public static final String BROWSER_FLAG_EXPOSURE = "flag_exposure";

  private TelemetryGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static Outcome validate(TelemetryPayload payload) {
    if (payload == null) {
      return new Outcome(STATE_DROPPED, "blank_payload", null);
    }
    Map<String, Object> attributes = payload.attributes == null
        ? Map.of() : payload.attributes;
    String violation = E13ForbiddenTelemetryKeys.firstViolation(attributes);
    if (violation != null) {
      String reason = isAudit(payload) || isCritical(violation)
          ? STATE_ESCALATED
          : STATE_DROPPED;
      return new Outcome(reason, "forbidden_key:" + violation, null);
    }
    boolean tenantScoped = isTenantScoped(payload);
    if (tenantScoped) {
      if (!attributes.containsKey("tenant_pseudo_id")) {
        return new Outcome(STATE_DROPPED, "tenant_pseudo_id_missing", null);
      }
      if (isAudit(payload) && !attributes.containsKey("actor_pseudo_id")) {
        return new Outcome(STATE_ESCALATED, "actor_pseudo_id_missing", null);
      }
    }
    if (KIND_AUDIT.equals(payload.signalKind)) {
      return new Outcome(STATE_ALLOWED, null, null);
    }
    if (KIND_LOG.equals(payload.signalKind)) {
      String reason = requiresPseudonym(payload)
          ? STATE_PSEUDONYMIZED
          : STATE_ALLOWED;
      return new Outcome(reason, null, null);
    }
    return new Outcome(STATE_ALLOWED, null, null);
  }

  public static Outcome validateOutboxEnvelope(OutboxEnvelope envelope) {
    if (envelope == null) {
      return new Outcome(STATE_DROPPED, "blank_envelope", null);
    }
    String[] required = {
        "traceparent", "tracestate", "trace_id", "span_id",
        "correlation_id", "tenant_pseudo_id", "actor_pseudo_id"
    };
    for (String key : required) {
      if (!envelope.fields.containsKey(key)
          || envelope.fields.get(key) == null
          || envelope.fields.get(key).toString().isBlank()) {
        return new Outcome(STATE_DROPPED, "envelope_missing:" + key, null);
      }
    }
    String violation = E13ForbiddenTelemetryKeys.firstViolation(envelope.fields);
    if (violation != null) {
      return new Outcome(STATE_ESCALATED, "envelope_forbidden:" + violation, null);
    }
    return new Outcome(STATE_ALLOWED, null, null);
  }

  public static Outcome validateTemporalSearchAttributes(
      Map<String, Object> attributes) {
    if (attributes == null) {
      return new Outcome(STATE_DROPPED, "blank_search_attributes", null);
    }
    String[] required = {"tenant_pseudo_id", "workflow_pseudo_id",
        "correlation_id", "trace_id"};
    for (String key : required) {
      if (!attributes.containsKey(key)) {
        return new Outcome(STATE_DROPPED, "temporal_missing:" + key, null);
      }
    }
    String[] rejected = {"tenant_id", "user_id", "email", "raw_dna", "raw_pii"};
    for (String key : rejected) {
      if (attributes.containsKey(key)) {
        return new Outcome(STATE_ESCALATED, "temporal_forbidden:" + key, null);
      }
    }
    return new Outcome(STATE_ALLOWED, null, null);
  }

  public static Outcome validateBrowserEvent(BrowserEvent event) {
    if (event == null || event.name == null || event.name.isBlank()) {
      return new Outcome(STATE_DROPPED, "blank_event", null);
    }
    if (!E13BrowserTelemetryWhitelist.isWhitelisted(event.name)) {
      return new Outcome(STATE_DROPPED, "event_not_whitelisted:" + event.name, null);
    }
    Map<String, Object> attributes = event.attributes == null
        ? Map.of() : event.attributes;
    if (E13ForbiddenTelemetryKeys.firstViolation(attributes) != null) {
      return new Outcome(STATE_ESCALATED,
          "browser_forbidden_key", null);
    }
    if (attributes.containsKey("content")
        || attributes.containsKey("inputValue")
        || attributes.containsKey("rawDna")
        || attributes.containsKey("rawMedia")) {
      return new Outcome(STATE_ESCALATED, "browser_capture_forbidden", null);
    }
    return new Outcome(STATE_ALLOWED, null, null);
  }

  private static boolean isAudit(TelemetryPayload p) {
    return KIND_AUDIT.equals(p.signalKind);
  }

  private static boolean isCritical(String key) {
    return "raw_dna".equals(key)
        || "raw_pii".equals(key)
        || "oidc_subject".equals(key)
        || "rawConsentReceipt".equals(key)
        || "rawSignatureBlob".equals(key);
  }

  private static boolean isTenantScoped(TelemetryPayload p) {
    return p.tenantScoped;
  }

  private static boolean requiresPseudonym(TelemetryPayload p) {
    return p.tenantScoped || p.containsIdentityHint;
  }

  public static final class TelemetryPayload {
    public final String signalKind;
    public final boolean tenantScoped;
    public final boolean containsIdentityHint;
    public final Map<String, Object> attributes;

    public TelemetryPayload(String signalKind, boolean tenantScoped,
        boolean containsIdentityHint, Map<String, Object> attributes) {
      this.signalKind = signalKind;
      this.tenantScoped = tenantScoped;
      this.containsIdentityHint = containsIdentityHint;
      this.attributes = attributes;
    }
  }

  public static final class OutboxEnvelope {
    public final Map<String, Object> fields;

    public OutboxEnvelope(Map<String, Object> fields) {
      this.fields = fields == null ? new LinkedHashMap<>() : fields;
    }
  }

  public static final class BrowserEvent {
    public final String name;
    public final Map<String, Object> attributes;

    public BrowserEvent(String name, Map<String, Object> attributes) {
      this.name = name;
      this.attributes = attributes;
    }
  }

  public static final class Outcome {
    public final String state;
    public final String reasonCode;
    public final String redactionSummary;

    public Outcome(String state, String reasonCode, String redactionSummary) {
      this.state = state;
      this.reasonCode = reasonCode;
      this.redactionSummary = redactionSummary;
    }
  }
}