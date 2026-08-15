# Genealogy Platform — Operator Runbook Index

> **Status:** E14.5 baseline; evidence in
> `.kiro/specs/genealogy-platform/evidence/E14.5.md`.
> Mirrors
> `contracts/disaster-recovery/operator-runbook-policy.yaml`.

## 1. Mandatory procedures

| Procedure | Owner | Severity | Runbook | Evidence |
 | | | | | |
| install | sre_primary | SEV2 | [onprem-bundle.md](./onprem-bundle.md) | [E14.3](../specs/genealogy-platform/evidence/E14.3.md) |
| configuration | sre_primary | SEV3 | [onprem-bundle.md](./onprem-bundle.md) | [E14.3](../specs/genealogy-platform/evidence/E14.3.md) |
| scaling | sre_primary | SEV3 | [capacity.md](./capacity.md) | [E13.3](../specs/genealogy-platform/evidence/E13.3.md) |
| backup | sre_primary | SEV2 | [backup.md](./backup.md) | [E14.1](../specs/genealogy-platform/evidence/E14.1.md) |
| restore | sre_primary | SEV1 | [backup.md](./backup.md) | [E14.1](../specs/genealogy-platform/evidence/E14.1.md) |
| key_rotation | security_engineer | SEV2 | [key-rotation.md](./key-rotation.md) | [E14.1](../specs/genealogy-platform/evidence/E14.1.md) |
| troubleshooting | sre_primary | SEV2 | [troubleshooting.md](./troubleshooting.md) | [E13.1](../specs/genealogy-platform/evidence/E13.1.md) |
| support_bundle | sre_primary | SEV3 | [support-bundle.md](./support-bundle.md) | [E14.5](../specs/genealogy-platform/evidence/E14.5.md) |

## 2. Disaster recovery

- [disaster-recovery.md](./disaster-recovery.md) — DR drill
  procedures; evidence [E14.2](../specs/genealogy-platform/evidence/E14.2.md).
- [upgrade-rollback.md](./upgrade-rollback.md) — Upgrade
  + rollback procedures; evidence
  [E14.4](../specs/genealogy-platform/evidence/E14.4.md).
- [resilience.md](./resilience.md) — Game day + chaos
  procedures; evidence
  [E13.4](../specs/genealogy-platform/evidence/E13.4.md).
- [slo.md](./slo.md) — SEV1..SEV4 + burn-rate cookbook;
  evidence
  [E13.2](../specs/genealogy-platform/evidence/E13.2.md).

## 3. Service-specific runbooks

- [tenant-service.md](./tenant-service.md) — Tenant
  operations + on-call (defer E13 dashboards).
- [research-service.md](./research-service.md) — Research
  + collaboration.
- [audit-service.md](./audit-service.md) — Audit
  pipeline + retention.

## 4. Support channels

| Channel | Use |
| ------- | --- |
| `portal` | default ticketing |
| `email` | asynchronous, low severity |
| `phone_sev1` | 24×7, immediate |
| `phone_sev2` | 24×7, ≤ 60 min |
| `chat_secure` | in-band, security |

Ad-hoc contacts (personal email / phone / chat) are
**forbidden** by the contract.

## 5. On-call rotations

| Rotation | Coverage |
| -------- | -------- |
| `sre_primary_24x7` | platform + tenant issues |
| `sre_secondary_24x7` | escalation backup |
| `security_engineer_business_hours` | security incidents |
| `dpo_delegate_business_hours` | privacy + consent |
| `product_owner_business_hours` | change approvals |

Each rotation is wired to PagerDuty / OpsGenie; manual
rosters are **forbidden**.

## 6. Shared-responsibility matrix

| Area | Owner |
| ---- | ----- |
| `kubernetes_cluster` | customer_managed |
| `postgres_database` | platform_managed |
| `kafka_cluster` | platform_managed |
| `object_storage` | customer_managed |
| `keycloak_realm` | platform_managed |
| `openfga_store` | platform_managed |
| `temporal_namespace` | platform_managed |
| `vault_kv` | platform_managed |
| `flagsmith_environment` | platform_managed |
| `tls_certificates` | customer_managed |
| `dns_records` | customer_managed |
| `on_call_rotation` | platform_managed |
| `upgrade_testing` | platform_managed |

## 7. SLA matrix

| Severity | Acknowledge | Resolve |
 | -------- | ----------- | ------- |
| SEV1 | 15 min | 4 h |
| SEV2 | 60 min | 24 h |
| SEV3 | 4 h | 5 d |
| SEV4 | 24 h | next sprint |

## 8. Review cadence

Every runbook is reviewed every 90 days. A runbook
`lastReviewedAt` older than 90 days ⇒ SEV2 + draft
update; the linter refuses to admit a procedure with
`daysSinceReview > 90`.