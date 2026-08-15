package com.genealogy.platform.services.search.authorized;

/**
 * Pure port for the OpenFGA relationship check (per ADR-E0.5-06).
 * The implementation lands in the worker subproject.
 */
public interface SearchOpenFgaPort {

  SearchOpenFgaVerdict check(String tenantScopeId, String resourceKind, String actorPseudoId);
}