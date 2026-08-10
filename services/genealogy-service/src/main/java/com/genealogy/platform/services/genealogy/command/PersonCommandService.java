package com.genealogy.platform.services.genealogy.command;

import com.genealogy.platform.services.genealogy.domain.LivingStatus;
import com.genealogy.platform.services.genealogy.domain.Person;
import com.genealogy.platform.services.genealogy.domain.PersonIdentifier;
import com.genealogy.platform.services.genealogy.domain.PersonName;
import com.genealogy.platform.services.genealogy.domain.PrivacyLevel;
import com.genealogy.platform.services.genealogy.domain.Pronoun;
import com.genealogy.platform.services.genealogy.outbox.JdbcTreeOutboxWriter;
import com.genealogy.platform.services.genealogy.outbox.PersonEventPayloads;
import com.genealogy.platform.services.genealogy.persistence.PersonRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Command service for the person aggregate. Mirrors `tasks.md` E4.2:
 *
 * <ul>
 *   <li>Create / read / update (profile / names / pronouns /
 *       identifiers / gender / biography).
 *   <li>{@code setLivingStatus} and {@code changePrivacyLevel}
 *       emit dedicated events so search / public projections
 *       can re-evaluate redaction (per `design.md` §6.3).
 *   <li>Optimistic concurrency via {@code version} (CAS). Every
 *       mutation increments {@code version} by exactly 1 and the
 *       repository rejects stale versions.
 *   <li>Every command records {@code actorId}, {@code reason} and
 *       the dotted-field diff. The audit ledger receives the
 *       change once the audit consumer is wired in E4.x.
 *   <li>No implicit User↔Person linking. {@code linkVerifiedUser}
 *       is the only path and it requires an opaque id handed by
 *       the verification workflow (out of scope for E4.2).
 * </ul>
 *
 * <p>The service is framework-free at the public surface; the
 * gRPC / REST controllers (out of scope for E4.2) wrap it and
 * pass the trusted tenant context from the gRPC metadata per
 * {@code design.md} §6.1.
 */
public final class PersonCommandService {

    private final PersonRepository repository;
    private final JdbcTreeOutboxWriter outbox;

