# libs/platform-spring-boot-starter/src/test

Starter sanity tests.

- `PlatformSpringBootStarterTest.java` — Spring Boot test context
  that boots the starter against the test fixtures in
  `libs/platform-testing/src/testFixtures/`. Verifies the
  autoconfiguration wiring, logback JSON encoder, trusted context
  filter and safe feature client.

Owner: platform-secondary. Reviewers: Security, Privacy.