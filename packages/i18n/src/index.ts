/**
 * Placeholder i18n package. Real implementation lands in E5.5 / E12.3
 * alongside ICU message catalogues, RTL helpers and locale fallback.
 */
export const supportedLocales = ["en", "vi"] as const;
export type SupportedLocale = (typeof supportedLocales)[number];
