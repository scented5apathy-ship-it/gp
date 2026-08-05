# dna-service

Service owner: see `OWNERS` (per-service CODEOWNERS file mirrored from
`config/teams.yaml`).

Implementation: delivered by downstream epics (E3.x for tenant, E4.x for
genealogy, E5.x for sharing/DNA, etc.). E1.1 only wires the Gradle
module, the package layout and the ArchUnit boundary guard so the
monorepo build, lockfile and CI smoke run end-to-end.
