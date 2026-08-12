package com.genealogy.platform.services.research.application;

import com.genealogy.platform.services.research.domain.AttachmentKind;
import com.genealogy.platform.services.research.domain.AttachmentRef;
import com.genealogy.platform.services.research.domain.Certainty;
import com.genealogy.platform.services.research.domain.Citation;
import com.genealogy.platform.services.research.domain.CitationQuality;
import com.genealogy.platform.services.research.domain.Conflict;
import com.genealogy.platform.services.research.domain.ConflictKind;
import com.genealogy.platform.services.research.domain.Hypothesis;
import com.genealogy.platform.services.research.domain.HypothesisStateMachine;
import com.genealogy.platform.services.research.domain.HypothesisStatus;
import com.genealogy.platform.services.research.domain.Locator;
import com.genealogy.platform.services.research.domain.ResearchAuditAttributes;
import com.genealogy.platform.services.research.domain.ResearchInvariants;
import com.genealogy.platform.services.research.domain.ResearchInvariants.Finding;
import com.genealogy.platform.services.research.domain.ResearchTask;
import com.genealogy.platform.services.research.domain.ResearchTaskStateMachine;
import com.genealogy.platform.services.research.domain.ResearchTaskStatus;
import com.genealogy.platform.services.research.domain.Repository;
import com.genealogy.platform.services.research.domain.RepositoryKind;
import com.genealogy.platform.services.research.domain.Source;
import com.genealogy.platform.services.research.domain.SourceKind;
import com.genealogy.platform.services.research.domain.TenantScopedId;
import com.genealogy.platform.services.research.domain.TranscriptSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Maps the wire DTOs (created by the controllers) onto the
 * framework-free {@link Commands} records and the domain
 * aggregates. The mapper is the only place where the public
 * JSON boundary touches the domain — closed-set enums are
 * decoded via the {@code fromWire} helpers so an unknown
 * constant surfaces as {@code IllegalArgumentException} BEFORE
 * the aggregate is built.
 *
 * <p>Per agent-execution.md §4.4 the controller does NOT pass
 * enum constants directly; the JSON layer only carries the
 * string form so:
 *
 * <ul>
 *   <li>adding a new policy constant requires a code change
 *       that the linter can audit;</li>
 *   <li>the wire format stays decoupled from the internal
 *       Java enum;</li>
 *   <li>unknown constants surface as {@code 400 invalid-request}
 *       before reaching the database.</li>
 * </ul>
 */
public final class DraftDomainMapper {

    private DraftDomainMapper() {
        // utility
    }

    /* ---------------- Enum decoders ---------------- */

    public static RepositoryKind repositoryKind(String wire) {
        try {
            return wire == null ? null : RepositoryKind.fromWire(wire);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(
                    "repositoryKind must be one of " + enumValues(RepositoryKind.class)
                            + " (got '" + wire + "')");
        }
    }

    public static SourceKind sourceKind(String wire) {
        try {
            return wire == null ? null : SourceKind.fromWire(wire);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(
                    "sourceKind must be one of " + enumValues(SourceKind.class)
                            + " (got '" + wire + "')");
        }
    }

    /**
     * Map a Java domain {@link RepositoryKind} to the
     * protobuf wire name. The protobuf enum values are
     * prefixed with {@code REPOSITORY_} to avoid the
     * cross-enum collision that protoc forbids (see
     * {@code contracts/protobuf/research/v1/repository_service.proto}).
     */
    public static String repositoryKindToProto(com.genealogy.platform.services.research.domain.RepositoryKind k) {
        if (k == null) {
            return "REPOSITORY_KIND_UNSPECIFIED";
        }
        return switch (k) {
            case ARCHIVE -> "REPOSITORY_ARCHIVE";
            case LIBRARY -> "REPOSITORY_LIBRARY";
            case CHURCH -> "REPOSITORY_CHURCH";
            case CIVIL_REGISTRY -> "REPOSITORY_CIVIL_REGISTRY";
            case CEMETERY -> "REPOSITORY_CEMETERY";
            case FAMILY_HOLDING -> "REPOSITORY_FAMILY_HOLDING";
            case DIGITAL_PLATFORM -> "REPOSITORY_DIGITAL_PLATFORM";
            case OTHER -> "REPOSITORY_OTHER";
        };
    }

