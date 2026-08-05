/**
 * Locale surface for the platform. The PWA shell (E1.5) and any
 * future Node.js tool that needs to negotiate a BCP-47 locale
 * imports the supported list and the `Locale` union from this
 * package. Full ICU MessageFormat catalogues land in E12.3 once
 * the translator workflow is wired; for now the package only
 * exports the type so the web shell and any future BFF locale
 * helpers can stay in lockstep.
 */
export const supportedLocales = ["en", "vi"] as const;
export type Locale = (typeof supportedLocales)[number];
export type SupportedLocale = Locale;
export const defaultLocale: Locale = "en";
