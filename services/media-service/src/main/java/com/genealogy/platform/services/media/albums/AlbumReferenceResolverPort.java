package com.genealogy.platform.services.media.albums;

import java.util.Map;

/**
 * Pure port for the cross-service reference resolver.
 *
 * <p>The implementation lives in the worker subproject; the
 * orchestrator passes an opaque {@code referencePseudoId} +
 * the {@link AlbumReferenceKind} and expects a closed-set
 * {@link AlbumReferenceOutcome} verdict. The
 * {@link ReconciliationReport} aggregates the per-item
 * outcome into the closed-set
 * {@link ReconciliationOutcome}.
 */
public interface AlbumReferenceResolverPort {

    AlbumReferenceVerdict resolve(
            String tenantScopeId,
            AlbumReferenceKind kind,
            String referencePseudoId);
}