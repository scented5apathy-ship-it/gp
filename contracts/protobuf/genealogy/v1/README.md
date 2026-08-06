# contracts/protobuf/genealogy/v1

Current gRPC services for the genealogy aggregate. Per
`ownership-catalog.md` §5.2 this package is pinned for **9 months**
after a stage transition (alpha → beta → GA).

Services:

- `person_service.proto` — `gp.genealogy.v1.PersonService`
  (create, update, redact, merge, query).
- `tree_service.proto` — `gp.genealogy.v1.TreeService`
  (CRUD + visibility change per `design.md` §6.3).

Privacy: all requests MUST carry `TrustedContext`; ABAC overlays
living/minor status, DNA consent and residency before any read or
write per `design.md` §6.2.

Owner: Core Genealogy team. Reviewers: Privacy, Security.