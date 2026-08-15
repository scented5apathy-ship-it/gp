package com.genealogy.platform.services.dna.shared;

/**
 * Numeric limits shared by every E10.x orchestrator.
 * Mirrors the numeric bounds declared in the E10.2 — E10.5 contracts.
 * Every limit MUST stay below or equal to the value defined in the
 * matching contract; this class is the single source of truth for
 * Java-side guards (the YAML contract is validated by the
 * <code>scripts/lint-dna-*.mjs</code> linters).
 */
public final class DnaLimits {

  public static final int KMS_KEY_ROTATION_INTERVAL_DAYS = 90;
  public static final int VAULT_POLICY_AUDIT_INTERVAL_SECONDS = 86_400;
  public static final int OPENFGA_TUPLE_CACHE_TTL_SECONDS = 300;
  public static final int OPENFGA_AUTHORIZATION_TIMEOUT_MS = 500;
  public static final int NODE_POOL_ADMISSION_TIMEOUT_SECONDS = 30;
  public static final int TASK_QUEUE_HEARTBEAT_INTERVAL_SECONDS = 30;
  public static final int ISOLATION_GUARD_EVALUATION_TIMEOUT_MS = 250;
  public static final int ISOLATION_GUARD_MAX_EVALUATIONS_PER_REQUEST = 16;
  public static final int LEGAL_JURISDICTION_CHECK_TIMEOUT_MS = 250;

  public static final int CONSENT_RECEIPT_MAX_BYTES = 65_536;
  public static final int CONSENT_RETENTION_SECONDS = 6_307_2000;
  public static final int CONSENT_MIN_VERSION_LENGTH = 1;
  public static final int CONSENT_MAX_VERSION_LENGTH = 32;
  public static final int CONSENT_MAX_PURPOSES_PER_SUBJECT = 16;

  public static final int RAW_UPLOAD_MAX_BYTES = 26_214_400;
  public static final int RAW_UPLOAD_QUARANTINE_TTL_SECONDS = 3_600;
  public static final int RAW_UPLOAD_DECRYPT_TIMEOUT_SECONDS = 300;
  public static final int MATCH_ALGORITHM_MAX_CONCURRENT = 8;
  public static final int MATCH_MAX_KITS_PER_REQUEST = 1_000;
  public static final int MATCH_MAX_SEGMENTS_PER_KIT = 50_000;

  public static final int REVOKE_TERMINATION_GRACE_SECONDS = 60;
  public static final int EXPORT_SIGNED_URL_TTL_SECONDS = 3_600;
  public static final int EXPORT_REVOCATION_PROPAGATION_SECONDS = 30;
  public static final int EXPORT_BUNDLE_MAX_BYTES = 524_288_000;
  public static final int EXPORT_EVIDENCE_MAX_BYTES = 1_048_576;
  public static final int LEGAL_HOLD_MIN_RETENTION_SECONDS = 63_072_000;

  public static final int DNA_BUCKET_ACCESS_FORBIDDEN = 1;
  public static final int SECRET_MAX_BYTES = 0;

  private DnaLimits() {
    throw new UnsupportedOperationException("constants holder");
  }
}