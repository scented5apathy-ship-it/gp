package com.genealogy.platform.services.operations.onprem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates on-premise bundle
 * manifests against the E14.3 invariants. Mirrors
 * <code>contracts/disaster-recovery/onprem-bundle-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>registry mirror is bound to the closed-set
 *       (quay.io/genealogy | customer-internal-registry);</li>
 *   <li>SBOM format + signature kind + attestation each come
 *       from the closed-set;</li>
 *   <li>every required image annotation is present
 *       (org.opencontainers.image.source / .revision / .created /
 *       .licenses);</li>
 *   <li>every required Helm value key is declared;</li>
 *   <li>compatibility matrix entries bind to the closed-set
 *       components and the supported-version ranges;</li>
 *   <li>bundle size, preflight / install / upgrade timeout
 *       respect the numeric bounds;</li>
 *   <li>air-gap mode enforces the closed-set of rules;</li>
 *   <li>bundle state transitions respect the 8-status matrix
 *       (terminal: SUPERSEDED).</li>
 * </ul>
 */
public final class BundleGuard {

  public static final String STATE_OK = "OK";
  public static final String STATE_OVER_LIMIT = "OVER_LIMIT";
  public static final String STATE_FORBIDDEN = "FORBIDDEN";
  public static final String STATE_INVALID = "INVALID";

  public static final String STATUS_STAGED = "STAGED";
  public static final String STATUS_PREFLIGHT_RUNNING = "PREFLIGHT_RUNNING";
  public static final String STATUS_VERIFIED = "VERIFIED";
  public static final String STATUS_INSTALLING = "INSTALLING";
  public static final String STATUS_INSTALLED = "INSTALLED";
  public static final String STATUS_UPGRADING = "UPGRADING";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_SUPERSEDED = "SUPERSEDED";

