package com.genealogy.platform.services.importexport.transfer;

/**
 * Closed-set workflow kinds supported by the Temporal transfer
 * framework. Mirrors <code>contracts/importexport/temporal-transfer-framework-policy.yaml</code>
 * <code>transferWorkflowKinds</code>.
 */
public enum TransferWorkflowKind {
  IMPORT_GEDCOM,
  IMPORT_PREVIEW,
  IMPORT_DEDUP,
  IMPORT_CONFIRM,
  IMPORT_CHUNK,
  IMPORT_RECONCILE,
  EXPORT_BRANCH,
  EXPORT_FULL,
  EXPORT_REDACTED_PREVIEW,
  PUBLIC_API_REQUEST,
  WEBHOOK_DISPATCH;

  public String wire() {
    return name();
  }

  public static TransferWorkflowKind fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("transferWorkflowKind MUST NOT be null");
    }
    try {
      return TransferWorkflowKind.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "transferWorkflowKind MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}