    /**
     * Map a Java domain {@link SourceKind} to the protobuf
     * wire name (prefixed with {@code SOURCE_} — see
     * {@link #repositoryKindToProto} for the rationale).
     */
    public static String sourceKindToProto(com.genealogy.platform.services.research.domain.SourceKind k) {
        if (k == null) {
            return "SOURCE_KIND_UNSPECIFIED";
        }
        return switch (k) {
            case PRIMARY -> "SOURCE_PRIMARY";
            case SECONDARY -> "SOURCE_SECONDARY";
            case DERIVED -> "SOURCE_DERIVED";
            case ARCHIVE -> "SOURCE_ARCHIVE";
            case FINDING_AID -> "SOURCE_FINDING_AID";
            case OTHER -> "SOURCE_OTHER";
        };
    }

    /**
     * Map a protobuf wire name (prefixed) back to the Java
     * domain {@link RepositoryKind}. Handles the bare
     * proto fallback ({@code OTHER} → {@code OTHER})
     * for backward compatibility with the REST surface.
     */
    public static RepositoryKind repositoryKindFromProto(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String stripped = wire.startsWith("REPOSITORY_") ? wire.substring("REPOSITORY_".length()) : wire;
        return repositoryKind(stripped);
    }

    /** Mirror of {@link #repositoryKindFromProto} for {@link SourceKind}. */
    public static SourceKind sourceKindFromProto(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String stripped = wire.startsWith("SOURCE_") ? wire.substring("SOURCE_".length()) : wire;
        return switch (stripped) {
            case "PRIMARY" -> SourceKind.PRIMARY;
            case "SECONDARY" -> SourceKind.SECONDARY;
            case "DERIVED" -> SourceKind.DERIVED;
            case "ARCHIVE" -> SourceKind.ARCHIVE;
            case "FINDING_AID" -> SourceKind.FINDING_AID;
            default -> sourceKind(stripped);
        };
    }

    public static CitationQuality citationQualityFromProto(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String stripped = wire.startsWith("CITATION_") ? wire.substring("CITATION_".length()) : wire;
        return citationQuality(stripped);
    }

    public static Citation.Disposition dispositionFromProto(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String stripped = wire.startsWith("CITATION_") ? wire.substring("CITATION_".length()) : wire;
        return disposition(stripped);
    }

    public static Certainty certaintyFromProto(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String stripped = wire.startsWith("CERTAINTY_") ? wire.substring("CERTAINTY_".length()) : wire;
        return switch (stripped) {
            case "POSSIBLE" -> Certainty.HYPOTHESIS;
            case "LIKELY" -> Certainty.ASSERTED;
            case "CERTAIN" -> Certainty.VERIFIED;
            default -> certainty(stripped);
        };
    }

    public static ResearchTaskStatus researchTaskStatusFromProto(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String stripped = wire.startsWith("RESEARCH_TASK_") ? wire.substring("RESEARCH_TASK_".length()) : wire;
        return researchTaskStatus(stripped);
    }

    public static HypothesisStatus hypothesisStatusFromProto(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String stripped = wire.startsWith("HYPOTHESIS_") ? wire.substring("HYPOTHESIS_".length()) : wire;
        return hypothesisStatus(stripped);
    }

    public static ConflictKind conflictKindFromProto(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String stripped = wire.startsWith("CONFLICT_") ? wire.substring("CONFLICT_".length()) : wire;
        return switch (stripped) {
            case "FACTUAL" -> ConflictKind.SOURCE_DISAGREES;
            case "INTERPRETATION" -> ConflictKind.HYPOTHESIS_COLLIDES;
            case "IDENTIFICATION" -> ConflictKind.OTHER;
            case "SOURCE_CONFLICT" -> ConflictKind.CLAIM_CONTRADICTS_SOURCE;
            default -> conflictKind(stripped);
        };
    }

    public static Conflict.ConflictStatus conflictStatusFromProto(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String stripped = wire.startsWith("CONFLICT_") ? wire.substring("CONFLICT_".length()) : wire;
        return switch (stripped) {
            case "DETECTED" -> Conflict.ConflictStatus.OPEN;
            case "IN_REVIEW" -> Conflict.ConflictStatus.INVESTIGATING;
            case "RESOLVED" -> Conflict.ConflictStatus.RESOLVED;
            case "REJECTED" -> Conflict.ConflictStatus.ABANDONED;
            default -> conflictStatus(stripped);
        };
    }

    public static CitationQuality citationQuality(String wire) {
        try {
            return wire == null ? null : CitationQuality.fromWire(wire);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(
                    "citationQuality must be one of " + enumValues(CitationQuality.class)
                            + " (got '" + wire + "')");
        }
    }

