# Runbook — Flagsmith + OpenFeature (E2.8)

## Source of truth

| File | Purpose |
| ---- | ------- |
| `platform/featureflags/flagsmith-server.yaml` | Server image pin, backing store, CORS allowlist, audit, telemetry, rate limit, cache, replicas |
| `platform/featureflags/environments.yaml` | 5 environments + organisation + 4 RBAC roles |
| `platform/featureflags/flag-taxonomy.yaml` | Canonical flag list (8 legal-gate + 4 rollout + 2 segment overrides) |
| `platform/featureflags/safe-defaults.yaml` | OpenFeature SDK safe-default rules + audit event contract |
| `platform/featureflags/sdk-config.yaml` | Bootstrap Job + SDK wiring (Spring Boot + Next.js) + Kong route + resilience |
| `platform/featureflags/OWNERS` | Mirrors `config/teams.yaml` |
| `platform/helm/genealogy-platform/files/featureflags/*.yaml` | Mirror of the five above (chart `helm template` reads from here) |
| `platform/helm/genealogy-platform/templates/components/featureflags/*` | 7 Helm templates: Deployment + Services + SAs + Secrets + ConfigMaps + bootstrap Job + NetworkPolicy |
| `platform/observability/alerts/flagsmith-rules.yaml` | 8 Prometheus alerts across 4 rule groups |

The platform deep linter is `scripts/lint-flagsmith-config.mjs`
(`pnpm lint:flagsmith`). The structural baseline check is
`scripts/check-platform-baseline.mjs` (E2.8 invariants).

## Recovery procedures

Each playbook requires `kubectl` + `curl` on PATH and a
working kubecontext. The umbrella chart's
`featureflags-bootstrap` Job is the canonical path to apply
the source-of-truth configs; operators never edit Flagsmith
via the admin UI directly per `design.md` §13.

### 1. Server down — `<runbook:flagsmith#server-down>`

`FlagsmithServerDown` fired (Flagsmith unreachable for 2m).

1. `kubectl -n gp-platform get pods -l app.kubernetes.io/component=featureflags`
   — confirm the Deployment is down.
2. `kubectl -n gp-platform describe deployment flagsmith` — check
   events (image pull errors, OOMKilled, Postgres connection
   refused).
3. `kubectl -n gp-platform logs -l app.kubernetes.io/component=featureflags
   --tail=500` — search for `panic`, `error`, `FATAL`.
4. Verify Postgres is reachable: `kubectl -n gp-data get pods -l app=postgres`.
5. The OpenFeature SDK MUST fall back to the typed safe
   default during the outage. Verify consumers are not
   observing `null` by checking the
   `FlagsmithDefaultUsedRateHigh` alert (it should fire in
   parallel; the runbook drill below walks the consumer
   audit log).

### 2. API latency high / critical — `<runbook:flagsmith#api-latency>`

`FlagsmithApiLatencyHigh` (> 500ms p95) or
`FlagsmithApiLatencyCritical` (> 1s p95) fired.

1. `kubectl -n gp-platform exec deploy/flagsmith -- curl -s http://localhost:8000/health`
   — verify the server is up and responsive.
2. Check Postgres latency: `pg_stat_statements` on the
   Flagsmith database.
3. The OpenFeature SDK evaluation timeout is 200ms; high
   latency causes SDK to fall back to the safe default.
4. Verify segment override correctness via
   `kubectl -n gp-platform get configmap genea-flagsmith-flag-taxonomy -o yaml`.

### 3. Eval error rate / default-used rate high — `<runbook:flagsmith#eval-errors>`

`FlagsmithEvalErrorRateHigh` (> 5%) or
`FlagsmithDefaultUsedRateHigh` (> 20%) fired.

1. Check the SDK wiring:
   `kubectl -n gp-{bff,services,workers} get pods -o jsonpath='{.items[*].spec.containers[*].env}'`
   — verify `platform.openfeature.provider=flagsmith` is set.
2. Check the Flagsmith base URL:
   `kubectl -n gp-{bff,services,workers} get pods -o jsonpath='{.items[*].spec.containers[*].env}'`
   — verify `platform.openfeature.flagsmith-base-url` resolves.
3. Verify the flag taxonomy matches the live Flagsmith
   environment via `curl http://flagsmith.gp-platform.svc.cluster.local:8000/api/v1/environments/{envId}/features/`.
4. Possible causes: SDK misconfiguration, missing flag
   definition, or provider persistently unreachable.

### 4. Bootstrap Job failed — `<runbook:flagsmith#bootstrap-failed>`

`FlagsmithBootstrapJobFailed` fired.

1. `kubectl -n gp-platform get jobs -l app.kubernetes.io/job=featureflags-bootstrap`
   — find the failed Job.
2. `kubectl -n gp-platform logs job/{job-name}` — check the
   error output.
3. Most likely cause: drift between
   `platform/featureflags/flag-taxonomy.yaml` and the live
   Flagsmith environment. Re-run the bootstrap with
   `--force` to overwrite, OR update the source-of-truth
   file to match the live state.

### 5. Drift detected — `<runbook:flagsmith#drift>`

`FlagsmithDriftDetected` fired.

1. `curl http://flagsmith.gp-platform.svc.cluster.local:8000/api/v1/environments/{envId}/features/`
   — list the live flags.
2. Diff against `platform/featureflags/flag-taxonomy.yaml`.
3. The helm upgrade aborts on the next run. Either:
   - Update the source-of-truth file to match the live state
     and commit; OR
   - Re-run the bootstrap with `--force` to overwrite the
     live state from the source-of-truth file.

### 6. Audit gap — `<runbook:flagsmith#audit-gap>`

`FlagsmithFlagChangeWithoutAudit` fired (flag change
detected without a matching audit event).

1. `kubectl -n gp-observability logs deploy/otel-collector`
   — verify the OTel Collector is receiving audit events.
2. `kubectl -n gp-platform logs deploy/flagsmith --tail=200`
   — check whether Flagsmith is logging audit events.
3. The platform privacy gate requires every flag create /
   update / delete to emit an audit event. Restore the
   audit pipeline before resuming flag changes.

## Backup / restore

The Flagsmith source-of-truth is the five YAML files in
`platform/featureflags/`. They are committed to git and
mirrored into the chart. Restore procedure:

1. **Restore the source-of-truth files.** Pull the latest
   `main` (or the release branch) and verify the five files
   match the desired state.
2. **Re-apply via the bootstrap Job.** The
   `featureflags-bootstrap` Job is idempotent; running it
   with the source-of-truth files in place re-creates the 5
   environments + RBAC roles + flags.
3. **Verify the drift check passes.** The Job exits with 0
   only when the live Flagsmith environment matches the
   source-of-truth.

Postgres PITR is the canonical restore path for the
Flagsmith backing store; Flagsmith's own CLI re-imports the
flag taxonomy from the source-of-truth files.

## SDK safe-default drill

1. Take down the Flagsmith Deployment:
   `kubectl -n gp-platform scale deploy/flagsmith --replicas=0`.
2. Verify the `FlagsmithServerDown` alert fires within 2m.
3. Verify the `FlagsmithDefaultUsedRateHigh` alert fires
   within 10m.
4. Verify consumer services still respond with the typed
   safe default (NOT `null`). The BFF should return
   `200 OK` with the default variant for every legal-gate
   flag; the GDPR / legal teams should NOT see a flag
   value leak.
5. Restore the Deployment: `kubectl -n gp-platform scale deploy/flagsmith --replicas=2`.
6. Verify the alerts clear within 5m.