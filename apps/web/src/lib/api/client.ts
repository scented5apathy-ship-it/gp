/**
 * apps/web BFF accessor — picks up the configured BFF origin and
 * hands a singleton `BffClient` to the rest of the shell.
 *
 * The shell deliberately uses a single client instance per
 * process so the OTel SDK can inject trace metadata into every
 * outgoing request without re-creating the headers on each call.
 */
import { createBffClient, type BffClient } from "@genealogy/api-client";

let singleton: BffClient | undefined;

/**
 * Resolve the BFF origin. The shell defaults to the empty string
 * so server-side rendering does not leak the configuration into
 * client bundles. Set `NEXT_PUBLIC_BFF_URL` at build time.
 */
export function bffOrigin(): string {
  return process.env["NEXT_PUBLIC_BFF_URL"] ?? "https://bff.genealogy-platform.com";
}

/**
 * Returns the singleton BFF client. The client is lazy so module
 * evaluation does not throw when the environment variable is
 * missing in unit tests.
 */
export function getBffClient(): BffClient {
  if (!singleton) {
    singleton = createBffClient({
      baseUrl: bffOrigin(),
      correlationId: generateCorrelationId(),
    });
  }
  return singleton;
}

function generateCorrelationId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return "00000000-0000-4000-8000-000000000000";
}

export type { BffClient } from "@genealogy/api-client";
export { ApiError } from "@genealogy/api-client";
export type { Problem } from "@genealogy/api-client";
