/**
 * apps/web/src/lib/print/glossary.ts
 *
 * E12.3 — Translator glossary adapter for the print/email
 * /report pipeline.
 *
 * Every email, PDF and in-app copy that ships must come from
 * the same glossary (R18.7). The helper exposes a single
 * `apply(locale, rawString)` entry point; downstream surfaces
 * call it before sending so the platform never ships a
 * string that drifted across channels.
 */
export type GlossaryDomain =
  | "PERSON"
  | "RELATIONSHIP"
  | "EVENT"
  | "PLACE"
  | "SOURCE"
  | "CITATION"
  | "CONSENT"
  | "DNA"
  | "PRIVACY"
  | "PRINT"
  | "TIMELINE"
  | "TREE"
  | "ALBUM"
  | "COLLABORATION";

export interface GlossaryEntry {
  readonly domain: GlossaryDomain;
  readonly key: string;
  readonly locale: string;
  readonly value: string;
}

export class GlossaryRegistry {
  private readonly entries = new Map<string, GlossaryEntry>();

  register(entry: GlossaryEntry): void {
    if (!entry.domain || !entry.key || !entry.locale) {
      throw new Error("glossary entry requires domain, key, locale");
    }
    if (entry.key.length > 128) {
      throw new Error("glossary key too long");
    }
    const id = `${entry.domain}:${entry.key}:${entry.locale}`;
    this.entries.set(id, entry);
  }

  apply(locale: string, rawString: string): string {
    let output = rawString;
    for (const [id, entry] of this.entries) {
      if (!id.endsWith(`:${locale}`)) continue;
      if (output.includes(entry.key)) {
        output = output.split(entry.key).join(entry.value);
      }
    }
    return output;
  }

  lookup(domain: GlossaryDomain, key: string, locale: string): GlossaryEntry | undefined {
    return this.entries.get(`${domain}:${key}:${locale}`);
  }

  has(domain: GlossaryDomain, key: string, locale: string): boolean {
    return this.lookup(domain, key, locale) !== undefined;
  }

  size(): number {
    return this.entries.size;
  }
}

export const GLOBAL_GLOSSARY = new GlossaryRegistry();

export function apply(locale: string, rawString: string): string {
  return GLOBAL_GLOSSARY.apply(locale, rawString);
}