    public static Citation.Disposition disposition(String wire) {
        if (wire == null) {
            return null;
        }
        try {
            return Citation.Disposition.valueOf(wire.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(
                    "disposition must be one of SUPPORTS, REFUTES, MENTIONS, UNCERTAIN"
                            + " (got '" + wire + "')");
        }
    }

    public static Certainty certainty(String wire) {
        try {
            return wire == null ? null : Certainty.fromWire(wire);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(
                    "certainty must be one of " + enumValues(Certainty.class)
                            + " (got '" + wire + "')");
        }
    }

    public static ResearchTaskStatus researchTaskStatus(String wire) {
        try {
            return wire == null ? null : ResearchTaskStatus.fromWire(wire);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(
                    "researchTaskStatus must be one of " + enumValues(ResearchTaskStatus.class)
                            + " (got '" + wire + "')");
        }
    }

    public static HypothesisStatus hypothesisStatus(String wire) {
        try {
            return wire == null ? null : HypothesisStatus.fromWire(wire);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(
                    "hypothesisStatus must be one of " + enumValues(HypothesisStatus.class)
                            + " (got '" + wire + "')");
        }
    }

    public static ConflictKind conflictKind(String wire) {
        try {
            return wire == null ? null : ConflictKind.fromWire(wire);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(
                    "conflictKind must be one of " + enumValues(ConflictKind.class)
                            + " (got '" + wire + "')");
        }
    }

    public static Conflict.ConflictStatus conflictStatus(String wire) {
        try {
            return wire == null ? null : Conflict.ConflictStatus.valueOf(
                    wire.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(
                    "conflictStatus must be one of OPEN, INVESTIGATING, RESOLVED, ABANDONED"
                            + " (got '" + wire + "')");
        }
    }

    /* ---------------- Value object decoders ---------------- */

    public static Locator locator(String raw, String page, String entry, String volume) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("locator.raw must not be blank");
        }
        return new Locator(raw, page, entry, volume);
    }

    public static AttachmentRef attachment(String kind, String mediaObjectId,
            String canonicalUrl, String caption, String locale) {
        if (mediaObjectId == null || mediaObjectId.isBlank()) {
            throw new InvalidRequestException(
                    "attachment.mediaObjectId must not be blank");
        }
        AttachmentKind attachmentKind;
        try {
            attachmentKind = kind == null ? AttachmentKind.OTHER : AttachmentKind.fromWire(kind);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(
                    "attachmentKind must be one of " + enumValues(AttachmentKind.class)
                            + " (got '" + kind + "')");
        }
        return new AttachmentRef(attachmentKind, mediaObjectId, canonicalUrl, caption, locale);
    }

    public static TranscriptSegment transcriptSegment(int lineNumber, String text,
            String originalScript, String translationLocale, String speaker) {
        return new TranscriptSegment(lineNumber, text, originalScript,
                translationLocale, speaker);
    }

    /* ---------------- Aggregate factory wrappers ---------------- */

    public static Repository createRepository(String tenantId, String id, Commands.CreateRepository cmd,
            ResearchAuditAttributes audit) {
        Objects.requireNonNull(cmd, "cmd");
        Objects.requireNonNull(audit, "audit");
        TenantScopedId repoId = TenantScopedId.of(tenantId,
                TenantScopedId.ResourceKind.REPOSITORY, id);
        return Repository.create(repoId, cmd.name(), cmd.kind(), cmd.locationLabel(),
                cmd.websiteUrl(), cmd.description(), audit);
    }

    public static Source createSource(String tenantId, String id, Commands.CreateSource cmd,
            ResearchAuditAttributes audit) {
        Objects.requireNonNull(cmd, "cmd");
        Objects.requireNonNull(audit, "audit");
        TenantScopedId sourceId = TenantScopedId.of(tenantId,
                TenantScopedId.ResourceKind.SOURCE, id);
        return Source.create(sourceId, sameTenant(cmd.repositoryId(), tenantId),
                cmd.title(), cmd.sourceKind(), cmd.author(), cmd.publisher(),
                cmd.publicationYear(), cmd.publisherLocation(), cmd.locator(),
                cmd.attachments(), cmd.description(), audit);
    }

