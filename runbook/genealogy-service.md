# runbook/genealogy-service — E4.1 tree aggregate

Operational playbook for the tree-service. Mirrors
`services/audit-service/README.md` (E3.6) and `runbook/audit.md`.

## Architecture

```
                         ┌──────────────────────────────────┐
   PWA / BFF / public-api│ gRPC + gRPC trusted-context      │
   ─────────────────────►│──────────────────────────────────│
                         │  services/genealogy-service      │
                         │                                  │
                         │  TreeCommandService               │
                         │   │                              │
                         │   ├─► JdbcTreeRepository  ─► PostgreSQL (RLS)
                         │   ├─► UnlistedTokenService        │
                         │   │     │                        │
                         │   │     ├─► JdbcUnlistedTokenRepo│
                         │   │     └─► outbox writer        │
                         │   │                              │
                         │   └─► JdbcTreeOutboxWriter ─► tree_service.outbox
                         │                                  │
                         └────────────┬─────────────────────┘
                                      │
                                      ▼
                       relay (out of scope, E4.7) ─► Kafka / Apicurio
                                      │
                                      ▼
                       search-service, public-api, audit-service
```

## Day-to-day operations

### Inspect a tree

```sql
SELECT tree_id, slug, visibility, lifecycle_state, version, updated_at
  FROM tree_service.tree
 WHERE tree_id = $1 AND tenant_id = current_setting('app.tenant_id')::uuid;
```

### Find active UNLISTED tokens for a tree

```sql
SELECT token_id, fingerprint, scope, branch_id, expires_at
  FROM tree_service.unlisted_token
 WHERE tenant_id = current_setting('app.tenant_id')::uuid
   AND tree_id = $1
   AND revoked_at IS NULL
   AND expires_at > NOW();
```

### Revoke a token (operator override)

```sql
UPDATE tree_service.unlisted_token
   SET revoked_at = NOW(),
       revoked_by = '00000000-0000-0000-0000-000000000000'::uuid,
       revocation_reason = 'DPO emergency revoke'
 WHERE tenant_id = $1 AND fingerprint = $2 AND revoked_at IS NULL;
```

> Every revoke emits `gp.genealogy.v1.UnlistedTokenRevoked` from
> the application path. Operator overrides done via raw SQL will
> NOT emit the event — use the application API instead.

### Archive a tree (soft-delete)

```sql
UPDATE tree_service.tree
   SET lifecycle_state = 'ARCHIVED', version = version + 1, updated_at = NOW()
 WHERE tree_id = $1 AND tenant_id = current_setting('app.tenant_id')::uuid;
```

> Archive hides the tree from search + public projections. Use
> the application API to emit `TreeArchived`.

### Transfer a tree to another tenant

```sql
-- App-level transfer:
--   POST /api/v1/trees/{treeId}/transfer { toTenantId, reason }
-- The application emits `TreeTransferred` and updates `tenant_id`.
```

## Privacy guarantees

- **Tenant isolation** — `FORCE ROW LEVEL SECURITY` on every
  tenant-scoped table. Application sets
  `app.tenant_id = <uuid>` per request.
- **Plaintext token** never persisted; only the SHA-256
  fingerprint. The fingerprint is a `CHAR(64)` column.
- **Append-only audit** — every tree mutation flows through
  `KafkaAuditPublisher` (platform-spring-boot-starter) before the
  event bus. Audit classes used:
  `tree.created`, `tree.visibilityChanged`, `tree.archived`,
  `tree.restored`, `tree.transferred`, `tree.deleted`,
  `unlistedToken.issued`, `unlistedToken.revoked`.
- **robots: noindex** — the BFF / public-api MUST return
  `X-Robots-Tag: noindex` and the equivalent `<meta>` tag for
  every UNLISTED page. Per
  `contracts/genealogy/unlisted-token.yaml::spec.robotsDirective`.

## Failure modes

| Symptom                       | Likely cause                 | Recovery                                                             |
| ----------------------------- | ---------------------------- | -------------------------------------------------------------------- |
| Slug conflict on create       | duplicate slug within tenant | Use `findBySlug` to identify the existing tree                       |
| Stale version on update       | concurrent editor            | Surface `409 Conflict` with the latest ETag; let the client re-fetch |
| Token verify throws           | expired or revoked           | BFF returns `410 Gone`                                               |
| `tree_service` schema missing | migration not applied        | `flyway -target=2 migrate`                                           |
| RLS denies a query            | `app.tenant_id` not set      | Check `TenantRlsTxInterceptor` (E3.5)                                |
