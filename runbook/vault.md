# Runbook — Vault + cloud KMS abstraction (E2.6)

## Source of truth

| File                                                            | Purpose                                                                                         |
| --------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `platform/vault/server-config.yaml`                             | Vault server HCL (storage Raft + listener + seal + telemetry)                                   |
| `platform/vault/auth-methods.yaml`                              | Auth methods (Kubernetes + Keycloak JWT + GitHub Actions AppRole) + per-namespace role bindings |
| `platform/vault/policies.yaml`                                  | Deny-all default + per-component ACL list                                                       |
| `platform/vault/kms-abstraction.yaml`                           | `KmsProvider` contract + active provider per env + per-data-class key                           |
| `platform/vault/injector-templates.yaml`                        | Vault Agent Injector annotations per workload class                                             |
| `platform/helm/genealogy-platform/files/vault/*.yaml`           | Mirror of the five above (chart `helm template` reads from here)                                |
| `platform/helm/genealogy-platform/templates/components/vault/*` | Helm templates for the StatefulSet + Services + SA + bootstrap Job + ConfigMaps + NetworkPolicy |
| `platform/observability/alerts/vault-rules.yaml`                | 11 Prometheus alerts across 5 rule groups                                                       |

The platform deep linter is `scripts/lint-vault-config.mjs`
(`pnpm lint:vault`). The structural baseline check is
`scripts/check-platform-baseline.mjs` (E2.6 invariants).

## Recovery procedures

Each playbook requires `kubectl` + `vault` CLI (1.17.x) on
PATH and a working kubecontext. The umbrella chart's
`vault-bootstrap` Job is the canonical path to apply the
source-of-truth configs; operators never `vault write` directly
per `design.md` §13.

### 1. Server down — `<runbook:vault#server-down>`

`VaultServerDown` fired (Vault unreachable for 2m).

1. `kubectl -n gp-data get pods -l app.kubernetes.io/component=vault`
   — confirm the StatefulSet is down.
2. `kubectl -n gp-data describe statefulset vault` — check
   events (image pull errors, OOMKilled, node eviction).
3. `kubectl -n gp-data logs -l app.kubernetes.io/component=vault
--tail=500` — search for `panic`, `error`, `FATAL`.
4. If the StatefulSet crashed during a chart upgrade, rollback:
   `kubectl -n gp-data rollout undo statefulset/vault`.
5. If the underlying `gp-data-ssd` PVC is full, expand it:
   `kubectl -n gp-data edit pvc data-vault-0` (set
   `spec.resources.requests.storage`).
6. Once Vault is Ready, re-run the bootstrap Job:
   `kubectl -n gp-data create job --from=cronjob/vault-bootstrap
vault-bootstrap-restart` (or `helm upgrade` chart).

### 2. Vault sealed — `<runbook:vault#sealed>`

`VaultSealed` fired (Vault has been sealed for 1m).

1. `kubectl -n gp-data exec vault-0 -- vault status` — confirm
   the cluster is sealed.
2. Identify the seal type:
   `kubectl -n gp-data get pod vault-0 -o jsonpath='{.spec.containers[0].env}'`
   and look for `VAULT_SEAL_TYPE`.
3. **For `awskms` (SaaS):** the KMS provider should auto-unseal.
   Check the IAM role for the service account (IRSA / pod
   identity) — a missing role binding or a rotated IAM key
   breaks auto-unseal. Validate with
   `kubectl -n gp-data exec vault-0 -- vault operator unseal`
   (the call should no-op if awskms is healthy).
4. **For `transit` (on-prem):** the transit token must be valid.
   `kubectl -n gp-data get secret vault-bootstrap-token -o
yaml | jq -r .data.token | base64 -d` — if expired, rotate
   it via the bootstrap Vault cluster.
5. **For `shamir` (dev only):** unseal manually with the
   persisted keys:
   `kubectl -n gp-data exec vault-0 -- vault operator unseal
<key>` (×3 for key-threshold=3).
6. Once unsealed, the bootstrap Job is a no-op on re-run.

### 3. KMS provider unhealthy — `<runbook:vault#kms-unhealthy>`

`VaultKMSProviderUnhealthy` / `VaultKMSProviderUnhealthyCritical`
fired.

1. Identify the seal type (`VAULT_SEAL_TYPE` env var on the
   StatefulSet).
