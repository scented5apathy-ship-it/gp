/**
 * E7.5 — media-service album + linking domain.
 *
 * <p>The {@code albums} package owns the {@code Album}
 * aggregate (visibility / lifecycle / sort order / items /
 * tags / captions / cross-service references) and the
 * reconciliation Temporal workflow that re-resolves
 * dangling / revoked references through the publishing
 * services. Per
 * {@code contracts/media/albums-linking-policy.yaml} +
 * {@code .kiro/specs/genealogy-platform/{requirements,
 * design, ownership-catalog}.md} + ADR-E0.5-06 (OpenFGA
 * store-per-tenant) + ADR-E0.5-07 (reconciliation Temporal
 * workflow) + ADR-E0.5-15 (DNA bucket shield).
 */
package com.genealogy.platform.services.media.albums;