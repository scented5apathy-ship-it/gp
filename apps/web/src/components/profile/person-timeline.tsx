/**
 * apps/web/src/components/profile/person-timeline.tsx
 *
 * Client component for the personal timeline view (R7.3,
 * E5.4). The component renders a bounded timeline returned by
 * the BFF:
 *
 *   - year-range picker (`fromYear` / `toYear`) capped by
 *     `PERSON_MAX_TIMELINE_YEARS=500` and `PERSON_MAX_TIMELINE_EVENTS=200`;
 *   - semantic `<ol>` of events (R18.4 / WCAG 2.2 AA);
 *   - redacted events render with `aria-label="redacted"` and a
 *     placeholder title; the UI never re-redacts (glossary §2.2);
 *   - reduced-motion respected via the existing
 *     `prefers-reduced-motion` CSS token (E1.5).
 */
"use client";

import { useCallback, useMemo, useState, type FormEvent } from "react";

import type { Translator } from "@/i18n";
import {
  PERSON_MAX_TIMELINE_EVENTS,
  PERSON_MAX_TIMELINE_YEARS,
  type TimelineEvent,
  type TimelineEventKind,
  TIMELINE_EVENT_KINDS,
} from "@genealogy/api-client";

export interface PersonTimelineProps {
  readonly translate: Translator;
  readonly locale: string;
  readonly events: readonly TimelineEvent[];
  readonly status: "idle" | "loading" | "ready" | "error";
  readonly onLoad: (input: { fromYear: number; toYear: number }) => void;
  readonly initialFromYear?: number;
  readonly initialToYear?: number;
}

const KIND_LABELS: Readonly<Record<TimelineEventKind, string>> = {
  BIRTH: "timeline.eventBIRTH",
  DEATH: "timeline.eventDEATH",
  MARRIAGE: "timeline.eventMARRIAGE",
  DIVORCE: "timeline.eventDIVORCE",
  RESIDENCE: "timeline.eventRESIDENCE",
  MIGRATION: "timeline.eventMIGRATION",
  MILITARY: "timeline.eventMILITARY",
  EDUCATION: "timeline.eventEDUCATION",
  RELIGION: "timeline.eventRELIGION",
  CUSTOM: "timeline.eventCUSTOM",
};

export function PersonTimeline({
  translate,
  locale,
  events,
  status,
  onLoad,
  initialFromYear,
  initialToYear,
}: PersonTimelineProps): JSX.Element {
  const defaultFrom = initialFromYear ?? Math.max(1, new Date().getFullYear() - 100);
  const defaultTo = initialToYear ?? Math.min(9999, new Date().getFullYear());
  const [fromYear, setFromYear] = useState<number>(defaultFrom);
  const [toYear, setToYear] = useState<number>(defaultTo);

  const handleSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      onLoad({ fromYear, toYear });
    },
    [fromYear, toYear, onLoad],
  );

  const sorted = useMemo(() => [...events].sort(compareEvents), [events]);
  const span = toYear - fromYear;

  return (
    <section
      aria-label={translate("timeline.sectionLabel")}
      data-timeline-status={status}
      className="person-timeline flex flex-col gap-4"
      lang={locale}
    >
      <header className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-surface-foreground">
          {translate("timeline.heading")}
        </h2>
        <span className="text-xs text-surface-muted" data-timeline-count={events.length}>
          {events.length}/{PERSON_MAX_TIMELINE_EVENTS}
        </span>
      </header>
      <form
        className="person-timeline__form flex flex-wrap items-end gap-2 text-sm"
        onSubmit={handleSubmit}
      >
        <fieldset className="flex flex-col gap-1">
          <legend className="text-surface-muted">{translate("timeline.rangeLabel")}</legend>
          <div className="flex gap-1">
            <label className="flex flex-col">
              <span className="text-xs text-surface-muted">{translate("timeline.fromLabel")}</span>
              <input
                type="number"
                min={1}
                max={9999}
                value={fromYear}
                onChange={(event) => setFromYear(Number.parseInt(event.target.value, 10) || 1)}
                className="w-24 rounded border border-surface-sunken bg-surface-raised px-2 py-1"
              />
            </label>
            <label className="flex flex-col">
              <span className="text-xs text-surface-muted">{translate("timeline.toLabel")}</span>
              <input
                type="number"
                min={1}
                max={9999}
                value={toYear}
                onChange={(event) => setToYear(Number.parseInt(event.target.value, 10) || 1)}
                className="w-24 rounded border border-surface-sunken bg-surface-raised px-2 py-1"
              />
            </label>
          </div>
          {span > PERSON_MAX_TIMELINE_YEARS ? (
            <span className="text-xs text-red-700">
              {`> ${PERSON_MAX_TIMELINE_YEARS}-year cap`}
            </span>
          ) : null}
        </fieldset>
        <button
          type="submit"
          disabled={status === "loading" || span > PERSON_MAX_TIMELINE_YEARS}
          className="rounded border border-surface-sunken bg-surface-raised px-3 py-2"
        >
          {status === "loading" ? translate("timeline.loading") : translate("timeline.loadAction")}
        </button>
      </form>
      {sorted.length === 0 ? (
        <p className="text-sm text-surface-muted">{translate("timeline.empty")}</p>
      ) : (
        <ol
          className="person-timeline__list flex flex-col gap-2"
          data-timeline-kind-closed-set={TIMELINE_EVENT_KINDS.join(",")}
        >
          {sorted.map((event) => (
            <li
              key={event.eventId}
              className="rounded border border-surface-sunken bg-surface-raised p-3 text-sm"
            >
              <h3 className="font-medium">
                {event.redacted
                  ? translate("profile.redacted")
                  : (event.title ?? translate(KIND_LABELS[event.kind]))}
              </h3>
              <p className="text-xs text-surface-muted">
                {translate(KIND_LABELS[event.kind])} · {renderDate(event)}
                {event.placeLabel ? ` · ${event.placeLabel}` : ""}
              </p>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function compareEvents(a: TimelineEvent, b: TimelineEvent): number {
  return (a.date.year ?? 0) - (b.date.year ?? 0);
}

function renderDate(event: TimelineEvent): string {
  if (event.date.year === undefined) return event.date.kind;
  if (event.date.month !== undefined && event.date.day !== undefined) {
    return `${event.date.year}-${String(event.date.month).padStart(2, "0")}-${String(event.date.day).padStart(2, "0")}`;
  }
  return `${event.date.year}`;
}
