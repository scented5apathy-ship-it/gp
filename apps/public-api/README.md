# apps/public-api

Spring Boot application that exposes the partner-facing REST/OpenAPI
surface of the genealogy platform. Per `design.md` §4.1 and
`ownership-catalog.md` §3 it is one of two edge apps:

| App          | Purpose                                                                            |
| ------------ | ---------------------------------------------------------------------------------- |
| `web-bff`    | Screen-shaped REST API for the Next.js PWA (`n_sync ≤ 3`).                         |
| `public-api` | Versioned REST/OpenAPI for partner integrations (`n_sync ≤ 2`, MUST NOT depend on `web-bff`). |

Owner: public-api team. SLO 99.95 %, p95 < 600 ms. Runbook:
`runbook/public-api.md` (added in E9.5).

This directory contains the Gradle module + `OWNERS` + `bin/`,
`build/` artefacts + `src/` skeleton. Implementation lives in later
epics. The boundary markers under
`apps/public-api/src/main/java/com/genealogy/platform/{publicapi,
webbff,}/` (added in the previous step) make boundary violations
visible at review time and are enforced by ArchUnit.