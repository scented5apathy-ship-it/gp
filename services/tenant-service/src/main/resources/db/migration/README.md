# services/tenant-service/src/main/resources/db/migration

Flyway migrations for the `tenant-service` schema. Per
`design.md` §13 ("Database migration dùng Flyway theo
expand-contract; jOOQ code generation chạy từ migration/schema
kiểm soát trong build") every change to the tenant aggregate goes
through here and follows the expand-contract pattern (add column →
backfill → switch → drop).

Current migration:

- `V1__baseline_schema.sql` — initial `Tenant`, `Membership`,
  `Invitation`, `Entitlement` tables + RLS policies + required
  indexes.

Rules enforced by CI (`AGENTS.md` §4 + `pnpm check:java`):

- Forward-only migrations; no destructive `DROP` without an
  expand-contract intermediate step.
- Every tenant-scoped table MUST include `tenant_id`, opaque PK,
  `created_at`, `updated_at`, `version` and a Row-Level Security
  policy.
- No literal PII (email, name, address) may appear in seed data.
- Migration filenames follow `V<version>__<description>.sql`
  (Flyway convention).

Owner: Identity & Tenant team. Reviewer: Privacy, Security.