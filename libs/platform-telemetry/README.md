# libs/platform-telemetry

Cross-cutting Spring Boot starter for OpenTelemetry instrumentation
shared by every Java service, BFF and worker.

Per `design.md` §13 ("OpenTelemetry SDK/agent → OTel Collector →
Prometheus, Tempo, Loki and Grafana") every workload MUST emit traces,
metrics and structured logs that share the same resource attributes,
label taxonomy and redaction guarantees. This starter is the single
source of those defaults so a service cannot accidentally bypass
them.

Concretely the starter ships:

- `OtelAutoConfiguration` — wires the OTel Java SDK with OTLP
  exporters (gRPC for SaaS, HTTP for on-prem), resource attributes
  (`service.name`, `service.version`, `deployment.environment`,
  `service.namespace`, `service.instance.id`) and propagators
  (`tracecontext`, `baggage`).
- `RedactionProcessor` — Span/LogRecord processor that strips raw
  PII, DNA, access tokens and tenant identifiers from attributes
  before export; only pseudonymous labels (`tenant_pseudo_id`,
  `user_pseudo_id`) survive.
- `KafkaTracing` and `JdbcTracing` — instrumentation hooks so
  producer/consumer hops and DB calls join the parent trace.
- `MetricsRegistry` — pre-registered RED metrics
  (`http.server.request.count`, `http.server.request.duration`,
  `process.cpu`, `jvm.memory.*`) plus a `gp.outbox.age` gauge fed by
  every service that owns a transactional outbox.
- `StructuredLogger` — Logback encoder producing JSON with the
  common fields (`timestamp`, `level`, `service`, `version`,
  `environment`, `traceId`, `spanId`, `tenantPseudoId`,
  `userPseudoId`).

This directory is intentionally empty in the E1.1 scaffold: the
Gradle module + `package-info.java` for the
`com.genealogy.platform.libs` package already exist; implementation
lands in later epics.

Owner: platform-secondary. Reviewers: SRE, Security, Privacy.
