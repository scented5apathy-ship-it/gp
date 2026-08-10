# services/genealogy-service

E4.1 owns the **tree aggregate** and the UNLISTED-visibility
token contract for `genealogy-service`. Subsequent E4 subtasks
(E4.2 Person, E4.3 Date/Place, E4.4 Relationship, E4.5 Event /
Claim, E4.6 Merge, E4.7 Outbox) will land additional packages
inside this service.

## Architecture

```
                          ┌──────────────────────────────────┐
                          │      apps/web-bff / public-api   │
                          │      (gRPC clients, E4.x+)       │
                          └────────────────┬─────────────────┘
                                           │ gRPC (E4.x)
                          ┌────────────────▼─────────────────┐
                          │  services/genealogy-service      │
                          │                                  │
                          │  ┌──────────────────────────┐    │
                          │  │ command/TreeCommandSvc   │────┼──► outbox (K8 Apicurio)
                          │  │ visibility/UnlistedToken │    │
                          │  │ persistence/JdbcTreeRepo │◄───┼──► PostgreSQL (RLS)
                          │  │ domain/Tree (record)     │    │
                          │  └──────────────────────────┘    │
                          └──────────────────────────────────┘
                                            │
                                            ▼
                            contracts/events/genealogy/v1/
                            (TreeCreated, TreeVisibilityChanged,
                             TreeArchived, TreeRestored,
                             TreeTransferred, TreeDeleted,
                             UnlistedTokenIssued,
                             UnlistedTokenRevoked)
```

## Scope (E4.1)

- `Tree` aggregate (record, framework-free) + lifecycle
  (`ACTIVE` / `ARCHIVED` / `DELETED`).
- Visibility closed-set `PRIVATE / UNLISTED / PUBLIC`.
- Collaboration mode closed-set `DIRECT_EDIT /
APPROVAL_REQUIRED / HYBRID_BY_ROLE`.
- Tree CRUD + `archive / restore / transfer / delete`.
- UNLISTED-token issuance + revocation. Plaintext token is
  returned ONCE; only the SHA-256 fingerprint is persisted.
- Locale / timezone / default calendar / branding keys / slug.
- Outbox publisher emits the events listed above.

## Privacy / tenant isolation

- `tree_service.tree` carries `tenant_id` + `slug` + RLS policy
  (`tree_tenant_isolation`, `FORCE ROW LEVEL SECURITY`).
- `tree_service.unlisted_token` carries `tenant_id` + RLS.
- Plaintext UNLISTED tokens never leave the service; only the
  SHA-256 fingerprint is on the event bus.
- No raw DNA, biography, PII or access token is ever placed in
  an event payload per `design.md` §7.3.

## Out of scope for E4.1

- Person / Relationship / Event / Claim / Merge aggregates
  (E4.2–E4.6).
- Cross-service projection rebuild (search-service, public
  projection) — the events are emitted; the consumers land in
  their own epics.
- gRPC + REST surfaces — the command service is the testable
  surface; the controllers are stubbed in E4.2+.
- Slug uniqueness is enforced at the database level; concurrent
  inserts rely on the unique constraint (CAS via
  `DuplicateKeyException`).
