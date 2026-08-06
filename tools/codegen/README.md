# tools/codegen

Code-generation tooling (TypeScript / Node). Per `design.md` §7.1
the typed REST client is generated from the OpenAPI specs in
`contracts/openapi/`; this tool hosts the generator entrypoint
when it cannot live in `packages/api-client/src/codegen/`
(e.g. cross-language generators, Protobuf -> JSON-Schema
publishers).

Owner: contract-first platform team.