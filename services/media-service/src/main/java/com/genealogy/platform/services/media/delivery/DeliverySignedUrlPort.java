package com.genealogy.platform.services.media.delivery;

/**
 * Pure port for the S3 / MinIO signed-URL issuer. The
 * implementation lives in the worker subproject (E7.x /
 * E11.x); the E7.4 orchestrator builds a {@link SignedUrlTicket}
 * skeleton and the adapter signs it.
 *
 * <p>Per the
 * {@code signedUrlTtlCeilingSeconds=900} invariant + the
 * {@code signedUrlRequiresPseudonymInAudit} guard, the
 * adapter MUST cap the TTL at 15 minutes and MUST emit an
 * audit entry carrying {@code actorPseudoId} +
 * {@code correlationId} only.
 */
public interface DeliverySignedUrlPort {

    SignedUrlTicket sign(
            String deliveryId,
            String derivedObjectKey,
            SignedUrlMethod method,
            DeliveryContentType contentType,
            DeliveryDisposition disposition,
            WatermarkOverlay watermark,
            int ttlSeconds,
            String actorPseudoId,
            String correlationId);
}