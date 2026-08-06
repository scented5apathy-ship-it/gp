# contracts/protobuf/genealogy

gRPC services owned by `services/genealogy-service/`. Per
`ownership-catalog.md` §2.2 these expose the genealogy aggregate
(tree, person, relationship, claim, merge).

Sub-directories:

- `v1/` — current service definitions
  (`person_service.proto`, `tree_service.proto`). Wire-incompatible
  changes require a new `gp.genealogy.v{n+1}` package running
  alongside for **9 months**.

Owner: Core Genealogy team. Reviewers: Privacy (living/minor
redaction), Security (relationship authorization).