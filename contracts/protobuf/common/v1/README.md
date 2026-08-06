# contracts/protobuf/common

Shared gRPC messages referenced by every per-domain service. Per
`design.md` §7.2 these ensure the trusted context, error envelope
and pagination shape are identical across services.

Schemas:

- `context.proto` — `TrustedContext { tenant_id, user_id,
  membership_id, trace_id, request_id }` carried via gRPC metadata.
  Validated by `libs/platform-security`.
- `error.proto` — `ErrorMeta` (pseudonymous IDs only) plus mapping
  to `google.rpc.Status` for gRPC trailers.
- `page.proto` — cursor/page token used by every list endpoint.

Owner: contract-first platform team. Reviewers: every service
owner named in `ownership-catalog.md` §2.