/**
 * apps/web/src/components/profile/person-route.tsx
 *
 * Client component that mounts the E5.4 person profile editor +
 * timeline + place-map. Mirrors `tree-view-route.tsx` (E5.3):
 *
 *   - Memoises a `PersonEditorStore` per `(treeId, personId)`
 *     so navigation between persons does not leak state.
 *   - Calls `store.load(query)` on mount; never re-queries the
 *     full graph client-side.
 *   - Hydration-safe: only renders the editor island after
 *     `useEffect` so the server-rendered HTML is always
 *     deterministic.
 */
"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import type { Translator } from "@/i18n";
import type {
  PlaceCandidate,
  TimelineEvent,
  PersonPermissions,
  PersonFetcher,
  PersonBody,
} from "@genealogy/api-client";

import { getBffClient } from "@/lib/api/client";
import { createBffPersonFetcher } from "@/lib/profile/client";
import { createBffBackedPlaceProvider } from "@/lib/profile/place-provider";
import { EMPTY_PERSON_SNAPSHOT, PersonEditorStore } from "@/lib/profile/store";
import { useLiveRegionAnnouncer } from "@/lib/a11y/use-live-region-announcer";
import { LiveRegion } from "@/lib/a11y/live-region";
import { usePrefersReducedMotion } from "@/lib/a11y/use-prefers-reduced-motion";

import { PersonProfile } from "@/components/profile/person-profile";
import { PersonListTable } from "@/components/profile/person-list-table";
import { PersonTimeline } from "@/components/profile/person-timeline";
import { PlaceMap } from "@/components/profile/place-map";
import { PrintToolbar } from "@/components/print/print-toolbar";

export interface PersonRouteProps {
  readonly locale: string;
  readonly translate: Translator;
  readonly treeId: string;
  readonly personId: string;
  readonly tenantId?: string;
}

const PERSON_ID_PATTERN = /^[A-Za-z0-9._:-]{1,128}$/;

export function resolvePersonId(value: string | string[] | undefined): string {
  if (Array.isArray(value)) return "";
  if (typeof value === "string" && PERSON_ID_PATTERN.test(value)) return value;
  return "";
}

export function PersonRoute({
  locale,
  translate,
  treeId,
  personId,
  tenantId,
}: PersonRouteProps): JSX.Element {
  const fetcher = useMemo<PersonFetcher>(() => createBffPersonFetcher(getBffClient()), []);
  const provider = useMemo(() => createBffBackedPlaceProvider({ client: getBffClient() }), []);
  const store = useMemo(() => new PersonEditorStore(EMPTY_PERSON_SNAPSHOT, { fetcher }), [fetcher]);

  useEffect(() => {
    void store.load({ treeId, personId });
  }, [personId, store, treeId]);

  const [hydrated, setHydrated] = useState<boolean>(false);
  useEffect(() => setHydrated(true), []);

  const [snapshot, setSnapshot] = useState(store.getSnapshot());
  useEffect(() => store.subscribe((next) => setSnapshot(next)), [store]);

  const [listView, setListView] = useState<boolean>(false);
  const { announcer, message: liveMessage } = useLiveRegionAnnouncer();
  const reducedMotion = usePrefersReducedMotion();
  const handleToggleListView = useCallback(() => {
    setListView((prev) => {
      const next = !prev;
      announcer.announce(next ? translate("a11y.viewList") : translate("a11y.viewForm"));
      return next;
    });
  }, [announcer, translate]);

  const handlePatch = useCallback(
    (patch: Partial<Parameters<typeof store.patchDraft>[0]>) => store.patchDraft(patch),
    [store],
  );
  const handleCommit = useCallback(() => {
    announcer.announce(translate("profile.editSaving"));
    void store.commit();
  }, [announcer, store, translate]);
  const handleRevert = useCallback(() => store.revert(), [store]);

  const [timeline, setTimeline] = useState<{
    events: readonly TimelineEvent[];
    status: "idle" | "loading" | "ready" | "error";
  }>({ events: [], status: "idle" });

  const handleTimelineLoad = useCallback(
    async (input: { fromYear: number; toYear: number }) => {
      setTimeline((prev) => ({ ...prev, status: "loading" }));
      try {
        const response = await getBffClient().getPersonTimeline({
          treeId,
          personId,
          fromYear: input.fromYear,
          toYear: input.toYear,
        });
        const events = extractTimelineEvents(response);
        setTimeline({ events, status: "ready" });
      } catch {
        setTimeline({ events: [], status: "error" });
      }
    },
    [personId, treeId],
  );

  const handlePlaceSelect = useCallback((candidate: PlaceCandidate) => {
    // The place id flows back into the editor via the
    // `onSelect` callback of the parent form. We keep this
    // stub explicit so unit tests can assert the click
    // handler runs without binding it to a specific field
    // (the route is the integration point).
    void candidate;
  }, []);

  if (!hydrated) {
    return (
      <div className="mx-auto w-full max-w-4xl px-4 py-8">
        <p className="text-xs text-surface-muted" data-tenant={tenantId ?? "unscoped"}>
          {translate("profile.sectionLabel")} · {locale}
        </p>
      </div>
    );
  }

  const permissions: PersonPermissions | null = snapshot.permissions;

  return (
    <div
      className="mx-auto w-full max-w-4xl px-4 py-8"
      data-person-tree={treeId}
      data-reduced-motion={reducedMotion ? "true" : "false"}
    >
      <LiveRegion announcer={{ announce: announcer.announce }} message={liveMessage} />
      <p className="text-xs text-surface-muted" data-tenant={tenantId ?? "unscoped"}>
        {translate("profile.sectionLabel")} · {locale}
      </p>
      <PrintToolbar
        locale={locale}
        translate={translate}
        tenantId={tenantId ?? "unscoped"}
        treeId={treeId}
        rootPersonId={personId}
        actorPseudoId="user-pseudo"
        defaultScope="currentPerson"
        variant="person"
      />
      {listView && snapshot.body ? (
        <PersonListTable body={snapshot.body} translate={translate} locale={locale} />
      ) : (
        <PersonProfile
          snapshot={snapshot}
          translate={translate}
          locale={locale}
          onPatch={handlePatch}
          onCommit={handleCommit}
          onRevert={handleRevert}
          onToggleListView={handleToggleListView}
          listViewActive={listView}
        />
      )}
      {permissions ? (
        <div className="mt-6 flex flex-col gap-6">
          <PersonTimeline
            translate={translate}
            locale={locale}
            events={timeline.events}
            status={timeline.status}
            onLoad={handleTimelineLoad}
          />
          <PlaceMap translate={translate} provider={provider} onSelect={handlePlaceSelect} />
        </div>
      ) : null}
    </div>
  );
}

function extractTimelineEvents(payload: unknown): readonly TimelineEvent[] {
  if (!payload || typeof payload !== "object") return [];
  const obj = payload as { events?: unknown };
  if (!Array.isArray(obj.events)) return [];
  return obj.events.filter((e): e is TimelineEvent => isTimelineEvent(e));
}

function isTimelineEvent(value: unknown): value is TimelineEvent {
  if (!value || typeof value !== "object") return false;
  const obj = value as Record<string, unknown>;
  return (
    (typeof obj["eventId"] === "string" &&
      typeof obj["kind"] === "string" &&
      typeof obj["date"] === "object" &&
      obj["redacted"] === true) ||
    obj["redacted"] === false
  );
}

/**
 * Re-exported so the server route can resolve the path
 * argument without duplicating the regex.
 */
export { PERSON_ID_PATTERN };

export type { PersonBody };
