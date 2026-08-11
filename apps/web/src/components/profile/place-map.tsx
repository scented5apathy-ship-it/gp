/**
 * apps/web/src/components/profile/place-map.tsx
 *
 * Client component for the E5.4 place-lookup map adapter
 * (ADR-E0.5-14). The component is intentionally **vendor-free**:
 * it does not embed Mapbox / Google Maps / MapLibre directly.
 * Instead it renders a list of `PlaceCandidate` returned by the
 * configured `PlaceProvider`. The vendor decision belongs to
 * the BFF / on-prem config (ADR-E0.5-14) — the UI just
 * presents whatever the adapter returns.
 *
 * Behaviour:
 *
 *   - Submitting the search calls `provider.lookup(q)` with a
 *     bounded query (2..PLACE_QUERY_MAX=128 chars, mirrored from
 *     the BFF contract);
 *   - `degraded=true` renders an explicit notice so the user
 *     knows to type the place manually (ADR-E0.5-14
 *     §Security/privacy);
 *   - Each candidate is rendered as a `<button>` so keyboard
 *     users can pick a place without a mouse (R18.4);
 *   - The `onSelect` callback hands the picked `PlaceCandidate`
 *     to the editor so the field can be filled.
 */
"use client";

import { useCallback, useState, type FormEvent } from "react";

import type { Translator } from "@/i18n";
import {
  PERSON_MAX_PLACE_CANDIDATES,
  type PlaceCandidate,
  type PlaceLookupResult,
  assertPlaceQuery,
} from "@genealogy/api-client";

import type { PlaceProvider } from "@/lib/profile/place-provider";

export interface PlaceMapProps {
  readonly translate: Translator;
  readonly provider: PlaceProvider;
  readonly initialQuery?: string;
  readonly onSelect: (candidate: PlaceCandidate) => void;
}

export function PlaceMap({
  translate,
  provider,
  initialQuery,
  onSelect,
}: PlaceMapProps): JSX.Element {
  const [query, setQuery] = useState<string>(initialQuery ?? "");
  const [result, setResult] = useState<PlaceLookupResult | null>(null);
  const [status, setStatus] = useState<"idle" | "loading" | "ready" | "error">("idle");

  const handleSearch = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      try {
        const trimmed = assertPlaceQuery(query);
        setStatus("loading");
        const outcome = await provider.lookup({ q: trimmed, limit: PERSON_MAX_PLACE_CANDIDATES });
        setResult(outcome);
        setStatus("ready");
      } catch {
        setStatus("error");
        setResult({ provider: "degraded", degraded: true, candidates: [] });
      }
    },
    [provider, query],
  );

  return (
    <section
      aria-label={translate("map.sectionLabel")}
      data-map-status={status}
      className="place-map flex flex-col gap-3"
    >
      <header className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-surface-foreground">
          {translate("map.heading")}
        </h2>
      </header>
      <form
        className="place-map__form flex flex-wrap items-end gap-2 text-sm"
        onSubmit={handleSearch}
      >
        <label className="flex flex-col">
          <span className="text-surface-muted">{translate("map.queryLabel")}</span>
          <input
            type="text"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={translate("map.queryPlaceholder")}
            minLength={2}
            maxLength={128}
            className="w-64 rounded border border-surface-sunken bg-surface-raised px-2 py-1"
          />
        </label>
        <button
          type="submit"
          className="rounded border border-surface-sunken bg-surface-raised px-3 py-2"
        >
          {translate("map.searchAction")}
        </button>
      </form>
      {result?.degraded === true ? (
        <p
          role="status"
          className="rounded border border-amber-400 bg-amber-50 px-3 py-2 text-sm text-amber-900"
        >
          {translate("map.degraded")}
        </p>
      ) : null}
      {result && result.degraded !== true && result.candidates.length === 0 ? (
        <p className="text-sm text-surface-muted">{translate("map.noResults")}</p>
      ) : null}
      {result && result.candidates.length > 0 ? (
        <ul className="place-map__results flex flex-col gap-1 text-sm" role="list">
          {result.candidates.map((candidate) => (
            <li
              key={candidate.placeId}
              className="rounded border border-surface-sunken bg-surface-raised"
            >
              <button
                type="button"
                onClick={() => onSelect(candidate)}
                className="flex w-full items-center justify-between gap-2 rounded px-2 py-1 text-left hover:bg-surface-sunken"
              >
                <span>{candidate.label}</span>
                <span className="text-xs text-surface-muted">
                  {candidate.providerPlaceId ?? candidate.placeId}
                </span>
              </button>
            </li>
          ))}
        </ul>
      ) : null}
      <p className="text-xs text-surface-muted">{translate("map.providerFootnote")}</p>
    </section>
  );
}
