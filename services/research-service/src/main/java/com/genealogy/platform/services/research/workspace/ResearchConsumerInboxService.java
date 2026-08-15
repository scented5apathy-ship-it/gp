package com.genealogy.platform.services.research.workspace;

import com.genealogy.platform.services.research.application.rls.ResearchRlsTxInterceptor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer-side idempotency layer. The
 * {@link ResearchConsumerInboxListener} invokes this service
 * for every delivery; the service claims the inbox row first
 * and only runs the projection mutation when the claim wins.
 *
 * <p>The service is the only writer of
 * {@code research_service.consumer_inbox}. The methods are
 * {@link Transactional} so the {@code INSERT} + the
 * projection {@code UPDATE} commit in the same database
 * transaction. The {@link ResearchRlsTxInterceptor#bind()}
 * call binds the tenant context so the {@code FORCE ROW
 * LEVEL SECURITY} posture stays intact.
 *
 * <p>Closes E6.1d Gap 5 — the durable consumer inbox that
 * makes re-delivery safe.
 */
@Service
public class ResearchConsumerInboxService {

    private static final Logger LOG = LoggerFactory.getLogger(ResearchConsumerInboxService.class);

    private final ResearchConsumerInboxRepository repository;
    private final ResearchRlsTxInterceptor rls;
    private final java.time.Clock clock;

    public ResearchConsumerInboxService(
            ResearchConsumerInboxRepository repository,
            ResearchRlsTxInterceptor rls,
            java.time.Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.rls = Objects.requireNonNull(rls, "rls");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Claim-then-apply wrapper. The {@code body} runs the
     * actual projection mutation only when the caller won
     * the inbox row; on duplicate, the body is skipped and
     * the row is flipped to
     * {@link ResearchConsumerInboxRow.Outcome#SKIPPED_DUPLICATE}.
     *
     * @return the outcome the listener recorded.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResearchConsumerInboxRow.Outcome apply(
            String tenantId,
            String sourceTopic,
            String eventId,
            String eventType,
            String payloadJson,
            String actorPseudoId,
            String correlationId,
            Supplier<Void> body) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(body, "body");
        rls.bind();

        String payloadHash = sha256Hex(payloadJson);
        Instant now = Instant.now(clock);
        ResearchConsumerInboxRow claim = new ResearchConsumerInboxRow(
                tenantId, sourceTopic, eventId, eventType, payloadHash, now,
                null, ResearchConsumerInboxRow.Outcome.IN_FLIGHT, null,
                actorPseudoId, correlationId);

        if (!repository.tryClaim(claim)) {
            LOG.debug(
                    "research consumer inbox: duplicate event tenantId={} sourceTopic={} eventId={}",
                    tenantId, sourceTopic, eventId);
            ResearchConsumerInboxRow existing = repository
                    .find(tenantId, sourceTopic, eventId)
                    .orElseThrow(() -> new IllegalStateException(
                            "consumer inbox: tryClaim=false but row missing for "
                                    + tenantId + "/" + sourceTopic + "/" + eventId));
            ResearchConsumerInboxRow skipped = existing.withOutcome(
                    ResearchConsumerInboxRow.Outcome.SKIPPED_DUPLICATE,
                    Instant.now(clock),
                    null);
            repository.finalizeOutcome(skipped);
            return ResearchConsumerInboxRow.Outcome.SKIPPED_DUPLICATE;
        }

        Instant processedAt = Instant.now(clock);
        ResearchConsumerInboxRow.Outcome outcome = ResearchConsumerInboxRow.Outcome.FAILED;
        String lastError = null;
        try {
            body.get();
            processedAt = Instant.now(clock);
            outcome = ResearchConsumerInboxRow.Outcome.PROCESSED;
        } catch (RuntimeException e) {
            processedAt = Instant.now(clock);
            outcome = ResearchConsumerInboxRow.Outcome.FAILED;
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            // Re-throw so the @KafkaListener can route the offset to
            // the DLT (per design.md §7.3 — failed events go to DLT,
            // not back into the topic).
            throw e;
        } finally {
            ResearchConsumerInboxRow next = claim.withOutcome(outcome, processedAt, lastError);
            repository.finalizeOutcome(next);
        }
        return outcome;
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on the JVM", e);
        }
    }

    /** Visible-for-test hook to mint a stable event id. */
    public static String newEventId() {
        return UUID.randomUUID().toString();
    }
}
