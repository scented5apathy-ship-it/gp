package com.genealogy.platform.services.operations.onprem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.operations.onprem.BundleGuard.CompatibilityEntry;
import com.genealogy.platform.services.operations.onprem.BundleGuard.Outcome;
import com.genealogy.platform.services.operations.onprem.BundleGuard.Sbom;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BundleGuardTest {

  private Sbom canonicalSbom() {
    Set<String> ann = new LinkedHashSet<>(
        E14OnpremLimits.IMAGE_ANNOTATIONS);
    return new Sbom("cyclonedx_1_5", "cosign", "slsa_provenance_v1", ann);
  }

  @Test
  void quayMirrorIsAccepted() {
    Outcome out = BundleGuard.validateRegistry("quay.io/genealogy");
    assertEquals(BundleGuard.STATE_OK, out.state);
  }

  @Test
  void customerInternalMirrorIsAccepted() {
    Outcome out = BundleGuard.validateRegistry(
        "customer-internal-registry");
    assertEquals(BundleGuard.STATE_OK, out.state);
  }

  @Test
  void adHocRegistryIsRejected() {
    Outcome out = BundleGuard.validateRegistry("docker.io/random");
    assertEquals(BundleGuard.STATE_INVALID, out.state);
  }

  @Test
  void canonicalSbomIsAccepted() {
    Outcome out = BundleGuard.validateSbom(canonicalSbom());
    assertEquals(BundleGuard.STATE_OK, out.state);
  }

  @Test
  void sbomWithoutAnnotationsIsRejected() {
    Outcome out = BundleGuard.validateSbom(
        new Sbom("cyclonedx_1_5", "cosign", "slsa_provenance_v1",
            Set.of("org.opencontainers.image.source")));
    assertEquals(BundleGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("MISSING_IMAGE_ANNOTATIONS"));
  }

  @Test
  void sbomWithoutSignatureIsRejected() {
    Outcome out = BundleGuard.validateSbom(
        new Sbom("cyclonedx_1_5", "gpg", "slsa_provenance_v1",
            E14OnpremLimits.IMAGE_ANNOTATIONS));
    assertEquals(BundleGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("UNKNOWN_SIGNATURE"));
  }

  @Test
  void helmValuesWithAllRequiredKeysIsAccepted() {
    Set<String> keys = new LinkedHashSet<>(
        E14OnpremLimits.HELM_REQUIRED_KEYS);
    keys.addAll(E14OnpremLimits.HELM_OPTIONAL_KEYS);
    Outcome out = BundleGuard.validateHelmValues(keys);
    assertEquals(BundleGuard.STATE_OK, out.state);
  }

  @Test
  void helmValuesMissingKeycloakIssuerIsRejected() {
    Set<String> keys = new LinkedHashSet<>(
        E14OnpremLimits.HELM_REQUIRED_KEYS);
    keys.remove("keycloakIssuerUrl");
    Outcome out = BundleGuard.validateHelmValues(keys);
    assertEquals(BundleGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("HELM_REQUIRED_KEYS_MISSING"));
  }

  @Test
  void allSupportedComponentsAreAccepted() {
    for (String c : E14OnpremLimits.COMPONENTS) {
      String version;
      switch (c) {
        case "kubernetes": version = "1.31"; break;
        case "postgresql": version = "16"; break;
        case "kafka": version = "3.8"; break;
        case "object_storage": version = "minio_2025"; break;
        case "keycloak": version = "26.0"; break;
        case "openfga": version = "1.9"; break;
        case "temporal": version = "1.24"; break;
        case "vault": version = "1.17"; break;
        case "flagsmith": version = "2.5"; break;
        default: throw new IllegalArgumentException(c);
      }
      Outcome out = BundleGuard.validateComponent(
          new CompatibilityEntry(c, version));
      assertEquals(BundleGuard.STATE_OK, out.state,
          () -> c + " " + version + " was " + out.violationCode);
    }
  }

  @Test
  void unsupportedKubernetesVersionIsRejected() {
    Outcome out = BundleGuard.validateComponent(
        new CompatibilityEntry("kubernetes", "1.99"));
    assertEquals(BundleGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("UNSUPPORTED_VERSION"));
  }

  @Test
  void airGapRulesCompleteIsAccepted() {
    Outcome out = BundleGuard.validateAirGap(
        E14OnpremLimits.AIR_GAP_RULES);
    assertEquals(BundleGuard.STATE_OK, out.state);
  }

  @Test
  void airGapRulesMissingNoRuntimeInternetIsRejected() {
    Set<String> rules = new LinkedHashSet<>(
        E14OnpremLimits.AIR_GAP_RULES);
    rules.remove("noRuntimeInternetCall");
    Outcome out = BundleGuard.validateAirGap(rules);
    assertEquals(BundleGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("AIRGAP_RULES_MISSING"));
  }

  @Test
  void bundleSizeWithinLimitIsAccepted() {
    long bytes = 10L * 1024L * 1024L * 1024L;
    Outcome out = BundleGuard.validateBundleSize(bytes);
    assertEquals(BundleGuard.STATE_OK, out.state);
  }

  @Test
  void bundleSizeOverLimitIsRejected() {
    long bytes = 100L * 1024L * 1024L * 1024L;
    Outcome out = BundleGuard.validateBundleSize(bytes);
    assertEquals(BundleGuard.STATE_OVER_LIMIT, out.state);
  }

  @Test
  void transitionStagedToPreflightIsAccepted() {
    Outcome out = BundleGuard.validateTransition(
        BundleGuard.STATUS_STAGED, BundleGuard.STATUS_PREFLIGHT_RUNNING);
    assertEquals(BundleGuard.STATE_OK, out.state);
  }

  @Test
  void transitionSupersededIsTerminal() {
    Outcome ok = BundleGuard.validateTransition(
        BundleGuard.STATUS_INSTALLED, BundleGuard.STATUS_SUPERSEDED);
    assertEquals(BundleGuard.STATE_OK, ok.state);
    Outcome bad = BundleGuard.validateTransition(
        BundleGuard.STATUS_SUPERSEDED, BundleGuard.STATUS_STAGED);
    assertFalse(bad.state.equals(BundleGuard.STATE_OK));
  }
}