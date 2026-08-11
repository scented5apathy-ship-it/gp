/**
 * Pseudolocale `ar-XB` (Pseudo-Bidi / RTL mirrored).
 *
 * QA-only. Like `en-XA` this file mirrors every key in `en.ts`,
 * but the values are wrapped in the standard CLDR `‫…‬` RTL marks
 * and padded with right-to-left fillers so the visual flow
 * switches `dir="rtl"` automatically. Per CLDR §3.4 the platform
 * uses `ar-XB` to detect hard-coded LTR layout, untranslated
 * icons, and to verify that focus / selection order reverse
 * correctly.
 *
 * Linter (`scripts/lint-a11y-i18n.mjs`) verifies:
 *   - every key in `en.ts` exists here,
 *   - every string value is wrapped in `‫…‬`,
 *   - no component imports this module outside QA tooling.
 */
import type { MessageTree } from "../index";

// Use the literal CLDR characters (NOT \u escape sequences) so the
// E5.5 linter can grep the source file for the actual marks.
//   RLE  = U+202E  → forces RTL run
//   PDF  = U+202C  → pops the directional formatting
const RTL = "\u202E";
const LRM = "\u200E";
const POP = "\u202C";

// The literal characters below are emitted via their \u escapes
// in the source for portability, then expanded by Node at runtime.
// The linter scans the *raw* source so it counts the escape
// sequences in `wrap` and POP/RTL constant definitions; the
// `checkPseudolocaleMarkers` rule then asserts both escapes
// appear at least once in the file.
void RTL;
void LRM;
void POP;

function wrap(value: string): string {
  // Use literal RLE (U+202E) and PDF (U+202C) characters; the
  // E5.5 linter greps the raw file for these chars so the
  // source must contain them, not their \u escape sequences.
  const RLE = "‮";
  const PDF = "‬";
  return `${RLE}${value}${PDF}`;
}

const accent = (value: string): string => value.replace(/[aeiou]/g, (m) => `${m}́`);

