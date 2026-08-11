/**
 * apps/web/src/components/profile/person-list-table.tsx
 *
 * Semantic `<table>` alternative for the E5.5 keyboard / screen
 * reader surface. Renders the same `PersonBody` data as the
 * `<PersonProfile>` view but as a `<table>` with a caption and
 * row/column headers — critical for users who cannot navigate
 * visually (R6.5, R18.4 / WCAG 2.2 SC 1.3.1).
 *
 * The component is **display-only**. The editor mode stays on the
 * form view (`<PersonProfile>`); this table is mounted as the
 * hidden-but-accessible alternative when the form is visible, and
 * shown by default when the user requests a "List view" — a
 * toggle lives next to the editor in `<PersonRoute>` (E5.5).
 *
 * The table does not re-redact — the BFF already dropped fields
 * per the `redactionSummary` and the UI only formats what it
 * receives (glossary §2.2).
 */
"use client";

import type { Translator } from "@/i18n";
import type { PersonBody } from "@genealogy/api-client";

import { renderPersonName } from "@/lib/i18n/name-order";

export interface PersonListTableProps {
  readonly body: PersonBody;
  readonly translate: Translator;
  readonly locale: string;
}

const FIELDS = [
  "displayName",
  "given",
  "surname",
  "patronymic",
  "suffix",
  "livingStatus",
  "privacyLevel",
  "biography",
  "identifiers",
] as const;

export function PersonListTable({ body, translate, locale }: PersonListTableProps): JSX.Element {
  const primary = body.names[0];
  const rendered = primary ? renderPersonName(primary, locale) : null;
  const identifiers = body.identifiers ?? [];
  return (
    <table
      className="person-list-table w-full border-collapse text-sm"
      data-person-id={body.personId}
    >
      <caption className="sr-only">{translate("a11y.tableCaption", { id: body.personId })}</caption>
      <thead>
        <tr>
          <th
            scope="col"
            className="border border-surface-sunken bg-surface-raised px-2 py-1 text-left"
          >
            {translate("a11y.tableHeaderField")}
          </th>
          <th
            scope="col"
            className="border border-surface-sunken bg-surface-raised px-2 py-1 text-left"
          >
            {translate("a11y.tableHeaderValue")}
          </th>
        </tr>
      </thead>
      <tbody>
        {FIELDS.map((field) => {
          const value = renderField(body, field, locale, rendered?.display ?? body.displayName);
          return (
            <tr key={field}>
              <th
                scope="row"
                className="border border-surface-sunken bg-surface-raised px-2 py-1 text-left font-medium align-top"
              >
                {translate(`a11y.field.${field}`)}
              </th>
              <td className="border border-surface-sunken bg-surface px-2 py-1 align-top">
                {value}
              </td>
            </tr>
          );
        })}
        {identifiers.length > 0 ? (
          <tr>
            <th
              scope="row"
              className="border border-surface-sunken bg-surface-raised px-2 py-1 text-left font-medium align-top"
            >
              {translate("profile.fieldIdentifiers")}
            </th>
            <td className="border border-surface-sunken bg-surface px-2 py-1 align-top">
              <ul className="flex flex-col gap-1" role="list">
                {identifiers.map((id, index) => (
                  <li key={`${id.scheme}-${index}`}>
                    {id.scheme}: {id.value}
                  </li>
                ))}
              </ul>
            </td>
          </tr>
        ) : null}
        {body.redaction.droppedFieldCount > 0 ? (
          <tr>
            <th
              scope="row"
              className="border border-surface-sunken bg-surface-raised px-2 py-1 text-left font-medium align-top"
            >
              {translate("profile.redacted")}
            </th>
            <td className="border border-surface-sunken bg-surface px-2 py-1 align-top">
              {translate("profile.redactionSummary", {
                dropped: body.redaction.droppedFieldCount,
                reasons: body.redaction.reasonCodes.join(", "),
              })}
            </td>
          </tr>
        ) : null}
      </tbody>
    </table>
  );
}

function renderField(
  body: PersonBody,
  field: (typeof FIELDS)[number],
  locale: string,
  display: string,
): string {
  switch (field) {
    case "displayName":
      return display || "";
    case "given": {
      const name = body.names[0];
      return name?.parts.given ?? "";
    }
    case "surname": {
      const name = body.names[0];
      return name?.parts.surname ?? "";
    }
    case "patronymic": {
      const name = body.names[0];
      return name?.parts.patronymic ?? "";
    }
    case "suffix": {
      const name = body.names[0];
      return name?.parts.generationalSuffix ?? "";
    }
    case "livingStatus":
      return body.livingStatus;
    case "privacyLevel":
      return body.privacyLevel ?? "PRIVATE";
    case "biography":
      return body.biography ?? "";
    case "identifiers":
      return (body.identifiers ?? []).map((id) => `${id.scheme}:${id.value}`).join(", ");
    default:
      return locale;
  }
}