    public PersonCommandService(PersonRepository repository, JdbcTreeOutboxWriter outbox) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
    }

    public Person createPerson(CreatePersonCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Instant now = cmd.now();
        Person person = new Person(
                UUID.randomUUID().toString(),
                cmd.tenantId(),
                cmd.treeId(),
                cmd.names() == null ? List.of() : cmd.names(),
                cmd.pronouns() == null ? List.of() : cmd.pronouns(),
                cmd.identifiers() == null ? List.of() : cmd.identifiers(),
                cmd.livingStatus() == null ? LivingStatus.UNKNOWN : cmd.livingStatus(),
                cmd.privacyLevel() == null ? PrivacyLevel.PRIVATE : cmd.privacyLevel(),
                cmd.genderDescription(),
                cmd.biography(),
                null,
                com.genealogy.platform.services.genealogy.domain.PersonLifecycle.ACTIVE,
                1L,
                now,
                now,
                cmd.actorId(),
                Map.of());
        repository.insert(person);
        outbox.enqueue(
                person.personId(),
                person.tenantId(),
                PersonEventPayloads.EVENT_PERSON_CREATED,
                PersonEventPayloads.PersonCreatedEvent.fromPerson(person),
                now,
                cmd.correlationId());
        return person;
    }

    public Person updateProfile(UpdateProfileCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Person before = requireActive(cmd.tenantId(), cmd.personId());
        Person after = before.withProfile(
                cmd.names(),
                cmd.pronouns(),
                cmd.identifiers(),
                cmd.genderDescription(),
                cmd.biography(),
                cmd.now());
        repository.update(after);
        outbox.enqueue(
                after.personId(),
                after.tenantId(),
                PersonEventPayloads.EVENT_PERSON_UPDATED,
                PersonEventPayloads.PersonUpdatedEvent.fromDiff(
                        before, after, cmd.actorId(), cmd.reason(), cmd.now()),
                cmd.now(),
                cmd.correlationId());
        return after;
    }

    public Person changeLivingStatus(ChangeLivingStatusCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Person before = requireActive(cmd.tenantId(), cmd.personId());
        LivingStatus from = before.livingStatus();
        LivingStatus to = cmd.to();
        if (from == to) {
            return before;
        }
        Person after = before.withLivingStatus(to, cmd.now());
        repository.update(after);
        outbox.enqueue(
                after.personId(),
                after.tenantId(),
                PersonEventPayloads.EVENT_PERSON_LIVING_STATUS_CHANGED,
                new PersonEventPayloads.PersonLivingStatusChangedEvent(
                        after.personId(),
                        after.treeId(),
                        after.version(),
                        from.wire(),
                        to.wire(),
                        cmd.actorId(),
                        cmd.reason(),
                        cmd.now()),
                cmd.now(),
                cmd.correlationId());
        // Living-status flips force a privacy-event re-emission
        // so projections always see a consistent picture.
        if (before.privacyLevel() != after.privacyLevel()) {
            outbox.enqueue(
                    after.personId(),
                    after.tenantId(),
                    PersonEventPayloads.EVENT_PERSON_PRIVACY_CHANGED,
                    new PersonEventPayloads.PersonPrivacyChangedEvent(
                            after.personId(),
                            after.treeId(),
                            after.version(),
                            before.privacyLevel().wire(),
                            after.privacyLevel().wire(),
                            cmd.actorId(),
                            "living-status re-evaluation",
                            cmd.now()),
                    cmd.now(),
                    cmd.correlationId());
        }
        return after;
    }

    public Person changePrivacyLevel(ChangePrivacyLevelCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Person before = requireActive(cmd.tenantId(), cmd.personId());
        PrivacyLevel from = before.privacyLevel();
        PrivacyLevel to = cmd.to();
        if (from == to) {
            return before;
        }
        Person after = before.withPrivacyLevel(to, cmd.now());
        repository.update(after);
        outbox.enqueue(
                after.personId(),
                after.tenantId(),
                PersonEventPayloads.EVENT_PERSON_PRIVACY_CHANGED,
                new PersonEventPayloads.PersonPrivacyChangedEvent(
                        after.personId(),
                        after.treeId(),
                        after.version(),
                        from.wire(),
                        to.wire(),
                        cmd.actorId(),
                        cmd.reason(),
                        cmd.now()),
                cmd.now(),
                cmd.correlationId());
        return after;
    }

    public Person linkVerifiedUser(LinkVerifiedUserCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Person before = requireActive(cmd.tenantId(), cmd.personId());
        Person after = before.withVerifiedUser(cmd.userId(), cmd.now());
        repository.update(after);
        outbox.enqueue(
                after.personId(),
                after.tenantId(),
                PersonEventPayloads.EVENT_PERSON_UPDATED,
                PersonEventPayloads.PersonUpdatedEvent.fromDiff(
                        before, after, cmd.actorId(), cmd.reason(), cmd.now()),
                cmd.now(),
                cmd.correlationId());
        return after;
    }

    public Person softDelete(SoftDeleteCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Person before = requireExists(cmd.tenantId(), cmd.personId());
        if (before.lifecycleState()
                == com.genealogy.platform.services.genealogy.domain.PersonLifecycle.DELETED) {
            return before;
        }
        Person after = before.softDeleted(cmd.now());
        repository.update(after);
        outbox.enqueue(
                after.personId(),
                after.tenantId(),
                PersonEventPayloads.EVENT_PERSON_DELETED,
                new PersonEventPayloads.PersonDeletedEvent(
                        after.personId(),
                        after.treeId(),
                        after.version(),
                        cmd.actorId(),
                        cmd.reason(),
                        cmd.now()),
                cmd.now(),
                cmd.correlationId());
        return after;
    }

    private Person requireActive(String tenantId, String personId) {
        Person person = requireExists(tenantId, personId);
        if (person.lifecycleState()
                != com.genealogy.platform.services.genealogy.domain.PersonLifecycle.ACTIVE) {
            throw new IllegalStateException(
                    "person is not ACTIVE, current state: " + person.lifecycleState());
        }
        return person;
    }

    private Person requireExists(String tenantId, String personId) {
        return repository.loadFull(tenantId, personId)
                .orElseThrow(() -> new IllegalStateException(
                        "person not found: " + personId));
    }

    public record CreatePersonCommand(
            String tenantId,
            String treeId,
            List<PersonName> names,
            List<Pronoun> pronouns,
            List<PersonIdentifier> identifiers,
            LivingStatus livingStatus,
            PrivacyLevel privacyLevel,
            String genderDescription,
            String biography,
            String actorId,
            String correlationId,
            Instant now) {
    }

    public record UpdateProfileCommand(
            String tenantId,
            String personId,
            List<PersonName> names,
            List<Pronoun> pronouns,
            List<PersonIdentifier> identifiers,
            String genderDescription,
            String biography,
            String actorId,
            String reason,
            String correlationId,
            Instant now) {
    }

    public record ChangeLivingStatusCommand(
            String tenantId,
            String personId,
            LivingStatus to,
            String actorId,
            String reason,
            String correlationId,
            Instant now) {
    }

    public record ChangePrivacyLevelCommand(
            String tenantId,
            String personId,
            PrivacyLevel to,
            String actorId,
            String reason,
            String correlationId,
            Instant now) {
    }

    public record LinkVerifiedUserCommand(
            String tenantId,
            String personId,
            String userId,
            String actorId,
            String reason,
            String correlationId,
            Instant now) {
    }

    public record SoftDeleteCommand(
            String tenantId,
            String personId,
            String actorId,
            String reason,
            String correlationId,
            Instant now) {
    }
}
