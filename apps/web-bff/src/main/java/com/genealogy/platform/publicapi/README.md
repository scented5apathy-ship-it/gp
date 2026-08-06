# apps/{public-api,web-bff}

Cross-application Java package marker. The `publicapi` and `webbff`
sub-packages are intentionally left empty here so any drift in
application boundary (e.g. a class accidentally imported from
`web-bff` into `public-api`) is visible at review time and caught by
the ArchUnit boundary tests described in `AGENTS.md` §2.

Per `ownership-catalog.md` §3 the two edge apps are owned by separate
teams and MUST NOT import each other's code:

- `public-api` (com.genealogy.platform.publicapi) — partner-facing
  versioned REST/OpenAPI surface. `n_sync ≤ 2`. Must not depend on
  `web-bff`.
- `web-bff` (com.genealogy.platform.webbff) — UI-shaped REST API
  consumed by `apps/web`. `n_sync ≤ 3`. Composition lives here, not
  in Kong.

Any code added under this directory belongs to the `web-bff`
application only — it is the boundary marker for the `web-bff`
module.
