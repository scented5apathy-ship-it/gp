package com.genealogy.platform.services.genealogy.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.genealogy.domain.LivingStatus;
import com.genealogy.platform.services.genealogy.domain.Person;
import com.genealogy.platform.services.genealogy.domain.PersonLifecycle;
import com.genealogy.platform.services.genealogy.domain.PersonName;
import com.genealogy.platform.services.genealogy.domain.PrivacyLevel;
import com.genealogy.platform.services.genealogy.domain.NameKind;
import com.genealogy.platform.services.genealogy.outbox.InMemoryOutboxWriter;
import com.genealogy.platform.services.genealogy.outbox.JdbcTreeOutboxWriter;
import com.genealogy.platform.services.genealogy.outbox.PersonEventPayloads;
import com.genealogy.platform.services.genealogy.persistence.InMemoryPersonRepository;
import com.genealogy.platform.services.genealogy.persistence.PersonRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PersonCommandServiceTest {

    private InMemoryPersonRepository repo;
    private InMemoryOutboxWriter outbox;
    private PersonCommandService service;

    @BeforeEach
    void setup() {
        repo = new InMemoryPersonRepository();
        outbox = new InMemoryOutboxWriter();
        service = new PersonCommandService(repo, shim());
    }

    private JdbcTreeOutboxWriter shim() {
        return new JdbcTreeOutboxWriter(mock(DataSource.class), new ObjectMapper()) {
            @Override
            public String enqueue(String aggregateId, String tenantId, String eventType,
                                  Object payload, Instant occurredAt, String correlationId) {
                return outbox.enqueue(aggregateId, tenantId, eventType, payload,
                        occurredAt, correlationId);
            }
        };
    }

    private PersonName birth(String display) {
        return new PersonName(
                "name-" + display, NameKind.BIRTH, "Latn", "en-US",
                display, null, false, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void createEmitsPersonCreatedEvent() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        Person person = service.createPerson(new PersonCommandService.CreatePersonCommand(
                "tenant-1", "tree-1", List.of(birth("An")), List.of(),
                List.of(), LivingStatus.LIVING, PrivacyLevel.PRIVATE,
                "MALE", "short bio", "user-1", "corr-1", t0));
        assertEquals(PersonLifecycle.ACTIVE, person.lifecycleState());
        assertEquals(1, outbox.countsByEventType()
                .getOrDefault(PersonEventPayloads.EVENT_PERSON_CREATED, 0L));
    }

    @Test
    void updateEmitsPersonUpdatedEventWithDiff() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        Person person = service.createPerson(new PersonCommandService.CreatePersonCommand(
                "tenant-1", "tree-1", List.of(birth("An")), List.of(),
                List.of(), LivingStatus.LIVING, PrivacyLevel.PRIVATE,
                "MALE", null, "user-1", "corr-1", t0));
        Instant t1 = t0.plusSeconds(60);
        Person updated = service.updateProfile(new PersonCommandService.UpdateProfileCommand(
                "tenant-1", person.personId(),
                List.of(birth("An")),
                List.of(),
                List.of(),
                "MALE",
                "expanded bio",
                "user-1", "editing", "corr-2", t1));
        assertEquals(2L, updated.version());
        assertEquals(1, outbox.countsByEventType()
                .getOrDefault(PersonEventPayloads.EVENT_PERSON_UPDATED, 0L));
    }

    @Test
    void livingStatusChangeEmitsLivingStatusChangedEvent() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        Person person = service.createPerson(new PersonCommandService.CreatePersonCommand(
                "tenant-1", "tree-1", List.of(birth("An")), List.of(),
                List.of(), LivingStatus.UNKNOWN, PrivacyLevel.PRIVATE,
                "MALE", null, "user-1", "corr-1", t0));
        Instant t1 = t0.plusSeconds(60);
        service.changeLivingStatus(new PersonCommandService.ChangeLivingStatusCommand(
                "tenant-1", person.personId(), LivingStatus.DECEASED,
                "user-1", "obituary received", "corr-2", t1));
        assertEquals(1, outbox.countsByEventType()
                .getOrDefault(PersonEventPayloads.EVENT_PERSON_LIVING_STATUS_CHANGED, 0L));
    }

    @Test
    void privacyChangeEmitsPersonPrivacyChangedEvent() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        Person person = service.createPerson(new PersonCommandService.CreatePersonCommand(
                "tenant-1", "tree-1", List.of(birth("An")), List.of(),
                List.of(), LivingStatus.LIVING, PrivacyLevel.PRIVATE,
                "MALE", null, "user-1", "corr-1", t0));
        Instant t1 = t0.plusSeconds(60);
        service.changePrivacyLevel(new PersonCommandService.ChangePrivacyLevelCommand(
                "tenant-1", person.personId(), PrivacyLevel.PUBLIC,
                "user-1", "publishing to public tree", "corr-2", t1));
        assertEquals(1, outbox.countsByEventType()
                .getOrDefault(PersonEventPayloads.EVENT_PERSON_PRIVACY_CHANGED, 0L));
    }

    @Test
    void softDeleteEmitsPersonDeletedEventAndIsTerminal() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        Person person = service.createPerson(new PersonCommandService.CreatePersonCommand(
                "tenant-1", "tree-1", List.of(birth("An")), List.of(),
                List.of(), LivingStatus.LIVING, PrivacyLevel.PRIVATE,
                "MALE", null, "user-1", "corr-1", t0));
        Instant t1 = t0.plusSeconds(60);
        Person deleted = service.softDelete(new PersonCommandService.SoftDeleteCommand(
                "tenant-1", person.personId(), "user-1", "GDPR request", "corr-2", t1));
        assertEquals(PersonLifecycle.DELETED, deleted.lifecycleState());
        assertEquals(1, outbox.countsByEventType()
                .getOrDefault(PersonEventPayloads.EVENT_PERSON_DELETED, 0L));
        assertThrows(IllegalStateException.class, () -> service.changePrivacyLevel(
                new PersonCommandService.ChangePrivacyLevelCommand(
                        "tenant-1", person.personId(), PrivacyLevel.PUBLIC,
                        "user-1", "should fail", "corr-3", t1.plusSeconds(60))));
    }

    @Test
    void linkVerifiedUserRequiresOpaqueUserId() {
        Instant t0 = Instant.parse("2026-08-10T10:00:00Z");
        Person person = service.createPerson(new PersonCommandService.CreatePersonCommand(
                "tenant-1", "tree-1", List.of(birth("An")), List.of(),
                List.of(), LivingStatus.LIVING, PrivacyLevel.PRIVATE,
                "MALE", null, "user-1", "corr-1", t0));
        Instant t1 = t0.plusSeconds(60);
        Person linked = service.linkVerifiedUser(
                new PersonCommandService.LinkVerifiedUserCommand(
                        "tenant-1", person.personId(), "keycloak-sub-123",
                        "user-1", "verification workflow", "corr-2", t1));
        assertTrue(linked.verifiedUserId() != null);
        assertEquals("keycloak-sub-123", linked.verifiedUserId());
        assertEquals(2L, linked.version());
    }
}
