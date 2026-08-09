# Platform — Kafka (E2.3)

Source-of-truth Kafka configuration for the genealogy platform.

## Layout

- `kafka.yaml` — canonical Strimzi `Kafka` CR (KRaft, TLS, quota).
- `topics.yaml` — canonical list of `KafkaTopic` resources (ADR-E0.5-08).
- `users.yaml` — canonical list of `KafkaUser` resources (TLS auth + ACLs).
- `metrics-config.yaml` — JMX-to-Prometheus exporter config (SLI source).

The same files are mirrored into the umbrella chart's
`files/kafka/` directory so `helm template` can render without
reading outside the chart root. Anything you change here MUST be
mirrored into the chart or the umbrella will drift.

## Validation

- `pnpm lint:kong` — Kong baseline.
- `pnpm check:platform:baseline` — extended with E2.3 invariants.
- `pnpm lint:kafka` — deep validation of `kafka.yaml`, `topics.yaml`,
  `users.yaml` (E2.3).

## Ownership

`OWNERS` mirrors `config/teams.yaml`. Primary = `platform`,
secondary = `@genealogy/data`, on-call = `data-primary`.
