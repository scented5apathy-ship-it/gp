# apps/web/.well-known

Static files served from `/.well-known/` per RFC 8615. Per
`design.md` §12 ("security.txt" disclosure channel) and platform
security policy.

Contents:

- `security.txt` — vulnerability disclosure contact, encryption key
  and policy URL. Required by `design.md` §12.

Owner: web-app team. Reviewer: Security.