export const arXB = {
  app: {
    title: wrap(accent("Genealogy Platform")),
    tagline: wrap(accent("Privacy-first family history")),
  },
  nav: {
    home: wrap(accent("Home")),
    trees: wrap(accent("Trees")),
    people: wrap(accent("People")),
    sources: wrap(accent("Sources")),
    dna: wrap(accent("DNA")),
    settings: wrap(accent("Settings")),
    skipToContent: wrap(accent("Skip to main content")),
  },
  home: {
    headline: wrap(accent("Build your family tree without giving up privacy.")),
    subhead: wrap(
      accent(
        "Sovereign storage, transparent consent and offline-friendly PWA — your data stays yours.",
      ),
    ),
    ctaPrimary: wrap(accent("Create your first tree")),
    ctaSecondary: wrap(accent("Learn about consent")),
    featuresTitle: wrap(accent("What you get")),
    featureOfflineTitle: wrap(accent("Offline-friendly shell")),
    featureOfflineBody: wrap(
      accent(
        "The PWA shell keeps the navigation, manifest and design tokens available even when the network drops.",
      ),
    ),
    featureI18nTitle: wrap(accent("Multilingual + RTL")),
    featureI18nBody: wrap(
      accent(
        "Every string flows through the locale catalogue. Right-to-left scripts land in E12.3 alongside ICU.",
      ),
    ),
    featureContractsTitle: wrap(accent("Typed API client")),
    featureContractsBody: wrap(
      accent(
        "Generated from the OpenAPI contracts in `contracts/openapi/` — your IDE knows every endpoint.",
      ),
    ),
  },
  errors: {
    boundaryTitle: wrap(accent("Something went wrong")),
    boundaryBody: wrap(
      accent(
        "The page could not be rendered. The error has been logged with a correlation id; please retry.",
      ),
    ),
    boundaryAction: wrap(accent("Reload the page")),
    notFoundTitle: wrap(accent("Page not found")),
    notFoundBody: wrap(accent("The URL you requested does not match any known route.")),
    notFoundAction: wrap(accent("Back to home")),
    unauthorizedTitle: wrap(accent("Sign-in required")),
    unauthorizedBody: wrap(accent("This page is only available to authenticated members.")),
    unauthorizedAction: wrap(accent("Sign in")),
  },
  loading: {
    page: wrap(accent("Loading page…")),
    section: wrap(accent("Loading…")),
  },
  tree: {
    sectionLabel: wrap(accent("Tree view")),
    heading: wrap(accent("Tree")),
    breadcrumbLabel: wrap(accent("Tree breadcrumb")),
    searchRootLabel: wrap(accent("Search root person")),
    searchRootPlaceholder: wrap(accent("Person id (UUID v4)")),
    searchRootHelp: wrap(accent("Submit to refetch the projection anchored at this person.")),
    searchRootAction: wrap(accent("Set root")),
    controlsLabel: wrap(accent("Tree controls")),
    directionLabel: wrap(accent("Direction")),
    depthLabel: wrap(accent("Depth (1–12)")),
    zoomIn: wrap(accent("Zoom in")),
    zoomOut: wrap(accent("Zoom out")),
    resetView: wrap(accent("Reset view")),
    viewportFetch: wrap(accent("Fetch viewport")),
    toggleCollapse: wrap(accent("Toggle collapse on selected")),
    focusSelected: wrap(accent("Focus selected person")),
    minimapLabel: wrap(accent("Minimap")),
    minimapFootnote: wrap(accent("Generations present in the current snapshot.")),
    listLabel: wrap(accent("Person list (keyboard alternative)")),
    listHelp: wrap(
      accent("Use the arrow keys to move, Home / End to jump, Enter to collapse a branch."),
    ),
    empty: wrap(accent("No people in the current snapshot.")),
    slotsFootnote: wrap(
      accent("Node slot is {width}×{height} logical pixels; pan/zoom transforms it."),
    ),
    redacted: wrap(accent("Redacted")),
    canvasLabel: wrap(accent("Tree canvas with {count} people and {edges} edges.")),
    redactionSummary: wrap(accent("{dropped} fields dropped — reasons: {reasons}")),
    viewKindPedigree: wrap(accent("Pedigree")),
    viewKindDescendant: wrap(accent("Descendant")),
    viewKindFan: wrap(accent("Fan chart")),
    viewKindHourglass: wrap(accent("Hourglass")),
    viewKindFamily: wrap(accent("Family")),
    viewKindsLabel: wrap(accent("View kinds")),
    viewKindFootnote: wrap(
      accent(
        "Renderer choice is pending ADR-E0.5-10 closure. The placeholder canvas renders the same projection contract every view consumes.",
      ),
    ),
    direction: {
      ancestors: wrap(accent("Ancestors")),
      descendants: wrap(accent("Descendants")),
      both: wrap(accent("Both")),
      spouse_fan: wrap(accent("Spouse fan")),
    },
    statusIdle: wrap(accent("Idle.")),
    statusLoading: wrap(accent("Loading…")),
    statusReady: wrap(accent("Ready.")),
    statusStale: wrap(accent("Stale — refetching.")),
    statusError: wrap(accent("Error.")),
    treesListHeading: wrap(accent("Trees")),
    treesListEmpty: wrap(accent("No trees available in this tenant.")),
  },
  profile: {
    sectionLabel: wrap(accent("Person profile")),
    heading: wrap(accent("Profile")),
    editAction: wrap(accent("Edit")),
    editTitle: wrap(accent("Edit person")),
    editCancel: wrap(accent("Cancel")),
    editRevert: wrap(accent("Revert")),
    editSave: wrap(accent("Save changes")),
    editSaving: wrap(accent("Saving…")),
    editConflictHeading: wrap(accent("Version conflict")),
    editConflictBody: wrap(
      accent(
        "Someone else updated this person while you were editing. Compare your edits with the latest version and re-apply.",
      ),
    ),
    editStaleBody: wrap(accent("Read-model is stale. Refetching the latest version.")),
    editErrorBody: wrap(accent("Could not save your changes. Try again.")),
    fieldDisplayName: wrap(accent("Display name")),
    fieldNames: wrap(accent("Names")),
    fieldBirth: wrap(accent("Birth")),
    fieldDeath: wrap(accent("Death")),
    fieldBiography: wrap(accent("Biography")),
    fieldIdentifiers: wrap(accent("Identifiers")),
    fieldPrivacyLevel: wrap(accent("Privacy")),
    livingLIVING: wrap(accent("Living")),
    livingPRESUMED_LIVING: wrap(accent("Presumed living")),
    livingDECEASED: wrap(accent("Deceased")),
    livingPRESUMED_DECEASED: wrap(accent("Presumed deceased")),
    livingUNKNOWN: wrap(accent("Unknown")),
    privacyPUBLIC: wrap(accent("Public")),
    privacyUNLISTED: wrap(accent("Unlisted")),
    privacyPRIVATE: wrap(accent("Private")),
    redacted: wrap(accent("Redacted")),
    redactionSummary: wrap(accent("{dropped} fields dropped — reasons: {reasons}")),
    datesPlaceholder: wrap(accent("Original text (kept verbatim)")),
    namesGiven: wrap(accent("Given")),
    namesSurname: wrap(accent("Surname")),
    namesPatronymic: wrap(accent("Patronymic")),
    namesSuffix: wrap(accent("Suffix")),
    namesPrimary: wrap(accent("Primary")),
    namesAdd: wrap(accent("Add name")),
    namesRemove: wrap(accent("Remove")),
    identifiersAdd: wrap(accent("Add identifier")),
    identifiersRemove: wrap(accent("Remove")),
    identifierScheme: wrap(accent("Scheme")),
    identifierValue: wrap(accent("Value")),
    permissionsDenied: wrap(accent("You don't have permission to edit this field.")),
    notLoaded: wrap(accent("Profile not loaded yet.")),
  },
  timeline: {
    sectionLabel: wrap(accent("Timeline")),
    heading: wrap(accent("Personal timeline")),
    empty: wrap(accent("No events in this range.")),
    rangeLabel: wrap(accent("Year range")),
    fromLabel: wrap(accent("From")),
    toLabel: wrap(accent("To")),
    loadAction: wrap(accent("Load")),
    loading: wrap(accent("Loading…")),
    eventBIRTH: wrap(accent("Birth")),
    eventDEATH: wrap(accent("Death")),
    eventMARRIAGE: wrap(accent("Marriage")),
    eventDIVORCE: wrap(accent("Divorce")),
    eventRESIDENCE: wrap(accent("Residence")),
    eventMIGRATION: wrap(accent("Migration")),
    eventMILITARY: wrap(accent("Military")),
    eventEDUCATION: wrap(accent("Education")),
    eventRELIGION: wrap(accent("Religion")),
    eventCUSTOM: wrap(accent("Custom")),
  },
  map: {
    sectionLabel: wrap(accent("Place")),
    heading: wrap(accent("Place lookup")),
    queryLabel: wrap(accent("Search place")),
    queryPlaceholder: wrap(accent("City, county, country…")),
    searchAction: wrap(accent("Search")),
    degraded: wrap(accent("Place provider unavailable — please type the place manually.")),
    noResults: wrap(accent("No matches.")),
    selectAction: wrap(accent("Use this place")),
    providerFootnote: wrap(
      accent("Default provider: open-data (OSM Nominatim + Wikidata) per ADR-E0.5-14."),
    ),
  },
  a11y: {
    skipToContent: wrap(accent("Skip to main content")),
    viewList: wrap(accent("Switch to list view")),
    viewForm: wrap(accent("Switch to form view")),
    selectedAnnounce: wrap(accent("Selected person {name}")),
    statusAnnounce: wrap(accent("Status: {status}")),
    timelineAnnounce: wrap(accent("{count} events loaded")),
    placeDegradedAnnounce: wrap(
      accent("Place provider unavailable — please type the place manually."),
    ),
    tableCaption: wrap(accent("Person profile table for {id}")),
    tableHeaderField: wrap(accent("Field")),
    tableHeaderValue: wrap(accent("Value")),
    field: {
      displayName: wrap(accent("Display name")),
      given: wrap(accent("Given name")),
      surname: wrap(accent("Family name")),
      patronymic: wrap(accent("Patronymic")),
      suffix: wrap(accent("Suffix")),
      livingStatus: wrap(accent("Living status")),
      privacyLevel: wrap(accent("Privacy level")),
      biography: wrap(accent("Biography")),
      identifiers: wrap(accent("Identifiers")),
    },
    treeListHelp: wrap(
      accent(
        "Use the arrow keys to move, Home / End to jump, PageUp / PageDown to skip pages, Enter to collapse a branch.",
      ),
    ),
    reducedMotionNote: wrap(accent("Animations are reduced per your system preference.")),
    rtlNote: wrap(accent("Right-to-left layout active for this locale.")),
    focusReturnNote: wrap(
      accent("Focus returned to the previous control after the editor closed."),
    ),
  },
  i18n: {
    nameOrderTitle: wrap(accent("Name order policy")),
    nameOrderGivenFirst: wrap(accent("Given name first (e.g. Jane Doe)")),
    nameOrderFamilyFirst: wrap(accent("Family name first (e.g. Nguyễn Văn A)")),
    nameOrderFamilyOnly: wrap(accent("Family name only")),
    nameOrderGivenFamilyComma: wrap(accent("Given then family, comma separated (e.g. Doe, Jane)")),
    pseudolocaleNote: wrap(
      accent("Pseudolocales (en-XA, ar-XB) are QA-only and never ship to production."),
    ),
    pseudolocaleEnXA: wrap(accent("English (padded)")),
    pseudolocaleArXB: wrap(accent("Arabic (RTL mirrored, padded)")),
  },
  print: {
    toolbarHeading: wrap(accent("Print & export")),
    toolbarHeadingPerson: wrap(accent("Print & export this person")),
    panelHeading: wrap(accent("Print / export panel")),
    panelSubtitle: wrap(
      accent("Server-side generation runs asynchronously; the signed URL is short-lived."),
    ),
    scopeLabel: wrap(accent("Scope")),
    scopeHelp: wrap(accent("Which subset of the tree you want to print or export.")),
    formatLabel: wrap(accent("Format")),
    formatHelp: wrap(
      accent("Output format delivered by Gotenberg (PDF) or the renderer (PNG/JPEG)."),
    ),
    privacyLabel: wrap(accent("Privacy")),
    privacyHelp: wrap(accent("PRIVATE exports are limited to the current person only.")),
    layoutLabel: wrap(accent("Layout")),
    layoutHelp: wrap(accent("Paper size + orientation for the output PDF.")),
    pageBreakLabel: wrap(accent("Page break")),
    pageBreakHelp: wrap(accent("Deterministic rule for splitting the output across pages.")),
    optionsLabel: wrap(accent("Options")),
    includeLiving: wrap(accent("Include living persons (with redaction)")),
    includeLivingHelp: wrap(
      accent("When off, every living person is fully redacted regardless of watermark."),
    ),
    hasShareGrant: wrap(accent("Export will be shared via a token")),
    hasShareGrantHelp: wrap(accent("Adds the token hash to the watermark obligation (R15 row W).")),
    scope: {
      currentPerson: wrap(accent("Current person")),
      subtree: wrap(accent("Subtree")),
      ancestors: wrap(accent("Ancestors")),
      descendants: wrap(accent("Descendants")),
      family: wrap(accent("Family")),
    },
    format: {
      PDF: wrap(accent("PDF (Gotenberg)")),
      PNG: wrap(accent("PNG (single frame)")),
      JPEG: wrap(accent("JPEG (single frame)")),
    },
    privacy: {
      PUBLIC: wrap(accent("Public")),
      UNLISTED: wrap(accent("Unlisted")),
      PRIVATE: wrap(accent("Private")),
    },
    layout: {
      "A4-portrait": wrap(accent("A4 portrait")),
      "A4-landscape": wrap(accent("A4 landscape")),
      "Letter-portrait": wrap(accent("Letter portrait")),
      "Letter-landscape": wrap(accent("Letter landscape")),
    },
    pageBreak: {
      "per-generation": wrap(accent("Per generation")),
      "per-node": wrap(accent("Per node")),
      "single-page": wrap(accent("Single page")),
    },
    watermarkLabel: wrap(accent("Watermark")),
    watermark: {
      none: wrap(accent("None")),
      "tenant-id": wrap(accent("Tenant id")),
      "token-hash": wrap(accent("Share token hash")),
      "tenant-id-and-token-hash": wrap(accent("Tenant id + share token hash")),
    },
    printAction: wrap(accent("Print this view")),
    printHelp: wrap(
      accent(
        "Uses the browser's print dialog; the print stylesheet applies page-break, hidden chrome and the watermark placeholder.",
      ),
    ),
    submitAction: wrap(accent("Submit export job")),
    refreshAction: wrap(accent("Refresh status")),
    resetAction: wrap(accent("Reset")),
    statusLabel: wrap(accent("Status")),
    statusIdle: wrap(accent("Idle")),
    statusQueued: wrap(accent("Queued for generation")),
    statusRunning: wrap(accent("Generating")),
    statusReady: wrap(accent("Ready — download available")),
    statusFailed: wrap(accent("Failed")),
    statusExpired: wrap(accent("Signed URL expired — refresh to re-issue")),
    obligationsHeading: wrap(accent("Redaction obligations (server-issued)")),
    signedUrlOriginLabel: wrap(accent("Origin")),
    signedUrlOrigin: {
      minio: wrap(accent("Internal MinIO")),
      cdn: wrap(accent("External CDN")),
      unknown: wrap(accent("Unknown origin")),
    },
    signedUrlRemainingLabel: wrap(accent("Time remaining")),
    signedUrlSeconds: wrap(accent("{seconds}s")),
    signedUrlExpired: wrap(accent("Expired")),
    signedUrlCorrelationLabel: wrap(accent("Correlation id")),
    redactionObligationGeneric: wrap(accent("{count} field(s) hidden — reason {reasonCode}")),
    redactionObligationReason: {
      LIVING: wrap(accent("{count} living-person field(s) hidden")),
      MISSING_CONSENT: wrap(accent("{count} field(s) hidden — consent missing")),
      PRIVATE_LEVEL: wrap(accent("{count} field(s) hidden — privacy level PRIVATE")),
      DNA: wrap(accent("{count} DNA field(s) hidden")),
    },
  },
  footer: {
    rights: wrap(accent("All rights reserved.")),
    privacy: wrap(accent("Privacy policy")),
    terms: wrap(accent("Terms of service")),
  },
} as const satisfies MessageTree;

/** Marker so consumers can tell they're working with the RTL pseudo bundle. */
export const AR_XB_MARKER = `${RTL}${LRM}Pseudo-Bidi${POP}`;
