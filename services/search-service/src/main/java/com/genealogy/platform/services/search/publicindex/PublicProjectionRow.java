package com.genealogy.platform.services.search.publicindex;

/**
 * Input for the public projection redactor. Mirrors the row-level
 * fields the redactor needs to decide whether the row may enter the
 * public index.
 */
public record PublicProjectionRow(
    String documentId,
    String tenantScopeId,
    PublicProjectionVisibility visibility,
    boolean living,
    boolean minor,
    boolean dnaAttached,
    boolean consentValid) {

  public PublicProjectionRow {
    if (documentId == null || documentId.isBlank()) {
      throw new IllegalArgumentException("documentId MUST NOT be blank");
    }
    if (tenantScopeId == null || tenantScopeId.isBlank()) {
      throw new IllegalArgumentException("tenantScopeId MUST NOT be blank");
    }
    if (visibility == null) {
      throw new IllegalArgumentException("visibility MUST NOT be null");
    }
  }
}