package com.genealogy.platform.services.dna.isolation;

import com.genealogy.platform.services.dna.shared.DnaForbiddenPayloadKeys;
import com.genealogy.platform.services.dna.shared.DnaLimits;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates DNA isolation invariants from
 * E10.2. Domain-service code is the source of truth; this class is
 * the contract-side guardrail. Each guard corresponds to one of the
 * closed-set failure reasons declared in the E10.2 contract.
 */
public final class DnaIsolationGuard {

  public static final List<String> REQUIRED_BUCKET_PREFIXES =
      List.of("dna/raw", "dna/match", "dna/consent");

  public static final List<String> REQUIRED_OPENFGA_NAMESPACES =
      List.of("dna.kits", "dna.consent", "dna.match", "dna.segment", "dna.research");

  public static final List<String> REQUIRED_NODE_POOL_LABELS =
      List.of(
          "dna-worker=true",
          "dna-tier=genetic",
          "dna-bucket-bound=true",
          "dna-vault-bound=true");

  public static final List<String> REQUIRED_TASK_QUEUES =
      List.of("dna.upload", "dna.match", "dna.revoke", "dna.export");

  public static final List<String> REQUIRED_VAULT_POLICIES =
      List.of(
          "dna-read-dek",
          "dna-rotate-dek",
          "dna-issue-data-key",
          "dna-revoke-data-key",
          "dna-bootstrap-envelope");

  public static final List<String> REQUIRED_KMS_KEY_RINGS =
      List.of("dna/raw-kek", "dna/match-kek", "dna/consent-kek");

  private static final Set<String> TREE_ROLE_BYPASS_KEYS =
      Set.of("tree.viewer", "tree.editor", "tree.contributor", "tree.co_owner", "tree.public_viewer");

