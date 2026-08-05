/**
 * Catalogue type definitions. Extracted so the loader
 * (`index.ts`) can import the structural types without circular
 * references between the catalogue files.
 *
 * The `MessageTree` is intentionally permissive: an ICU
 * translation may nest groups (e.g. `home.featuresTitle`) or
 * carry a `{ message, description? }` wrapper when the team
 * adopts FormatJS. The runtime normalises both shapes into a
 * plain string.
 */
export type MessageValue = string | { message: string };

export type MessageTree = {
  readonly [key: string]: MessageValue | MessageTree;
};
