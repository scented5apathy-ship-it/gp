package com.genealogy.platform.services.media.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure swept list of abandoned multipart sessions. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.abandonedMultipartReasons +
 * abandonedMultipartReAuthorizationRequiredOnReap +
 * maxAbandonedMultipartBatchSize` (E7.1) + `design.md` §8.2
 * (Dọn abandoned multipart bằng lifecycle / workflow).
 *
 * <p>The sweeper never mutates a session; it returns a
 * {@code SweepResult} describing which sessions should be
 * reaped and the abandoned reason. The application layer
 * applies the {@code transitionTo(ABANDONED)} on the
 * persisted session.
 */
public record AbandonedMultipartSweeper(
        long maxBatchSize,
        long sweepConcurrency) {

    public static final long MIN_BATCH_SIZE = 1L;
    public static final long MAX_BATCH_SIZE = 65536L;
    public static final long MIN_CONCURRENCY = 1L;
    public static final long MAX_CONCURRENCY = 256L;

    public AbandonedMultipartSweeper {
        if (maxBatchSize < MIN_BATCH_SIZE || maxBatchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "maxBatchSize must be in ["
                            + MIN_BATCH_SIZE + ", " + MAX_BATCH_SIZE + "], got " + maxBatchSize);
        }
        if (sweepConcurrency < MIN_CONCURRENCY || sweepConcurrency > MAX_CONCURRENCY) {
            throw new IllegalArgumentException(
                    "sweepConcurrency must be in ["
                            + MIN_CONCURRENCY + ", " + MAX_CONCURRENCY
                            + "], got " + sweepConcurrency);
        }
    }

    public static AbandonedMultipartSweeper defaults() {
        return new AbandonedMultipartSweeper(1024L, 16L);
    }

    public SweepResult sweep(
            List<UploadSession> sessions,
            Instant now,
            UploadAuthorizationDecision reapDecision) {
        Objects.requireNonNull(sessions, "sessions");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(reapDecision, "reapDecision");
        if (sessions.size() > maxBatchSize) {
            throw new IllegalArgumentException(
                    "batch size " + sessions.size() + " exceeds " + maxBatchSize);
        }
        if (!reapDecision.isAllow()) {
            return new SweepResult(List.of(), List.copyOf(sessions));
        }
        List<AbandonedReasoned> reaped = new ArrayList<>();
        List<UploadSession> skipped = new ArrayList<>();
        for (UploadSession session : sessions) {
            Optional<AbandonedMultipartReason> reason = reasonFor(session, now);
            if (reason.isEmpty()) {
                skipped.add(session);
                continue;
            }
            reaped.add(new AbandonedReasoned(session, reason.get()));
        }
        return new SweepResult(
                Collections.unmodifiableList(reaped),
                Collections.unmodifiableList(skipped));
    }

    public Optional<AbandonedMultipartReason> reasonFor(
            UploadSession session, Instant now) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(now, "now");
        if (session.status() == UploadSessionStatus.READY
                || session.status() == UploadSessionStatus.REJECTED
                || session.status() == UploadSessionStatus.FAILED
                || session.status() == UploadSessionStatus.ABANDONED) {
            return Optional.empty();
        }
        if (now.isAfter(session.expiresAt())) {
            return Optional.of(AbandonedMultipartReason.SESSION_TTL_EXPIRED);
        }
        return Optional.empty();
    }

    public record AbandonedReasoned(
            UploadSession session, AbandonedMultipartReason reason) {
        public AbandonedReasoned {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public record SweepResult(
            List<AbandonedReasoned> reaped, List<UploadSession> skipped) {
        public SweepResult {
            Objects.requireNonNull(reaped, "reaped");
            Objects.requireNonNull(skipped, "skipped");
            reaped = List.copyOf(reaped);
            skipped = List.copyOf(skipped);
        }
    }
}
