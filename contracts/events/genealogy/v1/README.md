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
- `tree-archived.avsc` — `gp.genealogy.v1.TreeArchived` (E4.1
  archive/restore/transfer/delete lifecycle).
- `tree-restored.avsc` — `gp.genealogy.v1.TreeRestored`.
- `tree-transferred.avsc` — `gp.genealogy.v1.TreeTransferred`
  (cross-tenant ownership transfer; consumers MUST invalidate cache).
- `tree-deleted.avsc` — `gp.genealogy.v1.TreeDeleted` (terminal;
  projections MUST remove every artifact).
- `unlisted-token-issued.avsc` — `gp.genealogy.v1.UnlistedTokenIssued`
  (only SHA-256 fingerprint on the wire; plaintext token never
  serialised per `design.md` §6.3).
- `unlisted-token-revoked.avsc` — `gp.genealogy.v1.UnlistedTokenRevoked`.

Owner: Core Genealogy team. Reviewers: Privacy (visibility
transitions are privacy-critical).
