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
- `platform-setup.md` — **một điểm vào duy nhất** cho việc cài
  toàn bộ platform E2.x (E2.1 baseline + E2.2 Kong + E2.3
  Kafka+Apicurio + E2.4 Temporal + …) trên Kubernetes. Bao gồm
  prerequisites chung, baseline, umbrella chart install, verify,
  upgrade/rollback, backup/restore, troubleshooting matrix chung,
  và checklist cho từng component.
- `e23-kafka-apicurio-setup.md` — chi tiết E2.3 (Strimzi operator,
  Postgres, namespace, troubleshoot 4 mục). Dùng làm tham chiếu
  khi cần drill-down vào Kafka / Apicurio.
- `e24-temporal-setup.md` — chi tiết E2.4 (DB + secret + Helm-hook
  Job + troubleshoot 12 mục). Dùng làm tham chiếu khi cần
  drill-down vào Temporal.
