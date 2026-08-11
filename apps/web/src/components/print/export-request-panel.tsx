/**
 * apps/web/src/components/print/export-request-panel.tsx
 *
 * Client component that hosts the print/export form (R6.4 /
 * J-SHARE-3). The panel:
 *
 *   - is mounted from `<PersonRoute>` and `<TreeViewRoute>` via
 *     a `<details>` toggle so the chrome stays out of the way
 *     until the user opts in;
 *   - submits an `ExportJob` via `submitExportJob` (idempotency
 *     key + correlation id), then surfaces the live region
 *     status via the E5.5 `useLiveRegionAnnouncer` hook;
 *   - previews the redaction summary, watermark mode and signed
 *     URL lifecycle (TTL countdown) without re-redacting the
 *     server payload;
 *   - marks every interactive control with the E5.5 a11y
 *     conventions (label, aria-pressed, aria-describedby for
 *     helper text);
 *   - carries `data-no-print` and `data-export-panel` so the
 *     print stylesheet strips it from the output.
 */
"use client";

import { useCallback, useEffect, useId, useMemo, useState } from "react";

import type { Translator } from "@/i18n";

import { LiveRegion } from "@/lib/a11y/live-region";
import { useLiveRegionAnnouncer } from "@/lib/a11y/use-live-region-announcer";
import { getBffClient } from "@/lib/api/client";

import {
  PAGE_BREAK_BEHAVIOURS,
  PRINT_FORMATS,
  PRINT_LAYOUTS,
  PRINT_SCOPES,
  PRIVACY_LEVELS,
  type PageBreakBehaviour,
  type PrintFormat,
  type PrintLayout,
  type PrintScope,
  type PrivacyLevel,
  type WatermarkMode,
  resolveDefaultWatermark,
} from "@/lib/print/print-policy";
import {
  formatRedactionSummary,
  previewObligationDescription,
  summariseObligations,
} from "@/lib/print/redaction-preview";
import { pollExportJob, submitExportJob, type SubmitExportJobInput } from "@/lib/print/client";
import {
  resolveSignedUrlOrigin,
  signedUrlTimeRemaining,
  type SignedUrlHandle,
} from "@/lib/print/signed-url-handle";
import type { ExportJobSnapshot } from "@/lib/print/export-job";

export interface ExportRequestPanelProps {
  readonly locale: string;
  readonly translate: Translator;
  readonly tenantId: string;
  readonly treeId: string;
  readonly rootPersonId: string;
  readonly actorPseudoId: string;
  readonly defaultScope?: PrintScope;
  readonly defaultPrivacy?: PrivacyLevel;
}

const DEFAULT_TTL_SECONDS = 600;

