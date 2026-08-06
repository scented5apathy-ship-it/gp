# contracts/events/shared

Cross-domain event value objects shared by every domain event
schema. Per `design.md` §7.3 these are the canonical reusable types
(id, visibility, residency, etc.) referenced by every per-domain
event.

Schemas:

- `identifiers.avsc` — opaque pseudonymous IDs used in envelope and
  payloads.
- `visibility.avsc` — `PRIVATE | UNLISTED | PUBLIC` per
  `design.md` §6.3.

Owner: contract-first platform team. Reviewers: Privacy, Security.