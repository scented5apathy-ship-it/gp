package com.genealogy.platform.services.genealogy.outbox;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.genealogy.platform.services.genealogy.domain.LivingStatus;
import com.genealogy.platform.services.genealogy.domain.Person;
import com.genealogy.platform.services.genealogy.domain.PersonLifecycle;
import com.genealogy.platform.services.genealogy.domain.PrivacyLevel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wire-format payloads for every event the person-service
 * publishes on the transactional outbox. The outbox publisher
 * relays them to Kafka via the Apicurio-registered schemas under
 * {@code contracts/events/genealogy/v1/}.
 *
 * <p>Each record mirrors the Avro schema field-for-field. JSON is
 * the intermediate encoding used by the outbox row; the relay
 * converts JSON → Avro at publish time.
 *
 * <p>NO raw DNA, biography, identifier value, file content,
 * access token or PII is ever placed in the payload
 * (`design.md` §7.3). Names are summarised by the
 * {@code PersonNameKind}/{@code preferred} field set; identifiers
 * are summarised by {@code identifierKind}/{@code verified}; the
 * biography is NEVER included.
 */
public final class PersonEventPayloads {

    private PersonEventPayloads() {
    }

    public static final String EVENT_PERSON_CREATED = "gp.genealogy.v1.PersonCreated";
    public static final String EVENT_PERSON_UPDATED = "gp.genealogy.v1.PersonUpdated";
    public static final String EVENT_PERSON_PRIVACY_CHANGED =
            "gp.genealogy.v1.PersonPrivacyChanged";
    public static final String EVENT_PERSON_LIVING_STATUS_CHANGED =
            "gp.genealogy.v1.PersonLivingStatusChanged";
    public static final String EVENT_PERSON_DELETED = "gp.genealogy.v1.PersonDeleted";

    public record PersonCreatedEvent(
            @JsonProperty("personId") String personId,
            @JsonProperty("treeId") String treeId,
            @JsonProperty("primaryName") String primaryName,
            @JsonProperty("nameCount") int nameCount,
            @JsonProperty("livingStatus") String livingStatus,
            @JsonProperty("privacyLevel") String privacyLevel,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("createdAt") Instant createdAt) {
        public static PersonCreatedEvent fromPerson(Person person) {
            String primary = person.primaryName().map(n -> n.display()).orElse("");
            return new PersonCreatedEvent(
                    person.personId(),
                    person.treeId(),
                    primary,
                    person.names().size(),
                    person.livingStatus().wire(),
                    person.privacyLevel().wire(),
                    person.createdBy(),
                    person.createdAt());
        }
    }

    public record PersonUpdatedEvent(
            @JsonProperty("personId") String personId,
            @JsonProperty("treeId") String treeId,
            @JsonProperty("aggregateVersion") long aggregateVersion,
            @JsonProperty("previousVersion") long previousVersion,
            @JsonProperty("changedFields") List<String> changedFields,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("reason") String reason,
            @JsonProperty("updatedAt") Instant updatedAt) {
        public static PersonUpdatedEvent fromDiff(
                Person before, Person after, String actorId, String reason, Instant at) {
            List<String> fields = new ArrayList<>(Person.diff(before, after));
            return new PersonUpdatedEvent(
                    after.personId(),
                    after.treeId(),
                    after.version(),
                    before.version(),
                    Collections.unmodifiableList(fields),
                    actorId,
                    reason,
                    at);
        }
    }

    public record PersonPrivacyChangedEvent(
            @JsonProperty("personId") String personId,
            @JsonProperty("treeId") String treeId,
            @JsonProperty("aggregateVersion") long aggregateVersion,
            @JsonProperty("from") String from,
            @JsonProperty("to") String to,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("reason") String reason,
            @JsonProperty("changedAt") Instant changedAt) {
    }

    public record PersonLivingStatusChangedEvent(
            @JsonProperty("personId") String personId,
            @JsonProperty("treeId") String treeId,
            @JsonProperty("aggregateVersion") long aggregateVersion,
            @JsonProperty("from") String from,
            @JsonProperty("to") String to,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("reason") String reason,
            @JsonProperty("changedAt") Instant changedAt) {
    }

    public record PersonDeletedEvent(
            @JsonProperty("personId") String personId,
            @JsonProperty("treeId") String treeId,
            @JsonProperty("aggregateVersion") long aggregateVersion,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("reason") String reason,
            @JsonProperty("deletedAt") Instant deletedAt) {
    }

    public static String livingStatusToWire(LivingStatus status) {
        return status == null ? null : status.wire();
    }

    public static String privacyLevelToWire(PrivacyLevel level) {
        return level == null ? null : level.wire();
    }

    public static String lifecycleToWire(PersonLifecycle lifecycle) {
        return lifecycle == null ? null : lifecycle.wire();
    }
}
