/**
 * E7.1 — `media-service` upload lifecycle domain. Owner:
 * `Media team` per `ownership-catalog.md` §2.5.
 *
 * <p>Pure domain records + executors mirror
 * `contracts/media/upload-lifecycle-policy.yaml` (E7.1).
 * The Java executor is the source of the deterministic
 * behaviour; the YAML contract is the cross-service
 * reference for the policy.
 *
 * <p>Aggregates:
 *
 * <ul>
 *   <li>{@code UploadSession} — the {@code REQUESTED ->
 *       SIGNED -> UPLOADING -> FINALIZING -> QUARANTINED ->
 *       READY} state machine with TTL + checksum + MIME
 *       + intent + scope metadata.</li>
 *   <li>{@code MultipartPart} — a received multipart part
 *       with size / checksum / sequence constraints.</li>
 *   <li>{@code QuotaLedger} — tenant-scoped bytes / items /
 *       seconds reservation ledger.</li>
 * </ul>
 *
 * <p>Executors:
 *
 * <ul>
 *   <li>{@code MimePolicy} — closed-set MIME allow / deny
 *       list with sandbox + deep-scan classification.</li>
 *   <li>{@code ChecksumVerifier} — deterministic
 *       constant-time checksum matcher.</li>
 *   <li>{@code QuarantineGate} — admission gate from
 *       {@code QUARANTINED} to {@code READY} / {@code
 *       REJECTED}.</li>
 *   <li>{@code AbandonedMultipartSweeper} — Temporal
 *       workflow helper that reaps unused sessions.</li>
 *   <li>{@code UploadAuthorizer} — pure executor that
 *       maps intent + media category + object key to an
 *       {@code UploadAuthorizationDecision}.</li>
 *   <li>{@code MediaInvariants} — pure invariant checker
 *       emitting DENY / WARN / INFO findings.</li>
 *   <li>{@code UploadAuthorizationPort} — port interface
 *       delegating to OpenFGA + ABAC at the application
 *       layer.</li>
 * </ul>
 *
 * <p>Closed-set enums: {@code UploadSessionStatus} +
 * {@code UploadSessionIntent} + {@code MediaCategory} +
 * {@code MimeVerdict} + {@code ChecksumAlgorithm} +
 * {@code FinalizeOutcome} + {@code QuotaDenialReason} +
 * {@code UploadGuardDenyReason} +
 * {@code AbandonedMultipartReason} + {@code QuotaUnit} +
 * {@code UploadAuthorizationOutcome}.
 */
@NonNullApi
package com.genealogy.platform.services.media.domain;

import org.springframework.lang.NonNullApi;