export function ExportRequestPanel({
  locale,
  translate,
  tenantId,
  treeId,
  rootPersonId,
  actorPseudoId,
  defaultScope = "subtree",
  defaultPrivacy = "PUBLIC",
}: ExportRequestPanelProps): JSX.Element {
  const formId = useId();
  const { announcer, message } = useLiveRegionAnnouncer();
  const [scope, setScope] = useState<PrintScope>(defaultScope);
  const [format, setFormat] = useState<PrintFormat>("PDF");
  const [privacy, setPrivacy] = useState<PrivacyLevel>(defaultPrivacy);
  const [layout, setLayout] = useState<PrintLayout>("A4-portrait");
  const [pageBreak, setPageBreak] = useState<PageBreakBehaviour>("per-generation");
  const [includeLiving, setIncludeLiving] = useState<boolean>(true);
  const [hasShareToken, setHasShareToken] = useState<boolean>(false);
  const [snapshot, setSnapshot] = useState<ExportJobSnapshot | null>(null);
  const [tick, setTick] = useState<number>(0);

  useEffect(() => {
    if (snapshot?.status !== "ready") return;
    const interval = setInterval(() => setTick((value) => value + 1), 1000);
    return () => clearInterval(interval);
  }, [snapshot]);

  const watermark: WatermarkMode = useMemo(
    () => resolveDefaultWatermark(privacy, hasShareToken),
    [privacy, hasShareToken],
  );

  const handleSubmit = useCallback(async () => {
    const input: SubmitExportJobInput = {
      tenantId,
      treeId,
      rootPersonId,
      scope,
      format,
      privacy,
      includeLiving,
      pageBreakBehaviour: pageBreak,
      layout,
      ttlSeconds: DEFAULT_TTL_SECONDS,
      hasShareToken,
      nodeCount: 250,
      locale,
      actorPseudoId,
      watermark,
    };
    announcer.announce(translate("print.statusQueued"));
    const { snapshot: next } = await submitExportJob({
      client: getBffClient(),
      input,
    });
    setSnapshot(next);
    announcer.announce(translate(`print.status${capitalize(next.status)}`));
  }, [
    actorPseudoId,
    announcer,
    format,
    hasShareToken,
    includeLiving,
    layout,
    locale,
    pageBreak,
    privacy,
    rootPersonId,
    scope,
    tenantId,
    translate,
    treeId,
    watermark,
  ]);

  const handleRefresh = useCallback(async () => {
    if (!snapshot) return;
    const next = await pollExportJob({
      client: getBffClient(),
      prev: snapshot,
      deadlineMs: 5_000,
    });
    setSnapshot(next);
    announcer.announce(translate(`print.status${capitalize(next.status)}`));
  }, [announcer, snapshot, translate]);

  const handleReset = useCallback(() => {
    setSnapshot(null);
    announcer.announce(translate("print.statusIdle"));
  }, [announcer, translate]);

  return (
    <section
      className="rounded border border-surface-sunken bg-surface-raised p-4"
      data-export-panel="true"
      data-no-print="true"
      aria-labelledby={`${formId}-heading`}
    >
      <header className="flex items-center justify-between">
        <h2 id={`${formId}-heading`} className="text-base font-semibold">
          {translate("print.panelHeading")}
        </h2>
        <p className="text-xs text-surface-muted">{translate("print.panelSubtitle")}</p>
      </header>
      <LiveRegion announcer={{ announce: announcer.announce }} message={message} />
      <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">{translate("print.scopeLabel")}</span>
          <select
            className="rounded border border-surface-sunken bg-surface px-2 py-1"
            value={scope}
            onChange={(event) => setScope(event.target.value as PrintScope)}
            aria-describedby={`${formId}-scope-help`}
          >
            {PRINT_SCOPES.map((value) => (
              <option key={value} value={value}>
                {translate(`print.scope.${value}`)}
              </option>
            ))}
          </select>
          <span id={`${formId}-scope-help`} className="text-xs text-surface-muted">
            {translate("print.scopeHelp")}
          </span>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">{translate("print.formatLabel")}</span>
          <select
            className="rounded border border-surface-sunken bg-surface px-2 py-1"
            value={format}
            onChange={(event) => setFormat(event.target.value as PrintFormat)}
            aria-describedby={`${formId}-format-help`}
          >
            {PRINT_FORMATS.map((value) => (
              <option key={value} value={value}>
                {translate(`print.format.${value}`)}
              </option>
            ))}
          </select>
          <span id={`${formId}-format-help`} className="text-xs text-surface-muted">
            {translate("print.formatHelp")}
          </span>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">{translate("print.privacyLabel")}</span>
          <select
            className="rounded border border-surface-sunken bg-surface px-2 py-1"
            value={privacy}
            onChange={(event) => setPrivacy(event.target.value as PrivacyLevel)}
            aria-describedby={`${formId}-privacy-help`}
          >
            {PRIVACY_LEVELS.map((value) => (
              <option key={value} value={value}>
                {translate(`print.privacy.${value}`)}
              </option>
            ))}
          </select>
          <span id={`${formId}-privacy-help`} className="text-xs text-surface-muted">
            {translate("print.privacyHelp")}
          </span>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">{translate("print.layoutLabel")}</span>
          <select
            className="rounded border border-surface-sunken bg-surface px-2 py-1"
            value={layout}
            onChange={(event) => setLayout(event.target.value as PrintLayout)}
            aria-describedby={`${formId}-layout-help`}
          >
            {PRINT_LAYOUTS.map((value) => (
              <option key={value} value={value}>
                {translate(`print.layout.${value}`)}
              </option>
            ))}
          </select>
          <span id={`${formId}-layout-help`} className="text-xs text-surface-muted">
            {translate("print.layoutHelp")}
          </span>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">{translate("print.pageBreakLabel")}</span>
          <select
            className="rounded border border-surface-sunken bg-surface px-2 py-1"
            value={pageBreak}
            onChange={(event) => setPageBreak(event.target.value as PageBreakBehaviour)}
            aria-describedby={`${formId}-pageBreak-help`}
          >
            {PAGE_BREAK_BEHAVIOURS.map((value) => (
              <option key={value} value={value}>
                {translate(`print.pageBreak.${value}`)}
              </option>
            ))}
          </select>
          <span id={`${formId}-pageBreak-help`} className="text-xs text-surface-muted">
            {translate("print.pageBreakHelp")}
          </span>
        </label>
        <fieldset className="flex flex-col gap-1 text-sm">
          <legend className="font-medium">{translate("print.optionsLabel")}</legend>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={includeLiving}
              onChange={(event) => setIncludeLiving(event.target.checked)}
              aria-describedby={`${formId}-includeLiving-help`}
            />
            <span>{translate("print.includeLiving")}</span>
          </label>
          <span id={`${formId}-includeLiving-help`} className="text-xs text-surface-muted">
            {translate("print.includeLivingHelp")}
          </span>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={hasShareToken}
              onChange={(event) => setHasShareToken(event.target.checked)}
              aria-describedby={`${formId}-shareToken-help`}
            />
            <span>{translate("print.hasShareGrant")}</span>
          </label>
          <span id={`${formId}-shareToken-help`} className="text-xs text-surface-muted">
            {translate("print.hasShareGrantHelp")}
          </span>
        </fieldset>
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <button
          type="button"
          className="rounded bg-primary px-3 py-1 text-sm text-on-primary"
          onClick={handleSubmit}
          data-action="submit-export-job"
          aria-pressed={snapshot?.status === "queued" || snapshot?.status === "running"}
          disabled={snapshot?.status === "queued" || snapshot?.status === "running"}
        >
          {translate("print.submitAction")}
        </button>
        <button
          type="button"
          className="rounded border border-surface-sunken px-3 py-1 text-sm"
          onClick={handleRefresh}
          disabled={!snapshot || snapshot.status === "idle" || snapshot.status === "ready"}
        >
          {translate("print.refreshAction")}
        </button>
        <button
          type="button"
          className="rounded border border-surface-sunken px-3 py-1 text-sm"
          onClick={handleReset}
        >
          {translate("print.resetAction")}
        </button>
        <span className="ml-2 text-xs text-surface-muted" data-testid="export-watermark">
          {translate("print.watermarkLabel")}: {translate(`print.watermark.${watermark}`)}
        </span>
      </div>
      <ExportStatusBlock snapshot={snapshot} translate={translate} tick={tick} />
    </section>
  );
}

