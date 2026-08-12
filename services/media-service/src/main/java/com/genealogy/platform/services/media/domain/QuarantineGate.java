package com.genealogy.platform.services.media.domain;

import java.util.Objects;

/**
 * Quarantine gate. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.uploadSessionStatuses + finalizeOutcomes +
 * quarantineGateReAuthorizationRequiredOnAdmit` (E7.1) +
 * `design.md` §8.2 (media ở trạng thái QUARANTINED; chỉ
 * asset READY mới được liên kết / xem).
 *
 * <p>The gate decides whether a {@code QUARANTINED} session
 * may be admitted to {@code READY} or {@code REJECTED}. The
 * decision depends on the MIME verdict, the
 * {@code MimePolicy} sandbox + deep-scan flags, and the
 *OpenFGA + ABAC re-authorization decision at admit time.
 */
public final class QuarantineGate {

    private final MimePolicy mimePolicy;

    public QuarantineGate(MimePolicy mimePolicy) {
        Objects.requireNonNull(mimePolicy, "mimePolicy");
        this.mimePolicy = mimePolicy;
    }

    public FinalizeOutcome evaluate(
            UploadSession session,
            MimeVerdict verdict,
            boolean antimalwarePassed,
            boolean metadataIndexPassed,
            UploadAuthorizationDecision admitDecision) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(admitDecision, "admitDecision");
        if (session.status() != UploadSessionStatus.QUARANTINED) {
            throw new IllegalStateException(
                    "session must be QUARANTINED, got " + session.status());
        }
        if (!admitDecision.isAllow()) {
            return FinalizeOutcome.REJECTED;
        }
        if (verdict == MimeVerdict.DENY) {
            return FinalizeOutcome.REJECTED;
        }
        if (verdict == MimeVerdict.SANDBOX_REQUIRED && !antimalwarePassed) {
            return FinalizeOutcome.QUARANTINED;
        }
        if (verdict == MimeVerdict.DEEP_SCAN_REQUIRED && !metadataIndexPassed) {
            return FinalizeOutcome.QUARANTINED;
        }
        if (!antimalwarePassed || !metadataIndexPassed) {
            return FinalizeOutcome.QUARANTINED;
        }
        return FinalizeOutcome.READY;
    }

    public MimePolicy mimePolicy() {
        return mimePolicy;
    }
}
