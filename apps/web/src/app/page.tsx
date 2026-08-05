import { redirect } from "next/navigation";
import { headers } from "next/headers";

import { defaultLocale, negotiateLocale } from "@/i18n";

/**
 * Top-level `/` redirect.
 *
 * The App Router convention is to perform the locale negotiation
 * inside a Server Component redirect so the response is cached at
 * the CDN. We resolve the locale from `Accept-Language` so the
 * user lands on their preferred language instead of the default.
 *
 * If `Accept-Language` is missing we fall back to the default
 * locale (`en`). Falling back is intentional — the redirect must
 * always succeed so the shell is never stranded without a locale
 * prefix (the BFF only accepts `/{locale}/...` URLs).
 */
export default async function RootIndex(): Promise<never> {
  const requestHeaders = await headers();
  const acceptLanguage = requestHeaders.get("accept-language");
  const locale = negotiateLocale(acceptLanguage) ?? defaultLocale;
  redirect(`/${locale}`);
}