    public static Citation createCitation(String tenantId, String id, Commands.CreateCitation cmd,
            ResearchAuditAttributes audit) {
        Objects.requireNonNull(cmd, "cmd");
        Objects.requireNonNull(audit, "audit");
        TenantScopedId citationId = TenantScopedId.of(tenantId,
                TenantScopedId.ResourceKind.CITATION, id);
        return Citation.create(citationId, sameTenant(cmd.sourceId(), tenantId),
                cmd.claimReference(), cmd.claimKind(), cmd.locator(), cmd.quality(),
                cmd.disposition(), cmd.certainty(), cmd.confidence(), cmd.quotedText(),
                cmd.transcriptSegments(), cmd.attachments(), cmd.externalUrls(), audit);
    }

    public static ResearchTask createResearchTask(String tenantId, String id,
            Commands.CreateResearchTask cmd, ResearchAuditAttributes audit) {
        Objects.requireNonNull(cmd, "cmd");
        Objects.requireNonNull(audit, "audit");
        TenantScopedId taskId = TenantScopedId.of(tenantId,
                TenantScopedId.ResourceKind.RESEARCH_TASK, id);
        return ResearchTask.create(taskId, cmd.title(), cmd.description(),
                cmd.subjectReference(), cmd.subjectKind(), audit);
    }

    public static ResearchTask transitionResearchTask(ResearchTask task,
            Commands.TransitionResearchTask cmd) {
        Objects.requireNonNull(cmd, "cmd");
        return ResearchTaskStateMachine.transition(task, cmd.next(),
                cmd.blockedReason(), cmd.resolvedProof());
    }

    public static Hypothesis createHypothesis(String tenantId, String id,
            Commands.CreateHypothesis cmd, ResearchAuditAttributes audit) {
        Objects.requireNonNull(cmd, "cmd");
        Objects.requireNonNull(audit, "audit");
        TenantScopedId hypothesisId = TenantScopedId.of(tenantId,
                TenantScopedId.ResourceKind.HYPOTHESIS, id);
        return Hypothesis.create(hypothesisId, cmd.statement(), cmd.subjectReference(),
                cmd.subjectKind(), cmd.certainty(), cmd.confidence(), audit);
    }

    public static Hypothesis transitionHypothesis(Hypothesis hypothesis,
            HypothesisStatus next) {
        return HypothesisStateMachine.transition(hypothesis, next);
    }

    public static Conflict createConflict(String tenantId, String id,
            Commands.CreateConflict cmd, ResearchAuditAttributes audit) {
        Objects.requireNonNull(cmd, "cmd");
        Objects.requireNonNull(audit, "audit");
        TenantScopedId conflictId = TenantScopedId.of(tenantId,
                TenantScopedId.ResourceKind.CONFLICT, id);
        List<Conflict.Participant> participants = cmd.participants() == null
                ? new ArrayList<>()
                : new ArrayList<>(cmd.participants());
        return Conflict.create(conflictId, cmd.summary(), cmd.kind(), cmd.kindNote(),
                participants, audit);
    }

    /**
     * Marker exception raised by the mapper whenever a wire
     * value fails to decode. The exception carries no PII / DNA
     * — the value is the raw wire string the client sent so the
     * operator can trace the source of the violation.
     */
    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }

    private static TenantScopedId sameTenant(TenantScopedId source, String tenantId) {
        Objects.requireNonNull(source, "source");
        if (!tenantId.equals(source.tenantId())) {
            throw new InvalidRequestException(
                    "tenantId on the wire (" + tenantId
                            + ") does not match the supplied foreign id (" + source.tenantId() + ")");
        }
        return source;
    }

    private static <E extends Enum<E>> String enumValues(Class<E> type) {
        StringBuilder sb = new StringBuilder();
        for (E value : type.getEnumConstants()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(value.name());
        }
        return sb.toString();
    }

    /**
     * Asserts the {@link ResearchInvariants} findings list is free
     * of {@link ResearchInvariants.Severity#DENY} entries. {@link
     * Findings} surfaces the first deny code so the controller can
     * map it to a {@code 422} Problem response.
     */
    public static void assertNoDeny(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return;
        }
        for (Finding finding : findings) {
            if (finding.severity() == ResearchInvariants.Severity.DENY) {
                throw new InvariantViolationException(finding);
            }
        }
    }

    /** Marker exception raised when an invariant check returns a DENY finding. */
    public static class InvariantViolationException extends RuntimeException {
        private final Finding finding;

        public InvariantViolationException(Finding finding) {
            super(finding.message());
            this.finding = finding;
        }

        public Finding finding() {
            return finding;
        }
    }
}
