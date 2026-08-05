/**
 * Top-level health probe. The locale-prefixed `/[locale]/health`
 * route delegates here so platform probes (Kong, Argo) can hit a
 * stable path that does not require locale negotiation.
 *
 * Returning a typed `Response` keeps the route zero-cost: the
 * App Router does not need to render React for the probe.
 */
export const dynamic = "force-static";
export const revalidate = 30;

interface HealthPayload {
  status: "ok";
  shell: "pwa-shell";
  version: string;
}

export function GET(): Response {
  const payload: HealthPayload = {
    status: "ok",
    shell: "pwa-shell",
    version: process.env["npm_package_version"] ?? "0.1.0",
  };
  return new Response(JSON.stringify(payload), {
    status: 200,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

export default function HealthPage() {
  return null;
}
