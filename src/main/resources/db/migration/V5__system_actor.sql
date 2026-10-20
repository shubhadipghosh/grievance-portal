-- =====================================================================
-- V5__system_actor.sql
-- Introduces a real SYSTEM user so automated transitions (nudge
-- auto-close, escalation) go through the same authorization path as
-- everyone else. No bypass, and every audit row is attributable.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Allow SYSTEM as a user type
-- ---------------------------------------------------------------------
ALTER TABLE app_user DROP CONSTRAINT app_user_user_type_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_user_type_check
    CHECK (user_type IN ('CITIZEN','STAFF','SYSTEM'));

-- The existing CHECKs are already written as "user_type <> X OR ...",
-- so SYSTEM (needing neither phone nor password) satisfies both as-is.
-- No change required to citizen_needs_phone or staff_needs_password.

-- At most one SYSTEM user, ever. Partial unique index on a constant
-- expression is the Postgres idiom for "at most one row matching".
CREATE UNIQUE INDEX uq_single_system_user ON app_user ((true))
    WHERE user_type = 'SYSTEM';

-- ---------------------------------------------------------------------
-- The system actor. Fixed id so application code can reference it as a
-- constant rather than querying for it on every scheduler run.
-- ---------------------------------------------------------------------
INSERT INTO app_user (id, user_type, full_name, status, preferred_locale)
VALUES ('00000000-0000-0000-0000-000000000001', 'SYSTEM', 'System', 'ACTIVE', 'en');

-- ---------------------------------------------------------------------
-- Least privilege: the scheduler gets one permission, not GRIEVANCE_CLOSE.
-- A human closing a grievance and the system auto-closing it after three
-- ignored nudges are different acts and must be separately auditable.
-- ---------------------------------------------------------------------
INSERT INTO permission (code, description) VALUES
    ('GRIEVANCE_CLOSE',      'Close a resolved or rejected grievance'),
    ('GRIEVANCE_CLOSE_AUTO', 'Auto-close after the citizen nudge cycle expires')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role (code, description)
VALUES ('SYSTEM', 'Automated actions only: nudge auto-close')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'GRIEVANCE_CLOSE_AUTO' FROM role r WHERE r.code = 'SYSTEM'
ON CONFLICT DO NOTHING;

-- Staff who close grievances manually need the human permission.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'GRIEVANCE_CLOSE' FROM role r
WHERE r.code IN ('SUPER_ADMIN','DEPT_ADMIN')
ON CONFLICT DO NOTHING;

-- Unscoped grant: the scheduler operates municipality-wide.
INSERT INTO user_role_grant (user_id, role_id, department_id, ward_id, granted_by)
SELECT '00000000-0000-0000-0000-000000000001', r.id, NULL, NULL, NULL
FROM role r WHERE r.code = 'SYSTEM';

-- ---------------------------------------------------------------------
-- Every status change is now attributable. The "NULL = system" case in
-- V1 is gone: the system is a real user with a real id.
-- ---------------------------------------------------------------------
ALTER TABLE grievance_status_history
    ALTER COLUMN actor_id SET NOT NULL;
