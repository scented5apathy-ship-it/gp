# libs/platform-testing/src/testFixtures

JUnit `@TestFixtures` consumed by every Java module's integration
tests. Per `design.md` §15 the fixtures wrap Testcontainers so
integration tests boot the same PostgreSQL, Kafka, Keycloak,
OpenFGA, Temporal, S3 and Valkey stack as the runtime.

Fixtures:

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