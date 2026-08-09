# docs

Repository documentation that complements the spec sources in
`.kiro/specs/genealogy-platform/`. Per `AGENTS.md` §1 specs are
authoritative; everything in this directory is guidance or
operations material.

Sub-directories:

- `ownership/` — mirror of `OWNERS` + `CODEOWNERS` with the team
  mapping.

Files:

- `local-toolchain-setup.md` — cài Docker, pnpm, helm cơ bản cho
  dev workstation.
- `local-k8s-setup.md` — dựng cluster Kubernetes local (kind) +
  verify + smoke E2.3 trên cluster thật.
- `e23-kafka-apicurio-setup.md` — hướng dẫn triển khai E2.3
  (Strimzi Kafka + Apicurio Registry) trên Kubernetes (production)
  và Docker Compose (local dev).