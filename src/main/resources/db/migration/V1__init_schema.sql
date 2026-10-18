-- =====================================================================
-- V1__init_schema.sql
-- Grievance Portal - core schema
-- Target: PostgreSQL 18 (uses native uuidv7()). On PG 16/17, generate
-- UUIDv7 in the application instead and drop the DEFAULTs.
-- =====================================================================

-- ---------------------------------------------------------------------
-- ORGANIZATION
-- ---------------------------------------------------------------------
CREATE TABLE ward (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
    -- Phase 2: boundary GEOMETRY(MultiPolygon, 4326) via PostGIS
);

CREATE TABLE department (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    code        VARCHAR(30)  NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Two-level taxonomy: parent_id NULL = category, set = sub-category
CREATE TABLE category (
    id                    UUID PRIMARY KEY DEFAULT uuidv7(),
    code                  VARCHAR(50) NOT NULL UNIQUE,
    parent_id             UUID REFERENCES category(id),
    default_department_id UUID NOT NULL REFERENCES department(id),
    sla_hours             INTEGER NOT NULL CHECK (sla_hours > 0),
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- i18n: names live here, never in enums or code
CREATE TABLE category_translation (
    category_id UUID        NOT NULL REFERENCES category(id),
    locale      VARCHAR(10) NOT NULL,          -- 'en', 'bn', 'hi' ...
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    PRIMARY KEY (category_id, locale)
);

-- Routing override: (category, ward) -> department. Falls back to
-- category.default_department_id when no row exists.
CREATE TABLE routing_rule (
    category_id   UUID NOT NULL REFERENCES category(id),
    ward_id       UUID NOT NULL REFERENCES ward(id),
    department_id UUID NOT NULL REFERENCES department(id),
    PRIMARY KEY (category_id, ward_id)
);

-- ---------------------------------------------------------------------
-- IDENTITY
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    user_type        VARCHAR(20)  NOT NULL CHECK (user_type IN ('CITIZEN','STAFF')),
    phone            VARCHAR(15)  UNIQUE,         -- E.164, citizen identity
    email            VARCHAR(255) UNIQUE,
    password_hash    VARCHAR(255),                -- staff only
    full_name        VARCHAR(150) NOT NULL,
    preferred_locale VARCHAR(10)  NOT NULL DEFAULT 'en',
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                     CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT citizen_needs_phone CHECK (user_type <> 'CITIZEN' OR phone IS NOT NULL),
    CONSTRAINT staff_needs_password CHECK (user_type <> 'STAFF' OR password_hash IS NOT NULL)
);

CREATE TABLE role (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    code        VARCHAR(50)  NOT NULL UNIQUE,     -- SUPER_ADMIN, DEPT_ADMIN, FIELD_OFFICER ...
    description VARCHAR(255)
);

CREATE TABLE permission (
    code        VARCHAR(80) PRIMARY KEY,          -- GRIEVANCE_ASSIGN, GRIEVANCE_RESOLVE ...
    description VARCHAR(255)
);

CREATE TABLE role_permission (
    role_id         UUID        NOT NULL REFERENCES role(id),
    permission_code VARCHAR(80) NOT NULL REFERENCES permission(code),
    PRIMARY KEY (role_id, permission_code)
);

-- Scoped grant: NULL department/ward = unrestricted on that axis.
-- "Field officer for Sanitation in ward 12" = one row.
CREATE TABLE user_role_grant (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id       UUID NOT NULL REFERENCES app_user(id),
    role_id       UUID NOT NULL REFERENCES role(id),
    department_id UUID REFERENCES department(id),
    ward_id       UUID REFERENCES ward(id),
    granted_by    UUID REFERENCES app_user(id),
    granted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_active_grant
    ON user_role_grant (user_id, role_id,
                        COALESCE(department_id, '00000000-0000-0000-0000-000000000000'),
                        COALESCE(ward_id,       '00000000-0000-0000-0000-000000000000'))
    WHERE revoked_at IS NULL;

CREATE TABLE otp_challenge (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    phone       VARCHAR(15)  NOT NULL,
    code_hash   VARCHAR(255) NOT NULL,            -- never store the raw OTP
    purpose     VARCHAR(20)  NOT NULL CHECK (purpose IN ('LOGIN','VERIFY_PHONE')),
    attempts    SMALLINT     NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_otp_phone_recent ON otp_challenge (phone, created_at DESC);

-- ---------------------------------------------------------------------
-- GRIEVANCE
-- ---------------------------------------------------------------------
CREATE SEQUENCE grievance_ticket_seq;

CREATE TABLE grievance (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    ticket_no           VARCHAR(30)  NOT NULL UNIQUE,   -- GRV-2026-000123, shown to citizens
    citizen_id          UUID         NOT NULL REFERENCES app_user(id),
    category_id         UUID         NOT NULL REFERENCES category(id),
    ward_id             UUID         NOT NULL REFERENCES ward(id),
    department_id       UUID         NOT NULL REFERENCES department(id),
    current_assignee_id UUID         REFERENCES app_user(id),
    title               VARCHAR(200) NOT NULL,
    description         TEXT         NOT NULL,
    address_text        VARCHAR(500),
    latitude            NUMERIC(9,6),
    longitude           NUMERIC(9,6),
    status              VARCHAR(20)  NOT NULL CHECK (status IN (
                            'SUBMITTED','ACKNOWLEDGED','ASSIGNED','IN_PROGRESS',
                            'ON_HOLD','RESOLVED','CLOSED','REJECTED','REOPENED')),
    priority            VARCHAR(10)  NOT NULL DEFAULT 'NORMAL'
                        CHECK (priority IN ('LOW','NORMAL','HIGH','CRITICAL')),
    sla_due_at          TIMESTAMPTZ  NOT NULL,
    sla_paused_at       TIMESTAMPTZ,                     -- set while ON_HOLD
    reopen_count        SMALLINT     NOT NULL DEFAULT 0,
    submitted_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    version             BIGINT       NOT NULL DEFAULT 0, -- optimistic locking
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT lat_range CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT lng_range CHECK (longitude BETWEEN -180 AND 180)
);

-- Citizen "my grievances" page
CREATE INDEX idx_grv_citizen   ON grievance (citizen_id, submitted_at DESC);
-- Department queue
CREATE INDEX idx_grv_dept_open ON grievance (department_id, status, sla_due_at)
    WHERE status NOT IN ('CLOSED','REJECTED');
-- Field officer "my work"
CREATE INDEX idx_grv_assignee  ON grievance (current_assignee_id, sla_due_at)
    WHERE status NOT IN ('CLOSED','REJECTED','RESOLVED');
-- SLA breach job
CREATE INDEX idx_grv_sla_due   ON grievance (sla_due_at)
    WHERE status NOT IN ('CLOSED','REJECTED','RESOLVED','ON_HOLD');

-- Full assignment history; grievance.current_assignee_id is the denormalized "now"
CREATE TABLE grievance_assignment (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    grievance_id  UUID NOT NULL REFERENCES grievance(id),
    assignee_id   UUID NOT NULL REFERENCES app_user(id),
    assigned_by   UUID NOT NULL REFERENCES app_user(id),
    reason        VARCHAR(500),
    assigned_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    unassigned_at TIMESTAMPTZ
);
CREATE INDEX idx_assign_grv ON grievance_assignment (grievance_id, assigned_at);

-- Append-only audit of every status change
CREATE TABLE grievance_status_history (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    grievance_id UUID        NOT NULL REFERENCES grievance(id),
    from_status  VARCHAR(20),                        -- NULL on creation
    to_status    VARCHAR(20) NOT NULL,
    actor_id     UUID        REFERENCES app_user(id), -- NULL = system (SLA job)
    reason       VARCHAR(1000),
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_hist_grv ON grievance_status_history (grievance_id, occurred_at);

CREATE TABLE grievance_comment (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    grievance_id  UUID    NOT NULL REFERENCES grievance(id),
    author_id     UUID    NOT NULL REFERENCES app_user(id),
    body          TEXT    NOT NULL,
    internal_only BOOLEAN NOT NULL DEFAULT FALSE,    -- hidden from citizen
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_comment_grv ON grievance_comment (grievance_id, created_at);

CREATE TABLE attachment (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    grievance_id UUID         NOT NULL REFERENCES grievance(id),
    uploaded_by  UUID         NOT NULL REFERENCES app_user(id),
    purpose      VARCHAR(20)  NOT NULL CHECK (purpose IN ('EVIDENCE','RESOLUTION_PROOF')),
    storage_key  VARCHAR(500) NOT NULL UNIQUE,       -- object storage key, not a URL
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL CHECK (size_bytes > 0),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_attach_grv ON attachment (grievance_id);

CREATE TABLE grievance_feedback (
    grievance_id UUID PRIMARY KEY REFERENCES grievance(id),
    rating       SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment      VARCHAR(1000),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- APPEND-ONLY ENFORCEMENT
-- The database refuses edits even if application code has a bug.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION forbid_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_status_history_immutable
    BEFORE UPDATE OR DELETE ON grievance_status_history
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();