2. **For `awskms`:** check the AWS KMS regional endpoint
   health (CloudWatch or the AWS Console). Validate the IRSA
   role binding:
   `kubectl -n gp-data describe sa vault | grep
eks.amazonaws.com/role-arn`.
3. **For `transit`:** the bootstrap Vault cluster may be down.
   Restore from snapshot (`vault operator raft snapshot
restore`) per `<runbook:vault#backup-restore>`.
4. **For `shamir`:** KMS provider is not in use — the alert
   should NOT fire. If it does, check the seal stanza in the
   rendered HCL.

### 4. Token count high — `<runbook:vault#token-count-high>`

`VaultTokenCountHigh` (50k) / `VaultTokenCountCritical` (100k)
fired.

1. `vault token list -format=json | jq '.[] | select(.policies
!= [])'` — find the workload creating tokens.
2. A leaking workload is the canonical cause. Check the
   workload's `vault.hashicorp.com/agent-revoke-on-shutdown`
   annotation — if missing, tokens are not revoked on
   shutdown.
3. Identify the workload namespace + SA:
   `vault token capabilities <token>` (requires root).
4. Restart the workload; the new pod should rotate tokens.
5. For a critical alert, page security on-call — a leaked
   token may be in use outside the workload lifecycle.

### 5. Raft storage low — `<runbook:vault#storage-low>`

`VaultRaftStorageLowDisk` fired.

1. `kubectl -n gp-data exec vault-0 -- df -h /vault/file` —
   confirm the disk usage.
2. Audit the largest KV mount: `vault secrets list` →
   `vault kv list secret/data/<mount>` → count entries.
3. Old audit entries can be rotated per Vault's audit log
   rotation policy (`platform/vault/server-config.yaml`).
4. Expand the StatefulSet PVC:
   `kubectl -n gp-data edit pvc data-vault-0` (set
   `spec.resources.requests.storage` to a larger value).

### 6. Raft no leader — `<runbook:vault#raft-no-leader>`

`VaultRaftNoLeader` fired (no leader election for 2m).

1. `kubectl -n gp-data logs -l app.kubernetes.io/component=vault
--tail=500 | grep -i raft` — find the leader election
   failure.
2. The most common cause is a network partition between Vault
   pods. Cross-check the NetworkPolicy + the Istio
   AuthorizationPolicy.
3. If 2 of 3 replicas are down, quorum is lost. Restore the
   third replica first.
4. Force a leader election (advanced):
   `kubectl -n gp-data exec vault-0 -- vault operator raft
force-leader` — only when 2 of 3 are healthy.

### 7. Bootstrap Job failed — `<runbook:vault#bootstrap-failed>`

`VaultBootstrapJobFailed` fired.

1. `kubectl -n gp-data get jobs -l
app.kubernetes.io/component=vault-bootstrap` — confirm
   the Job Pod failed.
2. `kubectl -n gp-data logs -l
app.kubernetes.io/component=vault-bootstrap --tail=200` —
   find the bootstrap step that failed.
3. Common failures:
   - Vault was already initialised but the root token
     Secret was deleted — re-run with
     `vault-bootstrap-token` recreated.
   - The KMS provider returned an error during auto-unseal
     — fix the seal stanza + re-run.
   - The `kubernetes` auth method host is unreachable —
     cross-check the NetworkPolicy + the in-cluster API
     server.
4. After fixing the source-of-truth, re-run `helm upgrade` —
   the chart will delete the failed Job and recreate it.

## Backup / restore

Per `tasks.md` E14.1, Vault backups consist of:

1. Raft snapshot — taken daily at 04:00 UTC (E14.1
   procedure). The snapshot is encrypted with a dedicated
   KEK from the customer's KMS (SaaS) or HSM (on-prem).
2. `vault audit enable -path=file file` is forbidden — the
   audit log is shipped to the OTel log pipeline (E2.10)
   which redacts PII / DNA per `privacy-and-legal-gate.md`
   §11.
3. KV v2 mounts are NOT backed up directly — the Raft
   snapshot contains the full KV state.

DR restoration is a Helm rollback + a Raft snapshot restore:

```bash
# 1. Roll back the chart.
helm history genealogy-platform -n gp-platform
helm rollback genealogy-platform <revision> -n gp-platform

# 2. Restore the Raft snapshot (if the failure is beyond
#    the chart's reach).
kubectl -n gp-data exec vault-0 -- \
  vault operator raft snapshot restore /tmp/vault-restore.snap
```

## Ownership

`OWNERS` mirror — primary = `platform-secondary`, secondary =
`@genealogy/security`, on-call = `sre-primary`.
