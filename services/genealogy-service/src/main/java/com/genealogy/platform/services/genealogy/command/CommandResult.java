package com.genealogy.platform.services.genealogy.command;

import com.genealogy.platform.services.genealogy.domain.Tree;
import java.time.Instant;

/**
 * Domain result returned by {@code TreeCommandService}. The
 * command service emits the corresponding event via the outbox
 * publisher; this record carries the post-mutation aggregate +
 * the event envelope metadata. The caller is responsible for
 * persisting both atomically (the command service already does
 * so via the {@code TreeCommandService} transactional boundary).
 */
public record CommandResult(
        Tree tree,
        Instant occurredAt,
        String correlationId) {
}
