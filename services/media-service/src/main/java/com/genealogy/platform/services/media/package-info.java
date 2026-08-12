/**
 * E1.1 + E7.1 — `media-service` skeleton + upload lifecycle.
 *
 * Per `design.md` §4 and `ownership-catalog.md` §2.5 this
 * package is owned by `Media team` and implements the
 * MediaAsset / MediaVariant / Album aggregate, the upload
 * lifecycle + signed URL + multipart + checksum + MIME policy
 * + quarantine + abandoned multipart reaper executors
 * (E7.1), the gRPC service, REST controllers, outbox publisher
 * and tests for the upload lifecycle / media pipeline.
 *
 * E7.1 ships the pure domain + invariants + executor only —
 * the Flyway migration + jOOQ repository + S3 / MinIO signed
 * URL adapter + Kafka producer / consumer + OpenFeature
 * wiring land in the later E7.x / E11.x sub-epics.
 */
@NonNullApi
package com.genealogy.platform.services.media;

import org.springframework.lang.NonNullApi;
