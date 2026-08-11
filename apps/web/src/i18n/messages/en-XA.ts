/**
 * Pseudolocale `en-XA` (English / Accented + padded).
 *
 * Per R18.4 + `design.md` §10.4 every catalogue the platform ships
 * must be mirrored into a pseudolocale so QA can detect hard-coded
 * English, untranslated keys, and layout blowups before the real
 * translator touches the strings.
 *
 * Generation rule (enforced by `scripts/lint-a11y-i18n.mjs`):
 *   1. every key in `en.ts` MUST exist here;
 *   2. every value is wrapped with `[…]` and padded with 8 `~`
 *      characters on each side so layout regressions show up
 *      visually (think longer + accented text);
 *   3. accents are applied where the source uses diacritics (so
 *      font fallbacks get exercised).
 *
 * This catalogue is QA-only and is gated by the
 * `GENEALOGY_PSEUDOLOCALE=1` env var at render time. It must
 * never reach production — the linter verifies that no React
 * component imports `en-XA` directly.
 */
import type { MessageTree } from "../index";

function pad(value: string): string {
  return `[${value}~${"~".repeat(Math.max(0, 8))}]`;
}

const accent = (value: string): string => value.replace(/[aeiou]/g, (m) => `${m}́`);

export const enXA = {
  app: {
    title: pad(accent("Genealogy Platform")),
    tagline: pad(accent("Privacy-first family history")),
  },
  nav: {
    home: pad(accent("Home")),
    trees: pad(accent("Trees")),
    people: pad(accent("People")),
    sources: pad(accent("Sources")),
    dna: pad(accent("DNA")),
    settings: pad(accent("Settings")),
    skipToContent: pad(accent("Skip to main content")),
  },
  home: {
    headline: pad(accent("Build your family tree without giving up privacy.")),
    subhead: pad(
      accent(
        "Sovereign storage, transparent consent and offline-friendly PWA — your data stays yours.",
      ),
    ),
    ctaPrimary: pad(accent("Create your first tree")),
    ctaSecondary: pad(accent("Learn about consent")),
    featuresTitle: pad(accent("What you get")),
    featureOfflineTitle: pad(accent("Offline-friendly shell")),
    featureOfflineBody: pad(
      accent(
        "The PWA shell keeps the navigation, manifest and design tokens available even when the network drops.",
      ),
    ),
    featureI18nTitle: pad(accent("Multilingual + RTL")),
    featureI18nBody: pad(
      accent(
        "Every string flows through the locale catalogue. Right-to-left scripts land in E12.3 alongside ICU.",
      ),
    ),
    featureContractsTitle: pad(accent("Typed API client")),
    featureContractsBody: pad(
      accent(
        "Generated from the OpenAPI contracts in `contracts/openapi/` — your IDE knows every endpoint.",
      ),
    ),
  },
  errors: {
    boundaryTitle: pad(accent("Something went wrong")),
    boundaryBody: pad(
      accent(
        "The page could not be rendered. The error has been logged with a correlation id; please retry.",
      ),
    ),
    boundaryAction: pad(accent("Reload the page")),
    notFoundTitle: pad(accent("Page not found")),
    notFoundBody: pad(accent("The URL you requested does not match any known route.")),
    notFoundAction: pad(accent("Back to home")),
    unauthorizedTitle: pad(accent("Sign-in required")),
    unauthorizedBody: pad(accent("This page is only available to authenticated members.")),
    unauthorizedAction: pad(accent("Sign in")),
  },
  loading: {
    page: pad(accent("Loading page…")),
    section: pad(accent("Loading…")),
  },
  tree: {
    sectionLabel: pad(accent("Tree view")),
    heading: pad(accent("Tree")),
    breadcrumbLabel: pad(accent("Tree breadcrumb")),
    searchRootLabel: pad(accent("Search root person")),
    searchRootPlaceholder: pad(accent("Person id (UUID v4)")),
    searchRootHelp: pad(accent("Submit to refetch the projection anchored at this person.")),
    searchRootAction: pad(accent("Set root")),
    controlsLabel: pad(accent("Tree controls")),
    directionLabel: pad(accent("Direction")),
    depthLabel: pad(accent("Depth (1–12)")),
    zoomIn: pad(accent("Zoom in")),
    zoomOut: pad(accent("Zoom out")),
    resetView: pad(accent("Reset view")),
    viewportFetch: pad(accent("Fetch viewport")),
    toggleCollapse: pad(accent("Toggle collapse on selected")),
    focusSelected: pad(accent("Focus selected person")),
    minimapLabel: pad(accent("Minimap")),
    minimapFootnote: pad(accent("Generations present in the current snapshot.")),
    listLabel: pad(accent("Person list (keyboard alternative)")),
    listHelp: pad(
      accent("Use the arrow keys to move, Home / End to jump, Enter to collapse a branch."),
    ),
    empty: pad(accent("No people in the current snapshot.")),
    slotsFootnote: pad(
      accent("Node slot is {width}×{height} logical pixels; pan/zoom transforms it."),
    ),
    redacted: pad(accent("Redacted")),
    canvasLabel: pad(accent("Tree canvas with {count} people and {edges} edges.")),
    redactionSummary: pad(accent("{dropped} fields dropped — reasons: {reasons}")),
    viewKindPedigree: pad(accent("Pedigree")),
    viewKindDescendant: pad(accent("Descendant")),
    viewKindFan: pad(accent("Fan chart")),
    viewKindHourglass: pad(accent("Hourglass")),
    viewKindFamily: pad(accent("Family")),
    viewKindsLabel: pad(accent("View kinds")),
    viewKindFootnote: pad(
      accent(
        "Renderer choice is pending ADR-E0.5-10 closure. The placeholder canvas renders the same projection contract every view consumes.",
      ),
    ),
    direction: {
      ancestors: pad(accent("Ancestors")),
      descendants: pad(accent("Descendants")),
      both: pad(accent("Both")),
      spouse_fan: pad(accent("Spouse fan")),
    },
    statusIdle: pad(accent("Idle.")),
    statusLoading: pad(accent("Loading…")),
    statusReady: pad(accent("Ready.")),
    statusStale: pad(accent("Stale — refetching.")),
    statusError: pad(accent("Error.")),
    treesListHeading: pad(accent("Trees")),
    treesListEmpty: pad(accent("No trees available in this tenant.")),
  },
  profile: {
    sectionLabel: pad(accent("Person profile")),
    heading: pad(accent("Profile")),
    editAction: pad(accent("Edit")),
    editTitle: pad(accent("Edit person")),
    editCancel: pad(accent("Cancel")),
    editRevert: pad(accent("Revert")),
    editSave: pad(accent("Save changes")),
    editSaving: pad(accent("Saving…")),
    editConflictHeading: pad(accent("Version conflict")),
    editConflictBody: pad(
      accent(
        "Someone else updated this person while you were editing. Compare your edits with the latest version and re-apply.",
      ),
    ),
    editStaleBody: pad(accent("Read-model is stale. Refetching the latest version.")),
    editErrorBody: pad(accent("Could not save your changes. Try again.")),
    fieldDisplayName: pad(accent("Display name")),
    fieldNames: pad(accent("Names")),
    fieldBirth: pad(accent("Birth")),
    fieldDeath: pad(accent("Death")),
    fieldBiography: pad(accent("Biography")),
    fieldIdentifiers: pad(accent("Identifiers")),
    fieldPrivacyLevel: pad(accent("Privacy")),
    livingLIVING: pad(accent("Living")),
    livingPRESUMED_LIVING: pad(accent("Presumed living")),
    livingDECEASED: pad(accent("Deceased")),
    livingPRESUMED_DECEASED: pad(accent("Presumed deceased")),
    livingUNKNOWN: pad(accent("Unknown")),
    privacyPUBLIC: pad(accent("Public")),
    privacyUNLISTED: pad(accent("Unlisted")),
    privacyPRIVATE: pad(accent("Private")),
    redacted: pad(accent("Redacted")),
    redactionSummary: pad(accent("{dropped} fields dropped — reasons: {reasons}")),
    datesPlaceholder: pad(accent("Original text (kept verbatim)")),
    namesGiven: pad(accent("Given")),
    namesSurname: pad(accent("Surname")),
    namesPatronymic: pad(accent("Patronymic")),
    namesSuffix: pad(accent("Suffix")),
    namesPrimary: pad(accent("Primary")),
    namesAdd: pad(accent("Add name")),
    namesRemove: pad(accent("Remove")),
    identifiersAdd: pad(accent("Add identifier")),
    identifiersRemove: pad(accent("Remove")),
    identifierScheme: pad(accent("Scheme")),
    identifierValue: pad(accent("Value")),
    permissionsDenied: pad(accent("You don't have permission to edit this field.")),
    notLoaded: pad(accent("Profile not loaded yet.")),
  },
  timeline: {
    sectionLabel: pad(accent("Timeline")),
    heading: pad(accent("Personal timeline")),
    empty: pad(accent("No events in this range.")),
    rangeLabel: pad(accent("Year range")),
    fromLabel: pad(accent("From")),
    toLabel: pad(accent("To")),
    loadAction: pad(accent("Load")),
    loading: pad(accent("Loading…")),
    eventBIRTH: pad(accent("Birth")),
    eventDEATH: pad(accent("Death")),
    eventMARRIAGE: pad(accent("Marriage")),
    eventDIVORCE: pad(accent("Divorce")),
    eventRESIDENCE: pad(accent("Residence")),
    eventMIGRATION: pad(accent("Migration")),
    eventMILITARY: pad(accent("Military")),
    eventEDUCATION: pad(accent("Education")),
    eventRELIGION: pad(accent("Religion")),
    eventCUSTOM: pad(accent("Custom")),
  },
  map: {
    sectionLabel: pad(accent("Place")),
    heading: pad(accent("Place lookup")),
    queryLabel: pad(accent("Search place")),
    queryPlaceholder: pad(accent("City, county, country…")),
    searchAction: pad(accent("Search")),
    degraded: pad(accent("Place provider unavailable — please type the place manually.")),
    noResults: pad(accent("No matches.")),
    selectAction: pad(accent("Use this place")),
    providerFootnote: pad(
      accent("Default provider: open-data (OSM Nominatim + Wikidata) per ADR-E0.5-14."),
    ),
  },
  a11y: {
    skipToContent: pad(accent("Skip to main content")),
    viewList: pad(accent("Switch to list view")),
    viewForm: pad(accent("Switch to form view")),
    selectedAnnounce: pad(accent("Selected person {name}")),
    statusAnnounce: pad(accent("Status: {status}")),
    timelineAnnounce: pad(accent("{count} events loaded")),
    placeDegradedAnnounce: pad(
      accent("Place provider unavailable — please type the place manually."),
    ),
    tableCaption: pad(accent("Person profile table for {id}")),
    tableHeaderField: pad(accent("Field")),
    tableHeaderValue: pad(accent("Value")),
    field: {
      displayName: pad(accent("Display name")),
      given: pad(accent("Given name")),
      surname: pad(accent("Family name")),
      patronymic: pad(accent("Patronymic")),
      suffix: pad(accent("Suffix")),
      livingStatus: pad(accent("Living status")),
      privacyLevel: pad(accent("Privacy level")),
      biography: pad(accent("Biography")),
      identifiers: pad(accent("Identifiers")),
    },
    treeListHelp: pad(
      accent(
        "Use the arrow keys to move, Home / End to jump, PageUp / PageDown to skip pages, Enter to collapse a branch.",
      ),
    ),
    reducedMotionNote: pad(accent("Animations are reduced per your system preference.")),
    rtlNote: pad(accent("Right-to-left layout active for this locale.")),
    focusReturnNote: pad(accent("Focus returned to the previous control after the editor closed.")),
  },
  i18n: {
    nameOrderTitle: pad(accent("Name order policy")),
    nameOrderGivenFirst: pad(accent("Given name first (e.g. Jane Doe)")),
    nameOrderFamilyFirst: pad(accent("Family name first (e.g. Nguyễn Văn A)")),
    nameOrderFamilyOnly: pad(accent("Family name only")),
    nameOrderGivenFamilyComma: pad(accent("Given then family, comma separated (e.g. Doe, Jane)")),
    pseudolocaleNote: pad(
      accent("Pseudolocales (en-XA, ar-XB) are QA-only and never ship to production."),
    ),
    pseudolocaleEnXA: pad(accent("English (padded)")),
    pseudolocaleArXB: pad(accent("Arabic (RTL mirrored, padded)")),
  },
  print: {
    toolbarHeading: pad(accent("Print & export")),
    toolbarHeadingPerson: pad(accent("Print & export this person")),
    panelHeading: pad(accent("Print / export panel")),
    panelSubtitle: pad(
      accent("Server-side generation runs asynchronously; the signed URL is short-lived."),
    ),
    scopeLabel: pad(accent("Scope")),
    scopeHelp: pad(accent("Which subset of the tree you want to print or export.")),
    formatLabel: pad(accent("Format")),
    formatHelp: pad(
      accent("Output format delivered by Gotenberg (PDF) or the renderer (PNG/JPEG)."),
    ),
    privacyLabel: pad(accent("Privacy")),
    privacyHelp: pad(accent("PRIVATE exports are limited to the current person only.")),
    layoutLabel: pad(accent("Layout")),
    layoutHelp: pad(accent("Paper size + orientation for the output PDF.")),
    pageBreakLabel: pad(accent("Page break")),
    pageBreakHelp: pad(accent("Deterministic rule for splitting the output across pages.")),
    optionsLabel: pad(accent("Options")),
    includeLiving: pad(accent("Include living persons (with redaction)")),
    includeLivingHelp: pad(
      accent("When off, every living person is fully redacted regardless of watermark."),
    ),
    hasShareGrant: pad(accent("Export will be shared via a token")),
    hasShareGrantHelp: pad(accent("Adds the token hash to the watermark obligation (R15 row W).")),
    scope: {
      currentPerson: pad(accent("Current person")),
      subtree: pad(accent("Subtree")),
      ancestors: pad(accent("Ancestors")),
      descendants: pad(accent("Descendants")),
      family: pad(accent("Family")),
    },
    format: {
      PDF: pad(accent("PDF (Gotenberg)")),
      PNG: pad(accent("PNG (single frame)")),
      JPEG: pad(accent("JPEG (single frame)")),
    },
    privacy: {
      PUBLIC: pad(accent("Public")),
      UNLISTED: pad(accent("Unlisted")),
      PRIVATE: pad(accent("Private")),
    },
    layout: {
      "A4-portrait": pad(accent("A4 portrait")),
      "A4-landscape": pad(accent("A4 landscape")),
      "Letter-portrait": pad(accent("Letter portrait")),
      "Letter-landscape": pad(accent("Letter landscape")),
    },
    pageBreak: {
      "per-generation": pad(accent("Per generation")),
      "per-node": pad(accent("Per node")),
      "single-page": pad(accent("Single page")),
    },
    watermarkLabel: pad(accent("Watermark")),
    watermark: {
      none: pad(accent("None")),
      "tenant-id": pad(accent("Tenant id")),
      "token-hash": pad(accent("Share token hash")),
      "tenant-id-and-token-hash": pad(accent("Tenant id + share token hash")),
    },
    printAction: pad(accent("Print this view")),
    printHelp: pad(
      accent(
        "Uses the browser's print dialog; the print stylesheet applies page-break, hidden chrome and the watermark placeholder.",
      ),
    ),
    submitAction: pad(accent("Submit export job")),
    refreshAction: pad(accent("Refresh status")),
    resetAction: pad(accent("Reset")),
    statusLabel: pad(accent("Status")),
    statusIdle: pad(accent("Idle")),
    statusQueued: pad(accent("Queued for generation")),
    statusRunning: pad(accent("Generating")),
    statusReady: pad(accent("Ready — download available")),
    statusFailed: pad(accent("Failed")),
    statusExpired: pad(accent("Signed URL expired — refresh to re-issue")),
    obligationsHeading: pad(accent("Redaction obligations (server-issued)")),
    signedUrlOriginLabel: pad(accent("Origin")),
    signedUrlOrigin: {
      minio: pad(accent("Internal MinIO")),
      cdn: pad(accent("External CDN")),
      unknown: pad(accent("Unknown origin")),
    },
    signedUrlRemainingLabel: pad(accent("Time remaining")),
    signedUrlSeconds: pad(accent("{seconds}s")),
    signedUrlExpired: pad(accent("Expired")),
    signedUrlCorrelationLabel: pad(accent("Correlation id")),
    redactionObligationGeneric: pad(accent("{count} field(s) hidden — reason {reasonCode}")),
    redactionObligationReason: {
      LIVING: pad(accent("{count} living-person field(s) hidden")),
      MISSING_CONSENT: pad(accent("{count} field(s) hidden — consent missing")),
      PRIVATE_LEVEL: pad(accent("{count} field(s) hidden — privacy level PRIVATE")),
      DNA: pad(accent("{count} DNA field(s) hidden")),
    },
  },
  footer: {
    rights: pad(accent("All rights reserved.")),
    privacy: pad(accent("Privacy policy")),
    terms: pad(accent("Terms of service")),
  },
} as const satisfies MessageTree;
