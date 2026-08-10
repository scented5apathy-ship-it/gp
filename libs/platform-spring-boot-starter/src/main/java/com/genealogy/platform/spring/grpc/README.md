# libs/platform-spring-boot-starter/.../spring/grpc

gRPC trusted tenant context wiring (E3.5).

Classes:

- `TrustedContextMetadataKeys` — closed-set constants for the
  gRPC metadata keys the BFF → service contract uses. Mirrors
  `contracts/trusted-context/policy.yaml::grpcMetadataKeys`.
- `TrustedContextReconstructor` — framework-free POJO that
  validates an `InboundCall` snapshot against the E3.5 contract
  and returns a populated `TrustedTenantContext`. Throws
  `TrustedContextViolation` with a closed-set `Reason` code on
  violation.
- `TrustedContextViolation` — closed-set exception with a
  machine-readable `Reason` enum. Surfaced in the gRPC trailer
  as `x-trusted-context-violation`.
- `TrustedContextFieldGuard` — defensive validator for the
  proto `Context` message field. Services call this AFTER
  deserialisation but BEFORE acting on the request body.
- `GrpcTrustedContextInterceptor` — gRPC `ServerInterceptor`
  shell that bridges `io.grpc.Metadata` to the reconstructor
  + populates the thread-local `TrustedTenantContext`.
- `GrpcTrustedContextClientInterceptor` — gRPC `ClientInterceptor`
  used by the BFF to propagate the REST-authenticated trusted
  context as gRPC metadata.

Privacy: the interceptor MUST validate that every metadata
key carries only opaque IDs (the reconstructor rejects
client-supplied proto fields by design).

Owner: `@genealogy/platform` (primary), `@genealogy/security`
(secondary).
