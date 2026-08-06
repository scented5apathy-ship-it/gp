# libs/platform-testing

Shared Java testing fixtures consumed by every service, BFF,
public-api and worker. Per `design.md` §15 ("Integration:
PostgreSQL/Kafka/S3/OIDC qua Testcontainers hoặc compatible test
environment") the fixtures wrap Testcontainers so a service test
boots the same PostgreSQL, Kafka, Keycloak, OpenFGA, Temporal, S3
and Valkey stack the runtime uses — without depending on a shared
dev environment.

Fixtures (under `src/testFixtures/java/com/genealogy/platform/testing/`):

- `PostgresFixture.java` — per-service schema + RLS seed.
- `KafkaFixture.java` — single-node Kafka + topic bootstrap.
- `KeycloakFixture.java` — realm + clients + test users.
- `OpenFgaFixture.java` — relationship model + tuples.
- `TemporalFixture.java` — dev server with namespace.
- `S3Fixture.java` — MinIO with per-bucket lifecycle.
- `ValkeyFixture.java` — single-node cache.
- `TestcontainersFixture.java`, `TestcontainersFixtures.java` —
  base classes wiring all of the above.

Owner: platform-secondary. Reviewers: every Java module owner.