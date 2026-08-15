package com.genealogy.platform.services.search.authorized;

/**
 * Pure ABAC overlay port. Returns a {@link SearchAbacVerdict}
 * after consulting the ABAC store (living / minor / DNA / consent).
 */
public interface SearchAbacPort {

  SearchAbacVerdict evaluate(String tenantScopeId, String actorPseudoId);
}