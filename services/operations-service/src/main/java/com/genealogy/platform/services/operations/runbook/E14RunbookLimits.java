package com.genealogy.platform.services.operations.runbook;

import java.util.Set;

/**
 * Closed-set + numeric catalogue for the E14.5 operator
 * runbook contract. Mirrors
 * <code>contracts/disaster-recovery/operator-runbook-policy.yaml</code>.
 */
public final class E14RunbookLimits {

  public static final Set<String> MANDATORY_PROCEDURES = Set.of(
      "install", "configuration", "scaling", "backup", "restore",
      "key_rotation", "troubleshooting", "support_bundle");

  public static final Set<String> OWNER_ROLES = Set.of(
      "sre_primary", "sre_secondary", "platform_sre",
      "security_engineer", "dpo_delegate", "product_owner",
      "finance_ops", "customer_success");

  public static final Set<String> SEVERITIES = Set.of(
      "SEV1", "SEV2", "SEV3", "SEV4");

  public static final Set<String> SUPPORT_CHANNELS = Set.of(
      "portal", "email", "phone_sev1", "phone_sev2", "chat_secure");

  public static final Set<String> ONCALL_ROTATIONS = Set.of(
      "sre_primary_24x7", "sre_secondary_24x7",
      "security_engineer_business_hours",
      "dpo_delegate_business_hours",
      "product_owner_business_hours");

  public static final Set<String> REDACTIONS = Set.of(
      "redact_secrets", "redact_pii", "redact_dna",
      "redact_raw_payloads", "redact_jwt",
      "redact_session_cookie", "redact_oauth_client_secret",
      "redact_audit_stream", "redact_consent_receipt",
      "redact_tree_viewer_bypass");

  public static final Set<String> RESPONSIBILITY_AREAS = Set.of(
      "kubernetes_cluster", "postgres_database", "kafka_cluster",
      "object_storage", "keycloak_realm", "openfga_store",
      "temporal_namespace", "vault_kv", "flagsmith_environment",
      "tls_certificates", "dns_records", "on_call_rotation",
      "upgrade_testing");

  public static final Set<String> RESPONSIBILITY_OWNERS = Set.of(
      "customer_managed", "platform_managed");

  public static final Set<String> RUNBOOK_STATUSES = Set.of(
      "DRAFT", "REVIEW", "PUBLISHED", "STALE", "SUPERSEDED");

  public static final int RUNBOOK_REVIEW_CADENCE_DAYS = 90;
  public static final int SUPPORT_BUNDLE_RETENTION_DAYS = 90;
  public static final int MAX_SUPPORT_BUNDLE_SIZE_GB = 2;
  public static final int MIN_REDACTION_RULES_PER_PROCEDURE = 3;
  public static final int MIN_PROCEDURES_DOCUMENTED = 8;
  public static final int MAX_PROCEDURES_DOCUMENTED = 16;
  public static final int SHARED_RESPONSIBILITY_AREAS_MIN = 8;
  public static final int RUNBOOK_SLA_MINUTES_SEV1 = 15;
  public static final int RUNBOOK_SLA_MINUTES_SEV2 = 60;
  public static final int RUNBOOK_SLA_MINUTES_SEV3 = 240;
  public static final int RUNBOOK_SLA_MINUTES_SEV4 = 1440;
  public static final int ONCALL_PRIMARY_SHIFT_HOURS = 12;

  private E14RunbookLimits() {
    throw new UnsupportedOperationException("constants holder");
  }
}