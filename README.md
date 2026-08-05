# Genealogy Platform — Monorepo

Multi-tenant SaaS + on-premise genealogy platform. Privacy-first,
contract-first, ownership-driven.

The canonical specification is in
[`.kiro/specs/genealogy-platform/`](.kiro/specs/genealogy-platform/)
(`requirements.md`, `design.md`, `tasks.md`, `ownership-catalog.md`,
`architecture-decisions.md`, `agent-execution.md`).

## Stack

| Layer | Choice |
|---|---|
| Frontend | Next.js 15 + TypeScript 5.6, Tailwind + shadcn/ui |
| Backend | Java 21 + Spring Boot 3.3 + jOOQ + Flyway |
| Edge | Kong Gateway (OSS) behind CDN/WAF |
| Internal API | gRPC + Kafka + Apicurio Registry |
| Identity | Keycloak (OIDC) + OpenFGA + ABAC |
| Workflow | Temporal |
| Data | PostgreSQL 16, S3 / MinIO, Valkey |
| Orchestration | Kubernetes + Helm + Argo CD + Argo Rollouts |
| Observability | OpenTelemetry → Grafana stack |
| Repo | pnpm + Turborepo + Gradle (this repo) |

Versions pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml)
per ADR-E0.5-01.

## Layout

```
/
├── apps/                    Next.js apps (web/, web-bff/, public-api/)
├── packages/                Shared TS libs (ui/, api-client/, i18n/, eslint-config/)
├── services/                11 domain services (Java 21 + Spring Boot)
├── workers/                 Background workers (media, search, export)
├── libs/                    Shared Java libraries (errors, telemetry, ...)
├── contracts/               OpenAPI / Protobuf / Kafka envelope schemas
├── platform/                Helm, local dev, observability config
├── config/                  teams.yaml and other org-wide config
├── docs/                    Architecture / ownership docs
├── scripts/                 Monorepo boundary & lockfile checks
├── tools/                   Codegen tooling
├── package.json             pnpm workspace root
├── pnpm-workspace.yaml
├── turbo.json
├── tsconfig.base.json
├── settings.gradle.kts      Gradle multi-project root
├── build.gradle.kts
├── gradle/libs.versions.toml
└── OWNERS                   Repo-wide CODEOWNERS mirror
```

## Build commands

```bash
pnpm install                  # install JS deps + write pnpm-lock.yaml
pnpm -r typecheck             # TypeScript strict check
pnpm -r lint                  # eslint + prettier
pnpm -r test                  # vitest
pnpm build                    # turbo run build
pnpm exec scripts/check-monorepo-boundaries.mjs   # cross-service guard
pnpm exec scripts/check-monorepo-lockfile.mjs     # lockfile presence

# Java side
./gradlew --write-locks       # generate gradle.lockfile per module
./gradlew test                # JUnit 5 + ArchUnit boundary checks
./gradlew build               # full multi-project build
```

## Guardrails

1. **Strict TypeScript** via `tsconfig.base.json`.
2. **Java 21 toolchain** via `platform-build-logic.java-conventions`.
3. **Dependency locking** — pnpm-lock.yaml is committed;
   `./gradlew --write-locks` regenerates per-module gradle.lockfile.
4. **Reproducible builds** — Gradle JARs are reproducible (`isReproducibleFileOrder = true`).
5. **Service boundary** — `services/<a>/...` cannot import
   `services/<b>/db/...` or `services/<b>/domain/...`. Enforced by:
   - `services/<svc>/build.gradle.kts` ArchUnit test
   - `scripts/check-monorepo-boundaries.mjs`
6. **OWNERS / CODEOWNERS** — every service has an `OWNERS` file mirroring
   `config/teams.yaml`. `docs/ownership/OWNERS.md` documents the mirror.

## Status

This repository is being built in waves per `tasks.md`. E1.1 ships the
monorepo skeleton; full implementation lives in E1.2 → E1.6 → E2.x → E3.x
→ … → E16.