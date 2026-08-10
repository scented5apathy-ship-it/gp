package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Person aggregate root. Mirrors `requirements.md` R4 and
 * `design.md` §5.2. Decoupled from {@code User} — the
 * {@code verifiedUserId} field is null until a verification
 * workflow binds a Keycloak subject to the person (E4.x).
 *
 * <p>Invariant highlights (R4.1 / R4.4 / R4.6 / NFR4):
 *
 * <ul>
 *   <li>At most one {@link NameKind#BIRTH} and at most one
 *       {@link NameKind#PREFERRED} attached at any time.
 *   <li>Up to {@code person-policy.yaml::spec.maxNamesPerPerson}
 *       names (default 16); same for identifiers and pronouns.
 *   <li>Biography ≤ {@code spec.maxBiographyChars} (default 8192).
 *   <li>User↔Person link REQUIRES verification
 *       ({@code person-policy.yaml::spec.userLinkRequiresVerification}).
 *   <li>Privacy level + living status transitions emit dedicated
 *       events so search / public projections can rebuild.
 *   <li>{@code version} is monotonic; every mutation increments
 *       by exactly 1 (CAS-friendly).
 *   <li>{@code history} is an append-only log per
 *       `requirements.md` R4.6 / design.md §6.2 obligations.
 * </ul>
 */
public record Person(
        String personId,
        String tenantId,
        String treeId,
        List<PersonName> names,
        List<Pronoun> pronouns,
        List<PersonIdentifier> identifiers,
        LivingStatus livingStatus,
        PrivacyLevel privacyLevel,
        String genderDescription,
        String biography,
        String verifiedUserId,
        PersonLifecycle lifecycleState,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        Map<String, String> auditAttributes) {

    /** Biography cap mirrors `person-policy.yaml::spec.maxBiographyChars`. */
    public static final int MAX_BIOGRAPHY_CHARS = 8192;
    /** Name cap mirrors `person-policy.yaml::spec.maxNamesPerPerson`. */
    public static final int MAX_NAMES = 16;
    /** Identifier cap mirrors `person-policy.yaml::spec.maxIdentifiersPerPerson`. */
    public static final int MAX_IDENTIFIERS = 16;
    /** Pronoun cap mirrors `person-policy.yaml::spec.maxPronounsPerPerson`. */
    public static final int MAX_PRONOUNS = 4;

    private static final Pattern GENDER_DESCRIPTION_PATTERN =
            Pattern.compile("^[A-Z_]{2,32}$");

    public Person {
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(treeId, "treeId");
        Objects.requireNonNull(livingStatus, "livingStatus");
        Objects.requireNonNull(privacyLevel, "privacyLevel");
        Objects.requireNonNull(lifecycleState, "lifecycleState");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(createdBy, "createdBy");
        names = names == null ? List.of() : Collections.unmodifiableList(names);
        pronouns = pronouns == null ? List.of() : Collections.unmodifiableList(pronouns);
        identifiers = identifiers == null
                ? List.of()
                : Collections.unmodifiableList(identifiers);
        auditAttributes = auditAttributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(auditAttributes));
        if (names.size() > MAX_NAMES) {
            throw new IllegalArgumentException(
                    "names exceeds " + MAX_NAMES + " rows: " + names.size());
        }
        if (identifiers.size() > MAX_IDENTIFIERS) {
            throw new IllegalArgumentException(
                    "identifiers exceeds " + MAX_IDENTIFIERS + " rows: "
                            + identifiers.size());
        }
        if (pronouns.size() > MAX_PRONOUNS) {
            throw new IllegalArgumentException(
                    "pronouns exceeds " + MAX_PRONOUNS + " rows: " + pronouns.size());
        }
        if (biography != null && biography.length() > MAX_BIOGRAPHY_CHARS) {
            throw new IllegalArgumentException(
                    "biography exceeds " + MAX_BIOGRAPHY_CHARS + " chars: "
                            + biography.length());
        }
        if (genderDescription != null
                && !GENDER_DESCRIPTION_PATTERN.matcher(genderDescription).matches()) {
            throw new IllegalArgumentException(
                    "genderDescription not in closed-set UPPER_SNAKE form: "
                            + genderDescription);
        }
        if (verifiedUserId != null && verifiedUserId.isBlank()) {
            throw new IllegalArgumentException("verifiedUserId must be null or opaque id");
        }
        // At most one BIRTH + at most one PREFERRED (R4.1).
        long birthCount = names.stream().filter(n -> n.kind() == NameKind.BIRTH).count();
        long preferredCount =
                names.stream().filter(n -> n.preferred() || n.kind() == NameKind.PREFERRED).count();
        if (birthCount > 1) {
            throw new IllegalArgumentException("at most one BIRTH name is allowed");
        }
        if (preferredCount > 1) {
            throw new IllegalArgumentException("at most one PREFERRED name is allowed");
        }
        // Duplicate (kind, display) is rejected.
        long distinctKindDisplay = names.stream()
                .map(n -> n.kind().wire() + "|" + n.display().toLowerCase(Locale.ROOT))
                .distinct()
                .count();
        if (distinctKindDisplay != names.size()) {
            throw new IllegalArgumentException("duplicate (kind, display) name");
        }
    }

    /** Prefer the PREFERRED name; fall back to BIRTH; else first name; else null. */
    public Optional<PersonName> primaryName() {
        for (PersonName name : names) {
            if (name.preferred() || name.kind() == NameKind.PREFERRED) {
                return Optional.of(name);
            }
        }
        for (PersonName name : names) {
            if (name.kind() == NameKind.BIRTH) {
                return Optional.of(name);
            }
        }
        return names.isEmpty() ? Optional.empty() : Optional.of(names.get(0));
    }

    public Person withLivingStatus(LivingStatus next, Instant at) {
        return new Person(
                personId, tenantId, treeId, names, pronouns, identifiers,
                next, privacyLevel, genderDescription, biography,
                verifiedUserId, lifecycleState, version + 1, createdAt, at,
                createdBy, auditAttributes);
    }

    public Person withPrivacyLevel(PrivacyLevel next, Instant at) {
        return new Person(
                personId, tenantId, treeId, names, pronouns, identifiers,
                livingStatus, next, genderDescription, biography,
                verifiedUserId, lifecycleState, version + 1, createdAt, at,
                createdBy, auditAttributes);
    }

    public Person withProfile(
            List<PersonName> nextNames,
            List<Pronoun> nextPronouns,
            List<PersonIdentifier> nextIdentifiers,
            String nextGenderDescription,
            String nextBiography,
            Instant at) {
        return new Person(
                personId, tenantId, treeId,
                nextNames == null ? names : nextNames,
                nextPronouns == null ? pronouns : nextPronouns,
                nextIdentifiers == null ? identifiers : nextIdentifiers,
                livingStatus, privacyLevel,
                nextGenderDescription == null ? genderDescription : nextGenderDescription,
                nextBiography == null ? biography : nextBiography,
                verifiedUserId, lifecycleState, version + 1, createdAt, at,
                createdBy, auditAttributes);
    }

    public Person withVerifiedUser(String opaqueUserId, Instant at) {
        if (opaqueUserId == null || opaqueUserId.isBlank()) {
            throw new IllegalArgumentException("verifiedUserId must be opaque id");
        }
        return new Person(
                personId, tenantId, treeId, names, pronouns, identifiers,
                livingStatus, privacyLevel, genderDescription, biography,
                opaqueUserId, lifecycleState, version + 1, createdAt, at,
                createdBy, auditAttributes);
    }

    public Person softDeleted(Instant at) {
        return new Person(
                personId, tenantId, treeId, names, pronouns, identifiers,
                livingStatus, privacyLevel, genderDescription, biography,
                verifiedUserId, PersonLifecycle.DELETED, version + 1, createdAt, at,
                createdBy, auditAttributes);
    }

    /** Dotted-field diff between two versions. Returns closed-set field names. */
    public static java.util.LinkedHashSet<String> diff(Person before, Person after) {
        java.util.LinkedHashSet<String> fields = new java.util.LinkedHashSet<>();
        if (!before.names.equals(after.names)) fields.add("names[]");
        if (!before.pronouns.equals(after.pronouns)) fields.add("pronouns[]");
        if (!before.identifiers.equals(after.identifiers)) fields.add("identifiers[]");
        if (before.livingStatus != after.livingStatus) fields.add("livingStatus");
        if (before.privacyLevel != after.privacyLevel) fields.add("privacyLevel");
        if (!java.util.Objects.equals(before.genderDescription, after.genderDescription)) {
            fields.add("genderDescription");
        }
        if (!java.util.Objects.equals(before.biography, after.biography)) fields.add("biography");
        if (!java.util.Objects.equals(before.verifiedUserId, after.verifiedUserId)) {
            fields.add("verifiedUserId");
        }
        return fields;
    }
}
