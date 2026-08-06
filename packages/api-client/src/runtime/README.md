# packages/api-client/src/runtime

Runtime helpers that wrap the generated REST client with
React-friendly ergonomics. Per `design.md` §10.1 ("REST client sinh
từ OpenAPI hoặc wrapper typed") the wrapper owns pagination, error
envelope and idempotency key injection; the per-endpoint fetchers
live in `../generated/`.

Files:

- `index.ts` — barrel.
- `problem.ts` — `ProblemDetails` mapping to user-facing errors per
  RFC 9457.

Owner: web-app team.