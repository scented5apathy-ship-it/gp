package com.genealogy.platform.services.media.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure invariant checker for every upload-lifecycle
 * aggregate. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.invariants + uploadGuardDenyReasons +
 * quotaDenialReasons + abandonedMultipartReasons` (E7.1) +
 * `requirements.md` R9.2 + `design.md` §8.2 + §6.2.
 *
 * <p>Findings are emitted with three severity levels:
 *
 * <ul>
 *   <li>{@link Severity#DENY} — the executor MUST NOT
 *       persist the state. Equivalent to a hard constraint.
 *   <li>{@link Severity#WARN} — the executor MAY persist;
 *       the editor must be informed (UI live region).
 *   <li>{@link Severity#INFO} — purely informational.
 * </ul>
 *
 * <p>Reason codes are closed-set; adding a new code requires
 * an ADR supersession and an update to the contract.
 */
public final class MediaInvariants {

    public enum Severity {
        DENY,
        WARN,
        INFO
    }

    public enum ConflictCode {
        UPLOAD_SESSION_BLANK_REQUESTER,
        UPLOAD_SESSION_BLANK_TENANT,
        UPLOAD_SESSION_BLANK_INTENT,
        UPLOAD_SESSION_INTENT_NOT_PERMITTED,
        UPLOAD_SESSION_BLANK_MEDIA_CATEGORY,
        UPLOAD_SESSION_BLANK_CHECKSUM,
        UPLOAD_SESSION_CHECKSUM_ALGORITHM_NOT_PERMITTED,
        UPLOAD_SESSION_BODY_BYTES_OUT_OF_RANGE,
        UPLOAD_SESSION_TTL_OUT_OF_RANGE,
        UPLOAD_SESSION_TOO_MANY_PER_USER,
        UPLOAD_SESSION_TOO_MANY_PER_TENANT,
        UPLOAD_SESSION_METADATA_KEY_FORBIDDEN,
        UPLOAD_SESSION_METADATA_VALUE_FORBIDDEN,
        UPLOAD_SESSION_MULTIPART_PART_COUNT_OVERFLOW,
        UPLOAD_SESSION_MULTIPART_PART_SIZE_OUT_OF_RANGE,
        UPLOAD_SESSION_NOT_OWNED_BY_CALLER,
        UPLOAD_SESSION_ALREADY_FINALIZED,
        UPLOAD_SESSION_FORBIDDEN_TRANSITION,
        UPLOAD_SESSION_BLANK_SCOPE_ID,
        QUOTA_EXCEEDED_BYTES,
        QUOTA_EXCEEDED_COUNT,
        QUOTA_EXCEEDED_SESSION_TTL,
        QUOTA_SCOPE_NOT_PERMITTED,
        QUOTA_TENANT_HEADROOM_INSUFFICIENT,
        MIME_NOT_PERMITTED,
        MIME_DENY_LISTED,
        MIME_SNIFF_MISMATCH,
        MIME_SANDBOX_REQUIRED,
        MIME_DEEP_SCAN_REQUIRED,
        MIME_SNIFF_BYTES_OVERFLOW,
        CHECKSUM_MISMATCH,
        CHECKSUM_ALGORITHM_NOT_PERMITTED,
        CHECKSUM_DIGEST_LENGTH_OUT_OF_RANGE,
        DECLARED_SIZE_MISMATCH,
        MULTIPART_PART_NUMBER_INVALID,
        MULTIPART_PART_NUMBER_DUPLICATE,
        MULTIPART_PART_SEQUENCE_GAP,
        MULTIPART_PART_SIZE_OUT_OF_RANGE,
        MULTIPART_PART_NOT_AUTHORIZED,
        SIGNED_URL_METHOD_FORBIDDEN,
        SIGNED_URL_CONTENT_TYPE_FORBIDDEN,
        SIGNED_URL_TTL_OUT_OF_RANGE,
        SIGNED_URL_MAX_SIZE_MISSING,
        SIGNED_URL_REAUTHORIZATION_REQUIRED,
        ANTIMALWARE_NOT_READY,
        QUARANTINE_GATE_FORBIDDEN_NEXT_STATE,
        PAYLOAD_DNA_BUCKET_FORBIDDEN,
        FINALIZE_REAUTHORIZATION_REQUIRED,
        FINALIZE_REAUTHORIZATION_DENIED,
        FINALIZE_REAUTHORIZATION_ABAC_DENIED,
        ABANDONED_MULTIPART_REAP_DENIED,
        ABANDONED_MULTIPART_BATCH_OVERFLOW,
        AUDIT_KEY_FORBIDDEN
    }

    public static final Set<String> FORBIDDEN_METADATA_KEYS = Set.of(
            "dnaRawData",
            "dnaMatchId",
            "consentReceipt",
            "livingMarker",
            "visibility",
            "redactedFields",
            "rawEmail",
            "rawPhone",
            "rawSsn",
            "rawPassport",
            "ownerPseudoId",
            "tenantId");

    public static final Set<String> FORBIDDEN_SELECTED_INTENTS = Set.of(
            UploadSessionIntent.DELIVERY_THUMBNAIL.wire());

    public record Finding(Severity severity, ConflictCode code, String message) {
        public Finding {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    private MediaInvariants() {
    }

    public static List<Finding> check(UploadSession session) {
        Objects.requireNonNull(session, "session");
        List<Finding> findings = new java.util.ArrayList<>();
        if (session.requesterPseudoId() == null
                || session.requesterPseudoId().isBlank()) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.UPLOAD_SESSION_BLANK_REQUESTER,
                    "requesterPseudoId must not be blank"));
        }
        if (session.id().tenantId() == null || session.id().tenantId().isBlank()) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.UPLOAD_SESSION_BLANK_TENANT,
                    "tenantId must not be blank"));
        }
        if (session.intent() == null) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.UPLOAD_SESSION_BLANK_INTENT,
                    "intent must not be null"));
        }
        if (session.mediaCategory() == null) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.UPLOAD_SESSION_BLANK_MEDIA_CATEGORY,
                    "mediaCategory must not be null"));
        }
        if (session.declaredChecksumDigest() == null
                || session.declaredChecksumDigest().isBlank()) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.UPLOAD_SESSION_BLANK_CHECKSUM,
                    "declaredChecksumDigest must not be blank"));
        }
        if (session.scopeId() == null || session.scopeId().isBlank()) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.UPLOAD_SESSION_BLANK_SCOPE_ID,
                    "scopeId must not be blank"));
        }
        if (session.intent() != null
                && FORBIDDEN_SELECTED_INTENTS.contains(session.intent().wire())) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.UPLOAD_SESSION_INTENT_NOT_PERMITTED,
                    "intent " + session.intent().wire() + " is not permitted at create"));
        }
        for (Map.Entry<String, String> e : session.metadata().entrySet()) {
            if (FORBIDDEN_METADATA_KEYS.contains(e.getKey())) {
                findings.add(new Finding(
                        Severity.DENY,
                        ConflictCode.UPLOAD_SESSION_METADATA_KEY_FORBIDDEN,
                        "metadata key '" + e.getKey() + "' is forbidden by policy"));
            }
        }
        return List.copyOf(findings);
    }

    public static List<Finding> checkTransition(
            UploadSession session, UploadSessionStatus next) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(next, "next");
        List<Finding> findings = new java.util.ArrayList<>();
        switch (session.status()) {
            case REQUESTED -> {
                if (next != UploadSessionStatus.SIGNED
                        && next != UploadSessionStatus.ABANDONED
                        && next != UploadSessionStatus.FAILED) {
                    findings.add(new Finding(
                            Severity.DENY,
                            ConflictCode.UPLOAD_SESSION_FORBIDDEN_TRANSITION,
                            "REQUESTED may only advance to SIGNED / ABANDONED / FAILED"));
                }
            }
            case SIGNED -> {
                if (next != UploadSessionStatus.UPLOADING
                        && next != UploadSessionStatus.ABANDONED
                        && next != UploadSessionStatus.FAILED) {
                    findings.add(new Finding(
                            Severity.DENY,
                            ConflictCode.UPLOAD_SESSION_FORBIDDEN_TRANSITION,
                            "SIGNED may only advance to UPLOADING / ABANDONED / FAILED"));
                }
            }
            case UPLOADING -> {
                if (next != UploadSessionStatus.FINALIZING
                        && next != UploadSessionStatus.ABANDONED
                        && next != UploadSessionStatus.FAILED) {
                    findings.add(new Finding(
                            Severity.DENY,
                            ConflictCode.UPLOAD_SESSION_FORBIDDEN_TRANSITION,
                            "UPLOADING may only advance to FINALIZING / ABANDONED / FAILED"));
                }
            }
            case FINALIZING -> {
                if (next != UploadSessionStatus.QUARANTINED
                        && next != UploadSessionStatus.REJECTED
                        && next != UploadSessionStatus.ABANDONED
                        && next != UploadSessionStatus.FAILED) {
                    findings.add(new Finding(
                            Severity.DENY,
                            ConflictCode.UPLOAD_SESSION_FORBIDDEN_TRANSITION,
                            "FINALIZING may only advance to QUARANTINED / REJECTED / ABANDONED / FAILED"));
                }
            }
            case QUARANTINED -> {
                if (next != UploadSessionStatus.READY
                        && next != UploadSessionStatus.REJECTED
                        && next != UploadSessionStatus.FAILED) {
                    findings.add(new Finding(
                            Severity.DENY,
                            ConflictCode.UPLOAD_SESSION_FORBIDDEN_TRANSITION,
                            "QUARANTINED may only advance to READY / REJECTED / FAILED"));
                }
            }
            case READY, REJECTED, ABANDONED, FAILED -> findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.UPLOAD_SESSION_ALREADY_FINALIZED,
                    "session already finalized; no further transition permitted"));
            default -> findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.UPLOAD_SESSION_FORBIDDEN_TRANSITION,
                    "unknown from-state " + session.status()));
        }
        return List.copyOf(findings);
    }

    public static List<Finding> checkPartSequence(
            List<MultipartPart> received, MultipartPart next) {
        Objects.requireNonNull(received, "received");
        Objects.requireNonNull(next, "next");
        List<Finding> findings = new java.util.ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        int max = 0;
        for (MultipartPart p : received) {
            if (!seen.add(p.partNumber())) {
                findings.add(new Finding(
                        Severity.DENY,
                        ConflictCode.MULTIPART_PART_NUMBER_DUPLICATE,
                        "duplicate part number " + p.partNumber()));
            }
            if (p.partNumber() > max) {
                max = p.partNumber();
            }
        }
        if (seen.contains(next.partNumber())) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.MULTIPART_PART_NUMBER_INVALID,
                    "part number " + next.partNumber() + " already received"));
        }
        if (!received.isEmpty() && next.partNumber() != max + 1) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.MULTIPART_PART_SEQUENCE_GAP,
                    "part number " + next.partNumber() + " must follow " + max));
        }
        return List.copyOf(findings);
    }

    public static Map<String, String> forbiddenMetadataLabel() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : FORBIDDEN_METADATA_KEYS) {
            map.put(key, "FORBIDDEN");
        }
        return Map.copyOf(map);
    }

    public static boolean hasDeny(List<Finding> findings) {
        for (Finding f : findings) {
            if (f.severity() == Severity.DENY) {
                return true;
            }
        }
        return false;
    }
}
