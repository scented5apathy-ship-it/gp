/**
 * apps/web/src/components/profile/person-profile.tsx
 *
 * Client component shell for the E5.4 person profile view.
 * Renders the current `PersonEditorSnapshot` with:
 *
 *   - redaction summary (R15) — never re-redacts client-side;
 *     the server already applied the obligation
 *     (glossary §2.2);
 *   - responsive form for displayName + names + birth/death +
 *     biography + identifiers (R4.6 / R17.1);
 *   - localized field/action labels (R18.1);
 *   - timeline link + map link when the snapshot is loaded;
 *   - permission gate — fields the user cannot edit are still
 *     rendered (so they know the field exists) but the form
 *     inputs are `readOnly` and an inline notice points at the
 *     ABAC reason (R10 / `design.md` §8.3 — server is the
 *     source of truth).
 *
 * The component is intentionally a Client Component because
 * the optimistic-update store mutates in-memory state on every
 * keystroke (R17.4 — "Mutation offline SHALL use a queue
 * … optimistic concurrency"); the server render path is
 * produced by `person-route.tsx` (server) which mounts this
 * client island.
 */
"use client";

import { useCallback, useMemo, useState, type ChangeEvent, type FormEvent } from "react";

import type { Translator } from "@/i18n";
import {
  type DateValue,
  type DateValueKind,
  DATE_VALUE_KINDS,
  type LivingStatus,
  LIVING_STATUSES,
  PERSON_PERMISSION_FIELDS,
  type PersonIdentifier,
  type PersonIdentifierScheme,
  PERSON_IDENTIFIER_SCHEMES,
  type PersonName,
  type PersonBody,
  type PersonPermissionField,
  type PersonPermissions,
} from "@genealogy/api-client";

import {
  applyOptimisticPatch,
  canAct,
  canEditField,
  type PersonEditorSnapshot,
} from "@/lib/profile/store";

export interface PersonProfileProps {
  readonly snapshot: PersonEditorSnapshot;
  readonly translate: Translator;
  readonly locale: string;
  readonly onPatch: (patch: Parameters<typeof applyOptimisticPatch>[1]) => void;
  readonly onCommit: () => void;
  readonly onRevert: () => void;
  readonly onToggleListView?: () => void;
  readonly listViewActive?: boolean;
}

const LIVING_STATUS_KEYS: Readonly<Record<LivingStatus, string>> = {
  LIVING: "profile.livingLIVING",
  PRESUMED_LIVING: "profile.livingPRESUMED_LIVING",
  DECEASED: "profile.livingDECEASED",
  PRESUMED_DECEASED: "profile.livingPRESUMED_DECEASED",
  UNKNOWN: "profile.livingUNKNOWN",
};

const PRIVACY_KEYS: Readonly<Record<string, string>> = {
  PUBLIC: "profile.privacyPUBLIC",
  UNLISTED: "profile.privacyUNLISTED",
  PRIVATE: "profile.privacyPRIVATE",
};

const DATE_KIND_KEYS: Readonly<Record<DateValueKind, string>> = {
  EXACT: "timeline.eventBIRTH",
  ABOUT: "profile.datesPlaceholder",
  RANGE: "timeline.eventBIRTH",
  BEFORE: "profile.datesPlaceholder",
  AFTER: "profile.datesPlaceholder",
  UNKNOWN: "profile.datesPlaceholder",
};

const IDENTIFIER_SCHEME_LABELS: Readonly<Record<PersonIdentifierScheme, string>> = {
  AFN: "AFN",
  ARK: "ARK",
  GRdbID: "GRdbID",
  WikiTreeID: "WikiTreeID",
  VRN: "VRN",
  Custom: "Custom",
};

