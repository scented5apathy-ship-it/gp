# libs/platform-feature-flags

Spring Boot starter that wraps OpenFeature / Flagsmith access for
Java services and BFFs. Per `design.md` §13 ("OpenFeature SDK có safe
default; Flagsmith outage không làm hỏng critical flow") and
`ownership-catalog.md` §5.4 every flag has an owner, expiry, audit
trail and a safe fallback that does not break the request path.

Concretely the starter ships:

- `FeatureFlagClient` — typed façade (`boolean`, `string`, `json`,
  `number`) backed by `dev.openfeature:sdk-java` with the Flagsmith
  provider. Provider outage falls back to the static default from the
  `application.yml` so the request never blocks.
- `FlagTaxonomy` enum — central catalogue (`legal.dna.enabled`,
  `sharing.publicLink.enabled`, `import.gedcom.enabled`, …). New
  flags require Product + Privacy + Security sign-off.
- `FlagAuditLogger` — emits `gp.platform.v1.FeatureFlagEvaluated`
  event with the pseudonymous subject so SRE can prove a flag was
  active during an incident without leaking user identifiers.
- `FlagHygieneReport` — produces the weekly
  `reports/feature-flag-hygiene.md` artefact checked by CI.

This directory is intentionally empty in the E1.1 scaffold: the
Gradle module + `package-info.java` for the
`com.genealogy.platform.libs` package already exist; implementation
lands in later epics.

Owner: platform-secondary. Reviewers: Product, Privacy, Security.