function capitalize(value: string): string {
  return value.length === 0 ? value : value[0]!.toUpperCase() + value.slice(1);
}

interface ExportStatusBlockProps {
  readonly snapshot: ExportJobSnapshot | null;
  readonly translate: Translator;
  readonly tick: number;
}

function ExportStatusBlock({
  snapshot,
  translate,
  tick,
}: ExportStatusBlockProps): JSX.Element | null {
  void tick;
  if (!snapshot) return null;
  const statusKey = `print.status${capitalize(snapshot.status)}`;
  return (
    <div
      className="mt-4 rounded border border-surface-sunken p-3 text-sm"
      data-export-status={snapshot.status}
    >
      <p>
        <strong>{translate("print.statusLabel")}:</strong> <span>{translate(statusKey)}</span>
      </p>
      {snapshot.obligations && snapshot.obligations.length > 0 ? (
        <div className="mt-2">
          <p className="font-medium">{translate("print.obligationsHeading")}</p>
          <ul className="ml-5 list-disc text-xs">
            {snapshot.obligations.map((obligation, index) => (
              <li key={`${obligation.reasonCode}-${index}`}>
                {previewObligationDescription(obligation, translate)}
              </li>
            ))}
          </ul>
          <p className="mt-1 text-xs text-surface-muted">
            {formatRedactionSummary({
              totalFieldsDropped: snapshot.obligations.reduce(
                (acc, o) => acc + o.droppedFieldCount,
                0,
              ),
              obligations: snapshot.obligations,
              emittedAt: snapshot.updatedAt,
            })}
          </p>
        </div>
      ) : null}
      {snapshot.status === "ready" && snapshot.signedUrl && snapshot.signedUrlExpiresAt ? (
        <SignedUrlBlock
          handle={{
            url: snapshot.signedUrl,
            issuedAt: new Date(snapshot.createdAt),
            expiresAt: new Date(snapshot.signedUrlExpiresAt),
            correlationId: snapshot.audit.correlationId,
            watermark: snapshot.audit.watermark,
            obligationCount: snapshot.obligations?.length ?? 0,
          }}
          translate={translate}
        />
      ) : null}
    </div>
  );
}

interface SignedUrlBlockProps {
  readonly handle: SignedUrlHandle;
  readonly translate: Translator;
}

function SignedUrlBlock({ handle, translate }: SignedUrlBlockProps): JSX.Element {
  const remaining = signedUrlTimeRemaining(handle);
  const origin = resolveSignedUrlOrigin(handle.url);
  const summary = summariseObligations([]);
  void summary;
  return (
    <div className="mt-2 rounded border border-surface-sunken p-2 text-xs" data-export-signed-url>
      <p>
        <strong>{translate("print.signedUrlOriginLabel")}:</strong>{" "}
        {translate(`print.signedUrlOrigin.${origin}`)}
      </p>
      <p>
        <strong>{translate("print.signedUrlRemainingLabel")}:</strong>{" "}
        {remaining.status === "expired"
          ? translate("print.signedUrlExpired")
          : translate("print.signedUrlSeconds", { seconds: remaining.secondsLeft })}
      </p>
      <p>
        <strong>{translate("print.signedUrlCorrelationLabel")}:</strong>{" "}
        <code>{handle.correlationId}</code>
      </p>
    </div>
  );
}
