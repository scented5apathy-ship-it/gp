package com.genealogy.platform.services.collaboration.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Normalized diff attached to a {@code ChangeProposal}.
 * Mirrors `requirements.md` R10.1 (proposal diff carries
 * base version + source + reason + scope) +
 * `design.md` §8.3 (normalized patch / command list, never
 * arbitrary JSON patch on forbidden fields).
 *
 * <p>The diff is the canonical wire format between the
 * client + the collaboration-service: it carries the
 * proposer's {@code baseVersion} (the version of the
 * resource the proposer read) and the command list (the
 * exact mutations the proposer wants to apply).
 */
public record DomainDiff(
        String baseResourceId,
        long baseVersion,
        List<DomainCommand> commands) {

    public static final int MAX_COMMANDS = 256;

    public DomainDiff {
        Objects.requireNonNull(baseResourceId, "baseResourceId");
        Objects.requireNonNull(commands, "commands");
        if (baseResourceId.isBlank()) {
            throw new IllegalArgumentException("baseResourceId must not be blank");
        }
        if (baseResourceId.length() > 128) {
            throw new IllegalArgumentException(
                    "baseResourceId exceeds 128 characters");
        }
        if (!baseResourceId.matches("[A-Za-z0-9._\\-]+")) {
            throw new IllegalArgumentException(
                    "baseResourceId contains forbidden characters: " + baseResourceId);
        }
        if (baseVersion <= 0) {
            throw new IllegalArgumentException(
                    "baseVersion must be positive, got " + baseVersion);
        }
        commands = Collections.unmodifiableList(new ArrayList<>(commands));
        if (commands.isEmpty()) {
            throw new IllegalArgumentException(
                    "commands must contain at least one entry");
        }
        if (commands.size() > MAX_COMMANDS) {
            throw new IllegalArgumentException(
                    "commands exceeds " + MAX_COMMANDS + ": " + commands.size());
        }
    }

    public static DomainDiff of(
            String baseResourceId,
            long baseVersion,
            List<DomainCommand> commands) {
        return new DomainDiff(baseResourceId, baseVersion,
                commands == null ? List.of() : List.copyOf(commands));
    }
}