export function PersonProfile({
  snapshot,
  translate,
  locale,
  onPatch,
  onCommit,
  onRevert,
  onToggleListView,
  listViewActive,
}: PersonProfileProps): JSX.Element {
  const body = snapshot.body;
  const draft = snapshot.draft;
  const permissions: PersonPermissions | null = snapshot.permissions;
  const [editing, setEditing] = useState<boolean>(false);

  const editable = useMemo(() => canAct(permissions, "person.edit"), [permissions]);

  const displayName = draft.displayName ?? body?.displayName ?? "";
  const names = (draft.names as readonly PersonName[] | undefined) ?? body?.names ?? [];
  const birth = (draft.birth as DateValue | undefined) ?? body?.birth;
  const death = (draft.death as DateValue | undefined) ?? body?.death;
  const biography = draft.biography ?? body?.biography ?? "";
  const identifiers =
    (draft.identifiers as readonly PersonIdentifier[] | undefined) ?? body?.identifiers ?? [];
  const privacyLevel = body?.privacyLevel;

  const handleStartEdit = useCallback(() => {
    if (!editable) return;
    setEditing(true);
  }, [editable]);

  const handleCancel = useCallback(() => {
    setEditing(false);
    onRevert();
  }, [onRevert]);

  const handleSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      onCommit();
    },
    [onCommit],
  );

  const redactionSummary = snapshot.body?.redaction;

  if (!body) {
    return (
      <section
        aria-label={translate("profile.sectionLabel")}
        className="person-profile flex flex-col gap-3"
        data-person-status={snapshot.meta.status}
      >
        <p className="text-sm text-surface-muted">{translate("profile.notLoaded")}</p>
      </section>
    );
  }

  return (
    <section
      aria-label={translate("profile.sectionLabel")}
      className="person-profile flex flex-col gap-4"
      data-person-status={snapshot.meta.status}
    >
      <header className="flex flex-wrap items-center gap-2">
        <h1 className="text-2xl font-semibold text-surface-foreground">
          {translate("profile.heading")}
        </h1>
        {snapshot.meta.status === "stale" ? (
          <span className="rounded border border-amber-400 bg-amber-50 px-2 py-0.5 text-xs text-amber-900">
            {translate("profile.editStaleBody")}
          </span>
        ) : null}
        {snapshot.meta.status === "conflict" ? (
          <span className="rounded border border-red-400 bg-red-50 px-2 py-0.5 text-xs text-red-900">
            {translate("profile.editConflictHeading")}
          </span>
        ) : null}
        {redactionSummary && redactionSummary.droppedFieldCount > 0 ? (
          <span className="rounded border border-surface-sunken bg-surface-raised px-2 py-0.5 text-xs text-surface-muted">
            {translate("profile.redactionSummary", {
              dropped: redactionSummary.droppedFieldCount,
              reasons: redactionSummary.reasonCodes.join(", "),
            })}
          </span>
        ) : null}
        {onToggleListView ? (
          <button
            type="button"
            onClick={onToggleListView}
            aria-pressed={listViewActive === true}
            className="ml-auto rounded border border-surface-sunken bg-surface-raised px-3 py-1 text-xs"
          >
            {translate(listViewActive ? "a11y.viewForm" : "a11y.viewList")}
          </button>
        ) : null}
      </header>

      {!editing ? (
        <PersonReadOnlyView
          body={body}
          translate={translate}
          locale={locale}
          editable={editable}
          onEdit={handleStartEdit}
        />
      ) : (
        <form
          className="person-profile__form flex flex-col gap-4"
          onSubmit={handleSubmit}
          aria-label={translate("profile.editTitle")}
        >
          <PersonFieldEditor
            field="displayName"
            label={translate("profile.fieldDisplayName")}
            translate={translate}
            permissions={permissions}
            value={displayName}
            onChange={(value) => onPatch({ displayName: value })}
          />
          <PersonNamesEditor
            names={names}
            permissions={permissions}
            translate={translate}
            onChange={(next) => onPatch({ names: next })}
          />
          <PersonDateEditor
            field="birth"
            label={translate("profile.fieldBirth")}
            value={birth}
            permissions={permissions}
            translate={translate}
            onChange={(next) => {
              const patch: { birth?: DateValue } = {};
              if (next) patch.birth = next;
              onPatch(patch);
            }}
          />
          <PersonDateEditor
            field="death"
            label={translate("profile.fieldDeath")}
            value={death}
            permissions={permissions}
            translate={translate}
            onChange={(next) => {
              const patch: { death?: DateValue } = {};
              if (next) patch.death = next;
              onPatch(patch);
            }}
          />
          <PersonBiographyEditor
            value={biography}
            permissions={permissions}
            translate={translate}
            onChange={(next) => onPatch({ biography: next })}
          />
          <PersonPrivacyReadOnly
            value={privacyLevel}
            permissions={permissions}
            translate={translate}
          />
          <PersonIdentifiersEditor
            identifiers={identifiers}
            permissions={permissions}
            translate={translate}
            onChange={(next) => onPatch({ identifiers: next })}
          />
          <PersonConflictBanner snapshot={snapshot} translate={translate} />
          <div className="flex flex-wrap gap-2">
            <button
              type="submit"
              disabled={snapshot.meta.status === "saving"}
              className="rounded border border-surface-sunken bg-surface-raised px-3 py-2 text-sm"
            >
              {snapshot.meta.status === "saving"
                ? translate("profile.editSaving")
                : translate("profile.editSave")}
            </button>
            <button
              type="button"
              onClick={handleCancel}
              className="rounded border border-surface-sunken bg-surface-raised px-3 py-2 text-sm"
            >
              {translate("profile.editCancel")}
            </button>
            <button
              type="button"
              onClick={onRevert}
              className="rounded border border-surface-sunken bg-surface-raised px-3 py-2 text-sm"
            >
              {translate("profile.editRevert")}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}

function PersonReadOnlyView({
  body,
  translate,
  locale,
  editable,
  onEdit,
}: {
  readonly body: PersonBody;
  readonly translate: Translator;
  readonly locale: string;
  readonly editable: boolean;
  readonly onEdit: () => void;
}): JSX.Element {
  return (
    <article className="person-profile__view flex flex-col gap-3" lang={locale}>
      <h2 className="text-xl font-medium">{body.displayName || translate("profile.redacted")}</h2>
      <dl className="grid grid-cols-1 gap-2 text-sm md:grid-cols-2">
        <PersonDescriptionRow
          label={translate("profile.fieldLiving")}
          value={translate(LIVING_STATUS_KEYS[body.livingStatus])}
        />
        {body.privacyLevel ? (
          <PersonDescriptionRow
            label={translate("profile.fieldPrivacyLevel")}
            value={translate(PRIVACY_KEYS[body.privacyLevel] ?? "profile.privacyPRIVATE")}
          />
        ) : null}
        {body.biography ? (
          <PersonDescriptionRow
            label={translate("profile.fieldBiography")}
            value={body.biography}
          />
        ) : null}
      </dl>
      {editable ? (
        <button
          type="button"
          onClick={onEdit}
          className="self-start rounded border border-surface-sunken bg-surface-raised px-3 py-2 text-sm"
        >
          {translate("profile.editAction")}
        </button>
      ) : null}
    </article>
  );
}

function PersonDescriptionRow({
  label,
  value,
}: {
  readonly label: string;
  readonly value: string;
}): JSX.Element {
  return (
    <div className="flex flex-col gap-1 rounded border border-surface-sunken bg-surface-raised p-3">
      <span className="text-xs uppercase tracking-wide text-surface-muted">{label}</span>
      <span className="text-sm text-surface-foreground">{value}</span>
    </div>
  );
}

function PersonConflictBanner({
  snapshot,
  translate,
}: {
  readonly snapshot: PersonEditorSnapshot;
  readonly translate: Translator;
}): JSX.Element | null {
  if (snapshot.meta.status !== "conflict" || !snapshot.conflict) return null;
  return (
    <aside
      role="alert"
      className="rounded border border-red-400 bg-red-50 p-3 text-sm text-red-900"
      aria-live="assertive"
    >
      <h3 className="font-semibold">{translate("profile.editConflictHeading")}</h3>
      <p>{translate("profile.editConflictBody")}</p>
      <dl className="mt-2 grid grid-cols-1 gap-2 text-xs">
        <PersonDescriptionRow label="server.displayName" value={snapshot.conflict.displayName} />
      </dl>
    </aside>
  );
}

function PersonFieldEditor({
  field,
  label,
  translate,
  permissions,
  value,
  onChange,
}: {
  readonly field: PersonPermissionField;
  readonly label: string;
  readonly translate: Translator;
  readonly permissions: PersonPermissions | null;
  readonly value: string;
  readonly onChange: (next: string) => void;
}): JSX.Element {
  const allowed = canEditField(permissions, field);
  return (
    <label className="flex flex-col gap-1 text-sm">
      <span className="text-surface-muted">{label}</span>
      <input
        type="text"
        value={value}
        readOnly={!allowed}
        onChange={(event: ChangeEvent<HTMLInputElement>) => onChange(event.target.value)}
        aria-readonly={!allowed}
        className="rounded border border-surface-sunken bg-surface-raised px-2 py-1"
      />
      {!allowed ? (
        <span className="text-xs text-surface-muted">{translate("profile.permissionsDenied")}</span>
      ) : null}
    </label>
  );
}

function PersonNamesEditor({
  names,
  permissions,
  translate,
  onChange,
}: {
  readonly names: readonly PersonName[];
  readonly permissions: PersonPermissions | null;
  readonly translate: Translator;
  readonly onChange: (next: readonly PersonName[]) => void;
}): JSX.Element {
  const allowed = canEditField(permissions, "names");
  return (
    <fieldset className="flex flex-col gap-2 text-sm" aria-readonly={!allowed} disabled={!allowed}>
      <legend className="text-surface-muted">{translate("profile.fieldNames")}</legend>
      {names.map((name, index) => (
        <div
          key={`name-${index}`}
          className="flex flex-wrap gap-2 rounded border border-surface-sunken bg-surface-raised p-2"
        >
          <input
            type="text"
            value={name.parts.given}
            readOnly={!allowed}
            aria-label={translate("profile.namesGiven")}
            onChange={(event) => {
              const next = names.map((n, i) =>
                i === index ? { ...n, parts: { ...n.parts, given: event.target.value } } : n,
              );
              onChange(next);
            }}
            className="rounded border border-surface-sunken bg-surface px-2 py-1"
          />
          <input
            type="text"
            value={name.parts.surname}
            readOnly={!allowed}
            aria-label={translate("profile.namesSurname")}
            onChange={(event) => {
              const next = names.map((n, i) =>
                i === index ? { ...n, parts: { ...n.parts, surname: event.target.value } } : n,
              );
              onChange(next);
            }}
            className="rounded border border-surface-sunken bg-surface px-2 py-1"
          />
          <button
            type="button"
            disabled={!allowed}
            onClick={() => onChange(names.filter((_, i) => i !== index))}
            className="rounded border border-surface-sunken bg-surface px-2 py-1 text-xs"
          >
            {translate("profile.namesRemove")}
          </button>
        </div>
      ))}
      <button
        type="button"
        disabled={!allowed}
        onClick={() =>
          onChange([...names, { locale: "en", script: "Latn", parts: { given: "", surname: "" } }])
        }
        className="self-start rounded border border-surface-sunken bg-surface px-2 py-1 text-xs"
      >
        {translate("profile.namesAdd")}
      </button>
    </fieldset>
  );
}

function PersonDateEditor({
  field,
  label,
  value,
  permissions,
  translate,
  onChange,
}: {
  readonly field: PersonPermissionField;
  readonly label: string;
  readonly value: DateValue | undefined;
  readonly permissions: PersonPermissions | null;
  readonly translate: Translator;
  readonly onChange: (next: DateValue | undefined) => void;
}): JSX.Element {
  const allowed = canEditField(permissions, field);
  const date: DateValue = value ?? { kind: "UNKNOWN" };
  return (
    <fieldset className="flex flex-col gap-2 text-sm" aria-readonly={!allowed} disabled={!allowed}>
      <legend className="text-surface-muted">{label}</legend>
      <div className="flex flex-wrap gap-2">
        <select
          value={date.kind}
          onChange={(event) => onChange({ ...date, kind: event.target.value as DateValueKind })}
          className="rounded border border-surface-sunken bg-surface-raised px-2 py-1"
        >
          {DATE_VALUE_KINDS.map((kind) => (
            <option key={kind} value={kind}>
              {translate(DATE_KIND_KEYS[kind])}
            </option>
          ))}
        </select>
        <input
          type="text"
          value={date.original ?? ""}
          placeholder={translate("profile.datesPlaceholder")}
          onChange={(event) => onChange({ ...date, original: event.target.value })}
          className="rounded border border-surface-sunken bg-surface-raised px-2 py-1"
        />
        <input
          type="number"
          min={1}
          max={9999}
          value={date.year ?? ""}
          onChange={(event) => {
            const raw = event.target.value;
            const parsed = raw === "" ? undefined : Number.parseInt(raw, 10);
            onChange(parsed === undefined ? { ...date } : { ...date, year: parsed });
          }}
          className="w-24 rounded border border-surface-sunken bg-surface-raised px-2 py-1"
          aria-label={`${label} year`}
        />
      </div>
      {!allowed ? (
        <span className="text-xs text-surface-muted">{translate("profile.permissionsDenied")}</span>
      ) : null}
    </fieldset>
  );
}

function PersonBiographyEditor({
  value,
  permissions,
  translate,
  onChange,
}: {
  readonly value: string;
  readonly permissions: PersonPermissions | null;
  readonly translate: Translator;
  readonly onChange: (next: string) => void;
}): JSX.Element {
  const allowed = canEditField(permissions, "biography");
  return (
    <label className="flex flex-col gap-1 text-sm">
      <span className="text-surface-muted">{translate("profile.fieldBiography")}</span>
      <textarea
        value={value}
        readOnly={!allowed}
        aria-readonly={!allowed}
        maxLength={8000}
        onChange={(event) => onChange(event.target.value)}
        className="rounded border border-surface-sunken bg-surface-raised px-2 py-1"
        rows={4}
      />
      {!allowed ? (
        <span className="text-xs text-surface-muted">{translate("profile.permissionsDenied")}</span>
      ) : null}
    </label>
  );
}

function PersonPrivacyReadOnly({
  value,
  permissions,
  translate,
}: {
  readonly value: string | undefined;
  readonly permissions: PersonPermissions | null;
  readonly translate: Translator;
}): JSX.Element {
  const canEdit = canEditField(permissions, "privacyLevel");
  return (
    <p className="rounded border border-surface-sunken bg-surface-raised p-3 text-sm">
      <span className="text-surface-muted">{translate("profile.fieldPrivacyLevel")}: </span>
      <span className="text-surface-foreground">
        {value ? translate(PRIVACY_KEYS[value] ?? "profile.privacyPRIVATE") : "—"}
      </span>
      {!canEdit ? (
        <span className="ml-2 text-xs text-surface-muted">
          {translate("profile.permissionsDenied")}
        </span>
      ) : null}
    </p>
  );
}

function PersonIdentifiersEditor({
  identifiers,
  permissions,
  translate,
  onChange,
}: {
  readonly identifiers: readonly PersonIdentifier[];
  readonly permissions: PersonPermissions | null;
  readonly translate: Translator;
  readonly onChange: (next: readonly PersonIdentifier[]) => void;
}): JSX.Element {
  const allowed = canEditField(permissions, "identifiers");
  return (
    <fieldset className="flex flex-col gap-2 text-sm" aria-readonly={!allowed} disabled={!allowed}>
      <legend className="text-surface-muted">{translate("profile.fieldIdentifiers")}</legend>
      {identifiers.map((identifier, index) => (
        <div
          key={`identifier-${index}`}
          className="flex flex-wrap gap-2 rounded border border-surface-sunken bg-surface-raised p-2"
        >
          <select
            value={identifier.scheme}
            onChange={(event) => {
              const next = identifiers.map((id, i) =>
                i === index ? { ...id, scheme: event.target.value as PersonIdentifierScheme } : id,
              );
              onChange(next);
            }}
            aria-label={translate("profile.identifierScheme")}
            className="rounded border border-surface-sunken bg-surface px-2 py-1"
          >
            {PERSON_IDENTIFIER_SCHEMES.map((scheme) => (
              <option key={scheme} value={scheme}>
                {IDENTIFIER_SCHEME_LABELS[scheme]}
              </option>
            ))}
          </select>
          <input
            type="text"
            value={identifier.value}
            readOnly={!allowed}
            aria-label={translate("profile.identifierValue")}
            onChange={(event) => {
              const next = identifiers.map((id, i) =>
                i === index ? { ...id, value: event.target.value } : id,
              );
              onChange(next);
            }}
            className="rounded border border-surface-sunken bg-surface px-2 py-1"
          />
          <button
            type="button"
            disabled={!allowed}
            onClick={() => onChange(identifiers.filter((_, i) => i !== index))}
            className="rounded border border-surface-sunken bg-surface px-2 py-1 text-xs"
          >
            {translate("profile.identifiersRemove")}
          </button>
        </div>
      ))}
      <button
        type="button"
        disabled={!allowed}
        onClick={() => onChange([...identifiers, { scheme: "Custom", value: "" }])}
        className="self-start rounded border border-surface-sunken bg-surface px-2 py-1 text-xs"
      >
        {translate("profile.identifiersAdd")}
      </button>
    </fieldset>
  );
}

/**
 * Re-exported so React Router can compose it with the editor +
 * timeline children without each route re-importing the
 * underlying closed-set lists.
 */
export const PROFILE_PERMISSION_FIELDS = PERSON_PERMISSION_FIELDS;
export { LIVING_STATUSES };
export { DATE_VALUE_KINDS };