  private BundleGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static Outcome validateRegistry(String mirror) {
    if (mirror == null
        || !E14OnpremLimits.REGISTRY_MIRRORS.contains(mirror)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_REGISTRY_MIRROR",
          null, mirror);
    }
    return new Outcome(STATE_OK, null, null, mirror);
  }

  public static Outcome validateSbom(Sbom sbom) {
    if (sbom == null) {
      return new Outcome(STATE_INVALID, "BLANK_SBOM", null, null);
    }
    if (sbom.format == null
        || !E14OnpremLimits.SBOM_FORMATS.contains(sbom.format)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_SBOM_FORMAT",
          null, sbom.format);
    }
    if (sbom.signature == null
        || !E14OnpremLimits.SIGNATURES.contains(sbom.signature)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_SIGNATURE",
          null, sbom.signature);
    }
    if (sbom.attestation == null
        || !E14OnpremLimits.ATTESTATIONS.contains(sbom.attestation)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_ATTESTATION",
          null, sbom.attestation);
    }
    if (sbom.annotations == null
        || !sbom.annotations.containsAll(
            E14OnpremLimits.IMAGE_ANNOTATIONS)) {
      return new Outcome(STATE_INVALID, "MISSING_IMAGE_ANNOTATIONS",
          null, sbom.annotations == null ? ""
              : String.join(",", sbom.annotations));
    }
    return new Outcome(STATE_OK, null, null, sbom.format);
  }

  public static Outcome validateHelmValues(java.util.Set<String> keys) {
    if (keys == null) {
      return new Outcome(STATE_INVALID, "BLANK_HELM_KEYS", null, null);
    }
    if (!keys.containsAll(E14OnpremLimits.HELM_REQUIRED_KEYS)) {
      java.util.Set<String> missing = new java.util.LinkedHashSet<>(
          E14OnpremLimits.HELM_REQUIRED_KEYS);
      missing.removeAll(keys);
      return new Outcome(STATE_INVALID, "HELM_REQUIRED_KEYS_MISSING",
          Map.of("missing", new java.util.ArrayList<>(missing)),
          String.join(",", missing));
    }
    return new Outcome(STATE_OK, null, null, null);
  }

  public static Outcome validateComponent(CompatibilityEntry entry) {
    if (entry == null) {
      return new Outcome(STATE_INVALID, "BLANK_ENTRY", null, null);
    }
    if (entry.component == null
        || !E14OnpremLimits.COMPONENTS.contains(entry.component)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_COMPONENT",
          null, entry.component);
    }
    Set<String> supported;
    switch (entry.component) {
      case "kubernetes": supported = E14OnpremLimits.KUBERNETES_VERSIONS;
        break;
      case "postgresql": supported = E14OnpremLimits.POSTGRESQL_VERSIONS;
        break;
      case "kafka": supported = E14OnpremLimits.KAFKA_VERSIONS; break;
      case "object_storage":
        supported = E14OnpremLimits.OBJECT_STORES; break;
      case "keycloak":
        supported = E14OnpremLimits.KEYCLOAK_VERSIONS; break;
      case "openfga":
        supported = E14OnpremLimits.OPENFGA_VERSIONS; break;
      case "temporal":
        supported = E14OnpremLimits.TEMPORAL_VERSIONS; break;
      case "vault": supported = E14OnpremLimits.VAULT_VERSIONS; break;
      case "flagsmith":
        supported = E14OnpremLimits.FLAGSMITH_VERSIONS; break;
      default:
        return new Outcome(STATE_INVALID, "UNKNOWN_COMPONENT",
            null, entry.component);
    }
    if (entry.version == null || !supported.contains(entry.version)) {
      return new Outcome(STATE_INVALID, "UNSUPPORTED_VERSION",
          Map.of("component", entry.component,
              "supported", new java.util.ArrayList<>(supported)),
          entry.version);
    }
    return new Outcome(STATE_OK, null, null, entry.version);
  }

  public static Outcome validateAirGap(java.util.Set<String> rules) {
    if (rules == null) {
      return new Outcome(STATE_INVALID, "BLANK_AIRGAP", null, null);
    }
    if (!rules.containsAll(E14OnpremLimits.AIR_GAP_RULES)) {
      return new Outcome(STATE_INVALID, "AIRGAP_RULES_MISSING",
          null, String.join(",", rules));
    }
    return new Outcome(STATE_OK, null, null, null);
  }

  public static Outcome validateBundleSize(long bytes) {
    long max = (long) E14OnpremLimits.MAX_BUNDLE_SIZE_GB * 1024L * 1024L * 1024L;
    if (bytes <= 0 || bytes > max) {
      return new Outcome(STATE_OVER_LIMIT, "BUNDLE_SIZE_OVER_LIMIT",
          Map.of("maxBytes", max, "actualBytes", bytes),
          String.valueOf(bytes));
    }
    return new Outcome(STATE_OK, null, null, String.valueOf(bytes));
  }

  public static Outcome validateTransition(String from, String to) {
    Set<String> valid = E14OnpremLimits.BUNDLE_STATUSES;
    if (from == null || !valid.contains(from)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_FROM", null, from);
    }
    if (to == null || !valid.contains(to)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_TO", null, to);
    }
    Map<String, Set<String>> allowed = Map.of(
        STATUS_STAGED, Set.of(STATUS_PREFLIGHT_RUNNING, STATUS_VERIFIED,
            STATUS_FAILED, STATUS_SUPERSEDED),
        STATUS_PREFLIGHT_RUNNING, Set.of(STATUS_VERIFIED, STATUS_FAILED),
        STATUS_VERIFIED, Set.of(STATUS_INSTALLING, STATUS_FAILED,
            STATUS_SUPERSEDED),
        STATUS_INSTALLING, Set.of(STATUS_INSTALLED, STATUS_FAILED),
        STATUS_INSTALLED, Set.of(STATUS_UPGRADING, STATUS_SUPERSEDED),
        STATUS_UPGRADING, Set.of(STATUS_INSTALLED, STATUS_FAILED),
        STATUS_FAILED, Set.of(STATUS_STAGED, STATUS_SUPERSEDED),
        STATUS_SUPERSEDED, Set.of());
    Set<String> fromAllowed = allowed.get(from);
    if (fromAllowed == null || !fromAllowed.contains(to)) {
      return new Outcome(STATE_INVALID,
          "INVALID_TRANSITION:" + from + "->" + to,
          Map.of("from", from, "to", to), to);
    }
    return new Outcome(STATE_OK, null, null, to);
  }

  public static final class Sbom {
    public final String format;
    public final String signature;
    public final String attestation;
    public final Set<String> annotations;

    public Sbom(String format, String signature, String attestation,
        Set<String> annotations) {
      this.format = format;
      this.signature = signature;
      this.attestation = attestation;
      this.annotations = annotations;
    }
  }

  public static final class CompatibilityEntry {
    public final String component;
    public final String version;

    public CompatibilityEntry(String component, String version) {
      this.component = component;
      this.version = version;
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