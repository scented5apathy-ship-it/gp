package com.genealogy.platform.services.importexport.webhook;

/**
 * Closed-set retry policies the webhook dispatcher supports. Mirrors
 * <code>contracts/importexport/webhook-policy.yaml</code>
 * <code>webhookRetryPolicies</code>.
 */
public enum WebhookRetryPolicy {
  EXPONENTIAL_BACKOFF,
  EXPONENTIAL_BACKOFF_JITTER,
  LINEAR_BACKOFF;

  public String wire() {
    return name();
  }

  public static WebhookRetryPolicy fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("webhookRetryPolicy MUST NOT be null");
    }
    try {
      return WebhookRetryPolicy.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "webhookRetryPolicy MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}