package com.genealogy.platform.services.media.albums;

/**
 * Pure port for the OpenFGA relationship check. The
 * implementation lives in the worker subproject (later
 * E7.x / E11.x sub-epic); the orchestrator treats the
 * verdict as a closed-set {@code ALLOW} / {@code DENY}.
 */
public interface AlbumOpenFgaPort {

    AlbumOpenFgaVerdict check(
            String tenantScopeId,
            String albumId,
            String actorPseudoId,
            String correlationId);
}