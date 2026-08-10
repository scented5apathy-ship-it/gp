/**
 * E4.1 tree-service domain layer.
 *
 * Owns the {@code Tree} aggregate, visibility closed-set,
 * collaboration mode, branding metadata, and the UNLISTED-token
 * contract. Mirrors {@code contracts/genealogy/tree-policy.yaml}
 * + {@code contracts/genealogy/collaboration-policy.yaml} +
 * {@code contracts/genealogy/unlisted-token.yaml} per
 * {@code agent-execution.md} §4.4 (config-as-code first).
 *
 * The aggregate is intentionally framework-free: the command
 * service hydrates it, jOOQ maps it, and the outbox publisher
 * emits the corresponding Avro events under
 * {@code contracts/events/genealogy/v1/}.
 */
@NonNullApi
package com.genealogy.platform.services.genealogy;

import org.springframework.lang.NonNullApi;