  private DnaIsolationGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  /**
   * Validates the isolation context for a DNA workflow / activity.
   * Mirrors <code>guardRails</code>, <code>sandboxEgressAllowlist</code>
   * and the closed-set vocabularies in E10.2.
   */
  public static IsolationOutcome validate(IsolationContext context) {
    if (context == null) {
      return IsolationOutcome.failed("DNA_FEATURE_FLAG_DISABLED", "context MUST NOT be null");
    }
    if (!context.legalDnaEnabled()) {
      return IsolationOutcome.failed("DNA_FEATURE_FLAG_DISABLED", "legal.dna.enabled MUST be true");
    }
    if (context.tenantPseudoId() == null || context.tenantPseudoId().isBlank()) {
      return IsolationOutcome.failed("DNA_TENANT_MISMATCH", "tenantPseudoId");
    }
    if (context.jurisdictionAllowlist() == null
        || context.jurisdictionAllowlist().isEmpty()) {
      return IsolationOutcome.failed("DNA_FEATURE_FLAG_DISABLED", "jurisdictionAllowlist MUST be set");
    }
    if (context.jurisdiction() == null
        || !context.jurisdictionAllowlist().contains(context.jurisdiction())) {
      return IsolationOutcome.failed("DNA_FEATURE_FLAG_DISABLED",
          "jurisdiction not in allowlist: " + context.jurisdiction());
    }
    if (context.taskQueue() == null || !REQUIRED_TASK_QUEUES.contains(context.taskQueue())) {
      return IsolationOutcome.failed("DNA_TASK_QUEUE_FORBIDDEN",
          "taskQueue MUST be one of " + REQUIRED_TASK_QUEUES);
    }
    if (context.nodePoolLabels() == null
        || !context.nodePoolLabels().containsAll(REQUIRED_NODE_POOL_LABELS)) {
      return IsolationOutcome.failed("DNA_NODE_POOL_LABEL_MISSING",
          "missing required node-pool labels: " + REQUIRED_NODE_POOL_LABELS);
    }
    if (context.openfgaNamespace() == null
        || !REQUIRED_OPENFGA_NAMESPACES.contains(context.openfgaNamespace())) {
      return IsolationOutcome.failed("DNA_OPENFGA_NAMESPACE_FORBIDDEN",
          "openfgaNamespace MUST be one of " + REQUIRED_OPENFGA_NAMESPACES);
    }
    if (context.vaultPolicies() == null
        || !context.vaultPolicies().containsAll(REQUIRED_VAULT_POLICIES)) {
      return IsolationOutcome.failed("DNA_VAULT_POLICY_DENIED",
          "missing required vault policies: " + REQUIRED_VAULT_POLICIES);
    }
    if (context.kmsKeyRings() == null
        || !context.kmsKeyRings().containsAll(REQUIRED_KMS_KEY_RINGS)) {
      return IsolationOutcome.failed("DNA_KMS_KEY_FORBIDDEN",
          "missing required KMS key rings: " + REQUIRED_KMS_KEY_RINGS);
    }
    if (context.bucketPrefix() == null
        || !REQUIRED_BUCKET_PREFIXES.contains(context.bucketPrefix())) {
      return IsolationOutcome.failed("DNA_BUCKET_PREFIX_FORBIDDEN",
          "bucketPrefix MUST be one of " + REQUIRED_BUCKET_PREFIXES);
    }
    if (context.databaseSchema() == null || !context.databaseSchema().startsWith("dna_")) {
      return IsolationOutcome.failed("DNA_DATABASE_SCHEMA_FORBIDDEN",
          "databaseSchema MUST start with dna_");
    }
    if (context.databaseRole() == null || !context.databaseRole().startsWith("dna_")) {
      return IsolationOutcome.failed("DNA_DATABASE_ROLE_FORBIDDEN",
          "databaseRole MUST start with dna_");
    }
    if (context.egressAllowlist() == null
        || !context.egressAllowlist().containsAll(List.of(
            "postgres-dna",
            "vault-agent-dna",
            "s3-dna",
            "openfga-dna",
            "audit-service",
            "kafka-dna",
            "temporal-frontend-dna"))) {
      return IsolationOutcome.failed("DNA_EGRESS_DENIED",
          "egress allowlist MUST include the 7 DNA-sandbox endpoints");
    }
    String forbidden = DnaForbiddenPayloadKeys.firstViolation(context.payload());
    if (forbidden != null) {
      if (forbidden.startsWith("rawDna") || forbidden.equals("rawFastq")
          || forbidden.equals("rawBam") || forbidden.equals("rawVcf")) {
        return IsolationOutcome.failed("DNA_BUCKET_POLICY_VIOLATION", forbidden);
      }
      if (TREE_ROLE_BYPASS_KEYS.contains(forbidden) || forbidden.equals("treeViewerBypass")) {
        return IsolationOutcome.failed("DNA_TREE_ROLE_BYPASS_DENIED", forbidden);
      }
      return IsolationOutcome.failed("DNA_BUCKET_POLICY_VIOLATION", forbidden);
    }
    return IsolationOutcome.ok(context);
  }

  /**
   * Result envelope. Exactly one of {@link #ok()} or
   * <code>failed(reason, detail)</code>.
   */
  public record IsolationOutcome(
      boolean valid, IsolationContext context, String failureReason, String detail) {

    public static IsolationOutcome ok(IsolationContext context) {
      return new IsolationOutcome(true, context, null, null);
    }

    public static IsolationOutcome failed(String reason, String detail) {
      return new IsolationOutcome(false, null, reason, detail);
    }
  }

  /**
   * Pure-data isolation context. Every field MUST be present and
   * pass the closed-set assertion above.
   */
  public record IsolationContext(
      String tenantPseudoId,
      String actorPseudoId,
      String correlationId,
      String jurisdiction,
      Set<String> jurisdictionAllowlist,
      boolean legalDnaEnabled,
      String taskQueue,
      Set<String> nodePoolLabels,
      String openfgaNamespace,
      Set<String> vaultPolicies,
      Set<String> kmsKeyRings,
      String bucketPrefix,
      String databaseSchema,
      String databaseRole,
      Set<String> egressAllowlist,
      Map<String, Object> payload,
      boolean containsSecret) {

    public IsolationContext {
      payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
  }
}