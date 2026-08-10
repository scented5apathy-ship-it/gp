/**
 * Package marker for the cross-cutting {@code platform-security} library.
 *
 * <p>This library is the only authorised Java home for ABAC policy
 * evaluation, OpenFGA client wiring and redaction helpers shared by
 * every domain service, BFF, public-api and worker. Domain
 * aggregates MUST stay inside {@code services/<svc>/domain/} per
 * {@code design.md} §5.1; this library never carries an aggregate
 * root.
 */
package com.genealogy.platform.libs.security;
