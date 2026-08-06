# contracts/events/genealogy

Kafka event schemas published by `services/genealogy-service/`. Per
`design.md` §7.3 events emitted from the genealogy aggregate cover
person lifecycle, tree visibility changes and merge outcomes so that
`search-service`, `audit-service`, `notification-service` and
`reporting-service` can build their projections and reactions.

Sub-directories:

- `v1/` — current schemas (`person-created.avsc`,
  `person-updated.avsc`, `tree-created.avsc`,
  `tree-visibility-changed.avsc`). Wrap payloads with the envelope
  in `../envelope/v1/`.

Compatibility: BACKWARD per `ownership-catalog.md` §5.3. Privacy:
events MUST NOT carry living/minor DNA without consent and MUST NOT
include raw PII fields not strictly required for downstream
projection.

Owner: Core Genealogy team. Reviewer: Privacy (living/minor
redaction).