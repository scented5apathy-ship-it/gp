package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set verb + resource-kind pair carried by every
 * {@link DomainCommand}. Mirrors
 * `contracts/collaboration/collaboration-policy.yaml::
 * spec.domainCommandKinds` (E6.2). The
 * {@code PartialMergeExecutor} refuses to materialise a
 * command whose kind is on the {@code forbidden} list for the
 * proposal's {@link ProposalKind}.
 */
public enum DomainCommandKind {
    CREATE_PERSON,
    UPDATE_PERSON,
    ARCHIVE_PERSON,
    CREATE_RELATIONSHIP,
    UPDATE_RELATIONSHIP,
    ARCHIVE_RELATIONSHIP,
    CREATE_LIFE_EVENT,
    UPDATE_LIFE_EVENT,
    ARCHIVE_LIFE_EVENT,
    CREATE_CLAIM,
    UPDATE_CLAIM,
    ARCHIVE_CLAIM,
    CREATE_SOURCE,
    UPDATE_SOURCE,
    ARCHIVE_SOURCE,
    CREATE_CITATION,
    UPDATE_CITATION,
    ARCHIVE_CITATION,
    SET_TREE_VISIBILITY;

    public static DomainCommandKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("domainCommandKind must not be null");
        }
        return DomainCommandKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}