# contracts/events/genealogy/v1

Current Avro schemas for the genealogy-service events. Per
`ownership-catalog.md` §5.3 these schemas are pinned for **6 months**
of consumer opt-in window after a deprecation announcement.

Schemas:

- `person-created.avsc` — `gp.genealogy.v1.PersonCreated`.
- `person-updated.avsc` — `gp.genealogy.v1.PersonUpdated`.
- `tree-created.avsc` — `gp.genealogy.v1.TreeCreated`.
- `tree-visibility-changed.avsc` —
  `gp.genealogy.v1.TreeVisibilityChanged` (PRIVATE/UNLISTED/PUBLIC
  transition per `design.md` §6.3).

Owner: Core Genealogy team. Reviewers: Privacy (visibility
transitions are privacy-critical).