package com.genealogy.platform.services.research.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.genealogy.platform.services.research.domain.Citation;
import com.genealogy.platform.services.research.domain.CitationQuality;
import com.genealogy.platform.services.research.domain.Conflict;
import com.genealogy.platform.services.research.domain.ConflictKind;
import com.genealogy.platform.services.research.domain.Certainty;
import java.time.Instant;

/**
 * Wire-format payloads for the three research events published
 * on the transactional outbox. Mirrors the Avro schemas under
 * {@code contracts/events/research/v1/} field-for-field.
 *
 * <p>Each record is the JSON intermediate the relay stores on
 * the outbox row; the relay converts JSON → Avro at publish
 * time so the schema registry stays the source of truth.
 *
 * <p>NO raw DNA, biography, file content, access token or PII
 * is ever placed in the payload. {@code design.md} §7.3 forbids
 * it. The {@code actorPseudoId} is the platform pseudonym
 * derived from the Keycloak subject + Kong boundary — never
 * the raw subject id, never the email.
 */
public final class ResearchEventPayloads {

    private ResearchEventPayloads() {
    }

    public static final String EVENT_CITATION_CREATED = "gp.research.v1.CitationCreated";
    public static final String EVENT_CLAIM_VERIFIED = "gp.research.v1.ClaimVerified";
    public static final String EVENT_CONFLICT_DETECTED = "gp.research.v1.ConflictDetected";

    /** Apicurio schema id for the {@link CitationCreatedEvent} payload. */
    public static final String SCHEMA_CITATION_CREATED =
            "research/v1/citation-created.avsc";
    public static final String SCHEMA_CLAIM_VERIFIED =
            "research/v1/claim-verified.avsc";
    public static final String SCHEMA_CONFLICT_DETECTED =
            "research/v1/conflict-detected.avsc";

    public record CitationCreatedEvent(
            @JsonProperty("citationId") String citationId,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("sourceId") String sourceId,
            @JsonProperty("claimReference") String claimReference,
            @JsonProperty("claimKind") String claimKind,
            @JsonProperty("quality") String quality,
            @JsonProperty("disposition") String disposition,
            @JsonProperty("certainty") String certainty,
            @JsonProperty("confidence") Double confidence,
            @JsonProperty("actorPseudoId") String actorPseudoId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("createdAt") Instant createdAt) {
    }

    public record ClaimVerifiedEvent(
            @JsonProperty("claimReference") String claimReference,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("verifyingCitationId") String verifyingCitationId,
            @JsonProperty("verifiedAt") Instant verifiedAt,
            @JsonProperty("actorPseudoId") String actorPseudoId,
            @JsonProperty("correlationId") String correlationId) {
    }

    public record ConflictDetectedEvent(
            @JsonProperty("conflictId") String conflictId,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("kind") String kind,
            @JsonProperty("participantCount") int participantCount,
            @JsonProperty("summary") String summary,
            @JsonProperty("actorPseudoId") String actorPseudoId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("detectedAt") Instant detectedAt) {
    }

    /** Best-effort close-set mapping for the {@code certainty} field. */
    public static String wireCertainty(Certainty c) {
        return c == null ? null : c.name();
    }

    /** Best-effort closed-set mapping for the {@code disposition} field. */
    public static String wireDisposition(Citation.Disposition d) {
        return d == null ? null : d.name();
    }

    /** Best-effort closed-set mapping for the {@code quality} field. */
    public static String wireQuality(CitationQuality q) {
        return q == null ? null : q.name();
    }

    /** Best-effort closed-set mapping for the {@code kind} field. */
    public static String wireConflictKind(ConflictKind k) {
        return k == null ? null : k.name();
    }
}
