package com.genealogy.platform.services.research.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pure invariant checker for every research aggregate. Mirrors
 * `contracts/research/research-policy.yaml::spec.invariants`
 * (E6.1) + `requirements.md` R8.1 (research log + status
 * proof) + R4.4 (every claim traceable to a citation) +
 * `design.md` §5.5 + §6.2 (ABAC obligations).
 *
 * <p>Findings are emitted with three severity levels:
 *
 * <ul>
 *   <li>{@link Severity#DENY} — the command service MUST NOT
 *       persist the state. Equivalent to a hard constraint.
 *   <li>{@link Severity#WARN} — the command service MAY
 *       persist; the editor must be informed (UI live region).
 *   <li>{@link Severity#INFO} — purely informational.
 * </ul>
 *
 * <p>Reason codes are closed-set; adding a new code requires
 * an ADR supersession and an update to the contract. The
 * lint-research-config script enforces the closed-set.
 */
public final class ResearchInvariants {

    public enum Severity {
        DENY,
        WARN,
        INFO
    }

    public enum ConflictCode {
        SOURCE_POINTER_REQUIRES_ATTACHMENT,
        TRANSCRIPT_QUALITY_REQUIRES_SEGMENT,
        TRANSCRIPT_LINE_OUT_OF_ORDER,
        RESEARCH_TASK_IN_PROGRESS_REQUIRES_ASSIGNMENT,
        RESEARCH_TASK_BLOCKED_REQUIRES_REASON,
        RESEARCH_TASK_RESOLVED_REQUIRES_PROOF,
        HYPOTHESIS_CORROBORATED_REQUIRES_CITATION,
        HYPOTHESIS_REFUTED_REQUIRES_CITATION,
        HYPOTHESIS_SUPERSEDED_REQUIRES_BACK_REFERENCE,
        CITATION_REQUIRES_LOCATOR_OR_QUOTE,
        CITATION_REQUIRES_CONFIRMATION_FOR_LIVING,
        REPOSITORY_PRIVATE_HOLDING_HIDES_BY_DEFAULT,
        CONFLICT_RESOLVED_REQUIRES_PROOF,
        CONFLICT_REQUIRE_MULTIPLE_PARTICIPANTS,
        ATTACHMENT_EXTERNAL_URL_REQUIRES_DOMAIN_WHITELIST,
        AUDIT_KEY_FORBIDDEN
    }

    public record Finding(Severity severity, ConflictCode code, String message) {
        public Finding {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    private ResearchInvariants() {
    }

    public static List<Finding> check(Repository repository) {
        Objects.requireNonNull(repository, "repository");
        return checkRepository(repository);
    }

    public static List<Finding> check(Source source) {
        Objects.requireNonNull(source, "source");
        return checkSource(source);
    }

    public static List<Finding> check(Citation citation) {
        Objects.requireNonNull(citation, "citation");
        return checkCitation(citation);
    }

    public static List<Finding> check(ResearchTask task) {
        Objects.requireNonNull(task, "task");
        return checkResearchTask(task);
    }

    public static List<Finding> check(Hypothesis hypothesis) {
        Objects.requireNonNull(hypothesis, "hypothesis");
        return checkHypothesis(hypothesis);
    }

    public static List<Finding> check(Conflict conflict) {
        Objects.requireNonNull(conflict, "conflict");
        return checkConflict(conflict);
    }

    public static boolean hasDeny(List<Finding> findings) {
        for (Finding f : findings) {
            if (f.severity() == Severity.DENY) {
                return true;
            }
        }
        return false;
    }

    private static List<Finding> checkRepository(Repository repository) {
        List<Finding> findings = new ArrayList<>();
        if (repository.privateHolding()) {
            findings.add(new Finding(
                    Severity.INFO,
                    ConflictCode.REPOSITORY_PRIVATE_HOLDING_HIDES_BY_DEFAULT,
                    "private-holding repository hides from default search projection"));
        }
        return Collections.unmodifiableList(findings);
    }

    private static List<Finding> checkSource(Source source) {
        List<Finding> findings = new ArrayList<>();
        if (source.isPointerOnly() && source.attachments().isEmpty()) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.SOURCE_POINTER_REQUIRES_ATTACHMENT,
                    "pointer-only source (ARCHIVE|FINDING_AID) requires at least one attachment"));
        }
        for (Citation citation : source.citations()) {
            findings.addAll(checkCitation(citation));
        }
        return Collections.unmodifiableList(findings);
    }

    private static List<Finding> checkCitation(Citation citation) {
        List<Finding> findings = new ArrayList<>();
        if (citation.locator() == null
                && (citation.quotedText() == null || citation.quotedText().isBlank())) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.CITATION_REQUIRES_LOCATOR_OR_QUOTE,
                    "citation requires either a non-null locator or a non-blank quotedText"));
        }
        if (citation.quality() == CitationQuality.TRANSCRIPT
                && citation.transcriptSegments().isEmpty()) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.TRANSCRIPT_QUALITY_REQUIRES_SEGMENT,
                    "citationQuality=TRANSCRIPT requires at least one transcriptSegment"));
        }
        for (int i = 1; i < citation.transcriptSegments().size(); i += 1) {
            if (citation.transcriptSegments().get(i).lineNumber()
                    <= citation.transcriptSegments().get(i - 1).lineNumber()) {
                findings.add(new Finding(
                        Severity.WARN,
                        ConflictCode.TRANSCRIPT_LINE_OUT_OF_ORDER,
                        "transcriptSegments must be strictly increasing by lineNumber"));
                break;
            }
        }
        for (AttachmentRef attachment : citation.attachments()) {
            if (attachment.kind() == AttachmentKind.EXTERNAL_URL
                    && !attachment.hasCanonicalUrl()) {
                findings.add(new Finding(
                        Severity.DENY,
                        ConflictCode.ATTACHMENT_EXTERNAL_URL_REQUIRES_DOMAIN_WHITELIST,
                        "attachmentKind=EXTERNAL_URL requires a canonicalUrl"));
            }
        }
        return Collections.unmodifiableList(findings);
    }

    private static List<Finding> checkResearchTask(ResearchTask task) {
        List<Finding> findings = new ArrayList<>();
        if (task.status() == ResearchTaskStatus.IN_PROGRESS
                && task.assignments().isEmpty()) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.RESEARCH_TASK_IN_PROGRESS_REQUIRES_ASSIGNMENT,
                    "researchTaskStatus=IN_PROGRESS requires at least one assignment"));
        }
        if (task.status() == ResearchTaskStatus.BLOCKED
                && (task.blockedReason() == null || task.blockedReason().isBlank())) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.RESEARCH_TASK_BLOCKED_REQUIRES_REASON,
                    "researchTaskStatus=BLOCKED requires a non-blank blockedReason"));
        }
        if (task.status() == ResearchTaskStatus.RESOLVED
                && (task.resolvedProof() == null || task.resolvedProof().isBlank())) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.RESEARCH_TASK_RESOLVED_REQUIRES_PROOF,
                    "researchTaskStatus=RESOLVED requires a non-blank resolvedProof"));
        }
        return Collections.unmodifiableList(findings);
    }

    private static List<Finding> checkHypothesis(Hypothesis hypothesis) {
        List<Finding> findings = new ArrayList<>();
        if (hypothesis.status() == HypothesisStatus.CORROBORATED
                && hypothesis.corroboratingCitations().isEmpty()) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.HYPOTHESIS_CORROBORATED_REQUIRES_CITATION,
                    "hypothesisStatus=CORROBORATED requires at least one corroboratingCitation"));
        }
        if (hypothesis.status() == HypothesisStatus.REFUTED
                && hypothesis.refutingCitations().isEmpty()) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.HYPOTHESIS_REFUTED_REQUIRES_CITATION,
                    "hypothesisStatus=REFUTED requires at least one refutingCitation"));
        }
        if (hypothesis.status() == HypothesisStatus.SUPERSEDED
                && (hypothesis.supersededByHypothesisId() == null
                        || hypothesis.supersededByHypothesisId().isBlank())) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.HYPOTHESIS_SUPERSEDED_REQUIRES_BACK_REFERENCE,
                    "hypothesisStatus=SUPERSEDED requires a non-blank supersededByHypothesisId"));
        }
        return Collections.unmodifiableList(findings);
    }

    private static List<Finding> checkConflict(Conflict conflict) {
        List<Finding> findings = new ArrayList<>();
        if (conflict.participants().size() < 2) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.CONFLICT_REQUIRE_MULTIPLE_PARTICIPANTS,
                    "conflict requires at least 2 participants"));
        }
        if (conflict.status() == Conflict.ConflictStatus.RESOLVED
                && (conflict.resolutionProof() == null
                        || conflict.resolutionProof().isBlank())) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.CONFLICT_RESOLVED_REQUIRES_PROOF,
                    "conflict.status=RESOLVED requires a non-blank resolutionProof"));
        }
        return Collections.unmodifiableList(findings);
    }
}
