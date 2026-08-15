package com.genealogy.platform.services.search.projection;

import com.genealogy.platform.services.search.shared.SearchLimits;
import java.util.List;

/**
 * Per-event payload consumed by the projection worker (E8.1).
 * Mirrors the closed-set enums in
 * <code>contracts/search/search-projection-policy.yaml</code>.
 */
public record SearchProjectionEvent(
    SearchEventType eventType,
    SearchDocumentKind documentKind,
    String documentId,
    String tenantScopeId,
    SearchPrivacyClass privacyClass,
    String idempotencyKey,
    List<String> aliases,
    List<String> languages,
    String actorPseudoId,
    String correlationId) {

  public SearchProjectionEvent {
    if (eventType == null) {
      throw new IllegalArgumentException("eventType MUST NOT be null");
    }
    if (documentKind == null) {
      throw new IllegalArgumentException("documentKind MUST NOT be null");
    }
    if (isBlank(documentId)) {
      throw new IllegalArgumentException("documentId MUST NOT be blank");
    }
    if (documentId.length() > SearchLimits.PROJECTION_DOCUMENT_ID_LENGTH) {
      throw new IllegalArgumentException(
          "documentId length MUST be <= "
              + SearchLimits.PROJECTION_DOCUMENT_ID_LENGTH
              + " (got "
              + documentId.length()
              + ")");
    }
    if (isBlank(tenantScopeId)) {
      throw new IllegalArgumentException("tenantScopeId MUST NOT be blank");
    }
    if (tenantScopeId.length() > SearchLimits.TENANT_SCOPE_ID_LENGTH) {
      throw new IllegalArgumentException(
          "tenantScopeId length MUST be <= "
              + SearchLimits.TENANT_SCOPE_ID_LENGTH
              + " (got "
              + tenantScopeId.length()
              + ")");
    }
    if (privacyClass == null) {
      throw new IllegalArgumentException("privacyClass MUST NOT be null");
    }
    if (isBlank(idempotencyKey)) {
      throw new IllegalArgumentException("idempotencyKey MUST NOT be blank");
    }
    if (idempotencyKey.length() > SearchLimits.IDEMPOTENCY_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "idempotencyKey length MUST be <= "
              + SearchLimits.IDEMPOTENCY_KEY_LENGTH
              + " (got "
              + idempotencyKey.length()
              + ")");
    }
    if (aliases == null) {
      throw new IllegalArgumentException("aliases MUST NOT be null");
    }
    if (aliases.size() > SearchProjectionLimits.MAX_ALIAS_PER_DOCUMENT) {
      throw new IllegalArgumentException(
          "aliases.size() MUST be <= "
              + SearchProjectionLimits.MAX_ALIAS_PER_DOCUMENT
              + " (got "
              + aliases.size()
              + ")");
    }
    for (String alias : aliases) {
      if (isBlank(alias)) {
        throw new IllegalArgumentException("alias MUST NOT be blank");
      }
      if (alias.length() > SearchProjectionLimits.MAX_ALIAS_LENGTH) {
        throw new IllegalArgumentException(
            "alias length MUST be <= "
                + SearchProjectionLimits.MAX_ALIAS_LENGTH
                + " (got "
                + alias.length()
                + ")");
      }
    }
    if (languages == null) {
      throw new IllegalArgumentException("languages MUST NOT be null");
    }
    if (languages.size() > SearchProjectionLimits.MAX_LANGUAGES_PER_DOCUMENT) {
      throw new IllegalArgumentException(
          "languages.size() MUST be <= "
              + SearchProjectionLimits.MAX_LANGUAGES_PER_DOCUMENT
              + " (got "
              + languages.size()
              + ")");
    }
    for (String lang : languages) {
      if (lang == null || lang.isBlank()) {
        throw new IllegalArgumentException("language tag MUST NOT be blank");
      }
      if (lang.length() > SearchProjectionLimits.MAX_BCP47_TAG_LENGTH) {
        throw new IllegalArgumentException(
            "language tag length MUST be <= "
                + SearchProjectionLimits.MAX_BCP47_TAG_LENGTH
                + " (got "
                + lang.length()
                + ")");
      }
    }
    if (isBlank(actorPseudoId)) {
      throw new IllegalArgumentException("actorPseudoId MUST NOT be blank");
    }
    if (actorPseudoId.length() > SearchLimits.ACTOR_PSEUDO_ID_LENGTH) {
      throw new IllegalArgumentException(
          "actorPseudoId length MUST be <= "
              + SearchLimits.ACTOR_PSEUDO_ID_LENGTH
              + " (got "
              + actorPseudoId.length()
              + ")");
    }
    if (isBlank(correlationId)) {
      throw new IllegalArgumentException("correlationId MUST NOT be blank");
    }
    if (correlationId.length() > SearchLimits.CORRELATION_ID_LENGTH) {
      throw new IllegalArgumentException(
          "correlationId length MUST be <= "
              + SearchLimits.CORRELATION_ID_LENGTH
              + " (got "
              + correlationId.length()
              + ")");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}