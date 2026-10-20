-- =====================================================================
-- V4__hold_and_nudge_tracking.sql
-- Adds ON_HOLD dependency tracking, citizen nudge scheduling,
-- and the one-reopen constraint.
-- =====================================================================

-- ---------------------------------------------------------------------
-- ON_HOLD dependency tracking
-- ON_HOLD is not a flat state: it records who is blocking and when to chase.
-- ---------------------------------------------------------------------
ALTER TABLE grievance
    ADD COLUMN hold_reason_type VARCHAR(30)
        CHECK (hold_reason_type IN ('AWAITING_CITIZEN','AWAITING_CONTRACTOR',
                                    'AWAITING_DEPARTMENT','AWAITING_BUDGET','OTHER')),
    ADD COLUMN hold_owner_type  VARCHAR(20)
        CHECK (hold_owner_type IN ('CITIZEN','STAFF','EXTERNAL')),
    ADD COLUMN hold_owner_id    UUID REFERENCES app_user(id),
    ADD COLUMN hold_owner_note  VARCHAR(255),   -- external party name, no FK
    ADD COLUMN hold_since       TIMESTAMPTZ,
    ADD COLUMN next_review_at   TIMESTAMPTZ;    -- 7-day ON_HOLD status update

-- Hold fields are set together or not at all
ALTER TABLE grievance
    ADD CONSTRAINT hold_fields_consistent CHECK (
        (status = 'ON_HOLD' AND hold_reason_type IS NOT NULL
                            AND hold_owner_type IS NOT NULL
                            AND hold_since IS NOT NULL)
        OR
        (status <> 'ON_HOLD' AND hold_reason_type IS NULL
                             AND hold_owner_type IS NULL
                             AND hold_since IS NULL)
    );

-- Scheduler: find holds due for their 7-day update
CREATE INDEX idx_grv_hold_review ON grievance (next_review_at)
    WHERE status = 'ON_HOLD';

-- ---------------------------------------------------------------------
-- Citizen nudge cycle
-- Applies to RESOLVED and REJECTED: nudge at day 5, 10, 15, then auto-close.
-- ---------------------------------------------------------------------
ALTER TABLE grievance
    ADD COLUMN nudge_count  SMALLINT NOT NULL DEFAULT 0
        CHECK (nudge_count BETWEEN 0 AND 3),
    ADD COLUMN next_nudge_at TIMESTAMPTZ;

-- Scheduler: find grievances awaiting citizen response whose nudge is due
CREATE INDEX idx_grv_nudge_due ON grievance (next_nudge_at)
    WHERE status IN ('RESOLVED','REJECTED') AND next_nudge_at IS NOT NULL;

-- ---------------------------------------------------------------------
-- One reopen per grievance
-- ---------------------------------------------------------------------
ALTER TABLE grievance
    ADD CONSTRAINT reopen_once CHECK (reopen_count BETWEEN 0 AND 1);

-- ---------------------------------------------------------------------
-- SLA pause accounting
-- When a hold ends, sla_due_at is extended by the paused duration.
-- This column accumulates total paused time for reporting.
-- ---------------------------------------------------------------------
ALTER TABLE grievance
    ADD COLUMN total_paused_seconds BIGINT NOT NULL DEFAULT 0
        CHECK (total_paused_seconds >= 0);

-- ---------------------------------------------------------------------
-- Nudge audit
-- Separate from status history: a nudge is not a status change, but we
-- need to prove the citizen was contacted before auto-closing.
--
-- Delivery receipts are deliberately NOT tracked here. They arrive
-- asynchronously from the gateway and are mutable (queued -> sent ->
-- delivered / failed), which this append-only table cannot express.
-- They belong to the notification module, keyed by provider message id.
-- ---------------------------------------------------------------------
CREATE TABLE grievance_nudge (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    grievance_id UUID        NOT NULL REFERENCES grievance(id),
    nudge_number SMALLINT    NOT NULL CHECK (nudge_number BETWEEN 1 AND 3),
    sent_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    channel      VARCHAR(20) NOT NULL CHECK (channel IN ('SMS','EMAIL','PUSH')),
    UNIQUE (grievance_id, nudge_number, channel)
);
CREATE INDEX idx_nudge_grv ON grievance_nudge (grievance_id);

CREATE TRIGGER trg_nudge_immutable
    BEFORE UPDATE OR DELETE ON grievance_nudge
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();

-- ---------------------------------------------------------------------
-- Permission seed data
-- ---------------------------------------------------------------------
INSERT INTO permission (code, description) VALUES
    ('GRIEVANCE_TRIAGE',         'Acknowledge and categorise new grievances'),
    ('GRIEVANCE_ASSIGN',         'Assign grievances to staff'),
    ('GRIEVANCE_WORK',           'Start work and record resolution'),
    ('GRIEVANCE_HOLD',           'Place on hold and release'),
    ('GRIEVANCE_REJECT',         'Reject a grievance'),
    ('GRIEVANCE_REVERT',         'Revert a resolved grievance to in progress'),
    ('GRIEVANCE_VIEW_INTERNAL',  'View internal-only comments'),
    ('GRIEVANCE_COMMENT',        'Add comments'),
    ('USER_MANAGE',              'Create and manage staff accounts'),
    ('ROLE_GRANT',               'Grant and revoke role assignments'),
    ('ORGANIZATION_MANAGE',      'Manage wards, departments, categories'),
    ('REPORT_VIEW',              'View dashboards and reports')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------
-- Role seed data
-- ---------------------------------------------------------------------
INSERT INTO role (code, description) VALUES
    ('SUPER_ADMIN',   'Municipality-wide administrator'),
    ('DEPT_ADMIN',    'Department administrator'),
    ('FIELD_OFFICER', 'Resolves grievances on the ground'),
    ('AUDITOR',       'Read-only across the municipality')
ON CONFLICT (code) DO NOTHING;

-- SUPER_ADMIN: everything
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code FROM role r CROSS JOIN permission p
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

-- DEPT_ADMIN: everything grievance-related plus reports, within scope
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code FROM role r CROSS JOIN permission p
WHERE r.code = 'DEPT_ADMIN'
  AND p.code IN ('GRIEVANCE_TRIAGE','GRIEVANCE_ASSIGN','GRIEVANCE_WORK',
                 'GRIEVANCE_HOLD','GRIEVANCE_REJECT','GRIEVANCE_REVERT',
                 'GRIEVANCE_VIEW_INTERNAL','GRIEVANCE_COMMENT','REPORT_VIEW')
ON CONFLICT DO NOTHING;

-- FIELD_OFFICER: work and comment only. Cannot reject, hold, or revert.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code FROM role r CROSS JOIN permission p
WHERE r.code = 'FIELD_OFFICER'
  AND p.code IN ('GRIEVANCE_WORK','GRIEVANCE_VIEW_INTERNAL','GRIEVANCE_COMMENT')
ON CONFLICT DO NOTHING;

-- AUDITOR: read only
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code FROM role r CROSS JOIN permission p
WHERE r.code = 'AUDITOR'
  AND p.code IN ('GRIEVANCE_VIEW_INTERNAL','REPORT_VIEW')
ON CONFLICT DO NOTHING;
