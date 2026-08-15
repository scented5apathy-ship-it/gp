package com.genealogy.platform.services.importexport.transfer;

/**
 * Closed-set run statuses emitted by the transfer framework state
 * machine. Mirrors
 * <code>contracts/importexport/temporal-transfer-framework-policy.yaml</code>
 * <code>transferStatuses</code>.
 */
public enum TransferStatus {
  QUEUED,
  RUNNING,
  AWAITING_INPUT,
  AWAITING_CONFIRMATION,
  CANCELLED,
  COMPENSATING,
  COMPENSATED,
  COMPLETED,
  FAILED,
  BLOCKED;

  public String wire() {
    return name();
  }

  public static TransferStatus fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("transferStatus MUST NOT be null");
    }
    try {
      return TransferStatus.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "transferStatus MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }

  public boolean isTerminal() {
    return this == CANCELLED
        || this == COMPENSATED
        || this == COMPLETED
        || this == FAILED
        || this == BLOCKED;
  }
}