package com.genealogy.platform.services.dna.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.dna.shared.DnaLimits;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DnaIsolationGuardTest {

  private static DnaIsolationGuard.IsolationContext validContext() {
    return new DnaIsolationGuard.IsolationContext(
        "tenant-1",
        "actor-1",
        "corr-1",
        "US-CA",
        Set.of("US-CA", "EU-DE"),
        true,
        "dna.upload",
        Set.of(
            "dna-worker=true",
            "dna-tier=genetic",
            "dna-bucket-bound=true",
            "dna-vault-bound=true"),
        "dna.kits",
        Set.of(
            "dna-read-dek",
            "dna-rotate-dek",
            "dna-issue-data-key",
            "dna-revoke-data-key",
            "dna-bootstrap-envelope"),
        Set.of("dna/raw-kek", "dna/match-kek", "dna/consent-kek"),
        "dna/raw",
        "dna_kit",
        "dna_app_rw",
        Set.of(
            "postgres-dna",
            "vault-agent-dna",
            "s3-dna",
            "openfga-dna",
            "audit-service",
            "kafka-dna",
            "temporal-frontend-dna"),
        Map.of("aggregate-id", "agg-1"),
        false);
  }

  @Test
  void validContextProducesOk() {
    DnaIsolationGuard.IsolationOutcome out = DnaIsolationGuard.validate(validContext());
    assertTrue(out.valid());
    assertNotNull(out.context());
    assertNull(out.failureReason());
  }

  @Test
  void featureFlagDisabledFails() {
    DnaIsolationGuard.IsolationContext ctx = new DnaIsolationGuard.IsolationContext(
        "tenant-1", "actor-1", "corr-1", "US-CA", Set.of("US-CA"), false,
        "dna.upload", Set.of("dna-worker=true"), "dna.kits",
        Set.of("dna-read-dek"), Set.of("dna/raw-kek"), "dna/raw",
        "dna_kit", "dna_app_rw", Set.of("postgres-dna"), Map.of(), false);
    DnaIsolationGuard.IsolationOutcome out = DnaIsolationGuard.validate(ctx);
    assertFalse(out.valid());
    assertEquals("DNA_FEATURE_FLAG_DISABLED", out.failureReason());
  }

  @Test
  void jurisdictionNotInAllowlistFails() {
    DnaIsolationGuard.IsolationContext ctx = new DnaIsolationGuard.IsolationContext(
        "tenant-1", "actor-1", "corr-1", "CN", Set.of("US-CA"), true,
        "dna.upload",
        Set.of("dna-worker=true", "dna-tier=genetic", "dna-bucket-bound=true", "dna-vault-bound=true"),
        "dna.kits",
        Set.of("dna-read-dek", "dna-rotate-dek", "dna-issue-data-key", "dna-revoke-data-key", "dna-bootstrap-envelope"),
        Set.of("dna/raw-kek", "dna/match-kek", "dna/consent-kek"),
        "dna/raw",
        "dna_kit", "dna_app_rw",
        Set.of(
            "postgres-dna", "vault-agent-dna", "s3-dna", "openfga-dna",
            "audit-service", "kafka-dna", "temporal-frontend-dna"),
        Map.of(), false);
    DnaIsolationGuard.IsolationOutcome out = DnaIsolationGuard.validate(ctx);
    assertFalse(out.valid());
    assertEquals("DNA_FEATURE_FLAG_DISABLED", out.failureReason());
  }

  @Test
  void missingTaskQueueFails() {
    DnaIsolationGuard.IsolationContext ctx = new DnaIsolationGuard.IsolationContext(
        "tenant-1", "actor-1", "corr-1", "US-CA", Set.of("US-CA"), true,
        "default",
        Set.of("dna-worker=true", "dna-tier=genetic", "dna-bucket-bound=true", "dna-vault-bound=true"),
        "dna.kits",
        Set.of("dna-read-dek", "dna-rotate-dek", "dna-issue-data-key", "dna-revoke-data-key", "dna-bootstrap-envelope"),
        Set.of("dna/raw-kek", "dna/match-kek", "dna/consent-kek"),
        "dna/raw",
        "dna_kit", "dna_app_rw",
        Set.of(
            "postgres-dna", "vault-agent-dna", "s3-dna", "openfga-dna",
            "audit-service", "kafka-dna", "temporal-frontend-dna"),
        Map.of(), false);
    DnaIsolationGuard.IsolationOutcome out = DnaIsolationGuard.validate(ctx);
    assertFalse(out.valid());
    assertEquals("DNA_TASK_QUEUE_FORBIDDEN", out.failureReason());
  }

  @Test
  void missingNodePoolLabelFails() {
    DnaIsolationGuard.IsolationContext ctx = new DnaIsolationGuard.IsolationContext(
        "tenant-1", "actor-1", "corr-1", "US-CA", Set.of("US-CA"), true,
        "dna.upload",
        Set.of("dna-worker=true"),
        "dna.kits",
        Set.of("dna-read-dek", "dna-rotate-dek", "dna-issue-data-key", "dna-revoke-data-key", "dna-bootstrap-envelope"),
        Set.of("dna/raw-kek", "dna/match-kek", "dna/consent-kek"),
        "dna/raw",
        "dna_kit", "dna_app_rw",
        Set.of(
            "postgres-dna", "vault-agent-dna", "s3-dna", "openfga-dna",
            "audit-service", "kafka-dna", "temporal-frontend-dna"),
        Map.of(), false);
    DnaIsolationGuard.IsolationOutcome out = DnaIsolationGuard.validate(ctx);
    assertFalse(out.valid());
    assertEquals("DNA_NODE_POOL_LABEL_MISSING", out.failureReason());
  }

  @Test
  void wrongBucketPrefixFails() {
    DnaIsolationGuard.IsolationContext ctx = new DnaIsolationGuard.IsolationContext(
        "tenant-1", "actor-1", "corr-1", "US-CA", Set.of("US-CA"), true,
        "dna.upload",
        Set.of("dna-worker=true", "dna-tier=genetic", "dna-bucket-bound=true", "dna-vault-bound=true"),
        "dna.kits",
        Set.of("dna-read-dek", "dna-rotate-dek", "dna-issue-data-key", "dna-revoke-data-key", "dna-bootstrap-envelope"),
        Set.of("dna/raw-kek", "dna/match-kek", "dna/consent-kek"),
        "media/preview",
        "dna_kit", "dna_app_rw",
        Set.of(
            "postgres-dna", "vault-agent-dna", "s3-dna", "openfga-dna",
            "audit-service", "kafka-dna", "temporal-frontend-dna"),
        Map.of(), false);
    DnaIsolationGuard.IsolationOutcome out = DnaIsolationGuard.validate(ctx);
    assertFalse(out.valid());
    assertEquals("DNA_BUCKET_PREFIX_FORBIDDEN", out.failureReason());
  }

  @Test
  void rawDnaPayloadFails() {
    DnaIsolationGuard.IsolationContext ctx = new DnaIsolationGuard.IsolationContext(
        "tenant-1", "actor-1", "corr-1", "US-CA", Set.of("US-CA"), true,
        "dna.upload",
        Set.of("dna-worker=true", "dna-tier=genetic", "dna-bucket-bound=true", "dna-vault-bound=true"),
        "dna.kits",
        Set.of("dna-read-dek", "dna-rotate-dek", "dna-issue-data-key", "dna-revoke-data-key", "dna-bootstrap-envelope"),
        Set.of("dna/raw-kek", "dna/match-kek", "dna/consent-kek"),
        "dna/raw",
        "dna_kit", "dna_app_rw",
        Set.of(
            "postgres-dna", "vault-agent-dna", "s3-dna", "openfga-dna",
            "audit-service", "kafka-dna", "temporal-frontend-dna"),
        Map.of("rawDnaSequence", "ACGT"), false);
    DnaIsolationGuard.IsolationOutcome out = DnaIsolationGuard.validate(ctx);
    assertFalse(out.valid());
    assertEquals("DNA_BUCKET_POLICY_VIOLATION", out.failureReason());
  }

  @Test
  void treeViewerBypassPayloadFails() {
    DnaIsolationGuard.IsolationContext ctx = new DnaIsolationGuard.IsolationContext(
        "tenant-1", "actor-1", "corr-1", "US-CA", Set.of("US-CA"), true,
        "dna.upload",
        Set.of("dna-worker=true", "dna-tier=genetic", "dna-bucket-bound=true", "dna-vault-bound=true"),
        "dna.kits",
        Set.of("dna-read-dek", "dna-rotate-dek", "dna-issue-data-key", "dna-revoke-data-key", "dna-bootstrap-envelope"),
        Set.of("dna/raw-kek", "dna/match-kek", "dna/consent-kek"),
        "dna/raw",
        "dna_kit", "dna_app_rw",
        Set.of(
            "postgres-dna", "vault-agent-dna", "s3-dna", "openfga-dna",
            "audit-service", "kafka-dna", "temporal-frontend-dna"),
        Map.of("treeViewerBypass", "true"), false);
    DnaIsolationGuard.IsolationOutcome out = DnaIsolationGuard.validate(ctx);
    assertFalse(out.valid());
    assertEquals("DNA_TREE_ROLE_BYPASS_DENIED", out.failureReason());
  }

  @Test
  void blankTenantFails() {
    DnaIsolationGuard.IsolationContext ctx = new DnaIsolationGuard.IsolationContext(
        "", "actor-1", "corr-1", "US-CA", Set.of("US-CA"), true,
        "dna.upload",
        Set.of("dna-worker=true", "dna-tier=genetic", "dna-bucket-bound=true", "dna-vault-bound=true"),
        "dna.kits",
        Set.of("dna-read-dek", "dna-rotate-dek", "dna-issue-data-key", "dna-revoke-data-key", "dna-bootstrap-envelope"),
        Set.of("dna/raw-kek", "dna/match-kek", "dna/consent-kek"),
        "dna/raw",
        "dna_kit", "dna_app_rw",
        Set.of(
            "postgres-dna", "vault-agent-dna", "s3-dna", "openfga-dna",
            "audit-service", "kafka-dna", "temporal-frontend-dna"),
        Map.of(), false);
    DnaIsolationGuard.IsolationOutcome out = DnaIsolationGuard.validate(ctx);
    assertFalse(out.valid());
    assertEquals("DNA_TENANT_MISMATCH", out.failureReason());
  }

  @Test
  void dnaLimitsMatchContract() {
    assertEquals(90, DnaLimits.KMS_KEY_ROTATION_INTERVAL_DAYS);
    assertEquals(86_400, DnaLimits.VAULT_POLICY_AUDIT_INTERVAL_SECONDS);
    assertEquals(300, DnaLimits.OPENFGA_TUPLE_CACHE_TTL_SECONDS);
    assertEquals(16, DnaLimits.ISOLATION_GUARD_MAX_EVALUATIONS_PER_REQUEST);
  }

  @Test
  void classCannotBeInstantiated() {
    assertThrows(UnsupportedOperationException.class,
        () -> {
          java.lang.reflect.Constructor<DnaIsolationGuard> ctor =
              DnaIsolationGuard.class.getDeclaredConstructor();
          ctor.setAccessible(true);
          try {
            ctor.newInstance();
          } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof UnsupportedOperationException uoe) {
              throw uoe;
            }
            throw e;
          }
        });
  }
}