# apps/web-bff

Spring Boot application that serves the screen-shaped REST API
consumed by `apps/web`. Per `design.md` §4.1 ("`web-bff`: Spring Boot
REST API tối ưu cho màn hình và orchestration UI; không sở hữu
domain data và không thay vai trò Kong") and
`ownership-catalog.md` §3 the BFF is the only place where synchronous
chains of depth > 2 are allowed (`n_sync ≤ 3`).

Owner: web-bff team. SLO 99.95 %, p95 compose < 800 ms. Runbook:
`runbook/web-bff.md`.

This directory contains the Gradle module + `OWNERS` + `bin/`,
`build/` artefacts + `src/` skeleton. Implementation lives in later
epics (E1.4, E5). The boundary markers under
`apps/web-bff/src/main/java/com/genealogy/platform/{publicapi,
webbff,}/` keep `web-bff` and `public-api` separate at code-review
time and are enforced by ArchUnit.