-- E4.3 — Date / Calendar / Place model (R4.1 / R4.4 / R10 / NFR4).
--
-- Schema-per-service per ADR-E0.5-02. RLS defence-in-depth per
-- `design.md` §5.1.
--
-- The `date_value` table is the persistence contract for the
-- `DateValue` record. Every date on a Person (E4.2) or Event
-- (E4.5) is stored as a row keyed by a synthetic id; the
-- parent aggregate stores the id back-reference. This keeps the
-- schema flat while preserving the four orthogonal fields
-- (original expression, calendar + qualifier + timezone,
-- normalized interval, certainty) per `design.md` §5.3.
--
-- The `place` table is the persistence contract for the `Place`
-- aggregate. Places are tenant-scoped (a Vietnamese hamlet and
-- a French commune may have the same display name; tenant
-- isolation is the platform's only multi-tenancy boundary per
-- ADR-E0.5-02). Hierarchy is stored as TEXT[] on the row;
-- deeper chains are rejected at the application layer.

CREATE TABLE tree_service.date_value (
    date_value_id      UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    original_expression TEXT NOT NULL
                         CHECK (length(original_expression) BETWEEN 1 AND 512),
    calendar           VARCHAR(32) NOT NULL CHECK (calendar IN (
                            'GREGORIAN','JAPANESE','VIETNAMESE_LUNISOLAR',
                            'KOREAN','CHINESE_LUNISOLAR','ISLAMIC_CIVIL',
                            'HEBREW','FRENCH_REPUBLICAN')),
    qualifier          VARCHAR(16) NOT NULL CHECK (qualifier IN (
                            'EXACT','ABOUT','BEFORE','AFTER','BETWEEN','UNKNOWN')),
    timezone           VARCHAR(64) NOT NULL,
    earliest_utc       TIMESTAMPTZ,
    latest_utc         TIMESTAMPTZ,
    certainty          VARCHAR(16) NOT NULL CHECK (certainty IN (
                            'HYPOTHESIS','ASSERTED','VERIFIED','DISPUTED')),
    recorded_at        TIMESTAMPTZ NOT NULL,
    CHECK (earliest_utc IS NOT NULL OR latest_utc IS NOT NULL),
    CHECK (earliest_utc IS NULL OR latest_utc IS NULL OR
           earliest_utc <= latest_utc)
);

CREATE INDEX date_value_tenant_idx ON tree_service.date_value (tenant_id);
CREATE INDEX date_value_calendar_idx ON tree_service.date_value (tenant_id, calendar);
CREATE INDEX date_value_earliest_idx ON tree_service.date_value (tenant_id, earliest_utc);

CREATE TABLE tree_service.place (
    place_id       UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    kind           VARCHAR(32) NOT NULL CHECK (kind IN (
                       'COUNTRY','REGION','LOCALITY','STREET','BUILDING',
                       'CEMETERY','RELIGIOUS_SITE','HOSPITAL','UNKNOWN')),
    display_name   TEXT NOT NULL,
    locale_tag     VARCHAR(35) NOT NULL,
    latitude       NUMERIC(9, 6),
    longitude      NUMERIC(9, 6),
    coordinate_datum VARCHAR(16) CHECK (coordinate_datum IS NULL
                          OR coordinate_datum IN ('WGS84')),
    authority_kind VARCHAR(32) CHECK (authority_kind IS NULL OR authority_kind IN (
                          'WIKIDATA','GEONAMES','NATIONAL_GAZETTEER','LOCAL')),
    authority_id   VARCHAR(128),
    hierarchy      TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    certainty      VARCHAR(16) NOT NULL CHECK (certainty IN (
                       'HYPOTHESIS','ASSERTED','VERIFIED','DISPUTED')),
    version        BIGINT NOT NULL CHECK (version >= 1),
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    created_by     UUID NOT NULL,
    CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL)
    ),
    CHECK (
        latitude IS NULL OR (latitude >= -90 AND latitude <= 90)
    ),
    CHECK (
        longitude IS NULL OR (longitude >= -180 AND longitude <= 180)
    ),
    CHECK (
        (authority_kind IS NULL AND authority_id IS NULL)
        OR (authority_kind IS NOT NULL AND authority_id IS NOT NULL)
    ),
    CHECK (length(display_name) BETWEEN 1 AND 512),
    CHECK (array_length(hierarchy, 1) IS NULL
           OR array_length(hierarchy, 1) <= 8)
);

CREATE INDEX place_tenant_idx ON tree_service.place (tenant_id);
CREATE INDEX place_tenant_kind_idx ON tree_service.place (tenant_id, kind);
CREATE INDEX place_authority_idx ON tree_service.place (tenant_id, authority_kind, authority_id);
CREATE INDEX place_display_name_idx ON tree_service.place (tenant_id, display_name);

-- Tenant isolation — RLS per `design.md` §5.1.
ALTER TABLE tree_service.date_value ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.date_value FORCE ROW LEVEL SECURITY;
CREATE POLICY date_value_tenant_isolation ON tree_service.date_value
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE tree_service.place ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.place FORCE ROW LEVEL SECURITY;
CREATE POLICY place_tenant_isolation ON tree_service.place
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Application role scope per ADR-E0.5-02.
GRANT SELECT, INSERT, UPDATE ON tree_service.date_value TO tree_service_app;
GRANT SELECT, INSERT, UPDATE ON tree_service.place TO tree_service_app;
